package jp.sourceforge.pdt_tools.indentguide.preferences;

import jp.sourceforge.pdt_tools.indentguide.Activator;

import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	/**
	 * The content types the guides are drawn on out of the box. The match is on
	 * exact equality, so listing CT_TEXT alone leaves the Java, XML and JSP
	 * editors without guides.
	 */
	private static final String[] DEFAULT_CONTENT_TYPES = {
			IContentTypeManager.CT_TEXT,
			"org.eclipse.jdt.core.javaSource", //$NON-NLS-1$
			"org.eclipse.jdt.core.javaProperties", //$NON-NLS-1$
			"org.eclipse.core.runtime.xml", //$NON-NLS-1$
			"org.eclipse.jst.jsp.core.jspsource", //$NON-NLS-1$
			"org.eclipse.wst.html.core.htmlsource", //$NON-NLS-1$
			"org.eclipse.wst.css.core.csssource", //$NON-NLS-1$
			"org.eclipse.wst.jsdt.core.jsSource", //$NON-NLS-1$
			"org.eclipse.php.core.phpsource", //$NON-NLS-1$
			"org.eclipse.cdt.core.cSource", //$NON-NLS-1$
			"org.eclipse.cdt.core.cxxSource" }; //$NON-NLS-1$

	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.ENABLED, true);
		// The guides are a hint, not text: they stay well under the code they
		// run beside, so that the eye finds them only when it looks for them.
		store.setDefault(PreferenceConstants.LINE_ALPHA, 130);
		store.setDefault(PreferenceConstants.LINE_STYLE, SWT.LINE_SOLID);
		store.setDefault(PreferenceConstants.LINE_WIDTH, 1);
		store.setDefault(PreferenceConstants.LINE_SHIFT, 0);
		store.setDefault(PreferenceConstants.LINE_COLOR,
				IndentGuideStyle.DEFAULT_LINE_COLOR);
		store.setDefault(PreferenceConstants.DRAW_LEFT_END, false);
		store.setDefault(PreferenceConstants.DRAW_BLANK_LINE, true);
		store.setDefault(PreferenceConstants.SKIP_COMMENT_BLOCK, false);
		store.setDefault(PreferenceConstants.CONTENT_TYPES,
				join(DEFAULT_CONTENT_TYPES));
		IndentGuideStyle.applyDefaults(store);
	}

	/** The content types are stored as a single "|" separated string. */
	private static String join(String[] values) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append('|');
			}
			sb.append(values[i]);
		}
		return sb.toString();
	}

}
