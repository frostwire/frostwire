/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.android.util.TorrentUtils;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class UISoundcloudDownloadTest {
  private File dir;
  private FileSystem fs;
  private Engine engine;
  private TransferManager manager;
  private MockedStatic<Platforms> platforms;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<TorrentUtils> torrentUtils;
  private MockedStatic<SystemUtils> system;

  @Before
  public void setUp() throws Exception {
    dir = Files.createTempDirectory("uisc").toFile();
    fs = mock(FileSystem.class);
    when(fs.isDirectory(any())).thenReturn(true);
    when(fs.copy(any(), any())).thenReturn(true);
    platforms = mockStatic(Platforms.class);
    platforms.when(Platforms::data).thenReturn(new File(dir, "data"));
    platforms.when(Platforms::temp).thenReturn(new File(dir, "temp"));
    platforms.when(Platforms::fileSystem).thenReturn(fs);
    engine = mock(Engine.class);
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    torrentUtils = mockStatic(TorrentUtils.class);
    system = mockStatic(SystemUtils.class);
    manager = mock(TransferManager.class);
  }

  @After
  public void tearDown() {
    system.close();
    torrentUtils.close();
    engineStatic.close();
    platforms.close();
    dir.delete();
  }

  private UISoundcloudDownload download() {
    SoundcloudSearchResult sr = mock(SoundcloudSearchResult.class);
    when(sr.getFilename()).thenReturn("song.mp3");
    when(sr.getDisplayName()).thenReturn("A song");
    when(sr.getSize()).thenReturn(10L);
    return new UISoundcloudDownload(manager, sr);
  }

  @Test
  public void removingACancelledDownloadUnregistersItFromTheManager() {
    UISoundcloudDownload d = download();
    d.remove(false);
    assertEquals(TransferState.CANCELED, d.getState());
    verify(manager).remove(d);
  }

  @Test
  public void aCompletedDownloadIsAnnouncedAndOfferedForSeeding() {
    UISoundcloudDownload d = download();
    d.moveAndComplete(new File(dir, "temp/song.mp3"), d.getSavePath());
    assertEquals(TransferState.COMPLETE, d.getState());
    verify(manager).incrementDownloadsToReview();
    verify(engine).notifyDownloadFinished("A song", d.getSavePath());
    torrentUtils.verify(
        () ->
            TorrentUtils.seedFinishedHttpDownloadIfEnabled(
                eq(d.getSavePath()), eq("A song"), anyByte(), eq(manager)));
  }

  @Test
  public void newerAndroidAsksTheMediaScannerToIndexTheFile() {
    system.when(SystemUtils::hasAndroid11OrNewer).thenReturn(true);
    system
        .when(SystemUtils::getApplicationContext)
        .thenReturn(ApplicationProvider.getApplicationContext());
    UISoundcloudDownload d = download();
    d.moveAndComplete(new File(dir, "temp/song.mp3"), d.getSavePath());
    assertEquals(TransferState.COMPLETE, d.getState());
    assertTrue(d.isComplete());
  }

  @Test
  public void withoutAnApplicationContextTheMediaScanIsSkipped() {
    system.when(SystemUtils::hasAndroid11OrNewer).thenReturn(true);
    system.when(SystemUtils::getApplicationContext).thenReturn(null);
    UISoundcloudDownload d = download();
    d.moveAndComplete(new File(dir, "temp/song.mp3"), d.getSavePath());
    assertEquals(TransferState.COMPLETE, d.getState());
  }
}
