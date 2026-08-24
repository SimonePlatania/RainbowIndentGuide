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

/**
 * Computes the indentation level of the lines a guide has to be drawn for.
 * <p>
 * Blank lines and comment lines inherit the indentation of their surrounding
 * code, so that they do not break the vertical guides, and the guide of a block
 * starts right below the line opening it and reaches the line closing it.
 */
public class IndentLevelCalculator {

	/**
	 * The text the levels are computed from.
	 */
	public interface ILineSource {

		int getLineCount();

		int getCharCount();

		String getLine(int line);
	}

	private static final int MAX_LEVELS = 64;

	private final ILineSource source;

	private int tabWidth = 4;

	private int[] indents;
	private int cachedCharCount = -1;
	private int cachedLineCount = -1;

	private final int[] memoStart = new int[MAX_LEVELS];
	private final int[] memoEnd = new int[MAX_LEVELS];
	private final boolean[] memoValue = new boolean[MAX_LEVELS];
	private final boolean[] memoValid = new boolean[MAX_LEVELS];

	public IndentLevelCalculator(ILineSource source) {
		this.source = source;
	}

	/**
	 * @param tabWidth
	 *            the width of a tabulation, in columns
	 */
	public void setTabWidth(int tabWidth) {
		this.tabWidth = tabWidth > 0 ? tabWidth : 4;
	}

	public int getTabWidth() {
		return tabWidth;
	}

	private String lineAt(int line) {
		try {
			return source.getLine(line);
		} catch (Exception e) {
			return ""; //$NON-NLS-1$
		}
	}

	/**
	 * Counts the leading whitespace of the given text in columns, expanding
	 * tabulations.
	 *
	 * @param str
	 *            the text of a line
	 * @return the indentation of the line, in columns
	 */
	public int countSpaces(String str) {
		int count = 0;
		for (int i = 0; i < str.length(); i++) {
			switch (str.charAt(i)) {
			case ' ':
				count++;
				break;
			case '\t':
				int z = tabWidth - count % tabWidth;
				count += z;
				break;
			default:
				return count;
			}
		}
		return count;
	}

	/**
	 * The column the character at the given index is displayed at, expanding
	 * tabulations.
	 *
	 * @param text
	 *            the text of a line
	 * @param index
	 *            a character index into that text
	 * @return the visual column of the character
	 */
	public int visualColumn(String text, int index) {
		int col = 0;
		if (text == null) {
			return 0;
		}
		int n = Math.min(index, text.length());
		for (int i = 0; i < n; i++) {
			if (text.charAt(i) == '\t') {
				col += tabWidth - (col % tabWidth);
			} else {
				col++;
			}
		}
		return col;
	}

