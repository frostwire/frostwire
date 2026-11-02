/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

/**
 * Theme images that exist only as GIFs (animated) load through Toolkit, and {@code new
 * ImageIcon(Image)} waits in {@code MediaTracker.waitForID} until the image fetcher thread has
 * decoded them. Built with the Transfers tab on the EDT, that wait froze the window for over 5 s
 * (Icebase #c6d108968951, frostwire/frostwire#1470). The startup thread preloads them into the
 * theme-image cache, as it does the header images.
 */
class ResourceManagerAnimatedImagePreloadTest {

  private static final Pattern THEME_IMAGE_LITERAL =
      Pattern.compile("getThemeImage\\(\\s*\"([^\"]+)\"\\s*\\)");

  /** Names desktop code passes to getThemeImage as string literals. */
  private static Set<String> themeImageLiterals() throws IOException {
    Set<String> names = new TreeSet<>();
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        Matcher m = THEME_IMAGE_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (m.find()) {
          names.add(m.group(1));
        }
      }
    }
    return names;
  }

  /** Whether getThemeImage resolves {@code name} to a GIF: no exact or PNG resource, a GIF one. */
  private static boolean resolvesToGif(String name) {
    ClassLoader loader = ResourceManager.class.getClassLoader();
    String base = "org/limewire/gui/images/" + name;
    return loader.getResource(base) == null
        && loader.getResource(base + ".png") == null
        && loader.getResource(base + ".gif") != null;
  }

  @Test
  void everyGifThemeImageIsPreloaded() throws IOException {
    Set<String> gifs = new TreeSet<>();
    for (String name : themeImageLiterals()) {
      if (resolvesToGif(name)) {
        gifs.add(name);
      }
    }
    assertFalse(gifs.isEmpty(), "the scan found no GIF theme images; is it reading the sources?");
    assertTrue(
        ResourceManager.ANIMATED_THEME_IMAGES.containsAll(gifs),
        "GIF theme images not preloaded at startup: " + gifs);
  }

  @Test
  void thePreloadCachesEachAnimatedImageOffTheEdt() throws Exception {
    Thread worker = new Thread(ResourceManager::preloadAnimatedThemeImages, "startup");
    worker.start();
    worker.join(30_000);

    for (String name : ResourceManager.ANIMATED_THEME_IMAGES) {
      ImageIcon preloaded = ResourceManager.getThemeImage(name);
      assertSame(preloaded, ResourceManager.getThemeImage(name), name + " is not cached");
    }
  }

  @Test
  void theStartupThreadPreloadsThemBeforeTheMainWindowIsBuilt() throws IOException {
    String source =
        Files.readString(
                Path.of("src/main/java/com/limegroup/gnutella/gui/Initializer.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    String earlyUi = source.substring(source.indexOf("privatevoidinitializeEarlyUI()"));
    earlyUi = earlyUi.substring(0, earlyUi.indexOf("BugManager.instance()"));
    assertTrue(
        earlyUi.contains("ResourceManager.preloadAnimatedThemeImages();"),
        "initializeEarlyUI does not preload the animated theme images");
  }
}
