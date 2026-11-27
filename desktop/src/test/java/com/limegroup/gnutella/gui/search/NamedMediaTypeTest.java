/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.MediaType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The media types the search tabs and filters are named by. */
class NamedMediaTypeTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  @Test
  void aTypeIsFoundByExtensionMediaTypeOrMimeDescriptionAndIsCached() {
    NamedMediaType byExtension = NamedMediaType.getFromExtension("mp3");
    assertSame(byExtension, NamedMediaType.getFromExtension("mp3"));
    assertSame(byExtension, NamedMediaType.getFromMediaType(MediaType.getAudioMediaType()));
    assertSame(
        byExtension,
        NamedMediaType.getFromDescription(MediaType.getAudioMediaType().getMimeType()));
    assertEquals(MediaType.getAudioMediaType(), byExtension.getMediaType());
  }

  @Test
  void differentExtensionsOfOneKindShareAType() {
    assertSame(NamedMediaType.getFromExtension("mp3"), NamedMediaType.getFromExtension("ogg"));
  }

  @Test
  void anUnknownExtensionHasNoType() {
    assertNull(NamedMediaType.getFromExtension("definitely-not-an-extension"));
  }

  @Test
  void aTypeHasAReadableNameAndPrintsIt() {
    NamedMediaType audio = NamedMediaType.getFromMediaType(MediaType.getAudioMediaType());
    assertFalse(audio.getName().isEmpty());
    assertEquals(audio.getName(), audio.toString());
  }

  @Test
  void typesOrderByNameAndAreEqualByName() {
    NamedMediaType audio = NamedMediaType.getFromMediaType(MediaType.getAudioMediaType());
    NamedMediaType video = NamedMediaType.getFromMediaType(MediaType.getVideoMediaType());
    assertEquals(audio.getName().compareTo(video.getName()) < 0, audio.compareTo(video) < 0);
    assertEquals(0, audio.compareTo(audio));
    assertEquals(audio, NamedMediaType.getFromExtension("mp3"));
    assertEquals(audio.hashCode(), NamedMediaType.getFromExtension("mp3").hashCode());
    assertNotEquals(audio, video);
    assertFalse(audio.equals(null));
    assertFalse(audio.equals("Audio"));
  }

  @Test
  void aTypeHasAnIconOnceItIsAsked() {
    NamedMediaType torrent = NamedMediaType.getFromMediaType(MediaType.getTorrentMediaType());
    assertNotNull(torrent.getIcon());
    assertTrue(torrent.isIconLoaded());
    assertSame(torrent.getIcon(), torrent.getIcon());
  }

  @Test
  void theLibraryTreeListsSixMediaTypes() {
    assertEquals(6, NamedMediaType.LIBRARY_TREE_MEDIA_TYPES.get().size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> NamedMediaType.LIBRARY_TREE_MEDIA_TYPES.get().add(MediaType.getAnyTypeMediaType()));
  }
}
