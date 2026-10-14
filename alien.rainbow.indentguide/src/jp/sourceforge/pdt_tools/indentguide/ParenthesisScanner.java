package jp.sourceforge.pdt_tools.indentguide;

import java.util.Arrays;

/**
 * Finds the matching round parentheses of a document and the color of each
 * pair. A scan reads the whole document, so that a pair keeps its color while
 * either end is scrolled or folded out of view; the painter keeps its result
 * until the document changes, and only draws the parentheses on screen.
 * <p>
 * The result is sorted by offset. Each entry packs the document offset of a
 * parenthesis, whether it closes, and the index of its color in the palette.
 */
final class ParenthesisScanner {

	private static final long CLOSING = 1L << 16;

	private static final long COLOR_MASK = CLOSING - 1;

	private ParenthesisScanner() {
	}

	/**
	 * Scans the given text. An opening offset and its depth are kept on a
	 * stack; consequently a closing parenthesis always receives the exact color
	 * of the opening parenthesis it removes from that stack. Parentheses in
	 * strings and comments are skipped, and the ones without a match are left
	 * out.
	 *
	 * @param text
	 *            the whole document text
	 * @param tabs
	 *            the width of a tabulation, in columns
	 * @param paletteLength
	 *            the number of colors of the palette, at least 1
	 * @return the matched parentheses, sorted by offset
	 */
	static long[] scan(CharSequence text, int tabs, int paletteLength) {
		// The color of a parenthesis carries on from the depth its line is
		// indented to, instead of restarting at the first tint: the innermost
		// guide reaching that line and the parenthesis opening there are two
		// steps of the same nesting, and must not share a color.
		int lineLevel = indentLevelAt(text, 0, tabs);
		int[] offsets = new int[64];
		int[] colors = new int[64];
		int size = 0;
		long[] found = new long[64];
		int count = 0;
		int state = 0; // 0 code, 1 single, 2 double, 3 line, 4 block, 5 backtick, 6 XML comment
		boolean escaped = false;
		int length = text.length();
		for (int i = 0; i < length; i++) {
			char ch = text.charAt(i);
			char next = i + 1 < length ? text.charAt(i + 1) : '\0';
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
				if (ch == '-' && next == '-' && i + 2 < length
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
			if (ch == '<' && next == '!' && i + 3 < length
					&& text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') {
				state = 6; i += 3; continue;
			}
			if (ch == '\'') { state = 1; escaped = false; continue; }
			if (ch == '"') { state = 2; escaped = false; continue; }
			if (ch == '`') { state = 5; escaped = false; continue; }
			if (ch == '(') {
				if (size == offsets.length) {
					offsets = Arrays.copyOf(offsets, size * 2);
					colors = Arrays.copyOf(colors, size * 2);
				}
				offsets[size] = i;
				colors[size] = (size > 0 ? colors[size - 1] + 1 : lineLevel + 1)
						% paletteLength;
				size++;
			} else if (ch == ')' && size > 0) {
				size--;
				if (count + 2 > found.length) {
					found = Arrays.copyOf(found, found.length * 2);
				}
				found[count++] = entry(offsets[size], false, colors[size]);
				found[count++] = entry(i, true, colors[size]);
			}
		}
		long[] result = Arrays.copyOf(found, count);
		Arrays.sort(result);
		return result;
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
	static int indentLevelAt(CharSequence text, int lineStart, int tabs) {
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

	/**
	 * Returns the index of the first entry at or after the given offset, or the
	 * number of entries if there is none.
	 */
	static int firstAtOrAfter(long[] entries, int offset) {
		int low = 0;
		int high = entries.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (offset(entries[middle]) < offset) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	static int offset(long entry) {
		return (int) (entry >>> 32);
	}

	static char glyph(long entry) {
		return (entry & CLOSING) != 0 ? ')' : '(';
	}

	static int color(long entry) {
		return (int) (entry & COLOR_MASK);
	}

	private static long entry(int offset, boolean closing, int color) {
		return ((long) offset << 32) | (closing ? CLOSING : 0) | color;
	}
}
