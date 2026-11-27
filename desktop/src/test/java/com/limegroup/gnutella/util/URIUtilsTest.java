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

package com.limegroup.gnutella.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

/** Turning dropped strings into URIs, encoding the ones that were not encoded. */
class URIUtilsTest {

  @Test
  void anAlreadyEncodedUriIsLeftAlone() throws Exception {
    URI uri = URIUtils.toURI("file:///tmp/my%20song.mp3?a=b+c&d=e");
    assertEquals("file:///tmp/my%20song.mp3?a=b+c&d=e", uri.toString());
    assertEquals("/tmp/my song.mp3", uri.getPath());
  }

  @Test
  void aSpaceInAPathBecomesPercentTwentyNotAPlus() throws Exception {
    URI uri = URIUtils.toURI("file:///tmp/my song.mp3");
    assertEquals("file:///tmp/my%20song.mp3", uri.toString());
    assertEquals("/tmp/my song.mp3", uri.getPath());
  }

  @Test
  void nonLatinOneCharactersSurviveAsUtf8() throws Exception {
    URI uri = URIUtils.toURI("file:///tmp/日本 é.mp3");
    assertEquals("/tmp/日本 é.mp3", uri.getPath());
    assertEquals("/tmp/%E6%97%A5%E6%9C%AC%20%C3%A9.mp3", uri.getRawPath());
  }

  @Test
  void anExistingEscapeIsNotEncodedTwice() throws Exception {
    URI uri = URIUtils.toURI("file:///tmp/a%20b c.mp3");
    assertEquals("/tmp/a b c.mp3", uri.getPath());
  }

  @Test
  void somethingStructurallyWrongStillThrows() {
    assertThrows(URISyntaxException.class, () -> URIUtils.toURI(":no scheme y"));
  }
}
