/*
 *     Copyright (c) 2026, FrostWire(R). All rights reserved.
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class UrlUtilsLogAddressTest {
  @Test
  void excludesAuthorizationQueryCredentialsAndFragment() {
    assertEquals("https://api.example.org:8443/media/track%20name",
        UrlUtils.logAddress("https://user:password@api.example.org:8443/media/track%20name?track_authorization=secret#secret"));
    assertEquals("https://api.example.org/media/track", UrlUtils.logAddress("https://api.example.org/media/track"));
  }

  @Test
  void malformedOrRelativeUrlsNeverEchoInput() {
    assertEquals("<invalid-url>", UrlUtils.logAddress(null));
    assertEquals("<invalid-url>", UrlUtils.logAddress("broken secret URL"));
    assertEquals("<invalid-url>", UrlUtils.logAddress("/relative?secret=value"));
  }
}
