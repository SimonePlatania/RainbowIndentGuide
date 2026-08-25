package jp.sourceforge.pdt_tools.indentguide;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import jp.sourceforge.pdt_tools.indentguide.preferences.PreferenceConstants;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IPainter;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.IDocumentProviderExtension4;
import org.eclipse.ui.texteditor.ITextEditor;

public class Starter implements IStartup {

	/** How many times the source viewer of an editor is waited for. */
	private static final int VIEWER_ATTEMPTS = 20;

	/** The delay between two of those attempts, in milliseconds. */
	private static final int VIEWER_RETRY_DELAY = 100;

	/**
	 * The viewers a painter has already been added to. An editor is offered to
	 * {@link #addListener(IEditorPart)} by several events, and every one of them
	 * has to be listened to: a tab restored with the workbench is never opened
	 * again, so waiting for <code>partOpened</code> alone leaves it without
	 * guides until it is closed and opened by hand. Painting it twice would
	 * double the opacity of every guide, hence this set.
	 */
	private final Map<Object, Boolean> painted = new WeakHashMap<Object, Boolean>();

	/** The windows a part listener is already installed on. */
	private final Map<Object, Boolean> listened = new WeakHashMap<Object, Boolean>();

	private void addListener(IEditorPart part) {
		addListener(part, VIEWER_ATTEMPTS);
	}

	private void addListener(final IEditorPart part, final int attemptsLeft) {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		if (store.getBoolean(PreferenceConstants.ENABLED)) {
			if (part instanceof AbstractTextEditor) {
				IContentType contentType = null;
				ITextEditor textEditor = (ITextEditor) part;
				IDocumentProvider provider = textEditor.getDocumentProvider();
				if (provider instanceof IDocumentProviderExtension4) {
					try {
						contentType = ((IDocumentProviderExtension4) provider)
								.getContentType(textEditor.getEditorInput());
					} catch (CoreException e) {
					}
				}
				if (contentType == null) {
					return;
				}
				String id = contentType.getId();
				String type = store
						.getString(PreferenceConstants.CONTENT_TYPES);
				String[] types = type.split("\\|");
				List<String> contentTypes = new LinkedList<String>();
				for (int i = 0; i < types.length; i++) {
					contentTypes.add(types[i]);
				}
				if (!contentTypes.contains(id)) {
					return;
				}
				Class<?> editor = part.getClass();
				while (!editor.equals(AbstractTextEditor.class)) {
					editor = editor.getSuperclass();
				}
				try {
					Method method = editor.getDeclaredMethod("getSourceViewer", //$NON-NLS-1$
							(Class[]) null);
					method.setAccessible(true);
					Object viewer = method.invoke(part, (Object[]) null);
					if (viewer instanceof ITextViewerExtension2) {
						if (painted.containsKey(viewer)) {
							return;
						}
						painted.put(viewer, Boolean.TRUE);
						IPainter painter = new IndentGuidePainter(
								(ITextViewer) viewer);
						((ITextViewerExtension2) viewer).addPainter(painter);
						liftComments((ITextViewer) viewer);
					} else if (viewer == null) {
						// The editor exists but its widget is not built yet,
						// which is the usual state of a tab being restored.
						// There is no event for that step, so it is waited for.
						retry(part, attemptsLeft);
					}
				} catch (SecurityException e) {
					Activator.log(e);
				} catch (NoSuchMethodException e) {
					Activator.log(e);
				} catch (IllegalArgumentException e) {
					Activator.log(e);
				} catch (IllegalAccessException e) {
					Activator.log(e);
				} catch (InvocationTargetException e) {
					Activator.log(e);
				}
			}
		}
	}

	/**
	 * Lets backspace lift a comment onto the line above it.
	 * <p>
	 * The listener is prepended where the viewer allows it, so that it is
	 * offered the keystroke before the editor's own handlers; on a viewer that
	 * does not, the widget takes it, which comes to the same thing as long as
	 * nothing else has consumed the key.
	 *
	 * @param viewer
	 *            the source viewer of the editor
	 */
	private void liftComments(ITextViewer viewer) {
		VerifyKeyListener lifter = new CommentLifter(viewer);
		if (viewer instanceof ITextViewerExtension) {
			((ITextViewerExtension) viewer).prependVerifyKeyListener(lifter);
			return;
		}
		StyledText widget = viewer.getTextWidget();
		if (widget != null && !widget.isDisposed()) {
			widget.addVerifyKeyListener(lifter);
		}
	}

