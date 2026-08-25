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

/**
 * Keys and default values of the style options: per level colors, highlighting
 * of the block under the caret, greying of irregular indentation.
 */
public final class IndentGuideStyle {

	/** Color the guides per indentation level; when off, LINE_COLOR is used. */
	public static final String RAINBOW_ENABLED = "rainbow_enabled"; //$NON-NLS-1$
	/** Colors of the levels, cycled past the last one. */
	public static final String RAINBOW_COLOR_PREFIX = "rainbow_color_"; //$NON-NLS-1$
	public static final int RAINBOW_COLOR_COUNT = 7;

	/** Color matching round parentheses with a separate seven-color palette. */
	public static final String PARENTHESIS_COLOR_ENABLED = "parenthesis_color_enabled"; //$NON-NLS-1$
	public static final String PARENTHESIS_COLOR_PREFIX = "parenthesis_color_"; //$NON-NLS-1$
	public static final int PARENTHESIS_COLOR_COUNT = 7;

	/** Lighten the guide of the block the caret sits in. */
	public static final String ACTIVE_ENABLED = "active_enabled"; //$NON-NLS-1$
	/** Follow the caret when no guide has been pinned with a click. */
	public static final String CARET_HIGHLIGHT_ENABLED = "caret_highlight_enabled"; //$NON-NLS-1$
	/** Opacity of the active guide, 0 to 255. */
	public static final String ACTIVE_ALPHA = "active_alpha"; //$NON-NLS-1$
	/** How much the active guide is lightened, 0 to 100 percent. */
	public static final String ACTIVE_LIGHTEN = "active_lighten"; //$NON-NLS-1$

	/** Repaint the braces of a block in the color of the guide of its line. */
	public static final String BRACE_COLOR_ENABLED = "brace_color_enabled"; //$NON-NLS-1$

	/** Draw the guides of blocks with irregular indentation in grey. */
	public static final String IRREGULAR_ENABLED = "irregular_enabled"; //$NON-NLS-1$
	public static final String IRREGULAR_COLOR = "irregular_color"; //$NON-NLS-1$
	public static final String IRREGULAR_ALPHA = "irregular_alpha"; //$NON-NLS-1$
	/**
	 * How long a guide blinks white once its indentation is fixed, in
	 * milliseconds. The greying says something is wrong; this says it is over.
	 */
	public static final String IRREGULAR_FLASH = "irregular_flash"; //$NON-NLS-1$

	public static final String[] DEFAULT_RAINBOW = {
			"169,96,95", "169,138,95", "138,169,95", "95,169,126", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"95,138,169", "126,95,169", "169,95,138" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	public static final String DEFAULT_LINE_COLOR = "60,60,64"; //$NON-NLS-1$
	public static final String DEFAULT_IRREGULAR_COLOR = "110,110,118"; //$NON-NLS-1$
	public static final int DEFAULT_ACTIVE_ALPHA = 255;
	public static final int DEFAULT_ACTIVE_LIGHTEN = 55;
	public static final boolean DEFAULT_CARET_HIGHLIGHT_ENABLED = false;
	public static final int DEFAULT_IRREGULAR_ALPHA = 70;
	public static final int DEFAULT_IRREGULAR_FLASH = 650;
	public static final boolean DEFAULT_BRACE_COLOR_ENABLED = true;
	public static final boolean DEFAULT_PARENTHESIS_COLOR_ENABLED = true;

	private IndentGuideStyle() {
	}

	public static String rainbowKey(int index) {
		return RAINBOW_COLOR_PREFIX + (index + 1);
	}

	public static String parenthesisKey(int index) {
		return PARENTHESIS_COLOR_PREFIX + (index + 1);
	}

	/**
	 * Installs the default values of the style keys. Idempotent, and called
	 * both from the preference page and from the painter, so that the settings
	 * hold even before the page has been opened once.
	 *
	 * @param store
	 *            the preference store of the plug-in
	 */
	public static void applyDefaults(IPreferenceStore store) {
		if (store == null) {
			return;
		}
		store.setDefault(RAINBOW_ENABLED, true);
		for (int i = 0; i < RAINBOW_COLOR_COUNT; i++) {
			store.setDefault(rainbowKey(i), DEFAULT_RAINBOW[i]);
		}
		store.setDefault(PARENTHESIS_COLOR_ENABLED,
				DEFAULT_PARENTHESIS_COLOR_ENABLED);
		for (int i = 0; i < PARENTHESIS_COLOR_COUNT; i++) {
			store.setDefault(parenthesisKey(i), DEFAULT_RAINBOW[i]);
		}
		store.setDefault(ACTIVE_ENABLED, true);
		store.setDefault(CARET_HIGHLIGHT_ENABLED,
				DEFAULT_CARET_HIGHLIGHT_ENABLED);
		store.setDefault(ACTIVE_ALPHA, DEFAULT_ACTIVE_ALPHA);
		store.setDefault(ACTIVE_LIGHTEN, DEFAULT_ACTIVE_LIGHTEN);
		store.setDefault(BRACE_COLOR_ENABLED, DEFAULT_BRACE_COLOR_ENABLED);
		store.setDefault(IRREGULAR_ENABLED, false);
		store.setDefault(IRREGULAR_COLOR, DEFAULT_IRREGULAR_COLOR);
		store.setDefault(IRREGULAR_ALPHA, DEFAULT_IRREGULAR_ALPHA);
		store.setDefault(IRREGULAR_FLASH, DEFAULT_IRREGULAR_FLASH);
	}
}
