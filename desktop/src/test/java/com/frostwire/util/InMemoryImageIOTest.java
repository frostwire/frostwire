/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.frostwire.jpeg.JPEGImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ImageIO's file cache is pointed at a directory that no longer exists: any decode that still
 * copies the image into a temporary file fails, so these pass only when decoding stays in memory.
 */
class InMemoryImageIOTest {
  private boolean useCache;
  private File cacheDirectory;
  private byte[] png;
  private byte[] jpeg;

  @BeforeEach
  void breakImageIoFileCache() throws IOException {
    png = encode("png");
    jpeg = encode("jpg");
    useCache = ImageIO.getUseCache();
    cacheDirectory = ImageIO.getCacheDirectory();
    Path gone = Files.createTempDirectory("imageio-cache");
    ImageIO.setUseCache(true);
    ImageIO.setCacheDirectory(gone.toFile());
    Files.delete(gone);
  }

  @AfterEach
  void restoreImageIo() {
    ImageIO.setUseCache(useCache);
    ImageIO.setCacheDirectory(cacheDirectory);
  }

  private static byte[] encode(String format) throws IOException {
    BufferedImage image = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  @Test
  void thePlainImageIoPathUsesTheFileCache() {
    assertThrows(Exception.class, () -> ImageIO.read(new ByteArrayInputStream(png)));
  }

  @Test
  void decodesBytesUrlsAndStreamsInMemory() throws Exception {
    assertEquals(8, InMemoryImageIO.read(png).getWidth());
    Path file = Files.createTempFile("image", ".png");
    try {
      Files.write(file, png);
      assertEquals(6, InMemoryImageIO.read(file.toUri().toURL()).getHeight());
    } finally {
      Files.delete(file);
    }
    assertNotNull(InMemoryImageIO.read(new ByteArrayInputStream(jpeg)));
  }

  @Test
  void unknownDataDecodesToNullLikeImageIo() throws Exception {
    assertNull(InMemoryImageIO.read(new byte[] {1, 2, 3, 4}));
  }

  /** Song artwork falls back to JPEGImageIO for JPEGs ImageIO rejects; it reads in memory too. */
  @Test
  void jpegFallbackDecodesInMemory() throws Exception {
    assertEquals(8, JPEGImageIO.read(new ByteArrayInputStream(jpeg)).getWidth());
  }
}
