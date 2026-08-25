/*******************************************************************************
 * Copyright (c) 2006, 2009 Wind River Systems, Inc., IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Anton Leherbauer (Wind River Systems) - initial API and implementation - https://bugs.eclipse.org/bugs/show_bug.cgi?id=22712
 *     Anton Leherbauer (Wind River Systems) - [painting] Long lines take too long to display when "Show Whitespace Characters" is enabled - https://bugs.eclipse.org/bugs/show_bug.cgi?id=196116
 *     Anton Leherbauer (Wind River Systems) - [painting] Whitespace characters not drawn when scrolling to right slowly - https://bugs.eclipse.org/bugs/show_bug.cgi?id=206633
 *     Tom Eicher (Avaloq Evolution AG) - block selection mode
 *     Simone Platania - per level colors, active block highlighting, irregular indentation
 *******************************************************************************/
package jp.sourceforge.pdt_tools.indentguide;

import jp.sourceforge.pdt_tools.indentguide.preferences.IndentGuideSettings;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IPaintPositionManager;
import org.eclipse.jface.text.IPainter;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextContent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/**
 * A painter for drawing visible characters for (invisible) whitespace
 * characters.
 *
 * @since 3.3
 * @see org.eclipse.jface.text.WhitespaceCharacterPainter
 */
public class IndentGuidePainter implements IPainter, PaintListener {

	/** The number of spaces measured at once to get a fractional column width. */
	private static final int PROBE_WIDTH = 80;

	/** How far from a guide, in pixels, a click still counts as hitting it. */
	private static final int HOVER_TOLERANCE = 4;

	/** Mirrors the depth the level calculator memoizes. */
	private static final int MAX_LEVELS = 64;

	/** Indicates whether this painter is active. */
	private boolean fIsActive = false;
	/** The source viewer this painter is attached to. */
	private ITextViewer fTextViewer;
	/** The viewer's widget. */
	private StyledText fTextWidget;
	/** Tells whether the advanced graphics sub system is available. */
	private final boolean fIsAdvancedGraphicsPresent;

	/** The preferences, refreshed on every change notification. */
	private final IndentGuideSettings settings = new IndentGuideSettings();
	private IPropertyChangeListener prefListener;

	private final IndentLevelCalculator calculator;

	private int spaceWidth;
	/**
	 * The real width of a column, in pixels, fraction included.
	 * <code>getAdvanceWidth()</code> rounds to the integer: with DPI scaling the
	 * true width is fractional, and multiplying it by the column number
	 * accumulates the error, drifting the guides to the right on the deeper
	 * levels.
	 */
	private double columnWidth;

	/** One color per indentation level, cycled past the last one. */
	private Color[] palette;
	/** The same tints, lightened, used for the guide of the active block. */
	private Color[] paletteBright;
	private Color irregularColor;
	/** A separate palette whose index is the round-parenthesis nesting depth. */
	private Color[] parenthesisPalette;

	/** The guide of the block holding the caret: column and lines covered. */
	private int activeColumn = -1;
	private int activeStart = -1;
	private int activeEnd = -1;

	/**
	 * Temporary result of the click hit test. It is cleared before repainting,
	 * so merely moving the pointer never changes the guide appearance.
	 */
	private int hoverColumn = -1;
	private int hoverStart = -1;
	private int hoverEnd = -1;

	/**
	 * The guide pinned by a click on it. While set it wins over the caret, so
	 * that the block stays lit while reading and typing elsewhere; a click that
	 * lands on no guide releases it.
	 */
	private int pinnedColumn = -1;
	private int pinnedLine = -1;

	/**
	 * Per level, the instant the opacity burst of a newly greyed guide ends,
	 * and whether that level was already known to be irregular. A level is only
	 * flashed on the pass that first draws it grey.
	 */
	private final long[] flashEnd = new long[MAX_LEVELS];
	private final boolean[] irregularKnown = new boolean[MAX_LEVELS];
	/** Scroll position the irregular transition state belongs to. */
	private int irregularTopIndex = -1;
	/** The deadline the booked flash timer covers, 0 when none is booked. */
	private long flashTimerFor;

	private CaretListener caretListener;
	private MouseListener mouseListener;

	/**
	 * Creates a new painter for the given text viewer.
	 *
	 * @param textViewer
	 *            the text viewer the painter should be attached to
	 */
	public IndentGuidePainter(ITextViewer textViewer) {
		super();
		fTextViewer = textViewer;
		fTextWidget = textViewer.getTextWidget();
		GC gc = new GC(fTextWidget);
		gc.setAdvanced(true);
		fIsAdvancedGraphicsPresent = gc.getAdvanced();
		gc.dispose();

		calculator = new IndentLevelCalculator(
				new IndentLevelCalculator.ILineSource() {

					public int getLineCount() {
						return fTextWidget.getLineCount();
					}

					public int getCharCount() {
						return fTextWidget.getCharCount();
					}

					public String getLine(int line) {
						return fTextWidget.getLine(line);
					}
				});

		loadSettings();
		installPreferenceListener();
	}

