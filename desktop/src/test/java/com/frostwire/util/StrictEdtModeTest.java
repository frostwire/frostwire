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
        assertFalse(StrictEdtMode.isRenderPipelineStall(stack));
        assertFalse(StrictEdtMode.isRenderPipelineStall(null));
    }

    @Test
    void xwaylandSoftwarePaintStallIsRenderPipeline() {
        StackTraceElement[] stack = {
                new StackTraceElement("sun.java2d.loops.MaskBlit",
                        "MaskBlit", "MaskBlit.java", -2),
                new StackTraceElement("sun.java2d.loops.Blit$GeneralMaskBlit",
                        "Blit", "Blit.java", 205),
                new StackTraceElement("sun.java2d.pipe.SpanShapeRenderer",
                        "renderSpans", "SpanShapeRenderer.java", 195),
                new StackTraceElement("java.awt.SunGraphics2D",
                        "drawLine", "SunGraphics2D.java", 2244),
                new StackTraceElement("javax.swing.border.EtchedBorder",
                        "paintBorder", "EtchedBorder.java", 153),
                new StackTraceElement("javax.swing.JComponent",
                        "paint", "JComponent.java", 1133),
                new StackTraceElement("javax.swing.RepaintManager$ProcessingRunnable",
                        "run", "RepaintManager.java", 1808),
                new StackTraceElement("java.awt.EventQueue",
                        "dispatchEvent", "EventQueue.java", 693)
        };

        assertTrue(StrictEdtMode.isRenderPipelineStall(stack));
    }

    @Test
    void watchdogFrameInPaintStackStillExempt() {
        StackTraceElement[] stack = {
                new StackTraceElement("sun.java2d.loops.MaskBlit",
                        "MaskBlit", "MaskBlit.java", -2),
                new StackTraceElement("sun.java2d.pipe.SpanShapeRenderer",
                        "renderSpans", "SpanShapeRenderer.java", 195),
                new StackTraceElement("javax.swing.border.EtchedBorder",
                        "paintBorder", "EtchedBorder.java", 153),
                new StackTraceElement("javax.swing.JComponent",
                        "paint", "JComponent.java", 1133),
                new StackTraceElement("com.frostwire.util.StrictEdtMode$TimingEventQueue",
                        "dispatchEvent", "StrictEdtMode.java", 68)
        };

        assertTrue(StrictEdtMode.isRenderPipelineStall(stack));
    }

    @Test
    void appFrameInPaintStackStillFailsStrictMode() {
        StackTraceElement[] stack = {
                new StackTraceElement("sun.java2d.loops.MaskBlit",
                        "MaskBlit", "MaskBlit.java", -2),
                new StackTraceElement("javax.swing.JComponent",
                        "paint", "JComponent.java", 1133),
                new StackTraceElement("com.limegroup.gnutella.gui.search.SearchMediator",
                        "paintResults", "SearchMediator.java", 42)
        };

        assertFalse(StrictEdtMode.isRenderPipelineStall(stack));
    }

    @Test
    void swingPaintWithoutJava2dStillFailsStrictMode() {
        StackTraceElement[] stack = {
                new StackTraceElement("javax.swing.JComponent",
                        "paint", "JComponent.java", 1133),
                new StackTraceElement("com.frostwire.gui.SomeAction",
                        "actionPerformed", "SomeAction.java", 42)
        };

        assertFalse(StrictEdtMode.isRenderPipelineStall(stack));
        assertFalse(StrictEdtMode.isRenderPipelineStall(new StackTraceElement[0]));
    }
}
