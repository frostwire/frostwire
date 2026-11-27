/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * TellurideCourier drives yt-dlp through the embedded Python runtime. The runtime is replaced by
 * mocks here; the calls run on a plain worker thread because the courier insists on being off the
 * main thread and Mockito's static mocks are per thread.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TellurideCourierQueryTest {

  private static final String URL = "https://example.com/watch?v=1";

  private static final class RecordingListener implements SearchListener {
    final List<SearchResult> results = new ArrayList<>();
    final List<Long> stopped = new ArrayList<>();

    @Override
    public void onResults(long token, List<? extends SearchResult> results) {
      this.results.addAll(results);
    }

    @Override
    public void onError(long token, SearchError error) {}

    @Override
    public void onStopped(long token) {
      stopped.add(token);
    }
  }

  /** What the mocked Python runtime does when the courier talks to it. */
  private static final class FakePython {
    Python python = mock(Python.class);
    PyObject module = mock(PyObject.class);
    boolean started = true;
    boolean pythonAvailable = true;

    Python instance() {
      return pythonAvailable ? python : null;
    }
  }

  private interface Body {
    void run(MockedStatic<Engine> engine, FakePython fake) throws Exception;
  }

  private static void withPython(FakePython fake, Body body) throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              Platform platform = mock(Platform.class);
              when(platform.isUIThread()).thenReturn(false);
              try (MockedStatic<Platforms> platforms = Mockito.mockStatic(Platforms.class);
                  MockedStatic<Python> py = Mockito.mockStatic(Python.class);
                  MockedStatic<Engine> engine = Mockito.mockStatic(Engine.class)) {
                platforms.when(Platforms::get).thenReturn(platform);
                py.when(Python::isStarted).thenAnswer(i -> fake.started);
                engine.when(Engine::getPythonInstance).thenAnswer(i -> fake.instance());
                when(fake.python.getModule("telluride")).thenReturn(fake.module);
                body.run(engine, fake);
              } catch (Throwable t) {
                failure.set(t);
              }
            },
            "courier-test-worker");
    worker.start();
    worker.join(30_000);
    if (worker.isAlive()) {
      fail("worker did not finish");
    }
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  private static TellurideCourierSearchPerformer performer(RecordingListener listener) {
    TellurideCourierSearchPerformer p = new TellurideCourierSearchPerformer(5, URL, false);
    p.setListener(listener);
    return p;
  }

  @Test
  public void pythonThatIsNotStartedYetIsStartedFirst() throws Exception {
    FakePython fake = new FakePython();
    fake.started = false;
    fake.pythonAvailable = false;
    withPython(
        fake,
        (engine, f) -> {
          performer(new RecordingListener()).perform();
          engine.verify(Engine::startPython);
        });
  }

  @Test
  public void withoutAPythonInstanceTheSearchEndsWithNoResults() throws Exception {
    FakePython fake = new FakePython();
    fake.pythonAvailable = false;
    withPython(
        fake,
        (engine, f) -> {
          RecordingListener listener = new RecordingListener();
          performer(listener).perform();
          assertTrue(listener.results.isEmpty());
          assertEquals(List.of(5L), listener.stopped);
        });
  }

  @Test
  public void aMissingTellurideModuleEndsTheSearchEmpty() throws Exception {
    FakePython fake = new FakePython();
    withPython(
        fake,
        (engine, f) -> {
          Mockito.doReturn(null).when(f.python).getModule("telluride");
          RecordingListener listener = new RecordingListener();
          performer(listener).perform();
          assertTrue(listener.results.isEmpty());
          assertEquals(List.of(5L), listener.stopped);
        });
  }

  @Test
  public void aModuleLoadFailureEndsTheSearchEmpty() throws Exception {
    FakePython fake = new FakePython();
    withPython(
        fake,
        (engine, f) -> {
          Mockito.doThrow(new RuntimeException("no module")).when(f.python).getModule("telluride");
          RecordingListener listener = new RecordingListener();
          performer(listener).perform();
          assertTrue(listener.results.isEmpty());
          assertEquals(List.of(5L), listener.stopped);
        });
  }

  @Test
  public void aFailingYtDlpCallEndsTheSearchEmpty() throws Exception {
    FakePython fake = new FakePython();
    withPython(
        fake,
        (engine, f) -> {
          Mockito.doThrow(new RuntimeException("yt-dlp"))
              .when(f.module)
              .callAttr("query_video", URL);
          RecordingListener listener = new RecordingListener();
          performer(listener).perform();
          assertTrue(listener.results.isEmpty());
          assertEquals(List.of(5L), listener.stopped);
        });
  }

  @Test
  public void aNullYtDlpResultEndsTheSearchEmpty() throws Exception {
    FakePython fake = new FakePython();
    withPython(
        fake,
        (engine, f) -> {
          when(f.module.callAttr("query_video", URL)).thenReturn(null);
          RecordingListener listener = new RecordingListener();
          performer(listener).perform();
          assertTrue(listener.results.isEmpty());
          assertEquals(List.of(5L), listener.stopped);
        });
  }

  @Test
  public void anAbortedCallbackStopsBeforeCallingYtDlp() throws Exception {
    FakePython fake = new FakePython();
    withPython(
        fake,
        (engine, f) -> {
          RecordingListener listener = new RecordingListener();
          TellurideCourierSearchPerformer p = performer(listener);
          p.stop();
          listener.stopped.clear();
          p.perform();
          assertEquals(
              0,
              Mockito.mockingDetails(f.module).getInvocations().stream()
                  .filter(inv -> inv.getMethod().getName().equals("callAttr"))
                  .count());
          assertTrue(listener.results.isEmpty());
          assertTrue(listener.stopped.isEmpty());
        });
  }

  @Test
  public void abortCurrentQueryAbortsTheCallbackThatWasLeftBehind() throws Exception {
    FakePython fake = new FakePython();
    fake.pythonAvailable = false;
    withPython(
        fake,
        (engine, f) -> {
          RecordingListener listener = new RecordingListener();
          TellurideCourierSearchPerformer p = performer(listener);
          p.perform();
          listener.stopped.clear();
          TellurideCourier.abortCurrentQuery();
          // The callback is aborted now: a late result is dropped.
          TellurideCourierCallback<?> late = new TellurideCourierCallback<>(p, URL);
          late.onResults(List.of(), false);
          assertFalse(p.isStopped());
        });
  }

  @Test
  public void playlistFailuresEndTheSearchEmpty() throws Exception {
    FakePython fake = new FakePython();
    withPython(
        fake,
        (engine, f) -> {
          RecordingListener listener = new RecordingListener();
          TellurideCourierSearchPerformer p = new TellurideCourierSearchPerformer(8, URL, true);
          p.setListener(listener);

          f.pythonAvailable = false;
          p.perform();
          f.pythonAvailable = true;
          Mockito.doThrow(new RuntimeException("no module")).when(f.python).getModule("telluride");
          p.perform();
          Mockito.doReturn(null).when(f.python).getModule("telluride");
          p.perform();
          Mockito.doReturn(f.module).when(f.python).getModule("telluride");
          Mockito.doThrow(new RuntimeException("yt-dlp"))
              .when(f.module)
              .callAttr("query_playlist", URL);
          p.perform();

          assertTrue(listener.results.isEmpty());
          assertEquals(List.of(8L, 8L, 8L, 8L), listener.stopped);
        });
  }

  @Test
  public void aQueryWithoutACallbackAndWithoutAResultJustReturns() throws Exception {
    FakePython fake = new FakePython();
    withPython(
        fake,
        (engine, f) -> {
          TellurideCourier.<AbstractListAdapter<TellurideSearchResult>>queryPage(URL, null);
          assertEquals(
              1,
              Mockito.mockingDetails(f.module).getInvocations().stream()
                  .filter(inv -> inv.getMethod().getName().equals("callAttr"))
                  .count());
        });
  }
}
