/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.search.FileSearchResult;
import com.frostwire.util.Ref;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** The preview player screen, without a real media player: layout, fullscreen and callbacks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PreviewPlayerActivityTest {

  private MockedStatic<Offers> offers;
  private MockedStatic<FWImageLoader> loaders;
  private MockedStatic<Engine> engines;
  private MockedStatic<UIUtils> ui;
  private FWImageLoader imageLoader;
  private ExecutorService pool;

  @Before
  public void setUp() {
    offers = mockStatic(Offers.class);
    offers.when(Offers::disabledAds).thenReturn(true);
    imageLoader = mock(FWImageLoader.class);
    loaders = mockStatic(FWImageLoader.class);
    loaders.when(() -> FWImageLoader.getInstance(any())).thenReturn(imageLoader);
    Engine engine = mock(Engine.class);
    pool = mock(ExecutorService.class);
    when(engine.getThreadPool()).thenReturn(pool);
    engines = mockStatic(Engine.class);
    engines.when(Engine::instance).thenReturn(engine);
    ui = mockStatic(UIUtils.class);
  }

  @After
  public void tearDown() {
    offers.close();
    loaders.close();
    engines.close();
    ui.close();
  }

  private ActivityController<PreviewPlayerActivity> controller(boolean audio, boolean fullScreen) {
    Intent intent =
        new Intent()
            .putExtra("displayName", "Track Name")
            .putExtra("source", "Artist Source")
            .putExtra("thumbnailUrl", "https://img.example.com/a.jpg")
            .putExtra("streamUrl", "not a url")
            .putExtra("hasVideo", !audio)
            .putExtra("audio", audio)
            .putExtra("isFullScreen", fullScreen);
    return Robolectric.buildActivity(PreviewPlayerActivity.class, intent);
  }

  @SuppressWarnings("deprecation")
  private static int fullscreenFlag(PreviewPlayerActivity activity) {
    return activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN;
  }

  private static String text(PreviewPlayerActivity a, int id) {
    return ((TextView) a.findViewById(id)).getText().toString();
  }

  @Test
  public void audioPreviewShowsTrackAndSourceAndLoadsTheThumbnail() {
    PreviewPlayerActivity activity = controller(true, false).create().start().resume().get();
    assertEquals("Track Name", text(activity, R.id.activity_preview_player_track_name));
    assertEquals("Artist Source", text(activity, R.id.activity_preview_player_artist_name));
    assertTrue(activity.getTitle().toString().endsWith("(buffering...)"));
    verify(imageLoader)
        .load(
            android.net.Uri.parse("https://img.example.com/a.jpg"),
            (ImageView) activity.findViewById(R.id.activity_preview_player_thumbnail),
            R.drawable.default_artwork);
    // audio previews hide the video surface behind a 1x1 texture
    View texture = activity.findViewById(R.id.activity_preview_player_videoview);
    assertEquals(1, texture.getLayoutParams().width);
    assertEquals(1, texture.getLayoutParams().height);
  }

  @Test
  public void optionsMenuOffersFullscreenOnlyForVideo() {
    PreviewPlayerActivity video = controller(false, false).create().start().resume().get();
    android.widget.PopupMenu popup = new android.widget.PopupMenu(video, new View(video));
    Menu menu = popup.getMenu();
    assertTrue(video.onCreateOptionsMenu(menu));
    assertTrue(video.onPrepareOptionsMenu(menu));
    assertTrue(menu.findItem(R.id.activity_preview_player_menu_fullscreen).isVisible());

    PreviewPlayerActivity audio = controller(true, false).create().start().resume().get();
    Menu audioMenu = new android.widget.PopupMenu(audio, new View(audio)).getMenu();
    audio.onCreateOptionsMenu(audioMenu);
    audio.onPrepareOptionsMenu(audioMenu);
    assertFalse(audioMenu.findItem(R.id.activity_preview_player_menu_fullscreen).isVisible());
  }

  @Test
  public void fullscreenMenuItemTogglesFullscreenAndBack() {
    PreviewPlayerActivity activity = controller(false, false).create().start().resume().get();
    MenuItem fullscreen = mock(MenuItem.class);
    when(fullscreen.getItemId()).thenReturn(R.id.activity_preview_player_menu_fullscreen);
    assertTrue(activity.onOptionsItemSelected(fullscreen));
    assertTrue(fullscreenFlag(activity) != 0);
    assertEquals(
        View.GONE,
        activity.findViewById(R.id.activity_preview_player_download_button).getVisibility());
    assertTrue(activity.onOptionsItemSelected(fullscreen));
    assertTrue(fullscreenFlag(activity) == 0);
    assertEquals(
        View.VISIBLE,
        activity.findViewById(R.id.activity_preview_player_download_button).getVisibility());
  }

  @Test
  public void touchingTheVideoTogglesFullscreenOnlyForVideoPreviews() {
    PreviewPlayerActivity video = controller(false, false).create().start().resume().get();
    View texture = video.findViewById(R.id.activity_preview_player_videoview);
    texture.dispatchTouchEvent(
        android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 1, 1, 0));
    assertEquals(
        View.GONE,
        video.findViewById(R.id.activity_preview_player_download_button).getVisibility());
  }

  @Test
  public void aStateRestoredFullscreenPreviewStartsFullscreen() {
    PreviewPlayerActivity activity = controller(false, true).create().start().resume().get();
    assertTrue(fullscreenFlag(activity) != 0);
  }

  @Test
  public void instanceStateKeepsWhatIsBeingPreviewed() {
    ActivityController<PreviewPlayerActivity> controller = controller(true, false).create();
    Bundle state = new Bundle();
    controller.saveInstanceState(state);
    assertEquals("Track Name", state.getString("displayName"));
    assertEquals("Artist Source", state.getString("source"));
    assertEquals("not a url", state.getString("streamUrl"));
    assertEquals("https://img.example.com/a.jpg", state.getString("thumbnailUrl"));
    assertTrue(state.getBoolean("audio"));
    assertFalse(state.getBoolean("isFullScreen"));
  }

  @Test
  public void downloadButtonWithoutAResultClosesThePreview() {
    PreviewPlayerActivity.srRef = null;
    PreviewPlayerActivity activity = controller(true, false).create().start().resume().get();
    ((Button) activity.findViewById(R.id.activity_preview_player_download_button)).performClick();
    assertTrue(activity.isFinishing());
  }

  @Test
  public void acceptingTheDownloadDialogReturnsToMainWithTheStartTransferAction() {
    PreviewPlayerActivity activity = controller(true, false).create().start().resume().get();
    // no result behind the dialog: closing the preview is all that happens
    activity.onDialogClick("other", Dialog.BUTTON_POSITIVE);
    assertFalse(activity.isFinishing());
    FileSearchResult sr = mock(FileSearchResult.class);
    com.frostwire.android.gui.dialogs.NewTransferDialog.srRef = Ref.weak(sr);
    activity.onDialogClick(
        com.frostwire.android.gui.dialogs.NewTransferDialog.TAG, Dialog.BUTTON_POSITIVE);
    Intent started = shadowOf(activity).getNextStartedActivity();
    assertEquals(MainActivity.class.getName(), started.getComponent().getClassName());
    assertEquals(Constants.ACTION_START_TRANSFER_FROM_PREVIEW, started.getAction());
    assertTrue(activity.isFinishing());
    com.frostwire.android.gui.dialogs.NewTransferDialog.srRef = null;
  }

  @Test
  public void surfaceAvailableResolvesTheStreamOnTheEnginePoolAndAnUnusableUrlClosesThePreview() {
    doAnswer(
            i -> {
              ((Runnable) i.getArgument(0)).run();
              return null;
            })
        .when(pool)
        .execute(any());
    PreviewPlayerActivity activity = controller(true, false).create().start().resume().get();
    activity.onSurfaceTextureAvailable(new SurfaceTexture(0), 10, 10);
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(activity.isFinishing());
    ui.verify(() -> UIUtils.showLongMessage(activity, R.string.check_internet_connection));
    activity.onSurfaceTextureSizeChanged(new SurfaceTexture(0), 20, 20);
    assertFalse(activity.onSurfaceTextureDestroyed(new SurfaceTexture(0)));
    activity.onSurfaceTextureUpdated(new SurfaceTexture(0));
  }

  @Test
  public void playerCallbacksUpdateTitleAndCloseOnEndOrError() {
    PreviewPlayerActivity activity = controller(true, false).create().start().resume().get();
    activity.onIsPlayingChanged(true);
    assertEquals(
        activity.getString(R.string.media_preview, activity.getString(R.string.audio)),
        activity.getTitle().toString());

    activity.onPlaybackStateChanged(Player.STATE_READY);
    AudioManager am =
        (AudioManager) activity.getSystemService(android.content.Context.AUDIO_SERVICE);
    assertNotNull(shadowOf(am).getLastAudioFocusRequest());
    assertFalse(activity.isFinishing());

    activity.onPlaybackStateChanged(Player.STATE_ENDED);
    assertTrue(activity.isFinishing());
  }

  @Test
  public void playbackErrorClosesThePreview() {
    PreviewPlayerActivity activity = controller(true, false).create().start().resume().get();
    activity.onPlayerError(
        new PlaybackException("boom", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED));
    assertTrue(activity.isFinishing());
  }

  @Test
  public void losingAudioFocusStopsTheBufferingTitleAndPausingReleasesThePlayer() {
    PreviewPlayerActivity activity = controller(false, false).create().start().resume().get();
    activity.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
    assertEquals(
        activity.getString(R.string.media_preview, activity.getString(R.string.video)),
        activity.getTitle().toString());
    activity.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
    ActivityController<PreviewPlayerActivity> c =
        controller(false, false).create().start().resume();
    c.pause().stop().destroy();
  }

  @Test
  public void startingAPreviewStopsTheAppMediaPlayer() {
    com.frostwire.android.gui.services.Engine engine = Engine.instance();
    com.frostwire.android.core.player.CoreMediaPlayer player =
        mock(com.frostwire.android.core.player.CoreMediaPlayer.class);
    when(player.isPlaying()).thenReturn(true);
    when(engine.getMediaPlayer()).thenReturn(player);
    controller(true, false).create();
    verify(player).stop();
  }
}
