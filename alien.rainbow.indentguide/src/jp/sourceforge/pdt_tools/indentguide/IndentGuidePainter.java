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
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextContent;
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

	private CaretListener caretListener;

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
	 * Locates the active guide: the one of the block the caret sits in,
	 * extended to every line of that block. The guide to light up is picked by
	 * the horizontal position of the caret, that is, the one clicked on, not
	 * the innermost one.
	 *
	 * @return <code>true</code> if the active guide has changed
	 */
	private boolean updateActiveBlock() {
		int col = -1, start = -1, end = -1;
		if (settings.isActiveEnabled()) {
			try {
				int tabs = tabWidth();
				calculator.setTabWidth(tabs);
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
					start = caretLine;
					end = caretLine;
					int lineCount = fTextWidget.getLineCount();
					while (start - 1 >= 0
							&& calculator.effectiveIndent(start - 1) > col) {
						start--;
					}
					while (end + 1 < lineCount
							&& calculator.effectiveIndent(end + 1) > col) {
						end++;
					}
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
				gc.setAlpha(settings.getLineAlpha());
				drawLineRange(gc, startLine, endLine, x, w);
				gc.setAlpha(alpha);
			} else {
				drawLineRange(gc, startLine, endLine, x, w);
			}
			gc.setForeground(fgColor);
			gc.setLineAttributes(lineAttributes);
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

		StyledTextContent content = fTextWidget.getContent();
		for (int line = startLine; line <= endLine; line++) {
			int widgetOffset = fTextWidget.getOffsetAtLine(line);
			if (!isFoldedLine(content.getLineAtOffset(widgetOffset))) {
				int count = calculator.effectiveIndent(line);
				boolean inActiveBlock = activeColumn >= 0 && line >= activeStart
						&& line <= activeEnd;
				for (int i = settings.isDrawLeftEnd() ? 0 : tabs; i < count; i += tabs) {
					boolean isActiveGuide = inActiveBlock && i == activeColumn;
					boolean grey = settings.isIrregularEnabled()
							&& calculator.blockMismatch(i, line);
					if (fIsAdvancedGraphicsPresent) {
						gc.setAlpha(grey ? settings.getIrregularAlpha()
								: (isActiveGuide ? settings.getActiveAlpha()
										: settings.getLineAlpha()));
					}
					if (grey && irregularColor != null) {
						gc.setForeground(irregularColor);
					} else if (palette != null && palette.length > 0) {
						int idx = (i / tabs) % palette.length;
						gc.setForeground(isActiveGuide && paletteBright != null
								? paletteBright[idx] : palette[idx]);
					}
					draw(gc, widgetOffset, i);
				}
			}
		}
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
	private void draw(GC gc, int offset, int column) {
		Point pos = fTextWidget.getLocationAtOffset(offset);
		double width = columnWidth > 0 ? columnWidth : spaceWidth;
		pos.x += (int) Math.floor(column * width) + settings.getLineShift();
		gc.drawLine(pos.x, pos.y, pos.x,
				pos.y + fTextWidget.getLineHeight(offset));
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
