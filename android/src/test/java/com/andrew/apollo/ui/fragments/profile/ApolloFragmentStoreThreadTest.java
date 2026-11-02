/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments.profile;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * "Remove from Favorites" and "Remove from Recent" deleted from their SQLite stores on the main
 * thread (GitHub #1455). Source check: the handlers need a live profile screen.
 */
public class ApolloFragmentStoreThreadTest {

  private static String body(String source, String method) {
    int start = source.indexOf(method);
    int end = source.indexOf("private", start + method.length());
    return source.substring(start, end);
  }

  @Test
  public void theStoresAreWrittenOffTheMainThread() throws Exception {
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve(
                            "apollo/src/com/andrew/apollo/ui/fragments/profile/ApolloFragment.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    for (String method :
        new String[] {"privatebooleanonRemoveFromRecent()", "privatevoidonRemoveFromFavorites()"}) {
      String body = body(source, method);
      int post = body.indexOf("postToHandler(SystemUtils.HandlerThreadName.MISC");
      int write = body.indexOf(".removeItem(");
      assertTrue(method + " posts to MISC before writing", post >= 0 && post < write);
    }
  }
}
