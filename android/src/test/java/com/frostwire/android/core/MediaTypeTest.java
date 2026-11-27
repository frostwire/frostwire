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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.frostwire.android.R;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;

public class MediaTypeTest {

  @Test
  public void extensionsMapToTheirMediaType() {
    assertSame(MediaType.getAudioMediaType(), MediaType.getMediaTypeForExtension("mp3"));
    assertSame(MediaType.getVideoMediaType(), MediaType.getMediaTypeForExtension("mp4"));
    assertSame(MediaType.getImageMediaType(), MediaType.getMediaTypeForExtension("jpg"));
    assertSame(MediaType.getDocumentMediaType(), MediaType.getMediaTypeForExtension("pdf"));
    assertSame(MediaType.getTorrentMediaType(), MediaType.getMediaTypeForExtension("torrent"));
    assertSame(MediaType.getApplicationsMediaType(), MediaType.getMediaTypeForExtension("apk"));
  }

  @Test
  public void extensionLookupIgnoresCase() {
    assertSame(MediaType.getAudioMediaType(), MediaType.getMediaTypeForExtension("MP3"));
    assertSame(MediaType.getImageMediaType(), MediaType.getMediaTypeForExtension("JpEg"));
  }

  @Test
  public void unknownOrMissingExtensionsAreUnknown() {
    assertSame(MediaType.TYPE_UNKNOWN, MediaType.getMediaTypeForExtension("zzzzqq"));
    assertSame(MediaType.TYPE_UNKNOWN, MediaType.getMediaTypeForExtension(null));
    assertSame(MediaType.TYPE_UNKNOWN, MediaType.getMediaTypeForExtension(""));
  }

  @Test
  public void sharedExtensionsGoToTheLastMatchingType() {
    // "rm" is listed for audio and video; "eps" for documents and pictures.
    assertSame(MediaType.getVideoMediaType(), MediaType.getMediaTypeForExtension("rm"));
    assertSame(MediaType.getImageMediaType(), MediaType.getMediaTypeForExtension("eps"));
  }

  @Test
  public void schemasAreLookedUpByName() {
    assertSame(MediaType.getAudioMediaType(), MediaType.getMediaTypeForSchema("audio"));
    assertSame(MediaType.getVideoMediaType(), MediaType.getMediaTypeForSchema("video"));
    assertSame(MediaType.getImageMediaType(), MediaType.getMediaTypeForSchema("image"));
    assertSame(MediaType.getDocumentMediaType(), MediaType.getMediaTypeForSchema("document"));
    assertSame(MediaType.getTorrentMediaType(), MediaType.getMediaTypeForSchema("torrent"));
    assertSame(
        MediaType.getApplicationsMediaType(), MediaType.getMediaTypeForSchema("application"));
    assertNull(MediaType.getMediaTypeForSchema("nonsense"));
    assertTrue(MediaType.isDefaultType("audio"));
    assertFalse(MediaType.isDefaultType("custom"));
  }

  @Test
  public void defaultTypesAreTheSixKnownOnes() {
    assertEquals(6, MediaType.getDefaultMediaTypes().length);
    for (MediaType type : MediaType.getDefaultMediaTypes()) {
      assertTrue(type.isDefault());
      assertFalse(type.getExtensions().isEmpty());
      assertEquals(type.toString(), type.getMimeType());
    }
  }

  @Test
  public void matchesLooksAtTheFileNameSuffix() {
    MediaType audio = MediaType.getAudioMediaType();
    assertTrue(audio.matches("song.mp3"));
    assertTrue(audio.matches("SONG.MP3"));
    assertTrue(audio.matches("dir.with.dots/song.flac"));
    assertFalse(audio.matches("song.mp4"));
    assertFalse(audio.matches("no_extension"));
  }

  @Test
  public void customTypesHaveAnEmptyOrGivenExtensionSet() {
    MediaType custom = new MediaType(99, MediaType.SCHEMA_CUSTOM, -1, null);
    assertTrue(custom.getExtensions().isEmpty());
    assertFalse(custom.matches("a.txt"));
    MediaType withExts = new MediaType(98, MediaType.SCHEMA_CUSTOM, 5, new String[] {"AbC", "xyz"});
    assertTrue(withExts.getExtensions().contains("abc"));
    assertTrue(withExts.matches("file.XYZ"));
    assertEquals(5, withExts.getDescriptionKeyResourceId());
    assertEquals(98, withExts.getId());
  }

