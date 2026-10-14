package jp.sourceforge.pdt_tools.indentguide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class IndentLevelCalculatorTest {

	private static IndentLevelCalculator calculator(String... lines) {
		IndentLevelCalculator calculator = new IndentLevelCalculator(new IndentLevelCalculator.ILineSource() {

			public int getLineCount() {
				return lines.length;
			}

			public int getCharCount() {
				return String.join("\n", lines).length();
			}

			public String getLine(int line) {
				return lines[line];
			}
		});
		calculator.setTabWidth(4);
		return calculator;
	}

	@Test
	public void testCountSpacesExpandsTabulations() {
		IndentLevelCalculator calculator = calculator();
		assertEquals(0, calculator.countSpaces("x"));
		assertEquals(3, calculator.countSpaces("   x"));
		assertEquals(8, calculator.countSpaces("\t\tx"));
		// a tabulation goes to the next tab stop
		assertEquals(8, calculator.countSpaces("  \t  \tx"));
		assertEquals(4, calculator.countSpaces("    "));
	}

	@Test
	public void testVisualColumn() {
		IndentLevelCalculator calculator = calculator();
		assertEquals(0, calculator.visualColumn("\tx", 0));
		assertEquals(4, calculator.visualColumn("\tx", 1));
		assertEquals(6, calculator.visualColumn("\tab", 3));
		assertEquals(0, calculator.visualColumn(null, 2));
	}

	@Test
	public void testInvalidTabWidth() {
		IndentLevelCalculator calculator = calculator();
		calculator.setTabWidth(0);
		assertEquals(4, calculator.getTabWidth());
	}

	@Test
	public void testBlankLineInsideABlockKeepsItsIndentation() {
		IndentLevelCalculator calculator = calculator(
				"class A {",
				"\tvoid f() {",
				"\t\tx();",
				"",
				"\t\ty();",
				"\t}",
				"}");
		calculator.refresh();
		assertEquals(8, calculator.effectiveIndent(2));
		// the guides go through the blank line, at the indentation of the lines around it
		assertEquals(8, calculator.effectiveIndent(3));
		assertEquals(4, calculator.effectiveIndent(5));
	}
}
