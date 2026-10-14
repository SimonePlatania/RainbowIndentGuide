package jp.sourceforge.pdt_tools.indentguide;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/**
 * Checks {@link ParenthesisScanner} against the scan that the painter did on
 * each paint in 1.2.5, which drew each pair as soon as it was closed.
 */
public class ParenthesisScannerTest {

	private static final int TABS = 4;

	private static final int COLORS = 7;

	@Test
	public void testNestedCalls() {
		String text = "a(b(c), d(e(f)))";
		assertEquals(List.of("1(1", "3(2", "5)2", "9(2", "11(3", "13)3", "14)2", "15)1"), scan(text));
		assertEquals(reference(text), scan(text));
	}

	@Test
	public void testColorStartsFromTheIndentationOfTheLine() {
		String text = "x\n\t\tf(g())\n";
		assertEquals(List.of("5(3", "7(4", "8)4", "9)3"), scan(text));
		assertEquals(reference(text), scan(text));
	}

	@Test
	public void testStringsAndCommentsAreSkipped() {
		String text = "f(\"(\", '(', `(`) // (\n/* ( */ g(# (\n) <!-- ( --> h()";
		assertEquals(reference(text), scan(text));
		assertEquals(List.of("1(1", "15)1", "31(1", "36)1", "50(1", "51)1"), scan(text));
	}

	@Test
	public void testUnmatchedParenthesesAreLeftOut() {
		String text = ")) a(b( c) d";
		assertEquals(List.of("6(2", "9)2"), scan(text));
		assertEquals(reference(text), scan(text));
	}

	@Test
	public void testDeepNesting() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 200; i++) {
			text.append('(');
		}
		for (int i = 0; i < 200; i++) {
			text.append(')');
		}
		assertEquals(400, scan(text.toString()).size());
		assertEquals(reference(text.toString()), scan(text.toString()));
	}

	@Test
	public void testRandomTexts() {
		Random random = new Random(42);
		String alphabet = "((()))\"'`\\/*#<!-\n\t  x";
		for (int n = 0; n < 20_000; n++) {
			int length = random.nextInt(80);
			StringBuilder text = new StringBuilder(length);
			for (int i = 0; i < length; i++) {
				text.append(alphabet.charAt(random.nextInt(alphabet.length())));
			}
			assertEquals(text.toString(), reference(text.toString()), scan(text.toString()));
		}
	}

	@Test
	public void testFirstAtOrAfter() {
		long[] entries = ParenthesisScanner.scan("(a) (b) (c)", TABS, COLORS); // 0 2 4 6 8 10
		assertEquals(0, ParenthesisScanner.firstAtOrAfter(entries, 0));
		assertEquals(1, ParenthesisScanner.firstAtOrAfter(entries, 1));
		assertEquals(1, ParenthesisScanner.firstAtOrAfter(entries, 2));
		assertEquals(5, ParenthesisScanner.firstAtOrAfter(entries, 10));
		assertEquals(6, ParenthesisScanner.firstAtOrAfter(entries, 11));
		assertEquals(0, ParenthesisScanner.firstAtOrAfter(new long[0], 3));
	}

	/** The parentheses found, as offset, glyph and color, in the order of the offsets. */
	private static List<String> scan(String text) {
		List<String> result = new ArrayList<>();
		for (long entry : ParenthesisScanner.scan(text, TABS, COLORS)) {
			result.add(ParenthesisScanner.offset(entry) + "" + ParenthesisScanner.glyph(entry)
					+ ParenthesisScanner.color(entry));
		}
		return result;
	}

	/** The scan of IndentGuidePainter.drawParentheses in 1.2.5, the parentheses it drew sorted by offset. */
	private static List<String> reference(String text) {
		List<long[]> drawn = new ArrayList<>();
		int lineLevel = ParenthesisScanner.indentLevelAt(text, 0, TABS);
		int[] offsets = new int[64];
		int[] colors = new int[64];
		int size = 0;
		int state = 0;
		boolean escaped = false;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
			if (ch == '\n') {
				lineLevel = ParenthesisScanner.indentLevelAt(text, i + 1, TABS);
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
				colors[size] = (size > 0 ? colors[size - 1] + 1 : lineLevel + 1) % COLORS;
				size++;
			} else if (ch == ')' && size > 0) {
				size--;
				drawn.add(new long[] { offsets[size], '(', colors[size] });
				drawn.add(new long[] { i, ')', colors[size] });
			}
		}
		Collections.sort(drawn, (a, b) -> Long.compare(a[0], b[0]));
		List<String> result = new ArrayList<>();
		for (long[] d : drawn) {
			result.add(d[0] + "" + (char) d[1] + d[2]);
		}
		return result;
	}
}
