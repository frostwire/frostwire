/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Decodes images through an in-memory stream.
 *
 * <p>ImageIO.read(InputStream) and ImageIO.read(URL) wrap non-seekable input in a
 * FileCacheImageInputStream by default (ImageIO.getUseCache()), copying the image into a temporary
 * file before decoding it, even when the bytes are already in memory. FrostWire's images (icons,
 * artwork, slides) are small, so that disk write is pure cost and stalls the EDT on slow disks.
 * ImageIO.read(File) reads the file directly and needs no replacement.
 */
public final class InMemoryImageIO {
  private InMemoryImageIO() {}

  /** Decodes {@code data}; returns null when no reader recognizes it, like ImageIO.read. */
  public static BufferedImage read(byte[] data) throws IOException {
    return read(new ByteArrayInputStream(data));
  }

  /** Decodes the resource at {@code url}; returns null when no reader recognizes it. */
  public static BufferedImage read(URL url) throws IOException {
    try (InputStream in = url.openStream()) {
      return read(in);
    }
  }

  /**
   * Decodes {@code in}; returns null when no reader recognizes it. Leaves {@code in} open, as
   * ImageIO.read(InputStream) does.
   */
  public static BufferedImage read(InputStream in) throws IOException {
    // ImageIO.read(ImageInputStream) closes the image stream (not the underlying InputStream).
    return ImageIO.read(new MemoryCacheImageInputStream(in));
  }
}