  @Test
  public void aMediaTypeNeedsASchema() {
    assertThrows(NullPointerException.class, () -> new MediaType(1, null, 0, null));
  }

  @Test
  public void equalityIsBySchemaAndExtensions() {
    MediaType a = new MediaType(1, "x", 0, new String[] {"a", "b"});
    MediaType same = new MediaType(2, "x", 0, new String[] {"B", "A"});
    MediaType otherSchema = new MediaType(1, "y", 0, new String[] {"a", "b"});
    MediaType otherExts = new MediaType(1, "x", 0, new String[] {"a"});
    assertEquals(a, same);
    assertNotEquals(a, otherSchema);
    assertNotEquals(a, otherExts);
    assertNotEquals(a, "x");
    assertEquals(
        MediaType.getAudioMediaType().hashCode(), MediaType.getAudioMediaType().hashCode());
  }

  @Test
  public void resourceIdsAreTheDescriptionsOfTheDefaultTypes() {
    assertEquals(
        R.string.media_type_audio, MediaType.getAudioMediaType().getDescriptionKeyResourceId());
    assertEquals(
        R.string.media_type_video, MediaType.getVideoMediaType().getDescriptionKeyResourceId());
    assertEquals(
        R.string.media_type_images, MediaType.getImageMediaType().getDescriptionKeyResourceId());
    assertEquals(
        R.string.media_type_documents,
        MediaType.getDocumentMediaType().getDescriptionKeyResourceId());
    assertEquals(
        R.string.media_type_torrents,
        MediaType.getTorrentMediaType().getDescriptionKeyResourceId());
    assertEquals(
        R.string.media_type_applications,
        MediaType.getApplicationsMediaType().getDescriptionKeyResourceId());
  }

  @Test
  public void fileTypeIconsFollowTheMediaType() {
    assertEquals(R.drawable.question_mark, MediaType.getFileTypeIconId("zzzzqq"));
    assertEquals(R.drawable.question_mark, MediaType.getFileTypeIconId(null));
    assertEquals(
        R.drawable.my_files_application_icon_selector_menu, MediaType.getFileTypeIconId("apk"));
    assertEquals(R.drawable.my_files_audio_icon_selector_menu, MediaType.getFileTypeIconId("mp3"));
    assertEquals(
        R.drawable.my_files_document_icon_selector_menu, MediaType.getFileTypeIconId("pdf"));
    assertEquals(
        R.drawable.my_files_picture_icon_selector_menu, MediaType.getFileTypeIconId("png"));
    assertEquals(R.drawable.my_files_video_icon_selector_menu, MediaType.getFileTypeIconId("mkv"));
    assertEquals(
        R.drawable.my_files_torrent_icon_selector_menu, MediaType.getFileTypeIconId("torrent"));
  }

  @Test
  public void aSerializedDefaultTypeComesBackAsTheSingleton() throws Exception {
    MediaType back = roundTrip(MediaType.getVideoMediaType());
    assertSame(MediaType.getVideoMediaType(), back);
  }

  @Test
  public void aSerializedCustomTypeComesBackAsACopy() throws Exception {
    MediaType custom = new MediaType(77, "custom-schema", 0, new String[] {"qq"});
    MediaType back = roundTrip(custom);
    assertNotNull(back);
    assertEquals(custom, back);
  }

  @Test
  public void caseInsensitiveComparatorOrdersIgnoringCase() {
    MediaType.CaseInsensitiveStringComparator c = new MediaType.CaseInsensitiveStringComparator();
    assertEquals(0, c.compare("MP3", "mp3"));
    assertTrue(c.compare("a", "B") < 0);
    assertTrue(c.compare("b", "A") > 0);
  }

  private static MediaType roundTrip(MediaType type) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(type);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (MediaType) in.readObject();
    }
  }
}
