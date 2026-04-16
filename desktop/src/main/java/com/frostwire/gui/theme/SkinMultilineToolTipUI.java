/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.theme;

import com.frostwire.util.SafeText;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.synth.ColorType;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthToolTipUI;
import java.awt.*;
import java.beans.PropertyChangeEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SkinMultilineToolTipUI extends SynthToolTipUI {
    private static final int TOOLTIP_WIDTH = 400;
    private final JTextArea textArea;
    private final CellRendererPane rendererPane;

    public SkinMultilineToolTipUI() {
        this.textArea = new JTextArea();
        this.rendererPane = new CellRendererPane();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setUI(new BasicTextAreaUI());
    }

    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinPanelUI();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return calculatePreferredSize(c);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        super.propertyChange(e);
        String name = e.getPropertyName();
        if (name.equals("tiptext")) {
            JToolTip tip = (JToolTip) e.getSource();
            String text = tip.getTipText();
            textArea.setText(SafeText.sanitize(text));
        }
    }

    @Override
    protected void paint(SynthContext context, Graphics g) {
        JToolTip toolTip = (JToolTip) context.getComponent();
        Insets insets = toolTip.getInsets();
        Rectangle paintTextR = new Rectangle(insets.left, insets.top, toolTip.getWidth() - (insets.left + insets.right), toolTip.getHeight() - (insets.top + insets.bottom));
        g.setColor(context.getStyle().getColor(context, ColorType.TEXT_FOREGROUND));
        g.setFont(toolTip.getFont());
        rendererPane.paintComponent(g, textArea, toolTip, insets.left + 2, insets.top, paintTextR.width, paintTextR.height, true);
    }

    private Dimension calculatePreferredSize(JComponent c) {
        try {
            String text = SafeText.sanitize(textArea.getText());
            if (text == null || text.isEmpty()) {
                return new Dimension(0, 0);
            }
            textArea.setFont(c.getFont());
            textArea.setSize(TOOLTIP_WIDTH, Short.MAX_VALUE);
            Dimension pref = textArea.getPreferredSize();
            return new Dimension(TOOLTIP_WIDTH, pref.height + 10);
        } catch (Throwable e) {
            return new Dimension(0, 0);
        }
    }
}
