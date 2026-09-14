/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.limegroup.gnutella.gui.TipOfTheDayMediator;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipOfTheDayTest {

    @Test
    void preloadHtmlEngine_completesOffEdtWithKitAvailable() {
        assertDoesNotThrow(TipOfTheDayMediator::preloadHtmlEngine);

        String kitClassName = new JEditorPane().getEditorKitClassNameForContentType("text/html");

        assertTrue(kitClassName != null && kitClassName.contains("HTMLEditorKit"),
                "HTML editor kit must resolve so tip contents render");
    }
}
