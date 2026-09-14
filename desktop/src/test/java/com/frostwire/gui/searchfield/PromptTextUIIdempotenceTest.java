/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.searchfield;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTextUIIdempotenceTest {

    @Test
    void repeatedSizingDoesNotRewriteUnchangedPrompt() {
        JTextField field = new JTextField();
        PromptSupport.init("Search...", Color.GRAY, Color.WHITE, field);

        assertTrue(field.getUI() instanceof PromptTextUI, "prompt-aware UI must be installed");
        PromptTextUI ui = (PromptTextUI) field.getUI();
        JTextComponent prompt = ui.getPromptComponent(field);

        AtomicInteger documentEvents = new AtomicInteger();
        prompt.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                documentEvents.incrementAndGet();
            }

            public void removeUpdate(DocumentEvent e) {
                documentEvents.incrementAndGet();
            }

            public void changedUpdate(DocumentEvent e) {
                documentEvents.incrementAndGet();
            }
        });

        ui.getPreferredSize(field);
        assertEquals(0, documentEvents.get(), "sizing an unchanged prompt must not rewrite its document");

        ui.getPreferredSize(field);
        assertEquals(0, documentEvents.get(), "repeated sizing must not rewrite the prompt document");
    }
}
