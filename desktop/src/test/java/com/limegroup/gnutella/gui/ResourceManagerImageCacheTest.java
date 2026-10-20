/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

class ResourceManagerImageCacheTest {

  /**
   * Theme images decoded with ImageIO.read(URL), whose default stream cache writes a temporary
   * file; on a 4-CPU macOS machine that disk write stalled the EDT while building a list's delete
   * buttons (Icebase #153796bc035d). With the file cache pointed at a directory that no longer
   * exists, that path fails and falls back to an unbuffered Toolkit image; an in-memory decode
   * still works.
   */
  @Test
  void themeImagesDecodeWithoutImageIoFileCache() throws Exception {
    boolean useCache = ImageIO.getUseCache();
    File cacheDirectory = ImageIO.getCacheDirectory();
    Path gone = Files.createTempDirectory("imageio-cache");
    try {
      ImageIO.setUseCache(true);
      ImageIO.setCacheDirectory(gone.toFile());
      Files.delete(gone);

      ImageIcon icon = ResourceManager.getThemeImage("search_result_download_over");

      assertTrue(
          icon.getImage() instanceof BufferedImage,
          "decoded without ImageIO's file cache, got " + icon.getImage().getClass());
    } finally {
      ImageIO.setUseCache(useCache);
      ImageIO.setCacheDirectory(cacheDirectory);
    }
  }
}
