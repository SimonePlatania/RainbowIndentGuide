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
package jp.sourceforge.pdt_tools.indentguide.preferences;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.graphics.RGB;

/**
 * A snapshot of the preferences the painter draws from. It is refreshed from
 * the preference store on every change notification, so that the paint listener
 * never has to reach the store itself.
 */
public class IndentGuideSettings {

	private static final RGB FALLBACK = new RGB(128, 128, 128);

	private int lineAlpha;
	private int lineStyle;
	private int lineWidth;
	private int lineShift;
	private boolean drawLeftEnd;

	private boolean rainbowEnabled;
	private RGB[] palette = new RGB[] { FALLBACK };

	private boolean activeEnabled;
	private boolean caretHighlightEnabled =
			IndentGuideStyle.DEFAULT_CARET_HIGHLIGHT_ENABLED;
	private int activeAlpha = IndentGuideStyle.DEFAULT_ACTIVE_ALPHA;
	private int activeLighten = IndentGuideStyle.DEFAULT_ACTIVE_LIGHTEN;

	private boolean braceColorEnabled = IndentGuideStyle.DEFAULT_BRACE_COLOR_ENABLED;
	private boolean parenthesisColorEnabled =
			IndentGuideStyle.DEFAULT_PARENTHESIS_COLOR_ENABLED;
	private RGB[] parenthesisPalette = new RGB[] { FALLBACK };

	private boolean irregularEnabled;
	private RGB irregularColor = parse(IndentGuideStyle.DEFAULT_IRREGULAR_COLOR,
			FALLBACK);
	private int irregularAlpha = IndentGuideStyle.DEFAULT_IRREGULAR_ALPHA;
	private int irregularFlash = IndentGuideStyle.DEFAULT_IRREGULAR_FLASH;

	/**
	 * Rereads every value from the given store.
	 *
	 * @param store
	 *            the preference store of the plug-in
	 */
	public void load(IPreferenceStore store) {
		IndentGuideStyle.applyDefaults(store);

		lineAlpha = store.getInt(PreferenceConstants.LINE_ALPHA);
		lineStyle = store.getInt(PreferenceConstants.LINE_STYLE);
		lineWidth = store.getInt(PreferenceConstants.LINE_WIDTH);
		lineShift = store.getInt(PreferenceConstants.LINE_SHIFT);
		drawLeftEnd = store.getBoolean(PreferenceConstants.DRAW_LEFT_END);

		rainbowEnabled = store.getBoolean(IndentGuideStyle.RAINBOW_ENABLED);
		activeEnabled = store.getBoolean(IndentGuideStyle.ACTIVE_ENABLED);
		caretHighlightEnabled = store
				.getBoolean(IndentGuideStyle.CARET_HIGHLIGHT_ENABLED);
		irregularEnabled = store.getBoolean(IndentGuideStyle.IRREGULAR_ENABLED);
		braceColorEnabled = store
				.getBoolean(IndentGuideStyle.BRACE_COLOR_ENABLED);
		parenthesisColorEnabled = store
				.getBoolean(IndentGuideStyle.PARENTHESIS_COLOR_ENABLED);
		activeLighten = store.getInt(IndentGuideStyle.ACTIVE_LIGHTEN);

		activeAlpha = IndentGuideStyle.DEFAULT_ACTIVE_ALPHA;
		int alpha = readInt(store, IndentGuideStyle.ACTIVE_ALPHA);
		if (alpha > 0 && alpha <= 255) {
			activeAlpha = alpha;
		}

		irregularAlpha = IndentGuideStyle.DEFAULT_IRREGULAR_ALPHA;
		int irregular = readInt(store, IndentGuideStyle.IRREGULAR_ALPHA);
		if (irregular > 0 && irregular <= 255) {
			irregularAlpha = irregular;
		}
		irregularColor = parse(
				readString(store, IndentGuideStyle.IRREGULAR_COLOR,
						IndentGuideStyle.DEFAULT_IRREGULAR_COLOR),
				parse(IndentGuideStyle.DEFAULT_IRREGULAR_COLOR, FALLBACK));

		irregularFlash = IndentGuideStyle.DEFAULT_IRREGULAR_FLASH;
		int flash = readInt(store, IndentGuideStyle.IRREGULAR_FLASH);
		if (flash >= 0 && flash <= 10000) {
			irregularFlash = flash;
		}

		palette = readPalette(store);
		parenthesisPalette = readParenthesisPalette(store);
	}

