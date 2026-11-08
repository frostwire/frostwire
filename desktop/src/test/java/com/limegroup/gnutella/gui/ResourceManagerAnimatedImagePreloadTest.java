/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.init.SocialRecommendationsWindow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

  /** Whether {@code name} is a theme image getThemeImage can load (an exact, PNG or GIF file). */
  private static boolean isThemeImage(String name) {
    ClassLoader loader = ResourceManager.class.getClassLoader();
    String base = "org/limewire/gui/images/" + name;
    return !name.isEmpty()
        && !name.contains("/")
        && (loader.getResource(base + ".png") != null || loader.getResource(base + ".gif") != null);
  }

  /**
   * The status bar sets its icons from refreshes on the EDT, and each first use read the image from
   * the jar there (Icebase #f6b720eb10bf, frostwire/frostwire#1481). Every string literal in these
   * classes that names a theme image is preloaded, whether it is passed to getThemeImage directly
   * or chosen through a variable or a conditional.
   */
  @Test
  void everyStatusBarThemeImageIsPreloaded() throws IOException {
    Pattern literal = Pattern.compile("\"([a-z0-9_]+)\"");
    Set<String> icons = new TreeSet<>();
    for (String file : new String[] {"StatusLine.java", "VPNStatusButton.java"}) {
      Matcher m =
          literal.matcher(
              Files.readString(
                  Path.of("src/main/java/com/limegroup/gnutella/gui").resolve(file),
                  StandardCharsets.UTF_8));
      while (m.find()) {
        if (isThemeImage(m.group(1))) {
          icons.add(m.group(1));
        }
      }
    }
    assertTrue(icons.contains("vpn_on"), "the scan found no status bar icons: " + icons);
    icons.removeAll(ResourceManager.STATUS_BAR_THEME_IMAGES);
    assertTrue(icons.isEmpty(), "status bar icons not preloaded at startup: " + icons);
  }

  @Test
  void thePreloadCachesEachAnimatedImageOffTheEdt() throws Exception {
    Thread worker = new Thread(ResourceManager::preloadStartupThemeImages, "startup");
    worker.start();
    worker.join(30_000);

    for (String name :
        Stream.concat(
                ResourceManager.ANIMATED_THEME_IMAGES.stream(),
                ResourceManager.STATUS_BAR_THEME_IMAGES.stream())
            .toList()) {
      ImageIcon preloaded = ResourceManager.getThemeImage(name);
      assertSame(preloaded, ResourceManager.getThemeImage(name), name + " is not cached");
    }
  }

  /**
   * The first search built its button row on the EDT, and each icon was read from the jar and
   * decoded there (Icebase #f071da08eef4, frostwire/frostwire#1486). The startup preload loads the
   * theme image each search button resolves to, in both icon sizes.
   */
  @Test
  void theSearchButtonIconsArePreloadedOffTheEdt() throws Exception {
    List<List<String>> candidates = new ArrayList<>();
    for (boolean small : new boolean[] {true, false}) {
      List<List<String>> sized =
          ButtonIconController.themeImageCandidates(ResourceManager.SEARCH_BUTTON_ICONS, small);
      assertEquals(
          ResourceManager.SEARCH_BUTTON_ICONS.size(),
          sized.size(),
          "a search button has no icon mapping");
      candidates.addAll(sized);
    }
    candidates.forEach(names -> names.forEach(ResourceManager::forgetThemeImage));

    Thread worker = new Thread(ResourceManager::preloadStartupThemeImages, "startup");
    worker.start();
    worker.join(30_000);

    for (List<String> names : candidates) {
      assertTrue(
          names.stream().anyMatch(ResourceManager::isThemeImageCached),
          "none of " + names + " was preloaded");
    }
  }

  /**
   * Building the setup wizard's social page on the EDT read and decoded its icons from the jar
   * there (Icebase #2c96471ade1e, frostwire/frostwire#1491). The page's preload, run on the startup
   * thread while the pages are decided, loads the theme image each icon resolves to.
   */
  @Test
  void theSetupWizardSocialIconsArePreloadedOffTheEdt() throws Exception {
    List<List<String>> candidates = new ArrayList<>();
    for (boolean small : new boolean[] {true, false}) {
      List<List<String>> sized =
          ButtonIconController.themeImageCandidates(SocialRecommendationsWindow.ICONS, small);
      assertEquals(
          SocialRecommendationsWindow.ICONS.size(), sized.size(), "a social icon has no mapping");
      candidates.addAll(sized);
    }
    candidates.forEach(names -> names.forEach(ResourceManager::forgetThemeImage));

    Thread worker = new Thread(SocialRecommendationsWindow::preloadIcons, "startup");
    worker.start();
    worker.join(30_000);

    for (List<String> names : candidates) {
      assertTrue(
          names.stream().anyMatch(ResourceManager::isThemeImageCached),
          "none of " + names + " was preloaded");
    }
  }

  /**
   * The main window's header reads these while it is built on the EDT; the startup thread's preload
   * must have decoded them first (frostwire/frostwire#1494).
   */
  @Test
  void theHeaderImagesArePreloadedOffTheEdt() throws Exception {
    Set<String> read = new TreeSet<>();
    Matcher m =
        THEME_IMAGE_LITERAL.matcher(
            Files.readString(
                Path.of("src/main/java/com/limegroup/gnutella/gui/ApplicationHeader.java"),
                StandardCharsets.UTF_8));
    while (m.find()) {
      read.add(m.group(1));
    }
    assertFalse(read.isEmpty());
    assertTrue(
        ApplicationHeader.THEME_IMAGES.containsAll(read),
        "not preloaded: " + read + " vs " + ApplicationHeader.THEME_IMAGES);
    ApplicationHeader.THEME_IMAGES.forEach(ResourceManager::forgetThemeImage);

    Thread worker = new Thread(ApplicationHeader::preloadImages, "startup");
    worker.start();
    worker.join(30_000);

    for (String name : ApplicationHeader.THEME_IMAGES) {
      assertTrue(ResourceManager.isThemeImageCached(name), name + " was not preloaded");
    }
  }

  /**
   * The Library's and Transfers' button rows are built at startup with their tabs, and each icon
   * was read from the jar and decoded on the EDT (Icebase #cfa1bcdf5328, frostwire/frostwire#1503).
   * The startup preload loads the theme image each of their buttons resolves to, in both sizes.
   */
  @Test
  void theLibraryAndTransferButtonIconsArePreloadedOffTheEdt() throws Exception {
    List<List<String>> candidates = new ArrayList<>();
    for (List<String> buttons :
        List.of(ResourceManager.LIBRARY_BUTTON_ICONS, ResourceManager.TRANSFER_BUTTON_ICONS)) {
      for (boolean small : new boolean[] {true, false}) {
        List<List<String>> sized = ButtonIconController.themeImageCandidates(buttons, small);
        assertEquals(
            buttons.size(), sized.size(), "a button in " + buttons + " has no icon mapping");
        candidates.addAll(sized);
      }
    }
    candidates.forEach(names -> names.forEach(ResourceManager::forgetThemeImage));

    Thread worker = new Thread(ResourceManager::preloadStartupThemeImages, "startup");
    worker.start();
    worker.join(30_000);

    for (List<String> names : candidates) {
      assertTrue(
          names.stream().anyMatch(ResourceManager::isThemeImageCached),
          "none of " + names + " was preloaded");
    }
  }

  /** Every icon the preload lists is one the Library or Transfers actions name. */
  @Test
  void theLibraryAndTransferPreloadListsNameTheirActionsIcons() throws IOException {
    Set<String> icons = new TreeSet<>();
    for (String file :
        List.of(
            "src/main/java/com/frostwire/gui/library/LibraryFilesTableMediator.java",
            "src/main/java/com/frostwire/gui/library/AbstractLibraryTableMediator.java",
            "src/main/java/com/frostwire/gui/bittorrent/BTDownloadActions.java")) {
      Matcher literal =
          Pattern.compile("ICON_NAME,\\s*\"([A-Z0-9_]+)\"")
              .matcher(Files.readString(Path.of(file), StandardCharsets.UTF_8));
      while (literal.find()) {
        icons.add(literal.group(1));
      }
    }
    assertTrue(icons.containsAll(ResourceManager.LIBRARY_BUTTON_ICONS), "library: " + icons);
    assertTrue(icons.containsAll(ResourceManager.TRANSFER_BUTTON_ICONS), "transfers: " + icons);
  }

  /** The preload list names exactly the icons SearchButtons builds. */
  @Test
  void theSearchButtonPreloadListMatchesSearchButtons() throws IOException {
    String source =
        Files.readString(
            Path.of("src/main/java/com/limegroup/gnutella/gui/search/SearchButtons.java"),
            StandardCharsets.UTF_8);
    Matcher array = Pattern.compile("iconNames\\s*=\\s*\\{([^}]*)\\}").matcher(source);
    assertTrue(array.find(), "SearchButtons no longer declares iconNames");
    Set<String> icons = new TreeSet<>();
    Matcher literal = Pattern.compile("\"([A-Z0-9_]+)\"").matcher(array.group(1));
    while (literal.find()) {
      icons.add(literal.group(1));
    }
    assertEquals(new TreeSet<>(ResourceManager.SEARCH_BUTTON_ICONS), icons);
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
        earlyUi.contains("ResourceManager.preloadStartupThemeImages();"),
        "initializeEarlyUI does not preload the animated theme images");
  }
}
