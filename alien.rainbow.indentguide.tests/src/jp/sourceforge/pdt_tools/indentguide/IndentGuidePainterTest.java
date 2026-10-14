package jp.sourceforge.pdt_tools.indentguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Paints a text viewer with and without the painter, as an editor is painted, with the default preferences.
 */
public class IndentGuidePainterTest {

	private static final String TEXT = String.join("\n",
			"class A {",
			"\tvoid f() {",
			"\t\tif (x) {",
			"\t\t\tg(1, (2));",
			"\t\t}",
			"\t}",
			"}",
			"");

	private Shell shell;
	private SourceViewer viewer;

	@BeforeEach
	public void createViewer() {
		shell = new Shell(Display.getDefault());
		shell.setLayout(new FillLayout());
		viewer = new SourceViewer(shell, null, SWT.V_SCROLL | SWT.H_SCROLL);
		viewer.setDocument(new Document(TEXT));
		// a blinking caret would differ from one picture to the next
		viewer.getTextWidget().setCaret(null);
		shell.setSize(600, 300);
		shell.open();
	}

	@AfterEach
	public void disposeViewer() {
		shell.dispose();
	}

	@Test
	public void testGuidesArePainted() {
		ImageData without = paint();
		// the viewer activates the painter as it adds it, as for an editor
		IndentGuidePainter painter = new IndentGuidePainter(viewer);
		viewer.addPainter(painter);
		assertTrue(differences(without, paint()) > 0, "the guides should be painted");

		viewer.removePainter(painter);
		assertEquals(0, differences(without, paint()), "nothing should be painted once the painter is removed");
		painter.dispose();
	}

	/**
	 * The viewer tells the painter of an edit: the guides of the new, deeper block are painted too.
	 */
	@Test
	public void testGuidesOfAnEdit() throws Exception {
		int before = guidePixels(() -> {
		});
		int after = guidePixels(
				() -> viewer.getDocument().replace(TEXT.indexOf("\t\t}"), 0, "\t\t\tif (y) {\n\t\t\t\th();\n\t\t\t}\n"));
		assertTrue(after > before, "the guides of the new block should be painted: " + before + " then " + after);
	}

	private interface Edit {
		void run() throws Exception;
	}

	/**
	 * Adds a painter, makes the given edit, and returns the number of pixels that the painter paints: the picture
	 * with the painter compared with the one once it is removed.
	 */
	private int guidePixels(Edit edit) throws Exception {
		IndentGuidePainter painter = new IndentGuidePainter(viewer);
		viewer.addPainter(painter);
		paint();
		edit.run();
		ImageData with = paint();
		viewer.removePainter(painter);
		painter.dispose();
		return differences(paint(), with);
	}

	/** Paints the text widget, with its paint listeners, as the screen would show it. */
	private ImageData paint() {
		StyledText text = viewer.getTextWidget();
		Display display = text.getDisplay();
		while (display.readAndDispatch()) {
			// let the viewer settle
		}
		Image image = new Image(display, text.getSize().x, text.getSize().y);
		GC gc = new GC(image);
		try {
			assertTrue(text.print(gc), "the text widget should be printed");
		} finally {
			gc.dispose();
		}
		ImageData data = image.getImageData();
		image.dispose();
		return data;
	}

	private static int differences(ImageData a, ImageData b) {
		assertEquals(a.width, b.width);
		assertEquals(a.height, b.height);
		int count = 0;
		for (int y = 0; y < a.height; y++) {
			for (int x = 0; x < a.width; x++) {
				if (!a.palette.getRGB(a.getPixel(x, y)).equals(b.palette.getRGB(b.getPixel(x, y)))) {
					count++;
				}
			}
		}
		return count;
	}
}
