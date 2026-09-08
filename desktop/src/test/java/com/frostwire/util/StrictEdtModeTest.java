/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictEdtModeTest {

    @Test
    void identifiesMacMetalQueueFlushStall() {
        StackTraceElement[] stack = {
                new StackTraceElement("java.lang.Object", "wait0", "Object.java", -2),
                new StackTraceElement("sun.java2d.metal.MTLRenderQueue$QueueFlusher",
                        "flushNow", "MTLRenderQueue.java", 180),
                new StackTraceElement("com.formdev.flatlaf.ui.FlatProgressBarUI",
                        "paint", "FlatProgressBarUI.java", 271)
        };

        assertTrue(StrictEdtMode.isMacMetalRenderPipelineStall(stack));
    }

    @Test
    void applicationEdtWaitStillFailsStrictMode() {
        StackTraceElement[] stack = {
                new StackTraceElement("com.frostwire.gui.SomeAction",
                        "actionPerformed", "SomeAction.java", 42)
        };

        assertFalse(StrictEdtMode.isMacMetalRenderPipelineStall(stack));
        assertFalse(StrictEdtMode.isMacMetalRenderPipelineStall(null));
    }
}
