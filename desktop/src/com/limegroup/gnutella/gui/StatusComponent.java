/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import org.limewire.util.OSUtils;

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
