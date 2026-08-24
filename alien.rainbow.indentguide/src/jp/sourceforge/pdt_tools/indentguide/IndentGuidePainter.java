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
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.MouseTrackListener;
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

	/** How far from a guide, in pixels, the pointer still counts as over it. */
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

	/** The guide of the block holding the caret: column and lines covered. */
	private int activeColumn = -1;
	private int activeStart = -1;
	private int activeEnd = -1;

	/**
	 * The guide the pointer is over, lit as a preview of what a click would
	 * pin. Same shape as the active guide, and never persisted.
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
	/** The deadline the booked flash timer covers, 0 when none is booked. */
	private long flashTimerFor;

	private CaretListener caretListener;
	private MouseMoveListener mouseMoveListener;
	private MouseListener mouseListener;
	private MouseTrackListener mouseTrackListener;

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
	}

	private void disposeColors() {
		if (irregularColor != null && !irregularColor.isDisposed()) {
			irregularColor.dispose();
		}
		irregularColor = null;
		paletteBright = dispose(paletteBright);
		palette = dispose(palette);
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
	 * The guides answer to the pointer as well as to the caret: moving over one
	 * lights it up and turns the pointer into a hand, clicking it pins it.
	 * Nothing here consumes the event, so the editor keeps placing the caret as
	 * usual.
	 */
	private void installMouseListeners() {
		if (mouseMoveListener != null || fTextWidget == null) {
			return;
		}
		mouseMoveListener = new MouseMoveListener() {

			public void mouseMove(MouseEvent e) {
				if (fTextWidget == null || fTextWidget.isDisposed()) {
					return;
				}
				if (updateHover(e.x, e.y)) {
					applyHoverCursor();
					redrawAll();
				}
			}
		};
		fTextWidget.addMouseMoveListener(mouseMoveListener);

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
				updateActiveBlock();
				redrawAll();
			}
		};
		fTextWidget.addMouseListener(mouseListener);

		mouseTrackListener = new MouseTrackAdapter() {

			public void mouseExit(MouseEvent e) {
				if (fTextWidget == null || fTextWidget.isDisposed()) {
					return;
				}
				if (hoverColumn >= 0) {
					hoverColumn = -1;
					hoverStart = -1;
					hoverEnd = -1;
					applyHoverCursor();
					redrawAll();
				}
			}
		};
		fTextWidget.addMouseTrackListener(mouseTrackListener);
	}

	private void removeMouseListeners() {
		if (fTextWidget != null && !fTextWidget.isDisposed()) {
			if (mouseMoveListener != null) {
				fTextWidget.removeMouseMoveListener(mouseMoveListener);
			}
			if (mouseListener != null) {
				fTextWidget.removeMouseListener(mouseListener);
			}
			if (mouseTrackListener != null) {
				fTextWidget.removeMouseTrackListener(mouseTrackListener);
			}
			fTextWidget.setCursor(null);
		}
		mouseMoveListener = null;
		mouseListener = null;
		mouseTrackListener = null;
	}

	/**
	 * A hand over a guide, the widget's own text pointer everywhere else.
	 * System cursors are shared and must not be disposed.
	 */
	private void applyHoverCursor() {
		if (fTextWidget == null || fTextWidget.isDisposed()) {
			return;
		}
		fTextWidget.setCursor(hoverColumn >= 0 ? fTextWidget.getDisplay()
				.getSystemCursor(SWT.CURSOR_HAND) : null);
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
	 * Locates the guide the pointer is over: the nearest one within
	 * {@link #HOVER_TOLERANCE} pixels on the line under the pointer.
	 *
	 * @param x
	 *            pointer position in widget coordinates
	 * @param y
	 *            pointer position in widget coordinates
	 * @return <code>true</code> if the hovered guide has changed
	 */
	private boolean updateHover(int x, int y) {
		int col = -1, start = -1, end = -1;
		// columnWidth is only known once the widget has been painted at least
		// once; before that there is nothing on screen to hover anyway.
		if (settings.isActiveEnabled() && columnWidth > 0) {
			try {
				int tabs = tabWidth();
				calculator.setTabWidth(tabs);
				calculator.refresh();
				int line = lineAt(y);
				int offset = fTextWidget.getOffsetAtLine(line);
				int count = calculator.effectiveIndent(line);
				int best = HOVER_TOLERANCE + 1;
				for (int i = settings.isDrawLeftEnd() ? 0 : tabs; i < count; i += tabs) {
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
				if (col < 0) {
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
						if (col < 0) {
							col = 0;
						}
						line = caretLine;
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

		long now = System.currentTimeMillis();
		boolean[] irregularNow = new boolean[MAX_LEVELS];
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
			for (int k = 0; k < visibleCount; k++) {
				colors[k] = null;
				if (!drawable[k] || col >= indents[k]) {
					continue;
				}
				int line = startLine + k;
				// The preview under the pointer is lit exactly like the pinned
				// one, so that a click is visibly a no-op.
				boolean lit = (activeColumn == col && line >= activeStart && line <= activeEnd)
						|| (hoverColumn == col && line >= hoverStart && line <= hoverEnd);
				boolean grey = settings.isIrregularEnabled()
						&& calculator.blockMismatch(col, line);
				boolean flashing = false;
				if (grey) {
					irregularNow[slot] = true;
					long end = flashDeadline(slot, now);
					if (end > now) {
						flashing = true;
						if (end > lastFlashEnd) {
							lastFlashEnd = end;
						}
					}
				}
				if (grey && irregularColor != null) {
					colors[k] = irregularColor;
					alphas[k] = flashing ? 255 : settings.getIrregularAlpha();
				} else if (idx >= 0) {
					colors[k] = lit && paletteBright != null ? paletteBright[idx]
							: palette[idx];
					alphas[k] = lit ? settings.getActiveAlpha()
							: settings.getLineAlpha();
				}
			}
			drawColumnRuns(gc, colors, alphas, startLine, col);
		}
		forgetVanishedIrregulars(irregularNow);
		ensureFlashTimer(lastFlashEnd, now);
		if (settings.isBraceColorEnabled()) {
			drawBraces(gc, startLine, endLine, tabs, content);
		}
	}

	private static int levelOf(int column, int tabs) {
		int level = column / tabs;
		return level < 0 ? 0 : (level >= MAX_LEVELS ? MAX_LEVELS - 1 : level);
	}

	/**
	 * Opens the opacity burst of a level the first time it is drawn grey, and
	 * reports when that burst ends. The flash is meant to catch the eye once,
	 * when the inconsistency appears, and then let the guide settle back to its
	 * muted grey.
	 *
	 * @param slot
	 *            the indentation level of the guide, already clamped
	 * @param now
	 *            the instant of the current drawing pass
	 * @return the instant the burst of that level ends, 0 if it is not bursting
	 */
	private long flashDeadline(int slot, long now) {
		int duration = settings.getIrregularFlash();
		if (duration <= 0) {
			return 0;
		}
		if (!irregularKnown[slot]) {
			irregularKnown[slot] = true;
			flashEnd[slot] = now + duration;
		}
		return flashEnd[slot];
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
	 * Levels that are no longer irregular become eligible to flash again, so
	 * that fixing the indentation and breaking it anew is signalled twice.
	 *
	 * @param irregularNow
	 *            the levels drawn grey by the pass that just ended
	 */
	private void forgetVanishedIrregulars(boolean[] irregularNow) {
		for (int i = 0; i < MAX_LEVELS; i++) {
			if (!irregularNow[i]) {
				irregularKnown[i] = false;
			}
		}
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
		for (int line = startLine; line <= endLine; line++) {
			int widgetOffset = fTextWidget.getOffsetAtLine(line);
			if (isFoldedLine(content.getLineAtOffset(widgetOffset))) {
				continue;
			}
			// The braces belong to the level of their own line, that is, to the
			// innermost guide drawn on it.
			int level = calculator.effectiveIndent(line) / tabs - 1;
			if (level < 0) {
				continue;
			}
			String text = fTextWidget.getLine(line);
			gc.setForeground(palette[level % palette.length]);
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