	private RGB[] readParenthesisPalette(IPreferenceStore store) {
		RGB[] colors = new RGB[IndentGuideStyle.PARENTHESIS_COLOR_COUNT];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = parse(readString(store,
					IndentGuideStyle.parenthesisKey(i),
					IndentGuideStyle.DEFAULT_RAINBOW[i]), FALLBACK);
		}
		return colors;
	}

	private RGB[] readPalette(IPreferenceStore store) {
		String[] specs;
		if (rainbowEnabled) {
			specs = new String[IndentGuideStyle.RAINBOW_COLOR_COUNT];
			for (int i = 0; i < specs.length; i++) {
				specs[i] = readString(store, IndentGuideStyle.rainbowKey(i),
						IndentGuideStyle.DEFAULT_RAINBOW[i]);
			}
		} else {
			specs = new String[] { readString(store,
					PreferenceConstants.LINE_COLOR,
					IndentGuideStyle.DEFAULT_LINE_COLOR) };
		}
		RGB[] colors = new RGB[specs.length];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = parse(specs[i], FALLBACK);
		}
		return colors;
	}

	private static String readString(IPreferenceStore store, String key,
			String fallback) {
		String value;
		try {
			value = store.getString(key);
		} catch (Exception e) {
			value = null;
		}
		if (value == null || value.trim().length() == 0) {
			return fallback;
		}
		return value;
	}

	private static int readInt(IPreferenceStore store, String key) {
		try {
			return store.getInt(key);
		} catch (Exception e) {
			return -1;
		}
	}

	private static RGB parse(String spec, RGB fallback) {
		try {
			String[] parts = spec.trim().split(","); //$NON-NLS-1$
			return new RGB(Integer.parseInt(parts[0].trim()) & 255,
					Integer.parseInt(parts[1].trim()) & 255,
					Integer.parseInt(parts[2].trim()) & 255);
		} catch (Exception e) {
			return fallback;
		}
	}

	/**
	 * The given color brought forward by the configured percentage: the same
	 * hue, brighter and a little deeper.
	 * <p>
	 * Pushing each channel towards 255 is what a lightening usually is, but it
	 * is also a walk towards white: it raises the brightness by taking the
	 * color out, and a lit yellow guide comes out whitish rather than yellower.
	 * Working in hue, saturation and brightness lets the brightness rise while
	 * the saturation rises with it, so the guide turns up the color it already
	 * had.
	 *
	 * @param rgb
	 *            the color of a level
	 * @return the color used for the active guide of that level
	 */
	public RGB lighten(RGB rgb) {
		int pct = (activeLighten < 0 || activeLighten > 100)
				? IndentGuideStyle.DEFAULT_ACTIVE_LIGHTEN : activeLighten;
		float amount = pct / 100f;
		float[] hsb = rgb.getHSB();
		float saturation = hsb[1] + (1 - hsb[1]) * amount * SATURATION_SHARE;
		float brightness = hsb[2] + (1 - hsb[2]) * amount;
		return new RGB(hsb[0], clamp(saturation), clamp(brightness));
	}

	/** How much of the lightening goes into the saturation. */
	private static final float SATURATION_SHARE = 0.4f;

	private static float clamp(float value) {
		if (value < 0) {
			return 0;
		}
		return value > 1 ? 1 : value;
	}

	public int getLineAlpha() {
		return lineAlpha;
	}

	public int getLineStyle() {
		return lineStyle;
	}

	public int getLineWidth() {
		return lineWidth;
	}

	public int getLineShift() {
		return lineShift;
	}

	public boolean isDrawLeftEnd() {
		return drawLeftEnd;
	}

	public RGB[] getPalette() {
		return palette;
	}

	public boolean isActiveEnabled() {
		return activeEnabled;
	}

	public boolean isCaretHighlightEnabled() {
		return caretHighlightEnabled;
	}

	public int getActiveAlpha() {
		return activeAlpha;
	}

	public boolean isIrregularEnabled() {
		return irregularEnabled;
	}

	public RGB getIrregularColor() {
		return irregularColor;
	}

	public int getIrregularAlpha() {
		return irregularAlpha;
	}

	/**
	 * @return how long a guide blinks white once fixed, in milliseconds; 0
	 *         disables the flash
	 */
	public int getIrregularFlash() {
		return irregularFlash;
	}

	public boolean isBraceColorEnabled() {
		return braceColorEnabled;
	}

	public boolean isParenthesisColorEnabled() {
		return parenthesisColorEnabled;
	}

	public RGB[] getParenthesisPalette() {
		return parenthesisPalette;
	}
}
