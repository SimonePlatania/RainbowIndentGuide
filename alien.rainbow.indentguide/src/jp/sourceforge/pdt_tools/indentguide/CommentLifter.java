/*******************************************************************************
 * Copyright (c) 2026 Simone Platania.
 *
 * New file contributed to a fork of Indent Guide
 * (https://github.com/kiritsuku/IndentGuide, Copyright (c) 2014 Simon Schaefer,
 * MIT License). The copyright and license notices of the original work are kept
 * unchanged in the files they belong to; no claim is made over that code.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Simone Platania - initial API and implementation
 *******************************************************************************/
package jp.sourceforge.pdt_tools.indentguide;

import jp.sourceforge.pdt_tools.indentguide.preferences.PreferenceConstants;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Point;

/**
 * Lifts a line onto the one above it when backspace is pressed at its
 * indentation.
 * <p>
 * A comment written under the brace closing a block sits at the indentation of
 * that brace, and pressing backspace there would only shave one level off an
 * indentation that is already right. What the comment is after is the line
 * above, so that is where it goes; the same keystroke on anything else keeps
 * the behaviour the editor gives it.
 * <p>
 * The same holds for code written hard against a brace, which is the other
 * half of this and is off until it is asked for: moving code is a heavier
 * thing to do by accident than moving a comment.
 */
public class CommentLifter implements VerifyKeyListener {

	private final ITextViewer viewer;

	public CommentLifter(ITextViewer viewer) {
		this.viewer = viewer;
	}

	/**
	 * Tells whether a line, whitespace already trimmed, is a comment.
	 *
	 * @param text
	 *            the trimmed text of a line
	 * @return <code>true</code> if the line is nothing but a comment
	 */
	private static boolean isComment(String text) {
		return text.startsWith("//") || text.startsWith("/*") //$NON-NLS-1$ //$NON-NLS-2$
				|| text.startsWith("*") || text.startsWith("#") //$NON-NLS-1$ //$NON-NLS-2$
				|| text.startsWith("<!--"); //$NON-NLS-1$
	}

