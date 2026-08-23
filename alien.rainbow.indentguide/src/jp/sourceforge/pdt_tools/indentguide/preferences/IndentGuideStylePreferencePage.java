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

import jp.sourceforge.pdt_tools.indentguide.Activator;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * The "Style" page, nested under the main Indent Guide page: it gathers the
 * options added by this fork, leaving the original page untouched.
 * <p>
 * Every field sits on the same parent, which is the only way a
 * {@link FieldEditorPreferencePage} in GRID mode lines the columns up.
 */
public class IndentGuideStylePreferencePage extends FieldEditorPreferencePage
		implements IWorkbenchPreferencePage {

	private BooleanFieldEditor rainbowEnabled;
	private BooleanFieldEditor activeEnabled;
	private BooleanFieldEditor irregularEnabled;

	private final ColorFieldEditor[] rainbowColors =
			new ColorFieldEditor[IndentGuideStyle.RAINBOW_COLOR_COUNT];
	private IntegerFieldEditor activeAlpha;
	private IntegerFieldEditor activeLighten;
	private ColorFieldEditor irregularColor;
	private IntegerFieldEditor irregularAlpha;

	/** In GRID mode this is always the same composite, needed for setEnabled. */
	private Composite parent;

	public IndentGuideStylePreferencePage() {
		super(GRID);
	}

	public void init(IWorkbench workbench) {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		IndentGuideStyle.applyDefaults(store);
		setPreferenceStore(store);
		setDescription("Colors and highlighting of the indent guides.");
	}

	protected void createFieldEditors() {
		parent = getFieldEditorParent();

		rainbowEnabled = new BooleanFieldEditor(
				IndentGuideStyle.RAINBOW_ENABLED,
				"Color each indentation level (rainbow)", parent);
		addField(rainbowEnabled);
		for (int i = 0; i < rainbowColors.length; i++) {
			rainbowColors[i] = new ColorFieldEditor(
					IndentGuideStyle.rainbowKey(i),
					"    Level " + (i + 1) + ":", parent);
			addField(rainbowColors[i]);
		}

		activeEnabled = new BooleanFieldEditor(IndentGuideStyle.ACTIVE_ENABLED,
				"Highlight the guide of the block holding the caret", parent);
		addField(activeEnabled);
		activeLighten = new IntegerFieldEditor(IndentGuideStyle.ACTIVE_LIGHTEN,
				"    Lighten (0-100%):", parent);
		activeLighten.setValidRange(0, 100);
		addField(activeLighten);
		activeAlpha = new IntegerFieldEditor(IndentGuideStyle.ACTIVE_ALPHA,
				"    Opacity (0-255):", parent);
		activeAlpha.setValidRange(0, 255);
		addField(activeAlpha);

		irregularEnabled = new BooleanFieldEditor(
				IndentGuideStyle.IRREGULAR_ENABLED,
				"Grey out the guides of blocks with mixed tabs and spaces",
				parent);
		addField(irregularEnabled);
		irregularColor = new ColorFieldEditor(IndentGuideStyle.IRREGULAR_COLOR,
				"    Color:", parent);
		addField(irregularColor);
		irregularAlpha = new IntegerFieldEditor(
				IndentGuideStyle.IRREGULAR_ALPHA, "    Opacity (0-255):",
				parent);
		irregularAlpha.setValidRange(0, 255);
		addField(irregularAlpha);
	}

	/**
	 * Buttons next to "Apply" and "Restore Defaults": shortcuts for the two
	 * ways the plug-in is used, colored or plain, plus a full reset.
	 *
	 * @param buttonBar
	 *            the bar the buttons are added to
	 */
	protected void contributeButtons(Composite buttonBar) {
		((GridLayout) buttonBar.getLayout()).numColumns += 3;
		button(buttonBar, "Rainbow", new Runnable() {

			public void run() {
				applyRainbow();
			}
		});
		button(buttonBar, "Plain", new Runnable() {

			public void run() {
				applyPlain();
			}
		});
		button(buttonBar, "Restore original", new Runnable() {

			public void run() {
				applyOriginal();
			}
		});
	}

	private void button(Composite parent, String text, final Runnable action) {
		Button b = new Button(parent, SWT.PUSH);
		b.setText(text);
		b.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {
				action.run();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
				action.run();
			}
		});
	}

	private void applyRainbow() {
		IPreferenceStore store = getPreferenceStore();
		store.setValue(IndentGuideStyle.RAINBOW_ENABLED, true);
		for (int i = 0; i < IndentGuideStyle.RAINBOW_COLOR_COUNT; i++) {
			store.setValue(IndentGuideStyle.rainbowKey(i),
					IndentGuideStyle.DEFAULT_RAINBOW[i]);
		}
		reloadFields();
	}

	private void applyPlain() {
		getPreferenceStore().setValue(IndentGuideStyle.RAINBOW_ENABLED, false);
		reloadFields();
	}

	private void applyOriginal() {
		IPreferenceStore store = getPreferenceStore();
		store.setValue(PreferenceConstants.ENABLED, true);
		store.setValue(PreferenceConstants.DRAW_LEFT_END, false);
		store.setValue(PreferenceConstants.DRAW_BLANK_LINE, true);
		store.setValue(PreferenceConstants.SKIP_COMMENT_BLOCK, false);
		store.setValue(PreferenceConstants.LINE_ALPHA, 200);
		store.setValue(PreferenceConstants.LINE_WIDTH, 1);
		store.setValue(PreferenceConstants.LINE_SHIFT, 0);
		store.setValue(PreferenceConstants.LINE_COLOR,
				IndentGuideStyle.DEFAULT_LINE_COLOR);
		store.setValue(IndentGuideStyle.ACTIVE_ENABLED, true);
		store.setValue(IndentGuideStyle.ACTIVE_ALPHA,
				IndentGuideStyle.DEFAULT_ACTIVE_ALPHA);
		store.setValue(IndentGuideStyle.ACTIVE_LIGHTEN,
				IndentGuideStyle.DEFAULT_ACTIVE_LIGHTEN);
		store.setValue(IndentGuideStyle.IRREGULAR_ENABLED, false);
		store.setValue(IndentGuideStyle.IRREGULAR_COLOR,
				IndentGuideStyle.DEFAULT_IRREGULAR_COLOR);
		store.setValue(IndentGuideStyle.IRREGULAR_ALPHA,
				IndentGuideStyle.DEFAULT_IRREGULAR_ALPHA);
		applyRainbow();
	}

	private void reloadFields() {
		rainbowEnabled.load();
		for (int i = 0; i < rainbowColors.length; i++) {
			rainbowColors[i].load();
		}
		activeEnabled.load();
		activeAlpha.load();
		activeLighten.load();
		irregularEnabled.load();
		irregularColor.load();
		irregularAlpha.load();
		updateEnablement();
	}

	protected void initialize() {
		super.initialize();
		updateEnablement();
	}

	protected void performDefaults() {
		super.performDefaults();
		updateEnablement();
	}

	public void propertyChange(PropertyChangeEvent event) {
		super.propertyChange(event);
		if (event.getSource() instanceof FieldEditor) {
			updateEnablement();
		}
	}

	private void updateEnablement() {
		if (parent == null || parent.isDisposed()) {
			return;
		}
		boolean rainbow = rainbowEnabled.getBooleanValue();
		for (int i = 0; i < rainbowColors.length; i++) {
			rainbowColors[i].setEnabled(rainbow, parent);
		}
		boolean active = activeEnabled.getBooleanValue();
		activeLighten.setEnabled(active, parent);
		activeAlpha.setEnabled(active, parent);
		boolean irregular = irregularEnabled.getBooleanValue();
		irregularColor.setEnabled(irregular, parent);
		irregularAlpha.setEnabled(irregular, parent);
	}
}
