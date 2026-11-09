/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.util.UrlUtils;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A magnet forwarded by a second FrostWire instance reaches the running one exactly as it was
 * given: the forwarded url is encoded once and must be decoded once (frostwire/frostwire#1359).
 */
class ExternalControlForwardedUrlTest {

  @Test
  void forwardedMagnetKeepsItsOwnEscapes() throws Exception {
    String magnet =
        "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
            + "&dn=50%25+off+Album&tr=udp%3A%2F%2Ftracker.example%3A1337";
    List<String> handled = new ArrayList<>();
    ActivityCallback callback =
        (ActivityCallback)
            Proxy.newProxyInstance(
                ActivityCallback.class.getClassLoader(),
                new Class<?>[] {ActivityCallback.class},
                (proxy, method, args) -> {
                  switch (method.getName()) {
                    case "handleTorrentMagnet" -> handled.add((String) args[0]);
                    case "isRemoteDownloadsAllowed" -> {
                      return true;
                    }
                    default -> {}
                  }
                  return null;
                });
    // The request path testForFrostWire sends, without the HTTP method and version.
    new ExternalControl(callback)
        .process("/download?url=" + UrlUtils.encode(magnet), new ByteArrayOutputStream());
    assertEquals(List.of(magnet), handled);
  }

  /**
   * A magnet FrostWire was started with (Windows and Linux pass it on the command line) is
   * downloaded whatever the order of its parameters. One whose {@code dn} came before {@code xt}
   * failed the old {@code startsWith("magnet:?xt=urn:btih")} test and was dropped
   * (frostwire/frostwire#1513).
   */
  @Test
  void startupMagnetWithItsNameFirstIsDownloaded() {
    String magnet =
        "magnet:?dn=Ubuntu&tr=udp%3A%2F%2Ftracker.example%3A1337"
            + "&xt=urn:btih:0123456789abcdef0123456789abcdef01234567";
    List<String> handled = new ArrayList<>();
    ExternalControl control = new ExternalControl(recordingCallback(handled));
    control.enqueueControlRequest(magnet);
    control.runQueuedControlRequest();
    assertEquals(List.of(magnet), handled);
  }

  @Test
  void startupMagnetWithAnUpperCaseSchemeIsDownloadedWithALowerCaseOne() {
    String hash = "0123456789abcdef0123456789abcdef01234567";
    List<String> handled = new ArrayList<>();
    ExternalControl control = new ExternalControl(recordingCallback(handled));
    control.enqueueControlRequest("Magnet:?xt=urn:btih:" + hash);
    control.runQueuedControlRequest();
    assertEquals(List.of("magnet:?xt=urn:btih:" + hash), handled);
  }

  private static ActivityCallback recordingCallback(List<String> handled) {
    return (ActivityCallback)
        Proxy.newProxyInstance(
            ActivityCallback.class.getClassLoader(),
            new Class<?>[] {ActivityCallback.class},
            (proxy, method, args) -> {
              if (method.getName().equals("handleTorrentMagnet")) {
                handled.add((String) args[0]);
              }
              return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            });
  }
}