	/**
	 * Lines which must not interrupt the guides: blank ones and comment ones.
	 * A line such as <code>} catch (...) {</code> is not transparent: there the
	 * guide is meant to stop and start again below.
	 *
	 * @param text
	 *            the text of a line
	 * @return <code>true</code> if the line inherits the surrounding indentation
	 */
	public static boolean isTransparentLine(String text) {
		if (text == null) {
			return true;
		}
		String t = text.trim();
		if (t.length() == 0) {
			return true;
		}
		return t.startsWith("//") || t.startsWith("/*") || t.startsWith("*"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	public static boolean opensBlock(String text) {
		return text.trim().endsWith("{"); //$NON-NLS-1$
	}

	public static boolean closesBlock(String text) {
		return text.trim().startsWith("}"); //$NON-NLS-1$
	}

	/**
	 * The index of the brace opening a block, that is, the last character of the
	 * line. Only that one is reported: a <code>{</code> sitting anywhere else is
	 * as likely to be inside a string or a comment as it is to open a block, and
	 * this class has no parser to tell the two apart.
	 *
	 * @param text
	 *            the text of a line
	 * @return the character index of the brace, or -1
	 */
	public static int openBraceIndex(String text) {
		return text != null && opensBlock(text) ? text.lastIndexOf('{') : -1;
	}

	/**
	 * The index of the brace closing a block, that is, the first non whitespace
	 * character of the line.
	 *
	 * @param text
	 *            the text of a line
	 * @return the character index of the brace, or -1
	 */
	public static int closeBraceIndex(String text) {
		return text != null && closesBlock(text) ? text.indexOf('}') : -1;
	}

	private int neighbourIndent(int line, boolean forward) {
		int lineCount = source.getLineCount();
		int step = forward ? 1 : -1;
		for (int i = line + step; i >= 0 && i < lineCount; i += step) {
			String t = lineAt(i);
			if (isTransparentLine(t)) {
				continue;
			}
			int c = countSpaces(t);
			if (!forward && opensBlock(t)) {
				c += tabWidth;
			}
			if (forward && closesBlock(t)) {
				c += tabWidth;
			}
			return c;
		}
		return 0;
	}

	/**
	 * The indentation the guides of the given line are drawn from.
	 *
	 * @param line
	 *            the widget line number
	 * @return the indentation of the line, in columns
	 */
	public int effectiveIndent(int line) {
		String text = lineAt(line);
		int own = countSpaces(text);
		if (!isTransparentLine(text)) {
			return own;
		}
		int before = neighbourIndent(line, false);
		int after = neighbourIndent(line, true);
		int context = Math.min(before, after);
		return Math.max(own, context);
	}

	/**
	 * Recomputes the per line cache if the text has changed, and clears the
	 * per pass memo. To be called once before each drawing pass.
	 */
	public void refresh() {
		int chars = source.getCharCount();
		int lines = source.getLineCount();
		if (indents == null || chars != cachedCharCount || lines != cachedLineCount
				|| indents.length != lines) {
			int[] computed = new int[lines];
			for (int i = 0; i < lines; i++) {
				computed[i] = effectiveIndent(i);
			}
			indents = computed;
			cachedCharCount = chars;
			cachedLineCount = lines;
		}
		for (int i = 0; i < MAX_LEVELS; i++) {
			memoValid[i] = false;
		}
	}

	/**
	 * Tells whether the guide sitting at the given column, on the given line,
	 * does not line up with the brace opening or closing its block. The answer
	 * holds for the whole block, so that single vertical line is drawn greyed
	 * over its whole height while the guides of the other levels, on the same
	 * lines, keep the colors of their level.
	 *
	 * @param col
	 *            the column of the guide
	 * @param line
	 *            the widget line number
	 * @return <code>true</code> if the guide does not match its braces
	 */
	public boolean blockMismatch(int col, int line) {
		if (indents == null || line >= indents.length) {
			return false;
		}
		int level = col / tabWidth;
		if (level >= 0 && level < MAX_LEVELS && memoValid[level]
				&& line >= memoStart[level] && line <= memoEnd[level]) {
			return memoValue[level];
		}
		int n = indents.length;
		int start = line, end = line;
		while (start - 1 >= 0 && indents[start - 1] > col) {
			start--;
		}
		while (end + 1 < n && indents[end + 1] > col) {
			end++;
		}
		boolean bad = false;
		for (int i = start - 1; i >= 0; i--) {
			String t = lineAt(i);
			if (isTransparentLine(t)) {
				continue;
			}
			if (opensBlock(t)) {
				bad = countSpaces(t) != col;
			}
			break;
		}
		if (!bad) {
			for (int i = end + 1; i < n; i++) {
				String t = lineAt(i);
				if (isTransparentLine(t)) {
					continue;
				}
				if (closesBlock(t)) {
					bad = countSpaces(t) != col;
				}
				break;
			}
		}
		if (level >= 0 && level < MAX_LEVELS) {
			memoStart[level] = start;
			memoEnd[level] = end;
			memoValue[level] = bad;
			memoValid[level] = true;
		}
		return bad;
	}
}
