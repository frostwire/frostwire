/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.CrashReportSpooler;
import com.limegroup.gnutella.gui.StallCpu;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StrictEdtModeTest {
  @Test
  void gcWindowIsTheActivityBetweenTwoReadings() {
    long start = TimeUnit.SECONDS.toNanos(100);
    CrashReportSpooler.GcWindow window =
        StrictEdtMode.gcWindow(
            new StrictEdtMode.GcTotals(10, 400),
            start,
            new StrictEdtMode.GcTotals(51, 2780),
            start + TimeUnit.MILLISECONDS.toNanos(2500));
    assertEquals(new CrashReportSpooler.GcWindow(41, 2380, 2500), window);
  }

  @Test
  void gcTimeIsCappedAtItsWindowAndMissingReadingsGiveNoWindow() {
    long start = TimeUnit.SECONDS.toNanos(100);
    // A collection that started before the window can add more time than the window holds.
    assertEquals(
        new CrashReportSpooler.GcWindow(1, 2500, 2500),
        StrictEdtMode.gcWindow(
            new StrictEdtMode.GcTotals(0, 0),
            start,
            new StrictEdtMode.GcTotals(1, 2600),
            start + TimeUnit.MILLISECONDS.toNanos(2500)));
    assertEquals(
        null, StrictEdtMode.gcWindow(null, start, StrictEdtMode.GcTotals.now(), start + 1));
  }

  @Test
  void aLateWatchdogWithoutGcIsAProcessStall() {
    // Icebase #2a14320f1f24: 5 s threshold, the second sample came 5,794 ms after the first.
    assertEquals(
        StrictEdtMode.PROCESS_STALL_TAG,
        StrictEdtMode.processStallTag(new CrashReportSpooler.GcWindow(0, 0, 5794), 5000));
    assertEquals(
        StrictEdtMode.PROCESS_STALL_TAG,
        StrictEdtMode.processStallTag(new CrashReportSpooler.GcWindow(0, 0, 3500), 5000));
  }

  @Test
  void aWatchdogOnTimeOrDelayedByGcIsNotAProcessStall() {
    assertEquals(
        "", StrictEdtMode.processStallTag(new CrashReportSpooler.GcWindow(0, 0, 2500), 5000));
    // A few hundred milliseconds late is scheduling noise (Icebase #4bafe7bb799f: 2,874 ms).
    assertEquals(
        "", StrictEdtMode.processStallTag(new CrashReportSpooler.GcWindow(0, 0, 2874), 5000));
    // GC pauses stop the watchdog thread too: that is a GC stall, reported with its stack.
    assertEquals(
        "", StrictEdtMode.processStallTag(new CrashReportSpooler.GcWindow(12, 3400, 6000), 5000));
    assertEquals("", StrictEdtMode.processStallTag(null, 5000));
  }

  @Test
  void gcTotalsNeverGoNegative() {
    StrictEdtMode.GcTotals totals = StrictEdtMode.GcTotals.now();
    assertTrue(totals.count() >= 0);
    assertTrue(totals.timeMs() >= 0);
  }

  @Test
  void identifiesMacMetalQueueFlushStall() {
    StackTraceElement[] stack = {
      new StackTraceElement("java.lang.Object", "wait0", "Object.java", -2),
      new StackTraceElement(
          "sun.java2d.metal.MTLRenderQueue$QueueFlusher", "flushNow", "MTLRenderQueue.java", 180),
      new StackTraceElement(
          "com.formdev.flatlaf.ui.FlatProgressBarUI", "paint", "FlatProgressBarUI.java", 271)
    };

    assertTrue(StrictEdtMode.isMacMetalRenderPipelineStall(stack));
  }

  @Test
  void applicationEdtWaitStillFailsStrictMode() {
    StackTraceElement[] stack = {
      new StackTraceElement(
          "com.frostwire.gui.SomeAction", "actionPerformed", "SomeAction.java", 42)
    };

    assertFalse(StrictEdtMode.isMacMetalRenderPipelineStall(stack));
    assertFalse(StrictEdtMode.isMacMetalRenderPipelineStall(null));
    assertFalse(StrictEdtMode.isRenderPipelineStall(stack));
    assertFalse(StrictEdtMode.isRenderPipelineStall(null));
  }

  @Test
  void xwaylandSoftwarePaintStallIsRenderPipeline() {
    StackTraceElement[] stack = {
      new StackTraceElement("sun.java2d.loops.MaskBlit", "MaskBlit", "MaskBlit.java", -2),
      new StackTraceElement("sun.java2d.loops.Blit$GeneralMaskBlit", "Blit", "Blit.java", 205),
      new StackTraceElement(
          "sun.java2d.pipe.SpanShapeRenderer", "renderSpans", "SpanShapeRenderer.java", 195),
      new StackTraceElement("java.awt.SunGraphics2D", "drawLine", "SunGraphics2D.java", 2244),
      new StackTraceElement(
          "javax.swing.border.EtchedBorder", "paintBorder", "EtchedBorder.java", 153),
      new StackTraceElement("javax.swing.JComponent", "paint", "JComponent.java", 1133),
      new StackTraceElement(
          "javax.swing.RepaintManager$ProcessingRunnable", "run", "RepaintManager.java", 1808),
      new StackTraceElement("java.awt.EventQueue", "dispatchEvent", "EventQueue.java", 693)
    };

    assertTrue(StrictEdtMode.isRenderPipelineStall(stack));
  }

  @Test
  void watchdogFrameInPaintStackStillExempt() {
    StackTraceElement[] stack = {
      new StackTraceElement("sun.java2d.loops.MaskBlit", "MaskBlit", "MaskBlit.java", -2),
      new StackTraceElement(
          "sun.java2d.pipe.SpanShapeRenderer", "renderSpans", "SpanShapeRenderer.java", 195),
      new StackTraceElement(
          "javax.swing.border.EtchedBorder", "paintBorder", "EtchedBorder.java", 153),
      new StackTraceElement("javax.swing.JComponent", "paint", "JComponent.java", 1133),
      new StackTraceElement(
          "com.frostwire.util.StrictEdtMode$TimingEventQueue",
          "dispatchEvent",
          "StrictEdtMode.java",
          68)
    };

    assertTrue(StrictEdtMode.isRenderPipelineStall(stack));
  }

  @Test
  void appFrameInPaintStackStillFailsStrictMode() {
    StackTraceElement[] stack = {
      new StackTraceElement("sun.java2d.loops.MaskBlit", "MaskBlit", "MaskBlit.java", -2),
      new StackTraceElement("javax.swing.JComponent", "paint", "JComponent.java", 1133),
      new StackTraceElement(
          "com.limegroup.gnutella.gui.search.SearchMediator",
          "paintResults",
          "SearchMediator.java",
          42)
    };

    assertFalse(StrictEdtMode.isRenderPipelineStall(stack));
  }

  @Test
  void swingPaintWithoutJava2dStillFailsStrictMode() {
    StackTraceElement[] stack = {
      new StackTraceElement("javax.swing.JComponent", "paint", "JComponent.java", 1133),
      new StackTraceElement(
          "com.frostwire.gui.SomeAction", "actionPerformed", "SomeAction.java", 42)
    };

    assertFalse(StrictEdtMode.isRenderPipelineStall(stack));
    assertFalse(StrictEdtMode.isRenderPipelineStall(new StackTraceElement[0]));
  }

  @Test
  void finalizerShutdownFrameMarksShutdownSequence() {
    StackTraceElement[] stack = {
      new StackTraceElement("java.lang.ClassLoader", "loadClass", "ClassLoader.java", 502),
      new StackTraceElement(
          "com.limegroup.gnutella.gui.Finalizer", "shutdown", "Finalizer.java", 33),
      new StackTraceElement(
          "com.limegroup.gnutella.gui.GUIMediator", "shutdown", "GUIMediator.java", 458)
    };

    assertTrue(StrictEdtMode.isShutdownSequence(stack));
  }

  @Test
  void otherStallsAreNotShutdownSequence() {
    StackTraceElement[] stack = {
      new StackTraceElement(
          "com.limegroup.gnutella.gui.GUIMediator", "shutdown", "GUIMediator.java", 458),
      new StackTraceElement(
          "com.frostwire.gui.SomeAction", "actionPerformed", "SomeAction.java", 42)
    };

    assertFalse(StrictEdtMode.isShutdownSequence(stack));
    assertFalse(StrictEdtMode.isShutdownSequence(null));
  }

  private static final StackTraceElement[] EDT_LOOP = {
    frame("java.awt.EventQueue", "dispatchEventImpl"),
    frame("java.awt.EventQueue", "dispatchEvent"),
    frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
    frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
    frame("java.awt.EventDispatchThread", "run")
  };

  private static StackTraceElement[] stack(StackTraceElement[]... parts) {
    return java.util.Arrays.stream(parts)
        .flatMap(java.util.Arrays::stream)
        .toArray(StackTraceElement[]::new);
  }

  /** Icebase #a42f7908d37a: the toolkit's own cursor update, inside a FrostWire modal dialog. */
  @Test
  void toolkitCursorUpdateIsWindowsToolkitOnlyStall() {
    StackTraceElement[] stack =
        stack(
            new StackTraceElement[] {
              frame("sun.awt.windows.WGlobalCursorManager", "findHeavyweightUnderCursor"),
              frame("sun.awt.GlobalCursorManager", "_updateCursor"),
              frame("sun.awt.GlobalCursorManager$NativeUpdater", "run"),
              frame("java.awt.event.InvocationEvent", "dispatch")
            },
            EDT_LOOP,
            new StackTraceElement[] {
              frame("java.awt.Dialog", "show"),
              frame("com.limegroup.gnutella.gui.FramedDialog", "lambda$showDialog$0"),
              frame("java.awt.event.InvocationEvent", "dispatch")
            },
            EDT_LOOP);

    assertTrue(StrictEdtMode.isWindowsToolkitOnlyStall(stack));
  }

  /** Icebase #ee3999fb71eb: the same cursor update, stalled setting the cursor natively. */
  @Test
  void toolkitCursorSetIsWindowsToolkitOnlyStall() {
    StackTraceElement[] stack =
        stack(
            new StackTraceElement[] {
              frame("sun.awt.windows.WGlobalCursorManager", "setCursor"),
              frame("sun.awt.GlobalCursorManager", "_updateCursor"),
              frame("sun.awt.GlobalCursorManager$NativeUpdater", "run"),
              frame("java.awt.event.InvocationEvent", "dispatch")
            },
            EDT_LOOP);

    assertTrue(StrictEdtMode.isWindowsToolkitOnlyStall(stack));
  }

  /** Icebase #c96101cf2130: validation start of a native resize. */
  @Test
  void nativeResizeValidationIsWindowsToolkitOnlyStall() {
    StackTraceElement[] stack =
        stack(
            new StackTraceElement[] {
              frame("sun.awt.windows.WComponentPeer", "beginValidate"),
              frame("java.awt.Container", "validate"),
              frame("java.awt.Window", "dispatchEventImpl"),
              frame("java.awt.Component", "dispatchEvent")
            },
            EDT_LOOP);

    assertTrue(StrictEdtMode.isWindowsToolkitOnlyStall(stack));
  }

  /** Icebase #81f177418e06: AWT asking Windows about the touch keyboard on a mouse release. */
  private static final StackTraceElement[] TOUCH_KEYBOARD_ON_MOUSE_RELEASE = {
    frame("sun.awt.windows.WToolkit", "showTouchKeyboard"),
    frame("sun.awt.windows.WToolkit", "showOrHideTouchKeyboard"),
    frame("java.awt.Component", "dispatchEventImpl"),
    frame("java.awt.Container", "dispatchEventImpl"),
    frame("java.awt.Component", "dispatchEvent"),
    frame("java.awt.LightweightDispatcher", "retargetMouseEvent"),
    frame("java.awt.LightweightDispatcher", "processMouseEvent"),
    frame("java.awt.LightweightDispatcher", "dispatchEvent"),
    frame("java.awt.Container", "dispatchEventImpl"),
    frame("java.awt.Window", "dispatchEventImpl"),
    frame("java.awt.Component", "dispatchEvent")
  };

  @Test
  void touchKeyboardCheckOnMouseReleaseIsWindowsToolkitOnlyStall() {
    assertTrue(
        StrictEdtMode.isWindowsToolkitOnlyStall(stack(TOUCH_KEYBOARD_ON_MOUSE_RELEASE, EDT_LOOP)));
  }

  /** The same native call with FrostWire code in the stalled event is still reported. */
  @Test
  void touchKeyboardCheckUnderAnAppFrameIsReported() {
    StackTraceElement[] withApp =
        stack(
            new StackTraceElement[] {
              frame("sun.awt.windows.WToolkit", "showTouchKeyboard"),
              frame("sun.awt.windows.WToolkit", "showOrHideTouchKeyboard"),
              frame("java.awt.Component", "dispatchEventImpl"),
              frame("com.limegroup.gnutella.gui.tables.LimeJTable", "processMouseEvent")
            },
            java.util.Arrays.copyOfRange(
                TOUCH_KEYBOARD_ON_MOUSE_RELEASE, 3, TOUCH_KEYBOARD_ON_MOUSE_RELEASE.length),
            EDT_LOOP);
    StackTraceElement[] partial =
        stack(java.util.Arrays.copyOfRange(TOUCH_KEYBOARD_ON_MOUSE_RELEASE, 0, 5), EDT_LOOP);

    assertFalse(StrictEdtMode.isWindowsToolkitOnlyStall(withApp));
    assertFalse(StrictEdtMode.isWindowsToolkitOnlyStall(partial));
  }

  /** The Windows input method deactivating on focus loss, from the bottom of the stall up. */
  private static final StackTraceElement[] IME_FOCUS_LOST = {
    frame("sun.awt.windows.WInputMethod", "getNativeLocale"),
    frame("sun.awt.windows.WInputMethod", "getLocale"),
    frame("sun.awt.windows.WInputMethod", "deactivate"),
    frame("sun.awt.im.InputContext", "deactivateInputMethod"),
    frame("sun.awt.im.InputContext", "focusLost"),
    frame("sun.awt.im.InputContext", "dispatchEvent"),
    frame("sun.awt.im.InputMethodContext", "dispatchEvent"),
    frame("java.awt.Component", "dispatchEventImpl"),
    frame("java.awt.Container", "dispatchEventImpl"),
    frame("java.awt.Component", "dispatchEvent"),
    frame("java.awt.KeyboardFocusManager", "redispatchEvent"),
    frame("java.awt.DefaultKeyboardFocusManager", "typeAheadAssertions"),
    frame("java.awt.DefaultKeyboardFocusManager", "dispatchEvent"),
    frame("java.awt.Component", "dispatchEventImpl"),
    frame("java.awt.Container", "dispatchEventImpl"),
    frame("java.awt.Component", "dispatchEvent")
  };

  /**
   * Icebase #347fc77bd6d5 (GitHub #1351): the Windows IME deactivating on a focus change, sampled
   * in the JDK code its native call went back into. Any JDK frames may sit on top.
   */
  @Test
  void imeDeactivationOnFocusLossIsWindowsToolkitOnlyStall() {
    StackTraceElement[] sampled = {
      frame("java.text.ParsePosition", "<init>"), frame("java.util.Locale", "forLanguageTag")
    };
    assertTrue(StrictEdtMode.isWindowsToolkitOnlyStall(stack(sampled, IME_FOCUS_LOST, EDT_LOOP)));
    assertTrue(
        StrictEdtMode.isWindowsToolkitOnlyStall(stack(IME_FOCUS_LOST, EDT_LOOP)),
        "sampled in the native call itself");
  }

  /** An app frame above the IME call, e.g. a focus listener it called back, is still reported. */
  @Test
  void imeDeactivationUnderAnAppFrameIsReported() {
    StackTraceElement[] sampled = {
      frame("com.frostwire.gui.SomeListener", "localeChanged"),
      frame("java.util.Locale", "forLanguageTag")
    };
    assertFalse(StrictEdtMode.isWindowsToolkitOnlyStall(stack(sampled, IME_FOCUS_LOST, EDT_LOOP)));
    StackTraceElement[] partial =
        java.util.Arrays.copyOfRange(IME_FOCUS_LOST, 4, IME_FOCUS_LOST.length);
    assertFalse(
        StrictEdtMode.isWindowsToolkitOnlyStall(stack(partial, EDT_LOOP)),
        "only the bottom of the signature");
  }

  /**
   * Icebase #3ae4ef04e569: the same cursor call from a Swing layout pass was an app revalidate
   * storm (GitHub #1321), so it must still be reported.
   */
  @Test
  void cursorUpdateFromLayoutPassIsReported() {
    StackTraceElement[] stack =
        stack(
            new StackTraceElement[] {
              frame("sun.awt.windows.WGlobalCursorManager", "findHeavyweightUnderCursor"),
              frame("sun.awt.GlobalCursorManager", "_updateCursor"),
              frame("sun.awt.GlobalCursorManager", "updateCursorImmediately"),
              frame("java.awt.Component", "updateCursorImmediately"),
              frame("java.awt.Container", "validate"),
              frame("javax.swing.RepaintManager", "validateInvalidComponents"),
              frame("javax.swing.RepaintManager$ProcessingRunnable", "run"),
              frame("java.awt.event.InvocationEvent", "dispatch")
            },
            EDT_LOOP);

    assertFalse(StrictEdtMode.isWindowsToolkitOnlyStall(stack));
  }

  @Test
  void toolkitCallUnderAppFrameIsReported() {
    StackTraceElement[] stack =
        stack(
            new StackTraceElement[] {
              frame("sun.awt.windows.WComponentPeer", "beginValidate"),
              frame("java.awt.Container", "validate"),
              frame("com.limegroup.gnutella.gui.MainFrame", "resize"),
              frame("java.awt.Window", "dispatchEventImpl"),
              frame("java.awt.Component", "dispatchEvent")
            },
            EDT_LOOP);

    assertFalse(StrictEdtMode.isWindowsToolkitOnlyStall(stack));
    assertFalse(StrictEdtMode.isWindowsToolkitOnlyStall(null));
    assertFalse(StrictEdtMode.isWindowsToolkitOnlyStall(new StackTraceElement[0]));
  }

  private static StackTraceElement frame(String className, String method) {
    return new StackTraceElement(className, method, null, -1);
  }

  @Test
  void shellFolderWaitInsideModalFileChooserIsJdkShellFolderStall() {
    StackTraceElement[] stack = {
      frame("java.util.concurrent.FutureTask", "get"),
      frame("sun.awt.shell.Win32ShellFolderManager2$ComInvoker", "invoke"),
      frame("sun.awt.shell.Win32ShellFolder2", "listFiles"),
      frame("javax.swing.JFileChooser", "setSelectedFile"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
      frame("java.awt.Dialog", "show"),
      frame("com.limegroup.gnutella.gui.FileChooserHandler", "getInput"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertTrue(StrictEdtMode.isJdkShellFolderStall(stack));
  }

  @Test
  void slowFolderAccessCheckInFileChooserIsJdkShellFolderStall() {
    StackTraceElement[] stack = {
      frame("java.io.WinNTFileSystem", "checkAccess0"),
      frame("java.io.File", "canWrite"),
      frame("sun.awt.shell.ShellFolder", "canWrite"),
      frame("sun.swing.FilePane", "doDirectoryChanged"),
      frame("javax.swing.JFileChooser", "setCurrentDirectory"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
      frame("com.limegroup.gnutella.gui.FileChooserHandler", "getInput")
    };

    assertTrue(StrictEdtMode.isJdkShellFolderStall(stack));
  }

  @Test
  void appCodeInStalledEventIsNotJdkShellFolderStall() {
    StackTraceElement[] appCallsShell = {
      frame("sun.awt.shell.Win32ShellFolder2", "listFiles"),
      frame("com.frostwire.gui.SomeAction", "actionPerformed"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };
    StackTraceElement[] noShellFolder = {
      frame("javax.swing.JFileChooser", "setSelectedFile"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertFalse(StrictEdtMode.isJdkShellFolderStall(appCallsShell));
    assertFalse(StrictEdtMode.isJdkShellFolderStall(noShellFolder));
    assertFalse(StrictEdtMode.isJdkShellFolderStall(null));
  }

  @Test
  void idleEdtStackIsNotInsideWatchedDispatch() {
    StackTraceElement[] idle = {
      frame("jdk.internal.misc.Unsafe", "park"),
      frame("java.awt.EventQueue", "getNextEvent"),
      frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
      frame("java.awt.EventDispatchThread", "run")
    };

    assertFalse(StrictEdtMode.isInsideWatchedDispatch(idle));
    assertFalse(StrictEdtMode.isInsideWatchedDispatch(null));
  }

  /** The stalled event opened a modal dialog, whose nested event loop was idle when sampled. */
  @Test
  void idleNestedLoopOfAModalDialogIsNotInsideWatchedDispatch() {
    StackTraceElement[] idleInDialog = {
      frame("jdk.internal.misc.Unsafe", "park"),
      frame("java.awt.EventQueue", "getNextEvent"),
      frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
      frame("java.awt.WaitDispatchSupport", "enter"),
      frame("java.awt.Dialog", "show"),
      frame("com.limegroup.gnutella.gui.options.OptionsConstructor", "setOptionsVisible"),
      frame("java.awt.EventQueue", "dispatchEvent"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
      frame("java.awt.EventDispatchThread", "run")
    };

    assertFalse(StrictEdtMode.isInsideWatchedDispatch(idleInDialog));
  }

  /** A nested event (inside a modal dialog's loop) that is itself stalled is still watched. */
  @Test
  void stalledEventInsideAModalDialogIsInsideWatchedDispatch() {
    StackTraceElement[] stalledInDialog = {
      frame("com.frostwire.gui.SomeAction", "actionPerformed"),
      frame("java.awt.EventQueue", "dispatchEvent"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
      frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
      frame("java.awt.Dialog", "show"),
      frame("java.awt.EventQueue", "dispatchEvent"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
      frame("java.awt.EventDispatchThread", "run")
    };

    assertTrue(StrictEdtMode.isInsideWatchedDispatch(stalledInDialog));
  }

  /**
   * The stalled event (a resize) finished while the stack was taken, and the sample caught the next
   * event's repaint inside its own watched dispatch (Icebase #bc9c29f151b2).
   */
  @Test
  void stackOfALaterEventIsTaggedStackMissed() {
    StackTraceElement[] laterRepaint = {
      frame("com.frostwire.gui.theme.SkinPanelUI", "paint"),
      frame("javax.swing.RepaintManager", "paintDirtyRegions"),
      frame("java.awt.EventQueue", "dispatchEvent"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
      frame("java.awt.EventDispatchThread", "run")
    };

    assertEquals(StrictEdtMode.STACK_MISSED_TAG, StrictEdtMode.reportTag("", laterRepaint, false));
    assertEquals("", StrictEdtMode.reportTag("", laterRepaint, true));
    assertEquals(
        StrictEdtMode.SHUTDOWN_TAG,
        StrictEdtMode.reportTag(StrictEdtMode.SHUTDOWN_TAG, laterRepaint, false));
  }

  private static final StackTraceElement[] IDLE_EDT = {
    frame("jdk.internal.misc.Unsafe", "park"),
    frame("java.awt.EventQueue", "getNextEvent"),
    frame("java.awt.EventDispatchThread", "pumpOneEventForFilters"),
    frame("java.awt.EventDispatchThread", "run")
  };

  private static final StackTraceElement[] SLOW_ACTION = {
    frame("com.frostwire.gui.SomeAction", "actionPerformed"),
    frame("java.awt.EventQueue", "dispatchEvent"),
    frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
    frame("java.awt.EventDispatchThread", "run")
  };

  /**
   * The stall ended just before the threshold sample, which caught the EDT idle; the sample taken
   * halfway through the same event is reported instead (Icebase #0c622d843b52).
   */
  @Test
  void idleThresholdSampleReportsTheEarlySampleOfTheSameEvent() {
    StrictEdtMode.StallReport report = StrictEdtMode.stallReport("", IDLE_EDT, false, SLOW_ACTION);

    assertEquals(StrictEdtMode.EARLY_SAMPLE_TAG, report.tag());
    assertSame(SLOW_ACTION, report.stack());
  }

  @Test
  void earlySampleKeepsTheTagDerivedFromItsStack() {
    StackTraceElement[] renderQueueWait = {
      frame("sun.awt.SunToolkit", "awtLock"),
      frame("sun.java2d.pipe.RenderQueue", "lock"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertEquals(
        StrictEdtMode.EARLY_SAMPLE_TAG + StrictEdtMode.RENDER_QUEUE_TAG,
        StrictEdtMode.stallReport("", IDLE_EDT, false, renderQueueWait).tag());
  }

  @Test
  void thresholdSampleOfTheStalledEventWinsOverTheEarlySample() {
    StackTraceElement[] early = {
      frame("com.frostwire.gui.Other", "step"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    StrictEdtMode.StallReport report = StrictEdtMode.stallReport("", SLOW_ACTION, true, early);

    assertEquals("", report.tag());
    assertSame(SLOW_ACTION, report.stack());
    assertSame(
        SLOW_ACTION,
        StrictEdtMode.stallReport(StrictEdtMode.SHUTDOWN_TAG, SLOW_ACTION, false, early).stack());
  }

  @Test
  void stackMissedOnlyWhenBothSamplesMissed() {
    StrictEdtMode.StallReport noEarly = StrictEdtMode.stallReport("", IDLE_EDT, false, null);
    StrictEdtMode.StallReport idleEarly = StrictEdtMode.stallReport("", IDLE_EDT, false, IDLE_EDT);

    assertEquals(StrictEdtMode.STACK_MISSED_TAG, noEarly.tag());
    assertSame(IDLE_EDT, noEarly.stack());
    assertEquals(StrictEdtMode.STACK_MISSED_TAG, idleEarly.tag());
  }

  @Test
  void earlySampleIsTakenHalfwayToTheThreshold() {
    assertEquals(2_500, StrictEdtMode.earlySampleDelayMs(5_000));
    assertTrue(StrictEdtMode.earlySampleDelayMs(1) < 1);
  }

  @Test
  void stalledEventStackIsInsideWatchedDispatch() {
    StackTraceElement[] stalled = {
      frame("com.frostwire.gui.SomeAction", "actionPerformed"),
      frame("java.awt.EventQueue", "dispatchEvent"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"),
      frame("java.awt.EventDispatchThread", "run")
    };

    assertTrue(StrictEdtMode.isInsideWatchedDispatch(stalled));
  }

  @Test
  void awtLockWaitInsideRenderQueueIsTaggedEvenWithAppPaintCodeBelow() {
    StackTraceElement[] stack = {
      frame("jdk.internal.misc.Unsafe", "park"),
      frame("java.util.concurrent.locks.ReentrantLock", "lock"),
      frame("sun.awt.SunToolkit", "awtLock"),
      frame("sun.java2d.pipe.RenderQueue", "lock"),
      frame("sun.java2d.pipe.BufferedRenderPipe", "fillParallelogram"),
      frame("sun.java2d.SunGraphics2D", "fillRect"),
      frame("com.frostwire.gui.theme.SkinPanelUI", "paint"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertTrue(StrictEdtMode.isRenderQueueLockWait(stack));
  }

  @Test
  void appCodeWaitingOnALockIsNotARenderQueueWait() {
    StackTraceElement[] appLock = {
      frame("jdk.internal.misc.Unsafe", "park"),
      frame("java.util.concurrent.locks.ReentrantLock", "lock"),
      frame("com.frostwire.gui.SomeModel", "update"),
      frame("sun.awt.SunToolkit", "awtLock"),
      frame("sun.java2d.pipe.RenderQueue", "lock"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };
    StackTraceElement[] noRenderQueue = {
      frame("sun.awt.SunToolkit", "awtLock"),
      frame("sun.awt.windows.WToolkit", "sync"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertFalse(StrictEdtMode.isRenderQueueLockWait(appLock));
    assertFalse(StrictEdtMode.isRenderQueueLockWait(noRenderQueue));
    assertFalse(StrictEdtMode.isRenderQueueLockWait(null));
  }

  /**
   * The tray icon's menu is a native Windows menu: the EDT waits in WPopupMenuPeer._show for as
   * long as the user keeps it open (Icebase #ac8b13da6506, frostwire/frostwire#1499).
   */
  @Test
  void anOpenNativeTrayMenuIsTaggedNativeMenu() {
    StackTraceElement[] stack = {
      frame("sun.awt.windows.WPopupMenuPeer", "_show"),
      frame("sun.awt.windows.WPopupMenuPeer", "show"),
      frame("sun.awt.windows.WTrayIconPeer", "lambda$showPopupMenu$0"),
      frame("java.awt.event.InvocationEvent", "dispatch"),
      frame("java.awt.EventQueue", "dispatchEventImpl"),
      frame("java.awt.EventQueue", "dispatchEvent"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertTrue(StrictEdtMode.isNativeWindowCall(stack));
    assertEquals(StrictEdtMode.NATIVE_MENU_TAG, StrictEdtMode.reportTag("", stack, true));
  }

  @Test
  void appWorkWhileANativeMenuIsShownIsNotTaggedNativeMenu() {
    StackTraceElement[] appAboveMenu = {
      frame("java.io.FileInputStream", "readBytes"),
      frame("com.frostwire.gui.tray.SomeMenuListener", "itemStateChanged"),
      frame("sun.awt.windows.WPopupMenuPeer", "_show"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };
    StackTraceElement[] noMenu = {
      frame("sun.awt.windows.WToolkit", "sync"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertFalse(StrictEdtMode.isNativeWindowCall(appAboveMenu));
    assertEquals("", StrictEdtMode.reportTag("", appAboveMenu, true));
    assertFalse(StrictEdtMode.isNativeWindowCall(noMenu));
    assertFalse(StrictEdtMode.isNativeWindowCall(null));
  }

  /**
   * The cursor lookup asks the AWT-Windows toolkit thread for the window under the cursor and
   * waits; no app frame is involved (Icebase #0207d9b0209b, frostwire/frostwire#1552).
   */
  @Test
  void aNativeCursorLookupIsTaggedNativeMenuAndTheReportIsKept() {
    StackTraceElement[] stack = {
      frame("sun.awt.windows.WGlobalCursorManager", "findHeavyweightUnderCursor"),
      frame("sun.awt.GlobalCursorManager", "findHeavyweightUnderCursor"),
      frame("java.awt.Component", "updateCursorImmediately"),
      frame("java.awt.Container", "validate"),
      frame("javax.swing.RepaintManager", "validateInvalidComponents"),
      frame("java.awt.event.InvocationEvent", "dispatch"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };
    StackTraceElement[] setCursor = {
      frame("sun.awt.windows.WGlobalCursorManager", "setCursor"),
      frame("sun.awt.GlobalCursorManager", "_updateCursor"),
      frame("java.awt.Component", "updateCursorImmediately"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertTrue(StrictEdtMode.isNativeWindowCall(stack));
    assertTrue(StrictEdtMode.isNativeWindowCall(setCursor));
    assertEquals(StrictEdtMode.NATIVE_MENU_TAG, StrictEdtMode.reportTag("", stack, true));
    StrictEdtMode.StallReport report = StrictEdtMode.stallReport("", stack, true, null);
    assertEquals(StrictEdtMode.NATIVE_MENU_TAG, report.tag());
    assertSame(stack, report.stack());
    StrictEdtMode.StallReport early =
        StrictEdtMode.stallReport("", new StackTraceElement[0], false, stack);
    assertEquals(StrictEdtMode.EARLY_SAMPLE_TAG + StrictEdtMode.NATIVE_MENU_TAG, early.tag());
    assertSame(stack, early.stack());
  }

  @Test
  void appWorkAboveANativeCursorLookupIsNotTaggedNativeMenu() {
    StackTraceElement[] appAboveLookup = {
      frame("java.io.FileInputStream", "readBytes"),
      frame("com.frostwire.gui.SomeLayout", "layoutContainer"),
      frame("sun.awt.windows.WGlobalCursorManager", "findHeavyweightUnderCursor"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };
    StackTraceElement[] otherNative = {
      frame("sun.awt.windows.WToolkit", "sync"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertFalse(StrictEdtMode.isNativeWindowCall(appAboveLookup));
    assertEquals("", StrictEdtMode.reportTag("", appAboveLookup, true));
    assertFalse(StrictEdtMode.isNativeWindowCall(otherNative));
    assertFalse(StrictEdtMode.isNativeWindowCall(null));
  }

  private static final String WATCHDOG_FRAME =
      "com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue";

  /** One stack per native window call of frostwire/frostwire#1552, the call on top, as reported. */
  private static StackTraceElement[][] nativeWindowCallStacks() {
    return new StackTraceElement[][] {
      {
        frame("sun.awt.windows.WComponentPeer", "hide"),
        frame("java.awt.Component", "hide"),
        frame("java.awt.Window", "hide"),
        frame("com.frostwire.gui.TipOfTheDayMediator", "constructDialog"),
        frame(WATCHDOG_FRAME, "dispatchEvent")
      },
      {
        frame("sun.awt.windows.WComponentPeer", "getLocationOnScreen"),
        frame("java.awt.Component", "getLocationOnScreen"),
        frame("javax.swing.ToolTipManager", "showTipWindow"),
        frame("java.awt.event.InvocationEvent", "dispatch"),
        frame(WATCHDOG_FRAME, "dispatchEvent")
      },
      {
        frame("sun.awt.windows.WCanvasPeer", "create"),
        frame("sun.awt.windows.WPanelPeer", "<init>"),
        frame("java.awt.Panel", "addNotify"),
        frame("javax.swing.JLayeredPane", "add"),
        frame("javax.swing.PopupFactory$MediumWeightPopup", "show"),
        frame("javax.swing.ToolTipManager", "showTipWindow"),
        frame(WATCHDOG_FRAME, "dispatchEvent")
      },
      {
        frame("sun.awt.windows.WPopupMenuPeer", "createMenu"),
        frame("sun.awt.windows.WPopupMenuPeer", "<init>"),
        frame("java.awt.PopupMenu", "addNotify"),
        frame("sun.awt.windows.WTrayIconPeer", "showPopupMenu"),
        frame(WATCHDOG_FRAME, "dispatchEvent")
      },
      {
        frame("sun.awt.windows.WMenuPeer", "createMenu"),
        frame("sun.awt.windows.WMenuPeer", "<init>"),
        frame("java.awt.Menu", "addNotify"),
        frame(WATCHDOG_FRAME, "dispatchEvent")
      },
      {
        frame("sun.awt.windows.WMenuPeer", "createSubMenu"),
        frame("sun.awt.windows.WMenuPeer", "<init>"),
        frame("java.awt.Menu", "addNotify"),
        frame(WATCHDOG_FRAME, "dispatchEvent")
      },
    };
  }

  /**
   * The other native window calls of frostwire/frostwire#1552 get the same tag as the cursor
   * lookup, and the report (stack and title otherwise) is kept.
   */
  @Test
  void everyNativeWindowCallIsTaggedNativeMenuAndTheReportIsKept() {
    for (StackTraceElement[] stack : nativeWindowCallStacks()) {
      String call = stack[0].getClassName() + "." + stack[0].getMethodName();
      assertTrue(StrictEdtMode.isNativeWindowCall(stack), call);
      assertEquals(StrictEdtMode.NATIVE_MENU_TAG, StrictEdtMode.reportTag("", stack, true), call);
      StrictEdtMode.StallReport report = StrictEdtMode.stallReport("", stack, true, null);
      assertEquals(StrictEdtMode.NATIVE_MENU_TAG, report.tag(), call);
      assertSame(stack, report.stack(), call);
      StrictEdtMode.StallReport early =
          StrictEdtMode.stallReport("", new StackTraceElement[0], false, stack);
      assertEquals(
          StrictEdtMode.EARLY_SAMPLE_TAG + StrictEdtMode.NATIVE_MENU_TAG, early.tag(), call);
      assertSame(stack, early.stack(), call);
    }
  }

  @Test
  void appWorkAboveAnyNativeWindowCallIsNotTagged() {
    for (StackTraceElement[] stack : nativeWindowCallStacks()) {
      String call = stack[0].getClassName() + "." + stack[0].getMethodName();
      StackTraceElement[] withApp = new StackTraceElement[stack.length + 1];
      withApp[0] = frame("com.frostwire.gui.SomeLayout", "layoutContainer");
      System.arraycopy(stack, 0, withApp, 1, stack.length);
      assertFalse(StrictEdtMode.isNativeWindowCall(withApp), call);
      assertEquals("", StrictEdtMode.reportTag("", withApp, true), call);
      for (String app : new String[] {"com.limegroup.gnutella.X", "org.limewire.Y"}) {
        withApp[0] = frame(app, "run");
        assertFalse(StrictEdtMode.isNativeWindowCall(withApp), call + " " + app);
      }
      StackTraceElement[] watchdogAbove = new StackTraceElement[stack.length + 1];
      watchdogAbove[0] = frame(WATCHDOG_FRAME, "doWork");
      System.arraycopy(stack, 0, watchdogAbove, 1, stack.length);
      assertFalse(StrictEdtMode.isNativeWindowCall(watchdogAbove), call);
    }
  }

  @Test
  void callsOutsideTheMaintainerDecisionAreNotTaggedNativeMenu() {
    String[][] excluded = {
      {"sun.awt.windows.WComponentPeer", "pShow"},
      {"sun.awt.windows.WWindowPeer", "setFocusableWindow"},
      {"sun.awt.windows.WDialogPeer", "createAwtDialog"},
      {"sun.awt.windows.WComponentPeer", "show"},
      {"sun.awt.windows.WCanvasPeer", "other"},
    };
    for (String[] call : excluded) {
      StackTraceElement[] stack = {
        frame(call[0], call[1]),
        frame("java.awt.Window", "show"),
        frame(WATCHDOG_FRAME, "dispatchEvent")
      };
      assertFalse(StrictEdtMode.isNativeWindowCall(stack), call[0] + "." + call[1]);
      assertEquals("", StrictEdtMode.reportTag("", stack, true), call[0] + "." + call[1]);
    }
  }

  @Test
  void nativeWindowCallPrecedenceKeepsEveryOtherTag() {
    StackTraceElement[] nativeCall = nativeWindowCallStacks()[0];
    StackTraceElement[] renderQueueAndNative = {
      frame("sun.awt.SunToolkit", "awtLock"),
      frame("sun.java2d.pipe.RenderQueue", "lock"),
      nativeCall[0],
      frame(WATCHDOG_FRAME, "dispatchEvent")
    };
    StackTraceElement[] modalAndNative = {
      nativeCall[0],
      frame("sun.awt.windows.WDialogPeer", "showModal"),
      frame(WATCHDOG_FRAME, "dispatchEvent")
    };
    StackTraceElement[] modalOnly = {
      frame("sun.awt.windows.WDialogPeer", "showModal"), frame(WATCHDOG_FRAME, "dispatchEvent")
    };
    // A native window call under a nested modal loop: the call is the innermost stall.
    assertTrue(StrictEdtMode.isNativeWindowCall(modalAndNative));
    assertEquals(StrictEdtMode.NATIVE_MENU_TAG, StrictEdtMode.reportTag("", modalAndNative, true));
    assertEquals(StrictEdtMode.NATIVE_MODAL_TAG, StrictEdtMode.reportTag("", modalOnly, true));
    // The render-queue wait is checked first and wins, as it always did.
    assertEquals(
        StrictEdtMode.RENDER_QUEUE_TAG, StrictEdtMode.reportTag("", renderQueueAndNative, true));
    // A caller's tag wins over everything; a missed stack is stack-missed, not native-menu.
    assertEquals("shutdown:", StrictEdtMode.reportTag("shutdown:", nativeCall, true));
    assertEquals(StrictEdtMode.STACK_MISSED_TAG, StrictEdtMode.reportTag("", nativeCall, false));
  }

  @Test
  void nullAndEmptyStacksAreNotNativeWindowCalls() {
    assertFalse(StrictEdtMode.isNativeWindowCall(null));
    assertFalse(StrictEdtMode.isNativeWindowCall(new StackTraceElement[0]));
    assertFalse(StrictEdtMode.isNativeWindowCall(new StackTraceElement[] {null}));
    assertEquals(StrictEdtMode.STACK_MISSED_TAG, StrictEdtMode.reportTag("", null, true));
    assertEquals(
        StrictEdtMode.STACK_MISSED_TAG,
        StrictEdtMode.reportTag("", new StackTraceElement[0], true));
  }

  /**
   * A modal dialog's native loop (WDialogPeer.showModal) returns only when the user closes the
   * dialog: the event that opened it is not a stall (Icebase #20673ed7668e, Windows 11).
   */
  @Test
  void anOpenModalDialogIsTaggedNativeModal() {
    StackTraceElement[] stack = {
      frame("sun.awt.windows.WDialogPeer", "showModal"),
      frame("sun.awt.windows.WDialogPeer", "realShow"),
      frame("java.awt.Dialog", "setVisible"),
      frame("com.frostwire.gui.bittorrent.TorrentFetcherDownload", "lambda$downloadTorrent$0"),
      frame("java.awt.event.InvocationEvent", "dispatch"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertTrue(StrictEdtMode.isNativeModalDialogShow(stack));
    assertEquals(StrictEdtMode.NATIVE_MODAL_TAG, StrictEdtMode.reportTag("", stack, true));
  }

  @Test
  void appWorkWhileAModalDialogIsShownIsNotTaggedNativeModal() {
    StackTraceElement[] appAboveDialog = {
      frame("java.io.FileInputStream", "readBytes"),
      frame("com.frostwire.gui.bittorrent.SomeListener", "componentShown"),
      frame("sun.awt.windows.WDialogPeer", "showModal"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };
    StackTraceElement[] otherNative = {
      frame("sun.awt.windows.WToolkit", "sync"),
      frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent")
    };

    assertFalse(StrictEdtMode.isNativeModalDialogShow(appAboveDialog));
    assertEquals("", StrictEdtMode.reportTag("", appAboveDialog, true));
    assertFalse(StrictEdtMode.isNativeModalDialogShow(otherNative));
    assertFalse(StrictEdtMode.isNativeModalDialogShow(null));
  }

  @Test
  void cancelledStallTimersLeaveTheWatchdogQueueImmediately() {
    ScheduledThreadPoolExecutor scheduler = StrictEdtMode.newWatchdogScheduler();
    try {
      for (int i = 0; i < 1_000; i++) {
        scheduler
            .schedule(() -> {}, 5, TimeUnit.SECONDS)
            .cancel(false); // the event finished within the threshold
      }

      assertTrue(scheduler.getQueue().isEmpty(), "cancelled timers must not linger");
    } finally {
      scheduler.shutdownNow();
    }
  }

  private static StackTraceElement f(String cls, String method, int line) {
    return new StackTraceElement(
        cls, method, cls.substring(cls.lastIndexOf('.') + 1) + ".java", line);
  }

  private static StackTraceElement[] dispatch(StackTraceElement... top) {
    StackTraceElement[] tail = {
      f("java.awt.event.InvocationEvent", "dispatch", 323),
      f("java.awt.EventQueue", "dispatchEventImpl", 698),
      f("java.awt.EventQueue", "dispatchEvent", 677),
      f("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent", 159),
      f("java.awt.EventDispatchThread", "pumpOneEventForFilters", 203),
      f("java.awt.EventDispatchThread", "run", 90)
    };
    StackTraceElement[] all = new StackTraceElement[top.length + tail.length];
    System.arraycopy(top, 0, all, 0, top.length);
    System.arraycopy(tail, 0, all, top.length, tail.length);
    return all;
  }

  private static StallCpu.Window cpu(int edtMs, int systemPct) {
    return new StallCpu.Window(edtMs, 281, systemPct, 9000, java.util.List.of());
  }

  /** Icebase #4a7293b05945 and #b979dedc6e56: 0 ms of EDT CPU, idle machine, cheap top frame. */
  @Test
  void aCheapFrameOnAnIdleMachineWithNoEdtCpuIsAnIdleStall() {
    StackTraceElement[] paint =
        dispatch(
            f("javax.swing.JComponent", "paintChildren", 860),
            f("javax.swing.JComponent", "paint", 1123),
            f("javax.swing.RepaintManager", "paintDirtyRegions", 786));
    StackTraceElement[] label =
        dispatch(
            f("java.awt.Component", "invalidate", 2970),
            f("javax.swing.JComponent", "revalidate", 4919),
            f("com.limegroup.gnutella.gui.SizeStableLabel", "revalidate", 43));
    assertEquals(StrictEdtMode.IDLE_STALL_TAG, StrictEdtMode.idleStallTag(cpu(0, 12), "", paint));
    assertEquals(StrictEdtMode.IDLE_STALL_TAG, StrictEdtMode.idleStallTag(cpu(0, 2), "", label));
    assertEquals(
        StrictEdtMode.IDLE_STALL_TAG,
        StrictEdtMode.idleStallTag(cpu(0, 2), StrictEdtMode.EARLY_SAMPLE_TAG, label));
  }

  @Test
  void anEdtThatWorkedOrAMachineThatWasBusyIsNeverAnIdleStall() {
    StackTraceElement[] paint = dispatch(f("javax.swing.JComponent", "paintChildren", 860));
    assertEquals("", StrictEdtMode.idleStallTag(cpu(93, 12), "", paint));
    assertEquals("", StrictEdtMode.idleStallTag(cpu(1, 12), "", paint));
    assertEquals("", StrictEdtMode.idleStallTag(cpu(0, 100), "", paint));
    assertEquals("", StrictEdtMode.idleStallTag(null, "", paint));
    assertEquals(
        "",
        StrictEdtMode.idleStallTag(
            new StallCpu.Window(null, 1, 5, 1, java.util.List.of()), "", paint));
  }

  /** A blocked EDT also uses 0 ms of CPU: native calls, locks and I/O must stay reportable. */
  @Test
  void aBlockedEdtIsNeverAnIdleStall() {
    StackTraceElement[] clipboard =
        dispatch(
            f("sun.awt.windows.WClipboard", "openClipboard0", -2),
            f("sun.awt.datatransfer.SunClipboard", "setContents", 96),
            f("com.limegroup.gnutella.gui.GUIMediator", "setClipboardContent", 1017));
    StackTraceElement[] park =
        dispatch(
            f("jdk.internal.misc.Unsafe", "park", -2),
            f("java.util.concurrent.locks.LockSupport", "park", 223),
            f("com.limegroup.gnutella.gui.Foo", "bar", 1));
    StackTraceElement[] io =
        dispatch(
            f("java.io.FileInputStream", "readBytes", 1),
            f("com.limegroup.gnutella.gui.Foo", "bar", 1));
    assertEquals("", StrictEdtMode.idleStallTag(cpu(0, 2), "", clipboard));
    assertEquals("", StrictEdtMode.idleStallTag(cpu(0, 2), "", park));
    assertEquals("", StrictEdtMode.idleStallTag(cpu(0, 2), "", io));
  }

  @Test
  void idleStallNeverReplacesAMoreSpecificTag() {
    StackTraceElement[] paint = dispatch(f("javax.swing.JComponent", "paintChildren", 860));
    for (String tag :
        new String[] {
          StrictEdtMode.STACK_MISSED_TAG,
          StrictEdtMode.RENDER_QUEUE_TAG,
          StrictEdtMode.NATIVE_MENU_TAG,
          StrictEdtMode.NATIVE_MODAL_TAG,
          StrictEdtMode.SHELL_FOLDER_TAG,
          StrictEdtMode.SHUTDOWN_TAG
        }) {
      assertEquals("", StrictEdtMode.idleStallTag(cpu(0, 2), tag, paint), tag);
    }
    assertEquals(
        "",
        StrictEdtMode.idleStallTag(
            cpu(0, 2), "", new StackTraceElement[] {f("javax.swing.JComponent", "paint", 1)}),
        "a stack outside the watched dispatch is not an idle stall");
  }

  /** Icebase #2b1089fbcfa1 (D3D), #7f60e8dd6328 (Metal), #edd721963e48 and #b9b13fabccb0 (D3D). */
  @Test
  void gpuFlushWaitsAreRenderQueueStalls() {
    StackTraceElement[] d3dUpdate =
        dispatch(
            f("java.lang.Object", "wait0", -2),
            f("java.lang.Object", "wait", 391),
            f("sun.java2d.d3d.D3DScreenUpdateManager", "runUpdateNow", 398),
            f("sun.java2d.d3d.D3DBlitLoops", "IsoBlit", 356),
            f("javax.swing.RepaintManager$PaintManager", "paintDoubleBuffered", 1533));
    StackTraceElement[] metal =
        dispatch(
            f("java.lang.Object", "wait0", -2),
            f("sun.java2d.metal.MTLRenderQueue$QueueFlusher", "flushNow", 100),
            f("sun.java2d.metal.MTLRenderQueue", "flushNow", 80),
            f("javax.swing.RepaintManager$PaintManager", "paintDoubleBufferedImpl", 1560));
    StackTraceElement[] d3dFlush =
        dispatch(
            f("sun.java2d.d3d.D3DRenderQueue", "flushBuffer", -2),
            f("sun.java2d.d3d.D3DRenderQueue", "flushBuffer", 154),
            f("sun.java2d.d3d.D3DRenderQueue", "flushNow", 138),
            f("sun.java2d.d3d.D3DBlitLoops", "Blit", 275),
            f("com.frostwire.gui.theme.AbstractSkinPainter", "fillShapeNoAA", 106));
    for (StackTraceElement[] stack : new StackTraceElement[][] {d3dUpdate, metal, d3dFlush}) {
      assertTrue(StrictEdtMode.isRenderQueueLockWait(stack));
      assertEquals(StrictEdtMode.RENDER_QUEUE_TAG, StrictEdtMode.reportTag("", stack, true));
    }
  }

  @Test
  void anAppFrameAboveAGpuFlushIsNotARenderQueueStall() {
    StackTraceElement[] appWork =
        dispatch(
            f("com.limegroup.gnutella.gui.SomePanel", "compute", 10),
            f("sun.java2d.d3d.D3DRenderQueue", "flushNow", 138));
    assertFalse(StrictEdtMode.isRenderQueueLockWait(appWork));
    StackTraceElement[] unrelatedWait =
        dispatch(
            f("java.lang.Object", "wait0", -2),
            f("javax.swing.SwingWorker", "get", 20),
            f("javax.swing.RepaintManager", "paintDirtyRegions", 786));
    assertFalse(StrictEdtMode.isRenderQueueLockWait(unrelatedWait));
    StackTraceElement[] otherJava2d =
        dispatch(
            f("sun.java2d.d3d.D3DBlitLoops", "Blit", 275),
            f("sun.java2d.pipe.DrawImage", "blitSurfaceData", 993));
    assertFalse(StrictEdtMode.isRenderQueueLockWait(otherJava2d));
  }
}
