/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Looper;
import android.view.TextureView;
import android.widget.FrameLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.FWImageLoader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The preview player with a fake ExoPlayer and a loopback HTTP server standing in for the stream
 * host: redirect resolution, player start-up, surface changes, resizing and release.
 */
@SuppressWarnings("try")
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PreviewPlayerPlaybackTest {

  private MockedStatic<Offers> offers;
  private MockedStatic<FWImageLoader> loaders;
  private MockedStatic<Engine> engines;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<MusicUtils> music;
  private ServerSocket server;
  private final java.util.List<ActivityController<PreviewPlayerActivity>> created =
      new java.util.ArrayList<>();
  private String redirectBase;

  @Before
  public void setUp() throws Exception {
    offers = mockStatic(Offers.class);
    offers.when(Offers::disabledAds).thenReturn(true);
    loaders = mockStatic(FWImageLoader.class);
    loaders.when(() -> FWImageLoader.getInstance(any())).thenReturn(mock(FWImageLoader.class));
    ExecutorService pool = mock(ExecutorService.class);
    doAnswer(
            i -> {
              ((Runnable) i.getArgument(0)).run();
              return null;
            })
        .when(pool)
        .execute(any());
    Engine engine = mock(Engine.class);
    when(engine.getThreadPool()).thenReturn(pool);
    engines = mockStatic(Engine.class);
    engines.when(Engine::instance).thenReturn(engine);
    ui = mockStatic(UIUtils.class);
    music = mockStatic(MusicUtils.class);
    music.when(MusicUtils::isPlaying).thenReturn(true);

    server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
    redirectBase = "http://127.0.0.1:" + server.getLocalPort();
    Thread acceptor =
        new Thread(
            () -> {
              while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                  BufferedReader in =
                      new BufferedReader(
                          new InputStreamReader(
                              socket.getInputStream(), StandardCharsets.US_ASCII));
                  String requestLine = in.readLine();
                  String line;
                  while ((line = in.readLine()) != null && !line.isEmpty()) {
                    // skip the request headers
                  }
                  String response =
                      requestLine != null && requestLine.contains("/redirect")
                          ? "HTTP/1.1 302 Found\r\nLocation: "
                              + redirectBase
                              + "/final.mp3\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                          : "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                  OutputStream out = socket.getOutputStream();
                  out.write(response.getBytes(StandardCharsets.US_ASCII));
                  out.flush();
                } catch (Exception closedOrBroken) {
                  // the test is over or the client hung up
                }
              }
            });
    acceptor.setDaemon(true);
    acceptor.start();
  }

  @After
  public void tearDown() throws Exception {
    // the resolved stream url is cached statically until an activity is destroyed
    for (ActivityController<PreviewPlayerActivity> c : created) {
      try {
        c.destroy();
      } catch (RuntimeException alreadyGone) {
        // a test may have destroyed it already
      }
    }
    server.close();
    offers.close();
    loaders.close();
    engines.close();
    ui.close();
    music.close();
  }

  private ActivityController<PreviewPlayerActivity> controller(boolean audio, String streamUrl) {
    Intent intent =
        new Intent()
            .putExtra("displayName", "Track")
            .putExtra("source", "Source")
            .putExtra("streamUrl", streamUrl)
            .putExtra("hasVideo", !audio)
            .putExtra("audio", audio);
    ActivityController<PreviewPlayerActivity> controller =
        Robolectric.buildActivity(PreviewPlayerActivity.class, intent);
    created.add(controller);
    return controller;
  }

  private static ExoPlayer fakePlayer() {
    ExoPlayer player = mock(ExoPlayer.class);
    when(player.getVideoSize()).thenReturn(new VideoSize(1280, 720));
    return player;
  }

  private MockedConstruction<ExoPlayer.Builder> fakeBuilder(ExoPlayer player) {
    return mockConstruction(
        ExoPlayer.Builder.class, (builder, context) -> when(builder.build()).thenReturn(player));
  }

  @Test
  public void aRedirectingStreamUrlIsResolvedBeforePlaybackStartsAndPausesTheMusicPlayer() {
    ExoPlayer player = fakePlayer();
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      PreviewPlayerActivity activity =
          controller(false, redirectBase + "/redirect").create().start().resume().get();
      activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 100, 100);
      shadowOf(Looper.getMainLooper()).idle();

      assertEquals(1, builders.constructed().size());
      ArgumentCaptor<MediaItem> item = ArgumentCaptor.forClass(MediaItem.class);
      verify(player).setMediaItem(item.capture());
      assertEquals(redirectBase + "/final.mp3", item.getValue().localConfiguration.uri.toString());
      verify(player).addListener(activity);
      verify(player).prepare();
      verify(player).play();
      music.verify(MusicUtils::playPauseOrResume);
      // a video preview renders onto the texture's surface
      verify(player).setVideoSurface(any(android.view.Surface.class));
      activity.onDestroy();
    }
  }

  @Test
  public void aStreamWithoutARedirectIsPlayedFromItsOwnUrl() {
    ExoPlayer player = fakePlayer();
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      PreviewPlayerActivity activity =
          controller(true, redirectBase + "/direct").create().start().resume().get();
      activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 1, 1);
      shadowOf(Looper.getMainLooper()).idle();
      ArgumentCaptor<MediaItem> item = ArgumentCaptor.forClass(MediaItem.class);
      verify(player).setMediaItem(item.capture());
      assertEquals(redirectBase + "/direct", item.getValue().localConfiguration.uri.toString());
      // audio previews never attach a video surface
      verify(player, never()).setVideoSurface(any(android.view.Surface.class));
    }
  }

  @Test
  public void aPreviewThatFinishedBeforeTheStreamResolvedNeverStartsAPlayer() {
    ExoPlayer player = fakePlayer();
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      PreviewPlayerActivity activity =
          controller(true, redirectBase + "/direct").create().start().resume().get();
      activity.finish();
      activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 1, 1);
      shadowOf(Looper.getMainLooper()).idle();
      assertTrue(builders.constructed().isEmpty());
    }
  }

  @Test
  public void aVideoSizeReportResizesTheTextureToTheScreenWidthKeepingTheAspectRatio() {
    ExoPlayer player = fakePlayer();
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      PreviewPlayerActivity activity =
          controller(false, redirectBase + "/direct").create().start().resume().get();
      activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 1, 1);
      shadowOf(Looper.getMainLooper()).idle();
      activity.onVideoSizeChanged(new VideoSize(1280, 720));
      TextureView texture = activity.findViewById(R.id.activity_preview_player_videoview);
      int screenWidth = activity.getWindowManager().getCurrentWindowMetrics().getBounds().width();
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) texture.getLayoutParams();
      assertEquals(screenWidth, params.width);
      assertEquals((int) (screenWidth * (720f / 1280f)), params.height);
      assertEquals(0f, texture.getRotation(), 0f);
    }
  }

  @Test
  public void fullscreenRotatesTheTextureAndSwapsItsDimensions() {
    ExoPlayer player = fakePlayer();
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      PreviewPlayerActivity activity =
          controller(false, redirectBase + "/direct").create().start().resume().get();
      activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 1, 1);
      shadowOf(Looper.getMainLooper()).idle();
      android.view.MenuItem fullscreen = mock(android.view.MenuItem.class);
      when(fullscreen.getItemId()).thenReturn(R.id.activity_preview_player_menu_fullscreen);
      activity.onOptionsItemSelected(fullscreen);
      TextureView texture = activity.findViewById(R.id.activity_preview_player_videoview);
      android.graphics.Rect bounds =
          activity.getWindowManager().getCurrentWindowMetrics().getBounds();
      assertEquals(90f, texture.getRotation(), 0f);
      assertEquals(bounds.height(), texture.getLayoutParams().width);
      assertEquals(bounds.width(), texture.getLayoutParams().height);
    }
  }

  @Test
  public void surfaceChangesRetargetThePlayerAndDestroyingTheSurfaceDetachesIt() {
    ExoPlayer player = fakePlayer();
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      PreviewPlayerActivity activity =
          controller(false, redirectBase + "/direct").create().start().resume().get();
      activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 1, 1);
      shadowOf(Looper.getMainLooper()).idle();
      activity.onSurfaceTextureSizeChanged(new SurfaceTexture(1), 50, 50);
      verify(player, org.mockito.Mockito.times(2)).setVideoSurface(any(android.view.Surface.class));
      assertFalse(activity.onSurfaceTextureDestroyed(new SurfaceTexture(2)));
      verify(player).setVideoSurface(null);
    }
  }

  @Test
  public void pausingOrLosingAudioFocusReleasesThePlayer() {
    ExoPlayer player = fakePlayer();
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      ActivityController<PreviewPlayerActivity> controller =
          controller(true, redirectBase + "/direct").create().start().resume();
      PreviewPlayerActivity activity = controller.get();
      activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 1, 1);
      shadowOf(Looper.getMainLooper()).idle();
      activity.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS);
      verify(player).stop();
      verify(player).removeListener(activity);
      verify(player).release();
      // releasing twice must be harmless: the player is already gone
      controller.pause();
      verify(player).release();
    }
  }

  @Test
  public void aPlayingPreviewSavesItsPositionInTheInstanceState() {
    ExoPlayer player = fakePlayer();
    when(player.isPlaying()).thenReturn(true);
    when(player.getCurrentPosition()).thenReturn(4321L);
    try (MockedConstruction<ExoPlayer.Builder> builders = fakeBuilder(player)) {
      ActivityController<PreviewPlayerActivity> controller =
          controller(true, redirectBase + "/direct").create().start().resume();
      controller.get().onSurfaceTextureAvailable(new SurfaceTexture(0), 1, 1);
      shadowOf(Looper.getMainLooper()).idle();
      Bundle state = new Bundle();
      controller.saveInstanceState(state);
      assertEquals(4321, state.getInt("currentPosition"));
    }
  }

  @Test
  public void aPreviewThatIsNotPlayingSavesNoPosition() {
    ActivityController<PreviewPlayerActivity> controller =
        controller(true, redirectBase + "/direct").create().start().resume();
    Bundle state = new Bundle();
    controller.saveInstanceState(state);
    assertFalse(state.containsKey("currentPosition"));
    assertNull(state.getString("thumbnailUrl"));
  }

  @Test
  public void videoSizeReportsWithoutDimensionsOrAnEngineDoNothing() {
    PreviewPlayerActivity activity =
        controller(false, redirectBase + "/direct").create().start().resume().get();
    TextureView texture = activity.findViewById(R.id.activity_preview_player_videoview);
    int before = texture.getLayoutParams().width;
    activity.onVideoSizeChanged(new VideoSize(0, 0));
    activity.onVideoSizeChanged(new VideoSize(640, 360));
    assertEquals("no player yet, so nothing is resized", before, texture.getLayoutParams().width);
  }
}
