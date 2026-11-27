/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.os.Environment;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.Constants;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Android 11 and newer: File access to the public Download folder is back. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AndroidPathsTest {

  private final Application app = ApplicationProvider.getApplicationContext();

  @Test
  public void dataIsTheFrostWireFolderInTheDownloadsDirectory() {
    AndroidPaths paths = new AndroidPaths(app);
    File expected =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "FrostWire");
    assertEquals(expected, paths.data());
    assertEquals(expected, AndroidPaths.android11AndUpStorage());
    assertEquals(new File(expected, AndroidPaths.TORRENTS_PATH), paths.torrents());
  }

  @Test
  public void tempAndLibtorrentLiveUnderTheInternalFilesDirectory() {
    AndroidPaths paths = new AndroidPaths(app);
    assertEquals(new File(app.getFilesDir(), "temp"), paths.temp());
    assertEquals(new File(app.getFilesDir(), "libtorrent"), paths.libtorrent());
  }

  @Test
  public void fileTypeComesFromTheExtension() {
    assertEquals(Constants.FILE_TYPE_AUDIO, AndroidPaths.getFileType("/x/song.mp3", false));
    assertEquals(Constants.FILE_TYPE_VIDEOS, AndroidPaths.getFileType("/x/clip.mp4", false));
    assertEquals(Constants.FILE_TYPE_PICTURES, AndroidPaths.getFileType("/x/pic.jpg", false));
    assertEquals(Constants.FILE_TYPE_UNKNOWN, AndroidPaths.getFileType("/x/blob.zzzzqq", false));
  }

  @Test
  public void torrentsCanBeReportedAsDocuments() {
    assertEquals(Constants.FILE_TYPE_TORRENTS, AndroidPaths.getFileType("/x/a.torrent", false));
    assertEquals(Constants.FILE_TYPE_DOCUMENTS, AndroidPaths.getFileType("/x/a.torrent", true));
    assertEquals(Constants.FILE_TYPE_AUDIO, AndroidPaths.getFileType("/x/a.mp3", true));
  }

  @Test
  public void internalFileInAndroid10MapsToTheSameRelativePlaceInDownloads() {
    File dataDir = new File("/storage/emulated/0/Android/data/com.frostwire.android/files");
    SystemPaths systemPaths = mock(SystemPaths.class);
    when(systemPaths.data()).thenReturn(dataDir);
    Platform platform = mock(Platform.class);
    when(platform.systemPaths()).thenReturn(systemPaths);
    File downloads = AndroidPaths.android11AndUpStorage();
    try (MockedStatic<Platforms> platforms = Mockito.mockStatic(Platforms.class)) {
      platforms.when(Platforms::get).thenReturn(platform);
      File topLevel = new File(dataDir, "movie.mp4");
      assertEquals(
          new File(downloads, "movie.mp4"),
          AndroidPaths.getDestinationFileFromInternalFileInAndroid10(topLevel));
      File inFolder = new File(dataDir, "Torrent Name/movie.mp4");
      File mapped = AndroidPaths.getDestinationFileFromInternalFileInAndroid10(inFolder);
      assertNotNull(mapped);
      assertEquals("movie.mp4", mapped.getName());
      assertTrue(mapped.getPath().contains("Torrent Name"));
      assertTrue(mapped.getPath().startsWith(downloads.getPath()));
    }
  }
}
