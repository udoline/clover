package org.openclover.eclipse.core.launching;

import org.openclover.eclipse.core.CloverPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;

public class CloveredLaunchShortcut implements ILaunchShortcut, IExecutableExtension {
    private static ILaunchShortcut EMPTY_SHORTCUT_DELEGATE = new ILaunchShortcut() {
        @Override
        public void launch(ISelection selection, String mode) { }
        @Override
        public void launch(IEditorPart editor, String mode) { }
    };

    private ILaunchShortcut delegate;

    @Override
    public void setInitializationData(IConfigurationElement config, String propertyName, Object data) throws CoreException {
        String delegateId = String.valueOf(data);

        try {
            IExtensionPoint extensionPoint =
                Platform.getExtensionRegistry().getExtensionPoint(
                    IDebugUIConstants.PLUGIN_ID,
                    IDebugUIConstants.EXTENSION_POINT_LAUNCH_SHORTCUTS);
            
            IConfigurationElement[] configs = extensionPoint.getConfigurationElements();
            for (IConfigurationElement cfg : configs) {
                if (delegateId.equals(cfg.getAttribute(LaunchingConstants.ID_ATTRIBUTE))) {
                    delegate = (ILaunchShortcut) cfg.createExecutableExtension(LaunchingConstants.CLASS_EXTENSION);
                }
            }
        } catch (CoreException e) {
            CloverPlugin.logError("Error creating launch delegate", e);
        }
        
        if (delegate == null) {
            delegate = EMPTY_SHORTCUT_DELEGATE;
            CloverPlugin.logError("Launch shortcut not found for id: " + delegateId);
        }
    }

    @Override
    public void launch(ISelection selection, String mode) {
        delegate.launch(selection, LaunchingConstants.CLOVER_MODE);
    }

    @Override
    public void launch(IEditorPart editor, String mode) {
        delegate.launch(editor, LaunchingConstants.CLOVER_MODE);
    }

}