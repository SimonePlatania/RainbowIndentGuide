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
	/** Structural guide columns contributed by multi-line round parentheses. */
	private long[] parenthesisGuides;
	private boolean[] parenthesisContext;
	private int cachedCharCount = -1;
	private int cachedLineCount = -1;
	private int cachedTextHash;

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

	/**
	 * Blanks out the comments of a line, leaving every other character where it
	 * was, so that an index taken on the result still addresses the original
	 * text. A brace with a comment behind it, <code>if (x) {// why</code>, is
	 * still the brace opening the block, and this is what lets it be seen as
	 * one.
	 * <p>
	 * Quoted text is left alone, so that a <code>//</code> inside a string does
	 * not swallow the rest of the line.
	 *
	 * @param text
	 *            the text of a line
	 * @return the same text with its comments replaced by spaces
	 */
	public static String maskComments(String text) {
		if (text == null || text.indexOf('/') < 0) {
			return text;
		}
		char[] chars = text.toCharArray();
		boolean block = false;
		char quote = 0;
		for (int i = 0; i < chars.length; i++) {
			char ch = chars[i];
			if (block) {
				boolean end = ch == '*' && i + 1 < chars.length
						&& chars[i + 1] == '/';
				chars[i] = ' ';
				if (end) {
					chars[++i] = ' ';
					block = false;
				}
			} else if (quote != 0) {
				if (ch == '\\' && i + 1 < chars.length) {
					i++;
				} else if (ch == quote) {
					quote = 0;
				}
			} else if (ch == '"' || ch == '\'') {
				quote = ch;
			} else if (ch == '/' && i + 1 < chars.length) {
				if (chars[i + 1] == '/') {
					// As far as the code goes, the line ends here.
					for (int j = i; j < chars.length; j++) {
						chars[j] = ' ';
					}
					break;
				} else if (chars[i + 1] == '*') {
					chars[i] = ' ';
					chars[++i] = ' ';
					block = true;
				}
			}
		}
		return new String(chars);
	}

	public static boolean opensBlock(String text) {
		return maskComments(text).trim().endsWith("{"); //$NON-NLS-1$
	}

	public static boolean closesBlock(String text) {
		return maskComments(text).trim().startsWith("}"); //$NON-NLS-1$
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
		return text != null && opensBlock(text) ? maskComments(text)
				.lastIndexOf('{') : -1;
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
		return text != null && closesBlock(text) ? maskComments(text)
				.indexOf('}') : -1;
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
		int textHash = 1;
		for (int i = 0; i < lines; i++) {
			textHash = 31 * textHash + lineAt(i).hashCode();
		}
		if (indents == null || chars != cachedCharCount || lines != cachedLineCount
				|| indents.length != lines || textHash != cachedTextHash) {
			int[] computed = new int[lines];
			for (int i = 0; i < lines; i++) {
				computed[i] = effectiveIndent(i);
			}
			indents = computed;
			cachedCharCount = chars;
			cachedLineCount = lines;
			cachedTextHash = textHash;
		}
		computeParenthesisGuides(lines);
		for (int i = 0; i < MAX_LEVELS; i++) {
			memoValid[i] = false;
		}
	}

	/**
	 * Finds multi-line parenthesis pairs and records only the indentation of
	 * their opening lines. Continuation indentation may be two or more tab
	 * widths, but those intermediate columns are alignment, not nested blocks.
	 */
	private void computeParenthesisGuides(int lineCount) {
		parenthesisGuides = new long[lineCount];
		parenthesisContext = new boolean[lineCount];
		int[] openLines = new int[64];
		int[] openColumns = new int[64];
		int size = 0;
		int state = 0; // 0 code, 1 single, 2 double, 4 block, 5 backtick, 6 XML
		boolean escaped = false;
		for (int line = 0; line < lineCount; line++) {
			String text = lineAt(line);
			for (int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
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
					if (escaped) escaped = false;
					else if (ch == '\\') escaped = true;
					else if (ch == quote) state = 0;
					continue;
				}
				if (ch == '/' && next == '/') break;
				if (ch == '#') break;
				if (ch == '/' && next == '*') { state = 4; i++; continue; }
				if (ch == '<' && next == '!' && i + 3 < text.length()
						&& text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') {
					state = 6; i += 3; continue;
				}
				if (ch == '\'') { state = 1; escaped = false; continue; }
				if (ch == '"') { state = 2; escaped = false; continue; }
				if (ch == '`') { state = 5; escaped = false; continue; }
				if (ch == '(') {
					if (size == openLines.length) {
						int[] lines = new int[size * 2];
						int[] columns = new int[size * 2];
						System.arraycopy(openLines, 0, lines, 0, size);
						System.arraycopy(openColumns, 0, columns, 0, size);
						openLines = lines;
						openColumns = columns;
					}
					openLines[size] = line;
					openColumns[size] = countSpaces(text);
					size++;
				} else if (ch == ')' && size > 0) {
					size--;
					int from = openLines[size];
					int column = openColumns[size];
					if (line > from && column % tabWidth == 0) {
						int level = column / tabWidth;
						if (level >= 0 && level < MAX_LEVELS) {
							long bit = 1L << level;
							for (int covered = from + 1; covered <= line; covered++) {
								parenthesisContext[covered] = true;
								parenthesisGuides[covered] |= bit;
							}
						}
					}
				}
			}
			// Ordinary quoted strings do not continue onto the next source line.
			if (state == 1 || state == 2) { state = 0; escaped = false; }
		}
	}

	public boolean isParenthesisContext(int line) {
		return parenthesisContext != null && line >= 0
				&& line < parenthesisContext.length && parenthesisContext[line];
	}

	public boolean isParenthesisGuide(int column, int line) {
		if (parenthesisGuides == null || line < 0
				|| line >= parenthesisGuides.length || column < 0
				|| column % tabWidth != 0) {
			return false;
		}
		int level = column / tabWidth;
		return level < MAX_LEVELS
				&& (parenthesisGuides[line] & (1L << level)) != 0;
	}

	/**
	 * The column of the outermost structural parenthesis guide of a line, that
	 * is, the indentation of the outermost multi-line parenthesis still open
	 * there. Guides left of it belong to the enclosing blocks and are drawn as
	 * usual; only the columns right of it are continuation alignment and must
	 * not be mistaken for nested blocks.
	 *
	 * @param line
	 *            the widget line number
	 * @return the column of the outermost parenthesis guide, or -1
	 */
	public int outermostParenthesisGuide(int line) {
		if (parenthesisGuides == null || line < 0
				|| line >= parenthesisGuides.length) {
			return -1;
		}
		long bits = parenthesisGuides[line];
		if (bits == 0) {
			return -1;
		}
		for (int level = 0; level < MAX_LEVELS; level++) {
			if ((bits & (1L << level)) != 0) {
				return level * tabWidth;
			}
		}
		return -1;
	}

	/** Returns the deepest structural parenthesis guide not right of column. */
	public int parenthesisGuideAtOrBefore(int column, int line) {
		int base = outermostParenthesisGuide(line);
		if (base < 0 || column <= base) {
			return column;
		}
		for (int candidate = column - column % tabWidth; candidate > base;
				candidate -= tabWidth) {
			if (isParenthesisGuide(candidate, line)) {
				return candidate;
			}
		}
		return base;
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
	/**
	 * The nearest line above the given one carrying code, comments and blank
	 * lines skipped.
	 *
	 * @param line
	 *            the line to start from, excluded
	 * @return the widget line number, or -1
	 */
	private int previousCode(int line) {
		for (int i = line - 1; i >= 0; i--) {
			if (!isTransparentLine(lineAt(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The nearest line below the given one carrying code.
	 *
	 * @param line
	 *            the line to start from, excluded
	 * @param lineCount
	 *            the number of lines of the text
	 * @return the widget line number, or -1
	 */
	private int nextCode(int line, int lineCount) {
		for (int i = line + 1; i < lineCount; i++) {
			if (!isTransparentLine(lineAt(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The indentation the brace on the given line has to be read at: that of
	 * the line the statement starts on.
	 * <p>
	 * A header spread over several lines, <code>catch (A | B</code> continued
	 * by <code>| C e) {</code>, carries its brace on a line indented to the
	 * alignment of the parentheses, which is alignment and not a level. The
	 * block still opens where the statement does, and it is that indentation
	 * the guide of its body lines up with.
	 *
	 * @param line
	 *            the widget line number of the line carrying the brace
	 * @return the indentation of the line the statement starts on, in columns
	 */
	private int openIndent(int line) {
		int start = line;
		while (start > 0 && isParenthesisContext(start)) {
			start--;
		}
		return countSpaces(lineAt(start));
	}

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
		// The braces of the block are the lines just outside the region, and
		// they are what the guide has to line up with. The first line inside
		// the region stands in for the brace above only when there is no brace
		// above: that is how a brace shifted right is caught, the region having
		// swallowed it because its indent is deeper than the guide. Read the
		// other way round, the line inside would answer for every block nested
		// one level deeper and grey correct code.
		int above = previousCode(start);
		if (above >= 0 && opensBlock(lineAt(above))) {
			bad = openIndent(above) != col;
		} else if (opensBlock(lineAt(start))) {
			bad = openIndent(start) != col;
		}
		if (!bad) {
			int below = nextCode(end, n);
			if (below >= 0 && closesBlock(lineAt(below))) {
				bad = countSpaces(lineAt(below)) != col;
			} else if (closesBlock(lineAt(end))) {
				bad = countSpaces(lineAt(end)) != col;
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