	private void loadSettings() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		settings.load(store);
		disposeColors();
		createColors();
	}

	private void createColors() {
		Display display = fTextWidget.getDisplay();
		RGB[] rgbs = settings.getPalette();
		palette = new Color[rgbs.length];
		paletteBright = new Color[rgbs.length];
		for (int i = 0; i < rgbs.length; i++) {
			palette[i] = new Color(display, rgbs[i]);
			paletteBright[i] = new Color(display, settings.lighten(rgbs[i]));
		}
		irregularColor = new Color(display, settings.getIrregularColor());
		RGB[] parentheses = settings.getParenthesisPalette();
		parenthesisPalette = new Color[parentheses.length];
		for (int i = 0; i < parentheses.length; i++) {
			parenthesisPalette[i] = new Color(display, parentheses[i]);
		}
	}

	private void disposeColors() {
		if (irregularColor != null && !irregularColor.isDisposed()) {
			irregularColor.dispose();
		}
		irregularColor = null;
		paletteBright = dispose(paletteBright);
		palette = dispose(palette);
		parenthesisPalette = dispose(parenthesisPalette);
	}

	private static Color[] dispose(Color[] colors) {
		if (colors != null) {
			for (int i = 0; i < colors.length; i++) {
				if (colors[i] != null && !colors[i].isDisposed()) {
					colors[i].dispose();
				}
			}
		}
		return null;
	}

	private void installPreferenceListener() {
		if (prefListener != null) {
			return;
		}
		prefListener = new IPropertyChangeListener() {

			public void propertyChange(PropertyChangeEvent event) {
				if (fTextWidget == null || fTextWidget.isDisposed()) {
					return;
				}
				fTextWidget.getDisplay().asyncExec(new Runnable() {

					public void run() {
						if (fTextWidget == null || fTextWidget.isDisposed()) {
							return;
						}
						loadSettings();
						redrawAll();
					}
				});
			}
		};
		Activator.getDefault().getPreferenceStore()
				.addPropertyChangeListener(prefListener);
	}

	private void removePreferenceListener() {
		if (prefListener != null) {
			try {
				Activator.getDefault().getPreferenceStore()
						.removePropertyChangeListener(prefListener);
			} catch (Exception e) {
				// the bundle may already be stopped on shutdown
			}
			prefListener = null;
		}
	}

	/**
	 * The paint manager does not guarantee a call to {@link #paint(int)} for a
	 * plain caret move, so the caret is listened to directly.
	 */
	private void installCaretListener() {
		if (caretListener != null || fTextWidget == null) {
			return;
		}
		caretListener = new CaretListener() {

			public void caretMoved(CaretEvent event) {
				if (fTextWidget == null || fTextWidget.isDisposed()) {
					return;
				}
				if (updateActiveBlock()) {
					redrawAll();
				}
			}
		};
		fTextWidget.addCaretListener(caretListener);
	}

	private void removeCaretListener() {
		if (caretListener != null && fTextWidget != null
				&& !fTextWidget.isDisposed()) {
			fTextWidget.removeCaretListener(caretListener);
		}
		caretListener = null;
	}

	/**
	 * Clicking a guide pins its highlight. Nothing here consumes the event, so
	 * the editor keeps its normal text cursor and places the caret as usual.
	 */
	private void installMouseListeners() {
		if (mouseListener != null || fTextWidget == null) {
			return;
		}
		mouseListener = new MouseAdapter() {

			public void mouseDown(MouseEvent e) {
				if (e.button != 1 || fTextWidget == null
						|| fTextWidget.isDisposed()) {
					return;
				}
				updateHover(e.x, e.y);
				if (hoverColumn >= 0) {
					pinnedColumn = hoverColumn;
					pinnedLine = lineAt(e.y);
				} else {
					clearPin();
				}
				hoverColumn = -1;
				hoverStart = -1;
				hoverEnd = -1;
				updateActiveBlock();
				redrawAll();
			}
		};
		fTextWidget.addMouseListener(mouseListener);
	}

	private void removeMouseListeners() {
		if (fTextWidget != null && !fTextWidget.isDisposed()) {
			if (mouseListener != null) {
				fTextWidget.removeMouseListener(mouseListener);
			}
			fTextWidget.setCursor(null);
		}
		mouseListener = null;
	}

	private void clearPin() {
		pinnedColumn = -1;
		pinnedLine = -1;
	}

	private int lineAt(int y) {
		int line = fTextWidget.getLineIndex(y);
		int count = fTextWidget.getLineCount();
		if (line < 0) {
			return 0;
		}
		return line >= count ? count - 1 : line;
	}

	/**
	 * Locates the guide hit by a click: the nearest one within
	 * {@link #HOVER_TOLERANCE} pixels on the line under the pointer.
	 *
	 * @param x
	 *            pointer position in widget coordinates
	 * @param y
	 *            pointer position in widget coordinates
	 * @return <code>true</code> if the hit-test result has changed
	 */
	private boolean updateHover(int x, int y) {
		int col = -1, start = -1, end = -1;
		// columnWidth is only known once the widget has been painted at least
		// once; before that there is nothing on screen to click anyway.
		if (settings.isActiveEnabled() && columnWidth > 0) {
			try {
				int tabs = tabWidth();
				calculator.setTabWidth(tabs);
				calculator.refresh();
				int line = lineAt(y);
				int offset = fTextWidget.getOffsetAtLine(line);
				int count = calculator.effectiveIndent(line);
				int best = HOVER_TOLERANCE + 1;
				int base = calculator.outermostParenthesisGuide(line);
				for (int i = settings.isDrawLeftEnd() ? 0 : tabs; i < count; i += tabs) {
					if (base >= 0 && i > base
							&& !calculator.isParenthesisGuide(i, line)) {
						continue;
					}
					int distance = Math.abs(x - guideX(offset, i));
					if (distance < best) {
						best = distance;
						col = i;
					}
				}
				if (col >= 0) {
					start = blockStart(col, line);
					end = blockEnd(col, line);
				}
			} catch (Exception e) {
				col = -1;
				start = -1;
				end = -1;
			}
		}
		boolean changed = col != hoverColumn || start != hoverStart
				|| end != hoverEnd;
		hoverColumn = col;
		hoverStart = start;
		hoverEnd = end;
		return changed;
	}

	private int blockStart(int col, int line) {
		int start = line;
		while (start - 1 >= 0 && calculator.effectiveIndent(start - 1) > col) {
			start--;
		}
		return start;
	}

	private int blockEnd(int col, int line) {
		int end = line;
		int lineCount = fTextWidget.getLineCount();
		while (end + 1 < lineCount
				&& calculator.effectiveIndent(end + 1) > col) {
			end++;
		}
		return end;
	}

	/**
	 * Locates the active guide. A guide pinned by a click wins; otherwise it is
	 * the guide of the block the caret sits in, extended to every line of that
	 * block, picked by the horizontal position of the caret, that is, the one
	 * clicked on, not the innermost one.
	 *
	 * @return <code>true</code> if the active guide has changed
	 */
	private boolean updateActiveBlock() {
		int col = -1, start = -1, end = -1;
		if (settings.isActiveEnabled()) {
			try {
				int tabs = tabWidth();
				calculator.setTabWidth(tabs);
				int line = -1;
				if (pinnedColumn >= 0 && pinnedLine >= 0
						&& pinnedLine < fTextWidget.getLineCount()) {
					col = pinnedColumn;
					line = pinnedLine;
					// An edit can have moved the indentation out from under the
					// pinned guide; drop it rather than light up a stale block.
					if (calculator.effectiveIndent(line) <= col) {
						clearPin();
						col = -1;
					}
				} else {
					clearPin();
				}
				if (col < 0 && settings.isCaretHighlightEnabled()) {
					int caretOffset = fTextWidget.getCaretOffset();
					int caretLine = fTextWidget.getLineAtOffset(caretOffset);
					int count = calculator.effectiveIndent(caretLine);
					if (count >= tabs) {
						int index = caretOffset
								- fTextWidget.getOffsetAtLine(caretLine);
						int caretCol = calculator.visualColumn(
								fTextWidget.getLine(caretLine), index);
						col = (caretCol / tabs) * tabs;
						int innermost = count - tabs;
						if (col > innermost) {
							col = innermost;
						}
						// Lighting a column nothing is drawn at lights nothing.
						// With the leftmost guide turned off - which is how it
						// comes - a caret anywhere in the first level of
						// indentation lands on column 0 and the highlight had
						// no line to appear on, which read as the caret being
						// followed by nothing at all.
						int leftmost = settings.isDrawLeftEnd() ? 0 : tabs;
						if (col < leftmost) {
							col = count > leftmost ? leftmost : -1;
						}
						if (col >= 0) {
							col = calculator.parenthesisGuideAtOrBefore(col,
									caretLine);
							line = caretLine;
						}
					}
				}
				if (col >= 0 && line >= 0) {
					start = blockStart(col, line);
					end = blockEnd(col, line);
				}
			} catch (Exception e) {
				col = -1;
				start = -1;
				end = -1;
			}
		}
		boolean changed = col != activeColumn || start != activeStart
				|| end != activeEnd;
		activeColumn = col;
		activeStart = start;
		activeEnd = end;
		return changed;
	}

	private int tabWidth() {
		int tabs = fTextWidget.getTabs();
		return tabs > 0 ? tabs : 4;
	}

	/*
	 * @see org.eclipse.jface.text.IPainter#dispose()
	 */
	public void dispose() {
		removePreferenceListener();
		removeMouseListeners();
		disposeColors();
		fTextViewer = null;
		fTextWidget = null;
	}

	/*
	 * @see org.eclipse.jface.text.IPainter#paint(int)
	 */
	public void paint(int reason) {
		IDocument document = fTextViewer.getDocument();
		if (document == null) {
			deactivate(false);
			return;
		}
		boolean activeChanged = updateActiveBlock();
		if (!fIsActive) {
			fIsActive = true;
			fTextWidget.addPaintListener(this);
			installCaretListener();
			installMouseListeners();
			redrawAll();
		} else if (activeChanged) {
			redrawAll();
		} else if (reason == CONFIGURATION || reason == INTERNAL) {
			redrawAll();
		} else if (reason == TEXT_CHANGE) {
			if (settings.isIrregularEnabled()) {
				// The keystroke that repairs an indentation is on one line, but
				// the guide it repairs runs over the whole block, and that is
				// what has to blink. Redrawing only the edited line would blink
				// one line of it.
				redrawAll();
				return;
			}
			// redraw current line only
			try {
				IRegion lineRegion = document
						.getLineInformationOfOffset(getDocumentOffset(fTextWidget
								.getCaretOffset()));
				int widgetOffset = getWidgetOffset(lineRegion.getOffset());
				int charCount = fTextWidget.getCharCount();
				int redrawLength = Math.min(lineRegion.getLength(), charCount
						- widgetOffset);
				if (widgetOffset >= 0 && redrawLength > 0) {
					fTextWidget.redrawRange(widgetOffset, redrawLength, true);
				}
			} catch (BadLocationException e) {
				// ignore
			}
		}
	}

	/*
	 * @see org.eclipse.jface.text.IPainter#deactivate(boolean)
	 */
	public void deactivate(boolean redraw) {
		if (fIsActive) {
			fIsActive = false;
			fTextWidget.removePaintListener(this);
			removeCaretListener();
			removeMouseListeners();
			clearPin();
			hoverColumn = -1;
			hoverStart = -1;
			hoverEnd = -1;
			if (redraw) {
				redrawAll();
			}
		}
	}

	/*
	 * @see
	 * org.eclipse.jface.text.IPainter#setPositionManager(org.eclipse.jface.
	 * text.IPaintPositionManager)
	 */
	public void setPositionManager(IPaintPositionManager manager) {
		// no need for a position manager
	}

	/*
	 * @see
	 * org.eclipse.swt.events.PaintListener#paintControl(org.eclipse.swt.events
	 * .PaintEvent)
	 */
	public void paintControl(PaintEvent event) {
		if (fTextWidget != null) {
			handleDrawRequest(event.gc, event.x, event.y, event.width,
					event.height);
		}
	}

	/*
	 * Draw characters in view range.
	 */
	private void handleDrawRequest(GC gc, int x, int y, int w, int h) {
		int startLine = fTextWidget.getLineIndex(y);
		int endLine = fTextWidget.getLineIndex(y + h - 1);
		if (startLine <= endLine && startLine < fTextWidget.getLineCount()) {
			Color fgColor = gc.getForeground();
			LineAttributes lineAttributes = gc.getLineAttributes();
			gc.setLineStyle(settings.getLineStyle());
			gc.setLineWidth(settings.getLineWidth());
			if (fIsAdvancedGraphicsPresent) {
				int alpha = gc.getAlpha();
				int antialias = gc.getAntialias();
				gc.setAlpha(settings.getLineAlpha());
				// Setting the alpha switches the GC to the advanced graphics
				// engine, which centres a stroke on its coordinate: a one pixel
				// vertical line lands half on one column of pixels and half on
				// the next, and antialiasing renders it as two faint columns
				// instead of one clean line. Snapping to whole pixels is what
				// keeps thin, barely opaque guides crisp.
				setAntialias(gc, SWT.OFF);
				drawLineRange(gc, startLine, endLine, x, w);
				setAntialias(gc, antialias);
				gc.setAlpha(alpha);
			} else {
				drawLineRange(gc, startLine, endLine, x, w);
			}
			gc.setForeground(fgColor);
			gc.setLineAttributes(lineAttributes);
		}
	}

	/**
	 * Not every platform accepts every antialiasing setting; a refusal is not
	 * worth losing the whole paint over, the guides just stay as they were.
	 *
	 * @param gc
	 *            the GC
	 * @param value
	 *            one of <code>SWT.DEFAULT</code>, <code>SWT.OFF</code>,
	 *            <code>SWT.ON</code>
	 */
	private static void setAntialias(GC gc, int value) {
		try {
			gc.setAntialias(value);
		} catch (Exception e) {
			// the GC keeps whatever it had
		}
	}

	/**
	 * Draw the given line range.
	 *
	 * @param gc
	 *            the GC
	 * @param startLine
	 *            first line number
	 * @param endLine
	 *            last line number (inclusive)
	 * @param x
	 *            the X-coordinate of the drawing range
	 * @param w
	 *            the width of the drawing range
	 */
	private void drawLineRange(GC gc, int startLine, int endLine, int x, int w) {
		int tabs = tabWidth();
		spaceWidth = gc.getAdvanceWidth(' ');
		columnWidth = measureColumnWidth(gc);
		calculator.setTabWidth(tabs);
		calculator.refresh();
		// Do not let an irregular block that has just been scrolled away make an
		// unrelated block at the same depth flash as if it were repaired. What
		// says a different block is now under a column is the widget having
		// scrolled, not the range of this paint: recent releases repair only
		// the damaged rectangle, so that range changes constantly while the
		// text stands still, and keying the memory on it wiped, every single
		// pass, the very state the flash is the difference between.
		int top = fTextWidget.getTopIndex();
		if (top != irregularTopIndex) {
			for (int i = 0; i < MAX_LEVELS; i++) {
				irregularKnown[i] = false;
				flashEnd[i] = 0;
			}
			irregularTopIndex = top;
		}

		long now = System.currentTimeMillis();
		long lastFlashEnd = 0;

		StyledTextContent content = fTextWidget.getContent();
		int visibleCount = endLine - startLine + 1;
		int[] indents = new int[visibleCount];
		boolean[] drawable = new boolean[visibleCount];
		int deepest = 0;
		for (int k = 0; k < visibleCount; k++) {
			int widgetOffset = fTextWidget.getOffsetAtLine(startLine + k);
			drawable[k] = !isFoldedLine(content.getLineAtOffset(widgetOffset));
			indents[k] = drawable[k] ? calculator.effectiveIndent(startLine + k)
					: 0;
			if (indents[k] > deepest) {
				deepest = indents[k];
			}
		}

		// One column at a time, so that the lines a guide spans become a single
		// drawLine. Drawn line by line instead, consecutive segments share an
		// endpoint: with alpha that pixel is blended twice and shows up as a
		// dot every line height, and the guide reads as dotted rather than
		// straight.
		Color[] colors = new Color[visibleCount];
		int[] alphas = new int[visibleCount];
		for (int col = settings.isDrawLeftEnd() ? 0 : tabs; col < deepest; col += tabs) {
			int slot = levelOf(col, tabs);
			int idx = palette != null && palette.length > 0
					? (col / tabs) % palette.length : -1;
			boolean drawn = false;
			boolean irregular = false;
			for (int k = 0; k < visibleCount; k++) {
				colors[k] = null;
				int line = startLine + k;
				boolean parenthesisGuide = calculator
						.isParenthesisGuide(col, line);
				// Inside a multi-line parenthesis only the columns right of the
				// outermost one are alignment; the guides of the blocks
				// enclosing it keep running, unbroken, through these lines.
				int base = calculator.outermostParenthesisGuide(line);
				boolean alignmentOnly = base >= 0 && col > base
						&& !parenthesisGuide;
				if (!drawable[k] || alignmentOnly
						|| (!parenthesisGuide && col >= indents[k])) {
					continue;
				}
				drawn = true;
				boolean lit = activeColumn == col && line >= activeStart
						&& line <= activeEnd;
				boolean grey = !parenthesisGuide && settings.isIrregularEnabled()
						&& calculator.blockMismatch(col, line);
				if (grey) {
					irregular = true;
				}
				if (grey && irregularColor != null) {
					colors[k] = irregularColor;
					alphas[k] = settings.getIrregularAlpha();
				} else if (idx >= 0) {
					// The lit guide keeps the colour of its own level and comes
					// forward by being brighter and fully opaque, so that the
					// highlight never costs the level its identity.
					colors[k] = lit && paletteBright != null ? paletteBright[idx]
							: palette[idx];
					alphas[k] = lit ? settings.getActiveAlpha()
							: settings.getLineAlpha();
				}
			}
			// The state of a column is only carried forward while it is on
			// screen; scrolling past a level must not read as it being fixed.
			if (drawn) {
				long end = notchFlash(slot, irregular, now);
				if (end > now) {
					if (end > lastFlashEnd) {
						lastFlashEnd = end;
					}
					whiten(colors, alphas);
				}
			}
			drawColumnRuns(gc, colors, alphas, startLine, col);
		}
		ensureFlashTimer(lastFlashEnd, now);
		if (settings.isBraceColorEnabled()) {
			drawBraces(gc, startLine, endLine, tabs, content);
		}
		if (settings.isParenthesisColorEnabled()) {
			drawParentheses(gc, startLine, endLine);
		}
	}

	/**
	 * Repaints matching round parentheses with a palette independent from the
	 * guides and braces. The opening offset and its depth are kept on a stack;
	 * consequently a closing parenthesis always receives the exact color of
	 * the opening parenthesis it removes from that stack.
	 * <p>
	 * The whole model document is scanned, not just the viewport, so a pair
	 * keeps its color while either end is scrolled or folded out of view.
	 */
	private void drawParentheses(GC gc, int startLine, int endLine) {
		if (parenthesisPalette == null || parenthesisPalette.length == 0) {
			return;
		}
		IDocument document = fTextViewer.getDocument();
		if (document == null) {
			return;
		}
		String text = document.get();
		int tabs = tabWidth();
		// The color of a parenthesis carries on from the depth its line is
		// indented to, instead of restarting at the first tint: the innermost
		// guide reaching that line and the parenthesis opening there are two
		// steps of the same nesting, and must not share a color.
		int lineLevel = indentLevelAt(text, 0, tabs);
		int[] offsets = new int[64];
		int[] colors = new int[64];
		int size = 0;
		int state = 0; // 0 code, 1 single, 2 double, 3 line, 4 block, 5 backtick, 6 XML comment
		boolean escaped = false;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
			if (ch == '\n') {
				lineLevel = indentLevelAt(text, i + 1, tabs);
			}
			if (state == 3) {
				if (ch == '\n' || ch == '\r') state = 0;
				continue;
			}
			if (state == 4) {
				if (ch == '*' && next == '/') { state = 0; i++; }
				continue;
			}
			if (state == 6) {
				if (ch == '-' && next == '-' && i + 2 < text.length()
						&& text.charAt(i + 2) == '>') { state = 0; i += 2; }
				continue;
			}
			if (state == 1 || state == 2 || state == 5) {
				char quote = state == 1 ? '\'' : (state == 2 ? '"' : '`');
				if (escaped) {
					escaped = false;
				} else if (ch == '\\') {
					escaped = true;
				} else if (ch == quote) {
					state = 0;
				}
				continue;
			}
			if (ch == '/' && next == '/') { state = 3; i++; continue; }
			if (ch == '/' && next == '*') { state = 4; i++; continue; }
			if (ch == '#') { state = 3; continue; }
			if (ch == '<' && next == '!' && i + 3 < text.length()
					&& text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') {
				state = 6; i += 3; continue;
			}
			if (ch == '\'') { state = 1; escaped = false; continue; }
			if (ch == '"') { state = 2; escaped = false; continue; }
			if (ch == '`') { state = 5; escaped = false; continue; }
			if (ch == '(') {
				if (size == offsets.length) {
					int[] largerOffsets = new int[size * 2];
					int[] largerColors = new int[size * 2];
					System.arraycopy(offsets, 0, largerOffsets, 0, size);
					System.arraycopy(colors, 0, largerColors, 0, size);
					offsets = largerOffsets;
					colors = largerColors;
				}
				offsets[size] = i;
				colors[size] = (size > 0 ? colors[size - 1] + 1 : lineLevel + 1)
						% parenthesisPalette.length;
				size++;
			} else if (ch == ')' && size > 0) {
				size--;
				int color = colors[size];
				drawParenthesis(gc, offsets[size], '(', color, startLine, endLine);
				drawParenthesis(gc, i, ')', color, startLine, endLine);
			}
		}
	}

	/**
	 * The indentation level of the line starting at the given index.
	 *
	 * @param text
	 *            the whole document text
	 * @param lineStart
	 *            the index of the first character of the line
	 * @param tabs
	 *            the width of a tabulation, in columns
	 * @return the indentation of the line, in levels
	 */
	private static int indentLevelAt(String text, int lineStart, int tabs) {
		int column = 0;
		for (int i = lineStart; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == ' ') {
				column++;
			} else if (ch == '\t') {
				column += tabs - column % tabs;
			} else {
				break;
			}
		}
		return column / tabs;
	}

	private void drawParenthesis(GC gc, int documentOffset, char glyph,
			int color, int startLine, int endLine) {
		int widgetOffset = getWidgetOffset(documentOffset);
		if (widgetOffset < 0 || widgetOffset >= fTextWidget.getCharCount()) {
			return;
		}
		int line = fTextWidget.getLineAtOffset(widgetOffset);
		if (line < startLine || line > endLine) {
			return;
		}
		Point selection = fTextWidget.getSelectionRange();
		if (selection != null && selection.y > 0
				&& widgetOffset >= selection.x
				&& widgetOffset < selection.x + selection.y) {
			return;
		}
		Color oldForeground = gc.getForeground();
		Color oldBackground = gc.getBackground();
		int oldAlpha = fIsAdvancedGraphicsPresent ? gc.getAlpha() : 255;
		if (fIsAdvancedGraphicsPresent) gc.setAlpha(255);
		gc.setForeground(parenthesisPalette[color]);
		Color lineBackground = fTextWidget.getLineBackground(line);
		gc.setBackground(lineBackground != null ? lineBackground
				: fTextWidget.getBackground());
		Point position = fTextWidget.getLocationAtOffset(widgetOffset);
		gc.drawText(String.valueOf(glyph), position.x, position.y, false);
		gc.setForeground(oldForeground);
		gc.setBackground(oldBackground);
		if (fIsAdvancedGraphicsPresent) gc.setAlpha(oldAlpha);
	}

	private static int levelOf(int column, int tabs) {
		int level = column / tabs;
		return level < 0 ? 0 : (level >= MAX_LEVELS ? MAX_LEVELS - 1 : level);
	}

	/**
	 * Tracks whether a level is drawn grey, and opens a white flash on the pass
	 * where it stops being so. Fixing the indentation is the moment worth
	 * acknowledging: the guide comes back to its color, and the blink says so.
	 *
	 * @param slot
	 *            the indentation level of the guide, already clamped
	 * @param irregular
	 *            whether the level is drawn grey by the current pass
	 * @param now
	 *            the instant of the current drawing pass
	 * @return the instant the flash of that level ends, 0 if it is not flashing
	 */
	private long notchFlash(int slot, boolean irregular, long now) {
		boolean fixed = irregularKnown[slot] && !irregular;
		irregularKnown[slot] = irregular;
		int duration = settings.getIrregularFlash();
		if (duration <= 0) {
			return 0;
		}
		if (fixed) {
			flashEnd[slot] = now + duration;
		}
		return flashEnd[slot];
	}

	/**
	 * Turns the guides of one column white for the length of the flash.
	 *
	 * @param colors
	 *            per visible line, the color of the guide
	 * @param alphas
	 *            per visible line, the opacity of the guide
	 */
	private void whiten(Color[] colors, int[] alphas) {
		Color white = fTextWidget.getDisplay().getSystemColor(SWT.COLOR_WHITE);
		for (int k = 0; k < colors.length; k++) {
			if (colors[k] != null) {
				colors[k] = white;
				alphas[k] = 255;
			}
		}
	}

	/**
	 * Books the redraw that brings the guides back to their resting opacity.
	 * Levels can start flashing at different instants, so the timer is keyed on
	 * the deadline it covers: a burst ending later than the pending one gets a
	 * timer of its own, an earlier one rides on what is already booked.
	 *
	 * @param deadline
	 *            the instant the last running burst ends
	 * @param now
	 *            the instant of the current drawing pass
	 */
	private void ensureFlashTimer(final long deadline, long now) {
		if (deadline <= now || deadline <= flashTimerFor
				|| fTextWidget == null || fTextWidget.isDisposed()) {
			return;
		}
		flashTimerFor = deadline;
		fTextWidget.getDisplay().timerExec((int) (deadline - now) + 20,
				new Runnable() {

					public void run() {
						if (flashTimerFor == deadline) {
							flashTimerFor = 0;
						}
						if (fTextWidget != null && !fTextWidget.isDisposed()) {
							redrawAll();
						}
					}
				});
	}

	/**
	 * Repaints the braces delimiting a block in the color of the last guide
	 * drawn on their line, so that the vertical line and the two braces it runs
	 * between read as one shape.
	 * <p>
	 * The glyph is drawn over the one the editor already painted, on the line's
	 * own background so that no dark fringe of the original survives. Braces
	 * inside the selection are skipped: there the background belongs to the
	 * selection, not to us.
	 *
	 * @param gc
	 *            the GC
	 * @param startLine
	 *            first line number
	 * @param endLine
	 *            last line number (inclusive)
	 * @param tabs
	 *            the width of a tabulation, in columns
	 * @param content
	 *            the widget content, for the folding check
	 */
	private void drawBraces(GC gc, int startLine, int endLine, int tabs,
			StyledTextContent content) {
		if (palette == null || palette.length == 0) {
			return;
		}
		Color background = gc.getBackground();
		int alpha = fIsAdvancedGraphicsPresent ? gc.getAlpha() : 255;
		if (fIsAdvancedGraphicsPresent) {
			gc.setAlpha(255);
		}
		Point selection = fTextWidget.getSelectionRange();
		int leftmost = settings.isDrawLeftEnd() ? 0 : tabs;
		for (int line = startLine; line <= endLine; line++) {
			int widgetOffset = fTextWidget.getOffsetAtLine(line);
			if (isFoldedLine(content.getLineAtOffset(widgetOffset))) {
				continue;
			}
			// The braces take the color of the guide that runs between them,
			// the one drawn through the body of the block they delimit. That
			// guide sits at the column their own line is indented to, one level
			// deeper than the innermost guide drawn on that line.
			int column = calculator.effectiveIndent(line);
			if (column < leftmost) {
				// Nothing is drawn that far left, so there is no color to
				// match: leave the braces to the editor.
				continue;
			}
			String text = fTextWidget.getLine(line);
			gc.setForeground(palette[(column / tabs) % palette.length]);
			Color lineBackground = fTextWidget.getLineBackground(line);
			gc.setBackground(lineBackground != null ? lineBackground
					: fTextWidget.getBackground());
			drawBrace(gc, widgetOffset,
					IndentLevelCalculator.closeBraceIndex(text), '}', selection);
			drawBrace(gc, widgetOffset,
					IndentLevelCalculator.openBraceIndex(text), '{', selection);
		}
		gc.setBackground(background);
		if (fIsAdvancedGraphicsPresent) {
			gc.setAlpha(alpha);
		}
	}

	private void drawBrace(GC gc, int widgetLineOffset, int index, char brace,
			Point selection) {
		if (index < 0) {
			return;
		}
		int offset = widgetLineOffset + index;
		if (selection != null && selection.y > 0 && offset >= selection.x
				&& offset < selection.x + selection.y) {
			return;
		}
		Point pos = fTextWidget.getLocationAtOffset(offset);
		gc.drawText(String.valueOf(brace), pos.x, pos.y, false);
	}

	/**
	 * Measures a run of spaces and divides, so that the column width keeps the
	 * decimals <code>getAdvanceWidth()</code> drops.
	 *
	 * @param gc
	 *            the GC
	 * @return the width of a column, in pixels
	 */
	private double measureColumnWidth(GC gc) {
		try {
			StringBuffer sb = new StringBuffer(PROBE_WIDTH);
			for (int i = 0; i < PROBE_WIDTH; i++) {
				sb.append(' ');
			}
			int width = gc.stringExtent(sb.toString()).x;
			if (width > 0) {
				return width / (double) PROBE_WIDTH;
			}
		} catch (Exception e) {
			// fall back on the integer width
		}
		return spaceWidth;
	}

	/**
	 * Check if the given widget line is a folded line.
	 *
	 * @param widgetLine
	 *            the widget line number
	 * @return <code>true</code> if the line is folded
	 */
	private boolean isFoldedLine(int widgetLine) {
		if (fTextViewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension = (ITextViewerExtension5) fTextViewer;
			int modelLine = extension.widgetLine2ModelLine(widgetLine);
			int widgetLine2 = extension.modelLine2WidgetLine(modelLine + 1);
			return widgetLine2 == -1;
		}
		return false;
	}

	/**
	 * Redraw all of the text widgets visible content.
	 */
	private void redrawAll() {
		fTextWidget.redraw();
	}

	/**
	 *
	 * @param gc
	 * @param offset
	 * @param column
	 */
	/**
	 * Draws the guide of one column over the visible lines, joining the lines
	 * that share a color and an opacity into a single stroke.
	 *
	 * @param gc
	 *            the GC
	 * @param colors
	 *            per visible line, the color of the guide, <code>null</code>
	 *            where the guide is absent
	 * @param alphas
	 *            per visible line, the opacity of the guide
	 * @param startLine
	 *            the widget line the arrays start at
	 * @param column
	 *            the column of the guide
	 */
	private void drawColumnRuns(GC gc, Color[] colors, int[] alphas,
			int startLine, int column) {
		int runStart = -1;
		Color runColor = null;
		int runAlpha = 0;
		for (int k = 0; k <= colors.length; k++) {
			Color color = k < colors.length ? colors[k] : null;
			int alpha = k < colors.length ? alphas[k] : 0;
			if (runStart >= 0 && (color != runColor || alpha != runAlpha)) {
				drawRun(gc, runColor, runAlpha, column, startLine + runStart,
						startLine + k - 1);
				runStart = -1;
			}
			if (color != null && runStart < 0) {
				runStart = k;
				runColor = color;
				runAlpha = alpha;
			}
		}
	}

	/**
	 * Strokes one guide from the top of the first line to the bottom of the
	 * last. The bottom is exclusive: a stroke reaching the first pixel of the
	 * next line would be overdrawn by the stroke starting there.
	 *
	 * @param gc
	 *            the GC
	 * @param color
	 *            the color of the stroke
	 * @param alpha
	 *            the opacity of the stroke
	 * @param column
	 *            the column of the guide
	 * @param fromLine
	 *            the first widget line covered
	 * @param toLine
	 *            the last widget line covered
	 */
	private void drawRun(GC gc, Color color, int alpha, int column,
			int fromLine, int toLine) {
		int fromOffset = fTextWidget.getOffsetAtLine(fromLine);
		int toOffset = fTextWidget.getOffsetAtLine(toLine);
		int top = fTextWidget.getLocationAtOffset(fromOffset).y;
		int bottom = fTextWidget.getLocationAtOffset(toOffset).y
				+ fTextWidget.getLineHeight(toOffset) - 1;
		if (bottom < top) {
			bottom = top;
		}
		if (fIsAdvancedGraphicsPresent) {
			gc.setAlpha(alpha);
		}
		gc.setForeground(color);
		int x = guideX(fromOffset, column);
		gc.drawLine(x, top, x, bottom);
	}

	/**
	 * The horizontal position of the guide of the given column, shared by the
	 * drawing and by the hit test, so that the two cannot drift apart.
	 *
	 * @param widgetLineOffset
	 *            the widget offset of the start of the line
	 * @param column
	 *            the column of the guide
	 * @return the X coordinate of the guide, in widget coordinates
	 */
	private int guideX(int widgetLineOffset, int column) {
		Point pos = fTextWidget.getLocationAtOffset(widgetLineOffset);
		double width = columnWidth > 0 ? columnWidth : spaceWidth;
		return pos.x + (int) Math.floor(column * width)
				+ settings.getLineShift();
	}

	/**
	 * Convert a document offset to the corresponding widget offset.
	 *
	 * @param documentOffset
	 *            the document offset
	 * @return widget offset
	 */
	private int getWidgetOffset(int documentOffset) {
		if (fTextViewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension = (ITextViewerExtension5) fTextViewer;
			return extension.modelOffset2WidgetOffset(documentOffset);
		}
		IRegion visible = fTextViewer.getVisibleRegion();
		int widgetOffset = documentOffset - visible.getOffset();
		if (widgetOffset > visible.getLength()) {
			return -1;
		}
		return widgetOffset;
	}

	/**
	 * Convert a widget offset to the corresponding document offset.
	 *
	 * @param widgetOffset
	 *            the widget offset
	 * @return document offset
	 */
	private int getDocumentOffset(int widgetOffset) {
		if (fTextViewer instanceof ITextViewerExtension5) {
			ITextViewerExtension5 extension = (ITextViewerExtension5) fTextViewer;
			return extension.widgetOffset2ModelOffset(widgetOffset);
		}
		IRegion visible = fTextViewer.getVisibleRegion();
		if (widgetOffset > visible.getLength()) {
			return -1;
		}
		return widgetOffset + visible.getOffset();
	}

}