	/**
	 * Tells whether a line, whitespace already trimmed, ends on a brace.
	 *
	 * @param text
	 *            the trimmed text of a line
	 * @return <code>true</code> if the line ends with a brace
	 */
	private static boolean endsWithBrace(String text) {
		return text.endsWith("{") || text.endsWith("}"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Tells whether a line, whitespace already trimmed, opens or closes a
	 * block, that is, whether it is a brace with at most a tail behind it.
	 *
	 * @param text
	 *            the trimmed text of a line
	 * @return <code>true</code> if the line starts on a brace
	 */
	private static boolean isBrace(String text) {
		return text.startsWith("}") || text.startsWith("{") //$NON-NLS-1$ //$NON-NLS-2$
				|| text.startsWith(")") || text.startsWith("]"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * The width of the leading whitespace of the given text, in columns.
	 *
	 * @param text
	 *            the text of a line
	 * @param tabWidth
	 *            the width of a tabulation, in columns
	 * @return the indentation of the line, in columns
	 */
	private static int indentWidth(String text, int tabWidth) {
		int column = 0;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == ' ') {
				column++;
			} else if (ch == '\t') {
				column += tabWidth - column % tabWidth;
			} else {
				break;
			}
		}
		return column;
	}

	public void verifyKey(VerifyEvent event) {
		if (!event.doit || event.character != SWT.BS || event.stateMask != 0) {
			return;
		}
		boolean lifted = false;
		try {
			IPreferenceStore store = Activator.getDefault()
					.getPreferenceStore();
			boolean comments = store
					.getBoolean(PreferenceConstants.LIFT_COMMENT);
			boolean code = store.getBoolean(PreferenceConstants.LIFT_CODE);
			if (!comments && !code) {
				return;
			}
			lifted = lift(comments, code);
		} catch (BadLocationException e) {
			// the keystroke is left to the editor
			return;
		} catch (Exception e) {
			Activator.log(e);
			return;
		}
		// Consumed only once the edit is through: a failure anywhere above has
		// to leave the editor its own backspace, not eat the keystroke.
		if (lifted) {
			event.doit = false;
		}
	}

	/**
	 * Moves the line the caret sits in onto the one above, if this is one of
	 * the places that calls for it.
	 *
	 * @param comments
	 *            whether a comment may be lifted
	 * @param code
	 *            whether anything else may be lifted
	 * @return <code>true</code> if the line was moved
	 * @throws BadLocationException
	 *             if the document changed under the caret
	 */
	private boolean lift(boolean comments, boolean code)
			throws BadLocationException {
		StyledText widget = viewer.getTextWidget();
		IDocument document = viewer.getDocument();
		if (widget == null || widget.isDisposed() || document == null
				|| widget.getBlockSelection()) {
			return false;
		}
		Point selection = widget.getSelectionRange();
		if (selection == null || selection.y != 0) {
			// A backspace over a selection deletes it, as it should.
			return false;
		}
		int offset = modelOffset(widget.getCaretOffset());
		if (offset < 0) {
			return false;
		}
		int line = document.getLineOfOffset(offset);
		if (line == 0) {
			return false;
		}
		IRegion region = document.getLineInformation(line);
		String text = document.get(region.getOffset(), region.getLength());
		int column = offset - region.getOffset();
		if (column <= 0 || column > text.length()) {
			// At the very start of the line the editor already joins the two.
			return false;
		}
		String prefix = text.substring(0, column);
		if (prefix.trim().length() != 0) {
			// The caret is inside the text, where backspace deletes a
			// character; only the indentation of the line leads here.
			return false;
		}
		String rest = text.substring(column).trim();
		if (rest.length() == 0) {
			return false;
		}
		boolean comment = isComment(rest);
		if (comment ? !comments : !code) {
			return false;
		}
		IRegion above = document.getLineInformation(line - 1);
		String aboveText = document.get(above.getOffset(), above.getLength());
		String aboveCode = aboveText.trim();
		if (aboveCode.length() == 0) {
			// Nothing to lift the line onto.
			return false;
		}
		if (!comment && isBrace(rest)) {
			// A brace of its own is looking for its level, not for the line
			// above: backspace has to go on walking it left until it lines up
			// with the block it closes.
			return false;
		}
		if (!comment && !endsWithBrace(aboveCode)) {
			// Code goes up only from right against a brace, which is the case
			// the option is about. Anywhere else two statements would be run
			// onto one line, and that is not a thing to do by accident.
			return false;
		}
		int tabWidth = widget.getTabs() > 0 ? widget.getTabs() : 4;
		if (indentWidth(prefix, tabWidth) > indentWidth(aboveText, tabWidth)) {
			// The line is still indented deeper than the code above it, so
			// there are levels left to shed: backspace goes on doing that, and
			// the lift happens once the two line up.
			return false;
		}
		int join = above.getOffset() + above.getLength();
		String separator = aboveText.endsWith(" ") || aboveText.endsWith("\t") //$NON-NLS-1$ //$NON-NLS-2$
				? "" : " "; //$NON-NLS-1$ //$NON-NLS-2$
		// One replace, so that one undo puts the comment back where it was.
		document.replace(join, offset - join, separator);
		viewer.setSelectedRange(join + separator.length(), 0);
		return true;
	}

	private int modelOffset(int widgetOffset) {
		if (viewer instanceof ITextViewerExtension5) {
			return ((ITextViewerExtension5) viewer)
					.widgetOffset2ModelOffset(widgetOffset);
		}
		IRegion visible = viewer.getVisibleRegion();
		if (visible == null || widgetOffset > visible.getLength()) {
			return -1;
		}
		return widgetOffset + visible.getOffset();
	}
}
