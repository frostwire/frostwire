/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.core.player.EphemeralPlaylist;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.ClearableEditTextView;
import com.frostwire.android.util.SystemUtils;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@SuppressWarnings("deprecation")
public class UIUtilsBehaviorTest {
  private MockedStatic<SystemUtils> system;
  private final List<Runnable> background = new ArrayList<>();
  private final List<Runnable> ui = new ArrayList<>();
  private Application app;

  @Before
  public void setUp() {
    app = ApplicationProvider.getApplicationContext();
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              background.add(call.getArgument(1));
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            call -> {
              ui.add(call.getArgument(0));
              return null;
            });
  }

  @After
  public void tearDown() {
    system.close();
  }

  @Test
  public void bytesAndSpeedsAreFormattedForHumans() {
    assertEquals("0.00 b", UIUtils.getBytesInHuman(0));
    assertEquals("1.00 b", UIUtils.getBytesInHuman(1));
    assertEquals("2.00 KB", UIUtils.getBytesInHuman(2048));
    assertEquals("3.00 Mb", UIUtils.getBytesInHuman(3 * 1024 * 1024));
    assertEquals("1.50 Gb", UIUtils.getBytesInHuman(1.5 * 1024 * 1024 * 1024));
    assertEquals("2.00 Tb", UIUtils.getBytesInHuman(2.0 * 1024 * 1024 * 1024 * 1024));
    assertTrue(UIUtils.rate2speed(1234.4).endsWith(" KB/s"));
    assertTrue(UIUtils.rate2speed(1234.4).startsWith("1"));
  }

  @Test
  public void mimeTypesAndAudioDetection() {
    assertEquals("audio/mpeg", UIUtils.getMimeType("/x/song.mp3"));
    assertTrue(UIUtils.isAudioFile("/x/song.MP3"));
    assertTrue(UIUtils.isAudioFile("/x/voice.amr"));
    assertTrue(UIUtils.isAudioFile("/x/song.flac"));
    assertTrue(UIUtils.isAudioFile("/x/tune.midi"));
    assertFalse(UIUtils.isAudioFile("/x/movie.webm"));
    assertFalse(UIUtils.isAudioFile("/x/movie.mp4"));
    assertFalse(UIUtils.isAudioFile(null));
  }

  @Test
  public void fileUrisUseTheProviderOnlyWhenAsked() {
    File file = new File("/storage/emulated/0/Download/a.txt");
    assertEquals(Uri.fromFile(file), UIUtils.getFileUri(app, file, false));
    assertEquals(Uri.fromFile(file), UIUtils.getFileUri(app, file.getPath(), false));
    assertEquals(Uri.fromFile(file), UIUtils.getFileUri(app, file.getPath()));
    assertEquals(Uri.fromFile(file), UIUtils.getFileUri(app, file));
    system.when(SystemUtils::hasNougatOrNewer).thenReturn(true);
    Uri provided = UIUtils.getFileUri(app, new File(app.getFilesDir(), "a.txt"), true);
    assertEquals("content", provided.getScheme());
    assertTrue(provided.getAuthority().endsWith(".fileprovider"));
  }

  @Test
  public void shortAndLongMessagesShowToastsOnTheUiThread() {
    UIUtils.showShortMessage(app, "hello");
    assertEquals("hello", ShadowToast.getTextOfLatestToast());
    assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().getDuration());
    UIUtils.showLongMessage(app, "longer");
    assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
    UIUtils.showShortMessage(app, R.string.dismiss);
    assertEquals(app.getString(R.string.dismiss), ShadowToast.getTextOfLatestToast());
    UIUtils.showLongMessage(app, R.string.dismiss);
    assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
    UIUtils.showLongMessage(app, R.string.dismiss, new Object[0]);
    UIUtils.showShortMessage(app, R.string.dismiss, new Object[0]);
    int shown = ShadowToast.shownToastCount();
    UIUtils.showToastMessage(null, "ignored", Toast.LENGTH_SHORT);
    UIUtils.showToastMessage(app, null, Toast.LENGTH_SHORT);
    assertEquals(shown, ShadowToast.shownToastCount());
  }

  @Test
  public void shutdownAndGoHomeIntentsTargetTheMainActivity() {
    ShadowApplication shadow = Shadows.shadowOf(app);
    UIUtils.sendShutdownIntent(app);
    Intent shutdown = shadow.getNextStartedActivity();
    assertEquals(MainActivity.class.getName(), shutdown.getComponent().getClassName());
    assertTrue(shutdown.getBooleanExtra("shutdown-frostwire", false));
    assertTrue((shutdown.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
    UIUtils.sendGoHomeIntent(app);
    Intent home = shadow.getNextStartedActivity();
    assertTrue(home.getBooleanExtra("gohome-frostwire", false));
    assertFalse(home.getBooleanExtra("shutdown-frostwire", false));
  }

  @Test
  public void broadcastActionCarriesByteExtras() {
    ShadowApplication shadow = Shadows.shadowOf(app);
    UIUtils.broadcastAction(app, "test.ACTION", new UIUtils.IntentByteExtra("k", (byte) 7));
    Intent sent = shadow.getBroadcastIntents().get(shadow.getBroadcastIntents().size() - 1);
    assertEquals("test.ACTION", sent.getAction());
    assertEquals(7, sent.getByteExtra("k", (byte) 0));
    int count = shadow.getBroadcastIntents().size();
    UIUtils.broadcastAction(app, "test.PLAIN");
    assertEquals(count + 1, shadow.getBroadcastIntents().size());
    UIUtils.broadcastAction(null, "x");
    UIUtils.broadcastAction(app, null);
    assertEquals(count + 1, shadow.getBroadcastIntents().size());
  }

  @Test
  public void openUrlLaunchesABrowsableViewIntentFromTheBackground() {
    UIUtils.openURL(app, "  https://www.frostwire.com/  ");
    assertEquals(1, background.size());
    assertNull(Shadows.shadowOf(app).peekNextStartedActivity());
    background.get(0).run();
    Intent launched = Shadows.shadowOf(app).getNextStartedActivity();
    assertEquals(Intent.ACTION_VIEW, launched.getAction());
    assertEquals("https://www.frostwire.com/", launched.getDataString());
    assertTrue(launched.hasCategory(Intent.CATEGORY_BROWSABLE));
    assertTrue((launched.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }

  @Test
  public void openUrlIgnoresMissingOrBlankInput() {
    UIUtils.openURL(null, "https://x");
    UIUtils.openURL(app, null);
    UIUtils.openURL(app, "   ");
    assertTrue(background.isEmpty());
  }

  @Test
  public void openUrlFromAnActivityDoesNotForceANewTask() {
    Activity activity = Robolectric.buildActivity(Activity.class).create().get();
    UIUtils.openURL(new ContextWrapper(activity), "https://www.frostwire.com/");
    background.get(0).run();
    Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
    assertEquals(0, launched.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  @Test
  public void openUrlTellsTheUserWhenNoBrowserExists() {
    Context context = mock(Context.class);
    when(context.getApplicationContext()).thenReturn(context);
    org.mockito.Mockito.doThrow(new ActivityNotFoundException("none"))
        .when(context)
        .startActivity(any(Intent.class));
    UIUtils.openURL(context, "https://www.frostwire.com/");
    background.get(0).run();
    assertEquals("an error toast is queued for the UI thread", 1, ui.size());
  }

  @Test
  public void clickUrlOpensTheUrlOnClick() {
    View view = new View(app);
    UIUtils.setupClickUrl(view, "https://www.frostwire.com/");
    view.performClick();
    assertEquals(1, background.size());
  }

  @Test
  public void diceRollThresholdsAreHonoured() {
    ConfigurationManager cm = mock(ConfigurationManager.class);
    when(cm.getInt("never")).thenReturn(0);
    when(cm.getInt("always")).thenReturn(100);
    when(cm.getInt("negative")).thenReturn(-5);
    when(cm.getInt("half")).thenReturn(50);
    assertFalse(UIUtils.diceRollPassesThreshold(cm, "never"));
    assertFalse(UIUtils.diceRollPassesThreshold(cm, "negative"));
    assertTrue(UIUtils.diceRollPassesThreshold(cm, "always"));
    int passes = 0;
    for (int i = 0; i < 400; i++) {
      if (UIUtils.diceRollPassesThreshold(cm, "half")) {
        passes++;
      }
    }
    assertTrue(
        "a 50% threshold passes sometimes, not always: " + passes, passes > 0 && passes < 400);
  }

  @Test
  public void randomPitchesStayInTheKnownRange() {
    for (int i = 0; i < 50; i++) {
      assertNotNull(app.getString(UIUtils.randomPitchResId(false)));
      assertNotNull(app.getString(UIUtils.randomPitchResId(true)));
    }
  }

  @Test
  public void screenLockStateComesFromTheKeyguard() {
    assertTrue(UIUtils.isScreenLocked(null));
    Context context = mock(Context.class);
    assertTrue("no keyguard manager means locked", UIUtils.isScreenLocked(context));
    android.app.KeyguardManager km = mock(android.app.KeyguardManager.class);
    when(context.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(km);
    when(km.isKeyguardLocked()).thenReturn(false);
    assertFalse(UIUtils.isScreenLocked(context));
    when(km.isKeyguardLocked()).thenReturn(true);
    assertTrue(UIUtils.isScreenLocked(context));
  }

  @Test
  public void clipboardLinksAreAutoPastedButOtherTextIsNot() {
    ClipboardManager clipboard = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
    ClearableEditTextView target = mock(ClearableEditTextView.class);
    clipboard.setPrimaryClip(ClipData.newPlainText("l", "  https://example.com/a.torrent \n"));
    UIUtils.autoPasteMagnetOrURL(app, target);
    verify(target).setText("https://example.com/a.torrent");
    clipboard.setPrimaryClip(
        ClipData.newPlainText("l", "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"));
    UIUtils.autoPasteMagnetOrURL(app, target);
    verify(target).setText("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567");
    ClearableEditTextView untouched = mock(ClearableEditTextView.class);
    clipboard.setPrimaryClip(ClipData.newPlainText("l", "just some words"));
    UIUtils.autoPasteMagnetOrURL(app, untouched);
    clipboard.setPrimaryClip(ClipData.newPlainText("l", "   "));
    UIUtils.autoPasteMagnetOrURL(app, untouched);
    verify(untouched, never()).setText(anyString());
  }

  @Test
  public void appIconColorResolvesFromResources() {
    assertEquals(
        androidx.core.content.ContextCompat.getColor(app, R.color.app_icon_primary),
        UIUtils.getAppIconPrimaryColor(app));
  }

  @Test
  public void transfersAreShownThroughTheLiveActivityOrANewIntent() {
    ConfigurationManager cm = mock(ConfigurationManager.class);
    try (MockedStatic<ConfigurationManager> config = mockStatic(ConfigurationManager.class);
        MockedStatic<MainActivity> main = mockStatic(MainActivity.class)) {
      config.when(ConfigurationManager::instance).thenReturn(cm);
      // setting off
      when(cm.showTransfersOnDownloadStart()).thenReturn(false);
      UIUtils.showTransfersOnDownloadStart(app);
      UIUtils.showTransfersOnDownloadStart(null);
      assertNull(Shadows.shadowOf(app).peekNextStartedActivity());
      when(cm.showTransfersOnDownloadStart()).thenReturn(true);
      // no activity: launch intent
      main.when(MainActivity::instance).thenReturn(null);
      UIUtils.showTransfersOnDownloadStart(app);
      Intent intent = Shadows.shadowOf(app).getNextStartedActivity();
      assertEquals(Constants.ACTION_SHOW_TRANSFERS, intent.getAction());
      // live activity
      MainActivity activity = mock(MainActivity.class);
      when(activity.isFinishing()).thenReturn(false);
      main.when(MainActivity::instance).thenReturn(activity);
      UIUtils.showTransfersOnDownloadStart(app);
      verify(activity).switchToTransfers();
      // finishing activity: back to the intent
      when(activity.isFinishing()).thenReturn(true);
      UIUtils.showTransfersOnDownloadStart(app);
      assertNotNull(Shadows.shadowOf(app).getNextStartedActivity());
    }
  }

  @Test
  public void keyboardHelpersWorkWithAndWithoutAFocusedView() {
    Activity activity = Robolectric.buildActivity(Activity.class).create().start().resume().get();
    View focus = new View(activity);
    activity.setContentView(focus);
    focus.setFocusable(true);
    focus.requestFocus();
    UIUtils.forceShowKeyboard(activity);
    UIUtils.forceShowKeyboard(app);
    UIUtils.showKeyboard(activity, focus);
    UIUtils.hideKeyboardFromActivity(activity);
    Context noImm = mock(Context.class);
    UIUtils.forceShowKeyboard(noImm);
    UIUtils.showKeyboard(noImm, focus);
    assertTrue(focus.hasFocus());
  }

  @Test
  public void goToMainActivityClearsTheTaskAndFinishes() {
    Activity activity = Robolectric.buildActivity(Activity.class).create().get();
    UIUtils.goToFrostWireMainActivity(activity);
    Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
    assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
    assertTrue(activity.isFinishing());
  }

  @Test
  public void screenshotsOfEmptyViewsAreNull() {
    assertNull(UIUtils.takeScreenshot(null));
    assertNull(UIUtils.takeScreenshot(new View(app)));
  }

  @Test
  public void screenshotsOfMeasuredViewsAreWrittenAsJpeg() throws Exception {
    View view = new View(app);
    view.layout(0, 0, 10, 10);
    File shot = UIUtils.takeScreenshot(view);
    try {
      assertNotNull(shot);
      assertEquals("fwPlayerScreenshot.tmp.jpg", shot.getName());
      assertTrue(Files.size(shot.toPath()) > 0);
      // a stale file is replaced
      File again = UIUtils.takeScreenshot(view);
      assertEquals(shot, again);
    } finally {
      if (shot != null) {
        shot.delete();
      }
    }
  }

  @Test
  public void audioFilesOpenInTheInternalPlayerAndOthersInTheViewer() throws Exception {
    File song = File.createTempFile("uiutils", ".mp3");
    File doc = File.createTempFile("uiutils", ".txt");
    try (MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class)) {
      assertTrue(UIUtils.openFile(app, song.getAbsolutePath(), "audio/mpeg", false));
      assertEquals(1, background.size());
      background.get(0).run();
      assertEquals(
          "the audio player is started from another background task", 2, background.size());
      assertNull(Shadows.shadowOf(app).peekNextStartedActivity());

      background.clear();
      UIUtils.openFile(app, doc.getAbsolutePath(), "text/plain", false);
      background.get(0).run();
      Intent view = Shadows.shadowOf(app).getNextStartedActivity();
      assertEquals(Intent.ACTION_VIEW, view.getAction());
      assertEquals("text/plain", view.getType());
      assertEquals(Uri.fromFile(doc), view.getData());
      assertTrue((view.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);

      // apk goes through the installer action
      background.clear();
      UIUtils.openFile(
          app, doc.getAbsolutePath(), Constants.MIME_TYPE_ANDROID_PACKAGE_ARCHIVE, false);
      background.get(0).run();
      assertEquals(
          Intent.ACTION_INSTALL_PACKAGE,
          Shadows.shadowOf(app).getNextStartedActivity().getAction());

      // video pauses the music player first
      background.clear();
      music.when(MusicUtils::isPlaying).thenReturn(true);
      UIUtils.openFile(app, doc, false);
      background.get(0).run();
      Shadows.shadowOf(app).getNextStartedActivity();
      background.clear();
      UIUtils.openFile(app, doc.getAbsolutePath(), "video/mp4", false);
      background.get(0).run();
      music.verify(MusicUtils::playPauseOrResume);
      assertNotNull(Shadows.shadowOf(app).getNextStartedActivity());
      background.clear();
      UIUtils.openFile(app, doc);
      assertEquals(1, background.size());
    } finally {
      song.delete();
      doc.delete();
    }
  }

  @Test
  public void aFileThatCannotBeOpenedShowsAnErrorOnTheUiThread() {
    Context context = mock(Context.class);
    org.mockito.Mockito.doThrow(new ActivityNotFoundException("none"))
        .when(context)
        .startActivity(any(Intent.class));
    UIUtils.openFile(context, "/nonexistent/dir/file.xyz", "application/x-unknown", false);
    background.get(0).run();
    assertEquals(1, ui.size());
  }

  @Test
  public void playingAnEphemeralPlaylistUsesTheRunningService() {
    CoreMediaPlayer player = mock(CoreMediaPlayer.class);
    Engine engine = mock(Engine.class);
    when(engine.getMediaPlayer()).thenReturn(player);
    Librarian librarian = mock(Librarian.class);
    EphemeralPlaylist playlist = mock(EphemeralPlaylist.class);
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.filePath = "/x/a.mp3";
    when(librarian.createEphemeralPlaylist(app, fd)).thenReturn(playlist);
    try (MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class);
        MockedStatic<Engine> engineStatic = mockStatic(Engine.class);
        MockedStatic<Librarian> librarianStatic = mockStatic(Librarian.class)) {
      engineStatic.when(Engine::instance).thenReturn(engine);
      librarianStatic.when(Librarian::instance).thenReturn(librarian);
      music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(true);
      UIUtils.playEphemeralPlaylist(app, fd);
      background.get(0).run();
      verify(player).play(playlist);

      // Librarian failing is swallowed
      when(librarian.createEphemeralPlaylist(app, fd)).thenThrow(new IllegalStateException("no"));
      background.get(0).run();
    }
  }

  @Test
  public void playingAnEphemeralPlaylistStartsTheServiceFirstWhenItIsNotRunning() {
    CoreMediaPlayer player = mock(CoreMediaPlayer.class);
    Engine engine = mock(Engine.class);
    when(engine.getMediaPlayer()).thenReturn(player);
    Librarian librarian = mock(Librarian.class);
    EphemeralPlaylist playlist = mock(EphemeralPlaylist.class);
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.filePath = "/x/b.mp3";
    when(librarian.createEphemeralPlaylist(any(), any())).thenReturn(playlist);
    List<Runnable> afterConnect = new ArrayList<>();
    try (MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class);
        MockedStatic<Engine> engineStatic = mockStatic(Engine.class);
        MockedStatic<Librarian> librarianStatic = mockStatic(Librarian.class)) {
      engineStatic.when(Engine::instance).thenReturn(engine);
      librarianStatic.when(Librarian::instance).thenReturn(librarian);
      music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(false);
      music
          .when(() -> MusicUtils.startMusicPlaybackService(any(), any(), any(Runnable.class)))
          .thenAnswer(
              call -> {
                afterConnect.add(call.getArgument(2));
                return null;
              });
      UIUtils.playEphemeralPlaylist(app, fd);
      background.get(0).run();
      assertEquals(1, afterConnect.size());
      verify(player, never()).play(any());
      afterConnect.get(0).run();
      verify(player).play(playlist);
    }
  }
}
