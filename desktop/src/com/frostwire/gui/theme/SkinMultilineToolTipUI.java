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

package com.frostwire.gui.theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.synth.ColorType;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthToolTipUI;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.beans.PropertyChangeEvent;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

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
            textArea.setText(text);
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
        Dimension dimension;
        try {
            String textAreaText = textArea.getText();
            if (textAreaText == null || textAreaText.isEmpty()) {
                dimension = new Dimension(0, 0);
            } else {
                AttributedString text = new AttributedString(textAreaText);
                Font font = c.getFont();
                FontMetrics fm = c.getFontMetrics(font);
                FontRenderContext frc = fm.getFontRenderContext();
                AttributedCharacterIterator charIt = text.getIterator();
                LineBreakMeasurer lineMeasurer = new LineBreakMeasurer(charIt, frc);
                float formatWidth = (float) (TOOLTIP_WIDTH - 48);
                lineMeasurer.setPosition(charIt.getBeginIndex());
                int noLines = 0;
                while (lineMeasurer.getPosition() < charIt.getEndIndex()) {
                    lineMeasurer.nextLayout(formatWidth);
                    noLines++;
                }
                if (lineMeasurer.getPosition() < textAreaText.length()) {
                    noLines++;
                }
                int height = fm.getHeight() * noLines + 10;
                dimension = new Dimension(TOOLTIP_WIDTH, height);
            }
        } catch (Throwable e) {
            // in case there is a problem with swing
            dimension = new Dimension(0, 0);
        }
        return dimension;
    }
}
