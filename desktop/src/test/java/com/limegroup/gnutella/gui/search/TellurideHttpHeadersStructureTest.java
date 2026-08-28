/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TellurideHttpHeadersStructureTest {

  @Test
  void ytDlpHeadersReachHttpClientSave() throws Exception {
    String uiResult =
        read("src/main/java/com/limegroup/gnutella/gui/search/TellurideUISearchResult.java");
    String mediator = read("src/main/java/com/frostwire/gui/bittorrent/BTDownloadMediator.java");
    String download = read("src/main/java/com/frostwire/gui/bittorrent/HttpDownload.java");
    String searchMediator =
        read("src/main/java/com/limegroup/gnutella/gui/search/SearchMediator.java");

    assertTrue(uiResult.contains("sr.getHttpHeaders()"));
    assertTrue(mediator.contains("httpHeaders"));
    assertTrue(download.contains("httpClient.save(url, incompleteFile"));
    assertTrue(download.contains("httpHeaders"));
    assertTrue(download.contains("MAX_TRANSIENT_RETRIES = 3"));
    assertTrue(download.contains("resume || attempts > 0"));
    assertTrue(download.contains("httpClient.isCanceled()"));

    String factory = read("../common/src/main/java/com/frostwire/util/HttpClientFactory.java");
    String httpBase = read("src/main/java/com/frostwire/gui/bittorrent/HttpBTDownload.java");
    assertTrue(factory.contains("newInstance(HttpContext context)"));
    assertTrue(httpBase.contains("HttpClientFactory.newInstance"));
    assertTrue(searchMediator.contains("extension.equals(\"m4a\")"));
    assertTrue(searchMediator.contains("(Faster Download)"));
  }

  private static String read(String relativePath) throws IOException {
    return Files.readString(
        Path.of(System.getProperty("user.dir")).resolve(relativePath), StandardCharsets.UTF_8);
  }
}