	/**
	 * Tries again a little later, up to {@link #VIEWER_ATTEMPTS} times, to give
	 * an editor still being restored the time to build its viewer.
	 *
	 * @param part
	 *            the editor waited for
	 * @param attemptsLeft
	 *            how many attempts are left, this one included
	 */
	private void retry(final IEditorPart part, final int attemptsLeft) {
		if (attemptsLeft <= 1 || !PlatformUI.isWorkbenchRunning()) {
			return;
		}
		PlatformUI.getWorkbench().getDisplay()
				.timerExec(VIEWER_RETRY_DELAY, new Runnable() {
					public void run() {
						addListener(part, attemptsLeft - 1);
					}
				});
	}

	public void earlyStartup() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			public void run() {
				IWorkbench workbench = PlatformUI.getWorkbench();
				IWorkbenchWindow[] windows = workbench.getWorkbenchWindows();
				for (int i = 0; i < windows.length; i++) {
					attach(windows[i]);
				}
				workbench.addWindowListener(new WindowListener());
			}
		});
	}

	/**
	 * Paints the editors a window already holds and listens for the ones it
	 * will hold. Every tab is taken, not only the active one: the others are
	 * restored without ever being opened, and would otherwise stay bare.
	 *
	 * @param window
	 *            the workbench window
	 */
	private void attach(IWorkbenchWindow window) {
		if (window == null) {
			return;
		}
		IWorkbenchPage[] pages = window.getPages();
		for (int i = 0; i < pages.length; i++) {
			IEditorReference[] editors = pages[i].getEditorReferences();
			for (int j = 0; j < editors.length; j++) {
				// false: an editor not restored yet is left alone, it is
				// caught by the part listener when it comes up.
				IEditorPart part = editors[j].getEditor(false);
				if (part != null) {
					addListener(part);
				}
			}
		}
		if (!listened.containsKey(window)) {
			listened.put(window, Boolean.TRUE);
			window.getPartService().addPartListener(new PartListener());
		}
	}

	private class PartListener implements IPartListener2 {

		/**
		 * Takes the editor behind a reference, if it has been restored.
		 * <p>
		 * Activation, visibility and opening all lead here: a tab restored with
		 * the workbench fires no <code>partOpened</code>, an inactive one is
		 * only built when it is first shown, and the guard on the viewers makes
		 * the overlap between the three harmless.
		 *
		 * @param partRef
		 *            the part the event is about
		 */
		private void take(IWorkbenchPartReference partRef) {
			IWorkbenchPart part = partRef == null ? null
					: partRef.getPart(false);
			if (part instanceof IEditorPart) {
				addListener((IEditorPart) part);
			}
		}

		public void partActivated(IWorkbenchPartReference partRef) {
			take(partRef);
		}

		public void partBroughtToTop(IWorkbenchPartReference partRef) {
			take(partRef);
		}

		public void partClosed(IWorkbenchPartReference partRef) {
		}

		public void partDeactivated(IWorkbenchPartReference partRef) {
		}

		public void partOpened(IWorkbenchPartReference partRef) {
			take(partRef);
		}

		public void partHidden(IWorkbenchPartReference partRef) {
		}

		public void partVisible(IWorkbenchPartReference partRef) {
			take(partRef);
		}

		public void partInputChanged(IWorkbenchPartReference partRef) {
		}
	}

	private class WindowListener implements IWindowListener {

		public void windowActivated(IWorkbenchWindow window) {
		}

		public void windowDeactivated(IWorkbenchWindow window) {
		}

		public void windowClosed(IWorkbenchWindow window) {
		}

		public void windowOpened(IWorkbenchWindow window) {
			attach(window);
		}
	}

}
