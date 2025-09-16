/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.init;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import org.apache.commons.io.IOUtils;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * State Your Intent.
 */
final class IntentWindow extends SetupWindow {
    private boolean setWillNot = false;
    private Properties properties;

    IntentWindow(SetupManager manager) {
        super(manager, I18n.tr("State Your Intent"), I18n.tr("One more thing..."));
    }

    private boolean isCurrentVersionChecked() {
        if (properties == null) {
            properties = new Properties();
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(getPropertiesFile());
                properties.load(fis);
            } catch (IOException iox) {
                System.out.println("Could not load properties from property file.");
                return false;
            } finally {
                IOUtils.closeQuietly(fis);
            }
        }
        String exists = properties.getProperty("willnot");
        return exists != null && exists.equals("true");
    }

    boolean isConfirmedWillNot() {
        return isCurrentVersionChecked() || setWillNot;
    }

    @Override
    protected void createWindow() {
        super.createWindow();
        JPanel innerPanel = new JPanel(new BorderLayout());
        final IntentPanel intentPanel = new IntentPanel();
        innerPanel.add(intentPanel, BorderLayout.CENTER);
        setSetupComponent(innerPanel);
        intentPanel.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
        setNext(null);
        intentPanel.addButtonListener(e -> {
            if (intentPanel.hasSelection()) {
                setNext(IntentWindow.this);
                setWillNot = intentPanel.isWillNot();
                _manager.enableActions(getAppropriateActions());
            }
        });
    }

    @Override
    public void applySettings(boolean loadCoreComponents) {
        if (setWillNot) {
            properties.put("willnot", "true");
            try {
                properties.store(new FileOutputStream(getPropertiesFile()), "Started & Ran Versions");
            } catch (IOException ignored) {
                System.out.println(ignored);
            }
        }
    }

    private File getPropertiesFile() {
        return new File(CommonUtils.getUserSettingsDir(), "intent.props");
    }
}
