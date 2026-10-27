/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StrictEdtModeTest {

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
}
