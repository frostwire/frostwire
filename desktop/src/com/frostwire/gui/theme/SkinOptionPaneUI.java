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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.synth.SynthOptionPaneUI;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class SkinOptionPaneUI extends SynthOptionPaneUI {
    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinOptionPaneUI();
    }

    @Override
    protected void addMessageComponents(Container container, GridBagConstraints cons, final Object msg, int maxll, boolean internallyCreated) {
        super.addMessageComponents(container, cons, msg, maxll, internallyCreated);
        if (msg instanceof JLabel) {
            ThemeMediator.fixComponentFont((JLabel) msg, getMessage());
        } else if (msg instanceof JTextField) {
            ThemeMediator.fixKeyStrokes((JTextField) msg);
            ((JTextField) msg).getDocument().addDocumentListener(new DocumentListener() {
                public void changedUpdate(DocumentEvent e) {
                    update();
                }

                public void removeUpdate(DocumentEvent e) {
                    update();
                }

                public void insertUpdate(DocumentEvent e) {
                    update();
                }

                private void update() {
                    optionPane.setInputValue(((JTextField) msg).getText());
                }
            });
        }
    }
}
