package com.limegroup.gnutella.settings;

import java.io.File;

import org.limewire.setting.FileSetSetting;
import org.limewire.setting.SettingsFactory;

/**
 * Handles installation preferences.
 */
public final class iTunesImportSettings extends LimeWireSettings {

    private static final iTunesImportSettings INSTANCE = new iTunesImportSettings();
    private static final SettingsFactory FACTORY = INSTANCE.getFactory();

    public static iTunesImportSettings instance() {
        return INSTANCE;
    }

    private iTunesImportSettings() {
        super("itunes.props", "FrostWire iTunes import settings");
    }

    public static final FileSetSetting IMPORT_FILES = FACTORY.createFileSetSetting("IMPORT_FILES", new File[0]);
}