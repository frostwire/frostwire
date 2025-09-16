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

package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;

/**
 * Displays a status update in various ways, depending on the
 * operating system & JDK.
 * <p>
 * OSX:
 * - Displays an indeterminate JProgressBar with a JLabel
 * left justified above it.
 * w/o OSX:
 * - Displays an indeterminate JProgressBar with the status text
 * inside the progressbar.
 */
final class StatusComponent extends JPanel {
    /**
     * The JProgressBar whose text is updated, if not running on OSX.
     */
    private final JProgressBar BAR;
    /**
     * The JLabel being updated if this is running on OSX.
     */
    private final JLabel LABEL;
    /**
     * Whether or not this status component is using steps.
     */
    private final boolean STEPPING;
    /**
     * The NumberFormat being used for stepping.
     */
    private final NumberFormat NF;

    /**
     * Creates a new StatusComponent with an indeterminate progressbar.
     */
    public StatusComponent() {
        STEPPING = false;
        NF = null;
        LABEL = new JLabel();
        BAR = new JProgressBar();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        construct();
        GUIUtils.setOpaque(false, this);
        if (BAR != null && !OSUtils.isMacOSX()) {
            BAR.setOpaque(true);
        }
        BAR.setIndeterminate(true);
    }

    /**
     * Creates a new StatusComponent with the specified number of steps.
     */
    public StatusComponent(int steps) {
        STEPPING = true;
        LABEL = new JLabel();
        LABEL.setFont(LABEL.getFont().deriveFont(Font.BOLD));
        BAR = new JProgressBar();
        NF = NumberFormat.getInstance(GUIMediator.getLocale());
        NF.setMaximumIntegerDigits(3);
        NF.setMaximumFractionDigits(0);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        construct();
        GUIUtils.setOpaque(false, this);
        BAR.setMaximum(steps + 1);
        BAR.setMinimum(0);
        BAR.setValue(0);
    }

    /**
     * Sets the preferred size of the progressbar.
     */
    public void setProgressPreferredSize(Dimension dim) {
        if (BAR != null) {
            BAR.setMinimumSize(dim);
            BAR.setMaximumSize(dim);
            BAR.setPreferredSize(dim);
        }
    }

    /**
     * Updates the status of this component.
     */
    public void setText(String text) {
        if (STEPPING) {
            BAR.setValue(BAR.getValue() + 1);
            String percent = NF.format(((double) BAR.getValue() / (double) BAR.getMaximum() * 100d));
            text = percent + "% (" + text + ")";
        }
        if (STEPPING || OSUtils.isMacOSX())
            LABEL.setText(text);
        else
            BAR.setString(text);
    }

    /**
     * Constructs the panel.
     */
    private void construct() {
        //aka, on Splash screen
        if (STEPPING) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setMinimumSize(new Dimension(400, 340));
            panel.setPreferredSize(new Dimension(400, 340));
            panel.setMaximumSize(new Dimension(400, 340));
            panel.add(BAR, BorderLayout.SOUTH);
            add(panel);
            LABEL.setForeground(new Color(0x426a81));
            LABEL.setMinimumSize(new Dimension(400, 20));
            LABEL.setPreferredSize(new Dimension(400, 20));
            LABEL.setMaximumSize(new Dimension(400, 20));
            LABEL.setAlignmentX(CENTER_ALIGNMENT); //not sure why this works
            LABEL.setFont(LABEL.getFont().deriveFont(9f));
            add(LABEL);
        } else {
            BAR.setStringPainted(true);
            add(BAR);
        }
    }
}
