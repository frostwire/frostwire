/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class FWFileDescriptorTest {

  private static FWFileDescriptor audio(int id, String path, String mime) {
    return new FWFileDescriptor(
        id,
        "artist",
        "title",
        "album",
        "2020",
        path,
        Constants.FILE_TYPE_AUDIO,
        mime,
        1234L,
        10L,
        20L,
        true);
  }

  @Test
  public void constructorKeepsAllFieldsAndAGivenMime() {
    FWFileDescriptor fd = audio(7, "/music/a.mp3", "audio/mpeg");
    assertEquals(7, fd.id);
    assertEquals("artist", fd.artist);
    assertEquals("title", fd.title);
    assertEquals("album", fd.album);
    assertEquals("2020", fd.year);
    assertEquals("/music/a.mp3", fd.filePath);
    assertEquals(Constants.FILE_TYPE_AUDIO, fd.fileType);
    assertEquals("audio/mpeg", fd.mime);
    assertEquals(1234L, fd.fileSize);
    assertEquals(10L, fd.dateAdded);
    assertEquals(20L, fd.dateModified);
    assertTrue(fd.deletable);
  }

  @Test
  public void missingMimeIsDetectedFromTheExtension() {
    assertEquals("audio/mpeg", audio(1, "/music/a.mp3", null).mime);
  }

  @Test
  public void torrentFilesGetTheTorrentMimeEvenIfAnotherWasGiven() {
    String torrentMime = audio(1, "/x/a.torrent", null).mime;
    assertEquals(torrentMime, audio(1, "/x/b.torrent", "text/plain").mime);
    assertNotEquals("text/plain", audio(1, "/x/b.torrent", "text/plain").mime);
  }

  @Test
  public void bundleRoundTripPreservesTheDescriptor() {
    FWFileDescriptor fd = audio(3, "/music/a.mp3", "audio/mpeg");
    Bundle bundle = fd.toBundle();
    FWFileDescriptor copy = new FWFileDescriptor(bundle);
    assertEquals(fd.id, copy.id);
    assertEquals(fd.artist, copy.artist);
    assertEquals(fd.title, copy.title);
    assertEquals(fd.album, copy.album);
    assertEquals(fd.year, copy.year);
    assertEquals(fd.filePath, copy.filePath);
    assertEquals(fd.fileType, copy.fileType);
    assertEquals(fd.mime, copy.mime);
    assertEquals(fd.fileSize, copy.fileSize);
    assertEquals(fd.dateAdded, copy.dateAdded);
    assertEquals(fd.dateModified, copy.dateModified);
    assertEquals(fd.deletable, copy.deletable);
  }

  @Test
  public void fromNullBundleChangesNothing() {
    FWFileDescriptor fd = audio(3, "/music/a.mp3", "audio/mpeg");
    fd.fromBundle(null);
    assertEquals(3, fd.id);
    assertEquals("/music/a.mp3", fd.filePath);
  }

  @Test
  public void equalsByIdAndTypeOrByPath() {
    FWFileDescriptor a = audio(1, "/a.mp3", "audio/mpeg");
    assertEquals(a, audio(1, "/other.mp3", "audio/mpeg"));
    assertEquals(a, audio(2, "/a.mp3", "audio/mpeg"));
    assertNotEquals(a, audio(2, "/b.mp3", "audio/mpeg"));
    assertFalse(a.equals("not a descriptor"));
    assertFalse(a.equals(null));
  }

  @Test
  public void equalsWithoutPathsOnlyComparesIdAndType() {
    FWFileDescriptor a = new FWFileDescriptor();
    FWFileDescriptor b = new FWFileDescriptor();
    a.id = 5;
    b.id = 6;
    assertNotEquals(a, b);
    b.id = 5;
    assertEquals(a, b);
  }

  @Test
  public void hashCodeCombinesIdAndType() {
    FWFileDescriptor fd = audio(4, "/a.mp3", "audio/mpeg");
    assertEquals(4 * 1000 + Constants.FILE_TYPE_AUDIO, fd.hashCode());
  }

  @Test
  public void cloneIsAnEqualButDistinctCopy() {
    FWFileDescriptor fd = audio(4, "/a.mp3", "audio/mpeg");
    FWFileDescriptor clone = fd.clone();
    assertNotSame(fd, clone);
    assertEquals(fd, clone);
    assertEquals(fd.title, clone.title);
    assertEquals(fd.fileSize, clone.fileSize);
    assertEquals(fd.deletable, clone.deletable);
  }

  @Test
  public void toStringNamesTheMainFields() {
    String s = audio(9, "/a.mp3", "audio/mpeg").toString();
    assertTrue(s.contains("id:9"));
    assertTrue(s.contains("t:title"));
    assertTrue(s.contains("p:/a.mp3"));
    assertNull(new FWFileDescriptor().title);
  }
}
