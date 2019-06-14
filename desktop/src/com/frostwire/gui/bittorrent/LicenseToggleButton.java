/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.AlphaIcon;
import com.limegroup.gnutella.gui.GUIMediator;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LicenseToggleButton extends JPanel {
    private final ImageIcon selectedIcon;
    private final AlphaIcon unselectedIcon;
    private final String title;
    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JLabel descriptionLabel;
    private boolean selected;
    private boolean toggleable;
    private final LicenseIcon licenseIcon;
    private LicenseToggleButtonOnToggleListener listener;

    LicenseToggleButton(LicenseIcon iconName, String text, String description, boolean selected, boolean toggleable) {
        this.toggleable = toggleable;
        setMeUp();
        licenseIcon = iconName;
        selectedIcon = getIcon(iconName);
        unselectedIcon = new AlphaIcon(selectedIcon, 0.2f);
        iconLabel = new JLabel((selected) ? selectedIcon : unselectedIcon);
        title = text;
        titleLabel = new JLabel("<html><b>" + text + "</b></html>");
        descriptionLabel = new JLabel("<html><small>" + description + "</small></html>");
        setLayout(new MigLayout("fill, wrap 1"));
        add(iconLabel, "top, aligny top, alignx center, wrap");
        add(titleLabel, "top, aligny top, alignx center, wrap");
        add(descriptionLabel, "top, aligny top, pushy, alignx center");
        initEventListeners();
    }

    private static ImageIcon getIcon(LicenseIcon iconName) {
        return GUIMediator.getThemeImage(iconName.toString() + ".png");
    }

    public String getTitle() {
        return title;
    }

    void setToggleable(boolean t) {
        toggleable = t;
    }

    LicenseIcon getLicenseIcon() {
        return licenseIcon;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        updateComponents();
    }

    void setOnToggleListener(LicenseToggleButtonOnToggleListener listener) {
        this.listener = listener;
    }

    private void onMouseEntered() {
        if (toggleable) {
            setOpaque(true);
            setBackground(Color.WHITE);
            BasicStroke stroke = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            setBorder(BorderFactory.createStrokeBorder(stroke, Color.GRAY));
            updateComponents();
        }
    }

    private void onMouseExited() {
        if (toggleable) {
            setMeUp();
            updateComponents();
        }
    }

    private void onToggle() {
        if (toggleable) {
            selected = !selected;
            updateComponents();
            if (listener != null) {
                listener.onButtonToggled(this);
            }
        }
    }

    private void initEventListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onToggle();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                onMouseEntered();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                onMouseExited();
            }
        });
    }

    private void updateComponents() {
        if (iconLabel != null && selectedIcon != null && unselectedIcon != null) {
            iconLabel.setIcon((selected) ? selectedIcon : unselectedIcon);
        }
        if (titleLabel != null) {
            titleLabel.setEnabled(selected);
        }
        if (descriptionLabel != null) {
            descriptionLabel.setEnabled(selected);
        }
    }

    private void setMeUp() {
        setBackground(null);
        setOpaque(false);
        setBorder(null);
    }

    public enum LicenseIcon {
        CC, BY, SA, ND, NC,
        APACHE,
        BSD,
        GPL3,
        LGPL3,
        MOZILLA,
        OPENSOURCE,
        CC0,
        PUBLICDOMAIN
    }
}