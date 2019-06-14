package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.gui.WindowsUtils;
import com.limegroup.gnutella.settings.StartupSettings;
import com.limegroup.gnutella.util.MacOSXUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;

/**
 * This class defines the panel in the options window that allows the user
 * to change whether or not FrostWire should automatically start on system
 * startup.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class StartupPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("System Startup");
    private final static String LABEL = I18n.tr("You can choose whether or not to automatically run FrostWire when your computer starts.");
    /**
     * Constant for the check box that specifies whether or not uploads
     * should be automatically cleared.
     */
    private final JCheckBox CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     *
     */
    public StartupPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant for the key of the locale-specific <code>String</code> for the
          upload pane check box label in the options window.
         */
        String CHECK_BOX_LABEL = I18n.tr("Run on System Startup:");
        LabeledComponent comp =
                new LabeledComponent(CHECK_BOX_LABEL, CHECK_BOX,
                        LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(comp.getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        CHECK_BOX.setSelected(StartupSettings.RUN_ON_STARTUP.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window.
     */
    public boolean applyOptions() {
        StartupSettings.RUN_ON_STARTUP.setValue(CHECK_BOX.isSelected());
        // If on OSX, add or remove the item from the login items.
        if (OSUtils.isMacOSX())
            MacOSXUtils.setLoginStatus(CHECK_BOX.isSelected());
        else if (WindowsUtils.isLoginStatusAvailable())
            WindowsUtils.setLoginStatus(CHECK_BOX.isSelected());
        return false;
    }

    public boolean isDirty() {
        return StartupSettings.RUN_ON_STARTUP.getValue() != CHECK_BOX.isSelected();
    }
}
