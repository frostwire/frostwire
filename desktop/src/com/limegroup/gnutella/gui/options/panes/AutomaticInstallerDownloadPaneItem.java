package com.limegroup.gnutella.gui.options.panes;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JCheckBox;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UpdateSettings;

public class AutomaticInstallerDownloadPaneItem extends AbstractPaneItem {

	public final static String TITLE = I18n.tr("Automatic Installer Download");

	public final static String LABEL = I18n.tr("FrostWire can automatically download a new installer via BitTorrent for you when it's available. It won't install it but next time you start FrostWire it'll let you know that it's there for you.");
	
	private final JCheckBox _checkbox;

	private boolean isDirty = false;
	
	public AutomaticInstallerDownloadPaneItem() {
		super(TITLE,LABEL);
		_checkbox = new JCheckBox(I18n.tr("Download new installers for me (Recommended)"));
		_checkbox.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				isDirty = true;
			}
		});
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
	public boolean applyOptions() throws IOException {
		UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(_checkbox.isSelected());
		return isDirty;
	}

}
