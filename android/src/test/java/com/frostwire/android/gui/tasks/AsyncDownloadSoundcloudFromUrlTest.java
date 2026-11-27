/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.tasks;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.gui.SoftwareUpdater;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.ConfirmSoundcloudDownloadDialog;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.soundcloud.SoundcloudUtils;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AsyncDownloadSoundcloudFromUrlTest {

  private MockedStatic<SystemUtils> system;
  private MockedStatic<SoftwareUpdater> updater;
  private MockedStatic<SoundcloudUtils> soundcloud;
  private MockedStatic<HttpClientFactory> http;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<ConfirmSoundcloudDownloadDialog> dialogs;
  private HttpClient client;
  private Context context;

  @Before
  public void setUp() {
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(0)).run();
              return null;
            });
    updater = mockStatic(SoftwareUpdater.class);
    updater.when(SoftwareUpdater::getSoundCloudClientId).thenReturn("client-id");
    updater.when(SoftwareUpdater::getSoundCloudAppVersion).thenReturn("app-version");
    soundcloud = mockStatic(SoundcloudUtils.class);
    client = mock(HttpClient.class);
    http = mockStatic(HttpClientFactory.class);
    http.when(() -> HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD))
        .thenReturn(client);
    ui = mockStatic(UIUtils.class);
    dialogs = mockStatic(ConfirmSoundcloudDownloadDialog.class);
    context = ApplicationProvider.getApplicationContext();
  }

  @After
  public void tearDown() {
    dialogs.close();
    ui.close();
    http.close();
    soundcloud.close();
    updater.close();
    system.close();
  }

  private static SoundcloudSearchResult result(long size) {
    SoundcloudSearchResult sr = mock(SoundcloudSearchResult.class);
    when(sr.getSize()).thenReturn(size);
    return sr;
  }

  @Test
  public void resolvesTheUrlWithoutThePlaylistSuffixAndParsesTheJson() throws Exception {
    soundcloud
        .when(
            () ->
                SoundcloudUtils.resolveUrl(
                    "https://soundcloud.com/a/b", "client-id", "app-version"))
        .thenReturn("https://api/resolve");
    when(client.get("https://api/resolve", 10000)).thenReturn("{json}");
    LinkedList<SoundcloudSearchResult> results =
        new LinkedList<>(Arrays.asList(result(1000), result(2000)));
    soundcloud
        .when(() -> SoundcloudUtils.fromJson("{json}", true, "client-id", "app-version"))
        .thenReturn(results);
    MainActivity activity = mock(MainActivity.class);
    FragmentManager fragments = mock(FragmentManager.class);
    when(activity.getSupportFragmentManager()).thenReturn(fragments);
    when(activity.getString(anyInt())).thenAnswer(i -> context.getString((int) i.getArgument(0)));
    when(activity.getString(anyInt(), any(Object[].class))).thenReturn("confirm text");
    ConfirmSoundcloudDownloadDialog dialog = mock(ConfirmSoundcloudDownloadDialog.class);
    dialogs
        .when(
            () ->
                ConfirmSoundcloudDownloadDialog.newInstance(
                    eq(activity), anyString(), anyString(), any()))
        .thenReturn(dialog);
    ui.when(() -> UIUtils.getBytesInHuman(3000.0)).thenReturn("3 KB");

    new AsyncDownloadSoundcloudFromUrl(activity, "https://soundcloud.com/a/b?in=someset");

    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    dialogs.verify(
        () ->
            ConfirmSoundcloudDownloadDialog.newInstance(
                eq(activity),
                eq(context.getString(R.string.confirm_download)),
                text.capture(),
                eq(results)));
    assertEquals("confirm text", text.getValue());
    verify(dialog).show(fragments);
  }

  @Test
  public void aPlaylistIsDescribedAsAPlaylistAndATrackAsATrack() {
    soundcloud
        .when(() -> SoundcloudUtils.resolveUrl(anyString(), anyString(), anyString()))
        .thenReturn("https://api/resolve");
    try {
      when(client.get(anyString(), anyInt())).thenReturn("{json}");
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
    LinkedList<SoundcloudSearchResult> one = new LinkedList<>(Arrays.asList(result(10)));
    LinkedList<SoundcloudSearchResult> two =
        new LinkedList<>(Arrays.asList(result(10), result(20)));
    soundcloud
        .when(() -> SoundcloudUtils.fromJson(anyString(), anyBoolean(), anyString(), anyString()))
        .thenReturn(one, two);
    MainActivity activity = mock(MainActivity.class);
    when(activity.getSupportFragmentManager()).thenReturn(mock(FragmentManager.class));
    List<Object[]> formatArgs = new ArrayList<>();
    when(activity.getString(anyInt())).thenAnswer(i -> "res" + i.getArgument(0));
    when(activity.getString(anyInt(), any(Object[].class)))
        .thenAnswer(
            i -> {
              formatArgs.add(i.getRawArguments());
              return "text";
            });
    ConfirmSoundcloudDownloadDialog dialog = mock(ConfirmSoundcloudDownloadDialog.class);
    dialogs
        .when(() -> ConfirmSoundcloudDownloadDialog.newInstance(any(), any(), any(), any()))
        .thenReturn(dialog);

    new AsyncDownloadSoundcloudFromUrl(activity, "https://soundcloud.com/one");
    new AsyncDownloadSoundcloudFromUrl(activity, "https://soundcloud.com/two");

    assertEquals(2, formatArgs.size());
    verify(activity).getString(R.string.track);
    verify(activity).getString(R.string.playlist);
  }

  @Test
  public void aMissingResultTellsTheUserNothingWasFound() throws Exception {
    soundcloud
        .when(() -> SoundcloudUtils.resolveUrl(anyString(), anyString(), anyString()))
        .thenReturn("https://api/resolve");
    when(client.get(anyString(), anyInt())).thenThrow(new java.io.IOException("offline"));

    new AsyncDownloadSoundcloudFromUrl(context, "https://soundcloud.com/gone");

    ui.verify(
        () ->
            UIUtils.showLongMessage(
                context,
                R.string.sorry_could_not_find_valid_download_location_at,
                "https://soundcloud.com/gone"));
    dialogs.verifyNoInteractions();
  }

  @Test
  public void anEmptyParseTellsTheUserNothingWasFound() throws Exception {
    soundcloud
        .when(() -> SoundcloudUtils.resolveUrl(anyString(), anyString(), anyString()))
        .thenReturn("https://api/resolve");
    when(client.get(anyString(), anyInt())).thenReturn("{}");
    soundcloud
        .when(() -> SoundcloudUtils.fromJson(anyString(), anyBoolean(), anyString(), anyString()))
        .thenReturn(new LinkedList<>());

    new AsyncDownloadSoundcloudFromUrl(context, "https://soundcloud.com/empty");

    ui.verify(
        () ->
            UIUtils.showLongMessage(
                context,
                R.string.sorry_could_not_find_valid_download_location_at,
                "https://soundcloud.com/empty"));
  }

  @Test
  public void aGoneContextShowsNothing() throws Exception {
    soundcloud
        .when(() -> SoundcloudUtils.resolveUrl(anyString(), anyString(), anyString()))
        .thenReturn("https://api/resolve");
    when(client.get(anyString(), anyInt())).thenReturn("{}");

    new AsyncDownloadSoundcloudFromUrl(null, "https://soundcloud.com/x");

    ui.verifyNoInteractions();
  }
}
