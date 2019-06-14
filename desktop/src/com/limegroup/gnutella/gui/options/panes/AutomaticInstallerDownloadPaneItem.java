package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UpdateSettings;

import javax.swing.*;

public class AutomaticInstallerDownloadPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Automatic Installer Download");
    private final static String LABEL = I18n.tr("FrostWire can automatically download a new installer via BitTorrent for you when it's available. It won't install it but next time you start FrostWire it'll let you know that it's there for you.");
    private final JCheckBox _checkbox;
    private boolean isDirty = false;

    public AutomaticInstallerDownloadPaneItem() {
        super(TITLE, LABEL);
        _checkbox = new JCheckBox(I18n.tr("Download new installers for me (Recommended)"));
        _checkbox.addActionListener(e -> isDirty = true);
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void initOptions() {
        _checkbox.setSelected(UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.getValue());
        add(_checkbox);
    }

    @Override
    public boolean applyOptions() {
        UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(_checkbox.isSelected());
        return isDirty;
    }
}
