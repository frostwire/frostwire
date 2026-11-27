/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.jpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;

final class JPEGImageIOTest {

  private static byte[] encode(BufferedImage img) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(img, "jpg", out));
    return out.toByteArray();
  }

  /** A 4-band JPEG: the JDK writes a Raster as-is, with no colour transform. */
  private static byte[] cmykJpeg(int w, int h, int c, int m, int y, int k) throws IOException {
    WritableRaster raster = Raster.createInterleavedRaster(DataBufferByte.TYPE_BYTE, w, h, 4, null);
    int[] pixel = {c, m, y, k};
    for (int x = 0; x < w; x++) {
      for (int row = 0; row < h; row++) raster.setPixel(x, row, pixel);
    }
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(ios);
      writer.write(null, new IIOImage(raster, null, null), null);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  @Test
  void rgbJpegsGoThroughTheStandardDecoder() throws IOException {
    BufferedImage src = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = src.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 16, 8);
    g.dispose();
    BufferedImage read = JPEGImageIO.read(new ByteArrayInputStream(encode(src)));
    assertNotNull(read);
    assertEquals(16, read.getWidth());
    assertEquals(8, read.getHeight());
    int rgb = read.getRGB(8, 4);
    assertTrue(((rgb >> 16) & 0xff) > 200, "red channel survives lossy compression");
    assertTrue((rgb & 0xff) < 60);
  }

  @Test
  void grayscaleJpegsAreDecoded() throws IOException {
    BufferedImage gray = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
    BufferedImage read = JPEGImageIO.read(new ByteArrayInputStream(encode(gray)));
    assertEquals(8, read.getWidth());
  }

  @Test
  void fourBandJpegsAreDecodedToTheSameSize() throws IOException {
    byte[] data = cmykJpeg(8, 8, 0, 0, 0, 0);
    BufferedImage read = JPEGImageIO.read(new ByteArrayInputStream(data));
    assertNotNull(read);
    assertEquals(8, read.getWidth());
    assertEquals(8, read.getHeight());
  }

  @Test
  void nonImagesAreRejectedOrIgnored() throws IOException {
    assertNull(JPEGImageIO.read(new ByteArrayInputStream(new byte[] {1, 2, 3})));
  }
}
