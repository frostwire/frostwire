/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.jpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;

/** The 4-component (CMYK and YCCK) paths of JPEGImageIO and CMYKJPEGImageReader. */
final class JPEGImageIOCmykTest {

  /** A 4-band JPEG: the JDK writes a Raster as-is, with no colour transform and no Adobe marker. */
  private static byte[] fourBandJpeg(int w, int h, int b0, int b1, int b2, int b3)
      throws IOException {
    WritableRaster raster = Raster.createInterleavedRaster(DataBufferByte.TYPE_BYTE, w, h, 4, null);
    int[] pixel = {b0, b1, b2, b3};
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

  /** Inserts an Adobe APP14 segment with the given colour transform right after SOI. */
  private static byte[] withAdobeTransform(byte[] jpeg, int transform) {
    byte[] app14 = {
      (byte) 0xff, (byte) 0xee, 0, 14, 'A', 'd', 'o', 'b', 'e', 0, 100, 0, 0, 0, 0, (byte) transform
    };
    byte[] out = new byte[jpeg.length + app14.length];
    System.arraycopy(jpeg, 0, out, 0, 2);
    System.arraycopy(app14, 0, out, 2, app14.length);
    System.arraycopy(jpeg, 2, out, 2 + app14.length, jpeg.length - 2);
    return out;
  }

  private static int[] rgbAt(BufferedImage img, int x, int y) {
    int p = img.getRGB(x, y);
    return new int[] {(p >> 16) & 0xff, (p >> 8) & 0xff, p & 0xff};
  }

  @Test
  void theFrameComponentCountIsReadNotTheSamplePrecision() throws IOException {
    byte[] jpeg = fourBandJpeg(8, 8, 0, 0, 0, 0);
    BufferedImage img = JPEGImageIO.read(new ByteArrayInputStream(jpeg));
    assertNotNull(img);
    assertEquals(BufferedImage.TYPE_INT_RGB, img.getType(), "CMYK path builds an INT_RGB image");
    assertEquals(8, img.getWidth());
    assertEquals(8, img.getHeight());
  }

  @Test
  void cmykBlankPaperIsWhiteAndFullBlackIsDark() throws IOException {
    BufferedImage white =
        JPEGImageIO.read(new ByteArrayInputStream(fourBandJpeg(8, 8, 0, 0, 0, 0)));
    int[] w = rgbAt(white, 4, 4);
    assertTrue(w[0] > 230 && w[1] > 230 && w[2] > 230, "no ink is white: " + Arrays.toString(w));
    BufferedImage black =
        JPEGImageIO.read(new ByteArrayInputStream(fourBandJpeg(8, 8, 0, 0, 0, 255)));
    int[] k = rgbAt(black, 4, 4);
    assertTrue(k[0] < 90 && k[1] < 90 && k[2] < 90, "full K is dark: " + Arrays.toString(k));
  }

  @Test
  void cmykCyanAndYellowInkKeepTheirHue() throws IOException {
    int[] cyan =
        rgbAt(JPEGImageIO.read(new ByteArrayInputStream(fourBandJpeg(8, 8, 255, 0, 0, 0))), 4, 4);
    assertTrue(cyan[0] < cyan[1] - 60 && cyan[0] < cyan[2] - 60, "cyan: " + Arrays.toString(cyan));
    int[] yellow =
        rgbAt(JPEGImageIO.read(new ByteArrayInputStream(fourBandJpeg(8, 8, 0, 0, 255, 0))), 4, 4);
    assertTrue(
        yellow[2] < yellow[0] - 60 && yellow[2] < yellow[1] - 60,
        "yellow: " + Arrays.toString(yellow));
  }

  @Test
  void ycckJpegsAreConvertedThroughTheAdobeTransform() throws IOException {
    // neutral chroma, full luma, no black: no ink
    byte[] light = withAdobeTransform(fourBandJpeg(8, 8, 255, 128, 128, 0), 2);
    BufferedImage img = JPEGImageIO.read(new ByteArrayInputStream(light));
    assertNotNull(img);
    assertEquals(BufferedImage.TYPE_INT_RGB, img.getType());
    int[] l = rgbAt(img, 4, 4);
    assertTrue(l[0] > 200 && l[1] > 200 && l[2] > 200, "light ycck: " + Arrays.toString(l));
    // the same chroma with full black ink is dark
    byte[] dark = withAdobeTransform(fourBandJpeg(8, 8, 255, 128, 128, 255), 2);
    int[] d = rgbAt(JPEGImageIO.read(new ByteArrayInputStream(dark)), 4, 4);
    assertTrue(d[0] < 90 && d[1] < 90 && d[2] < 90, "dark ycck: " + Arrays.toString(d));
  }

  @Test
  void adobeTransformZeroIsHandledAsCmyk() throws IOException {
    byte[] jpeg = withAdobeTransform(fourBandJpeg(8, 8, 0, 0, 0, 0), 0);
    BufferedImage img = JPEGImageIO.read(new ByteArrayInputStream(jpeg));
    assertEquals(BufferedImage.TYPE_INT_RGB, img.getType());
    int[] w = rgbAt(img, 4, 4);
    assertTrue(w[0] > 230 && w[1] > 230 && w[2] > 230, Arrays.toString(w));
  }

  @Test
  void cmykReaderClassAlsoFindsTheComponentCount() throws IOException {
    byte[] jpeg = withAdobeTransform(fourBandJpeg(8, 8, 255, 128, 128, 0), 2);
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
      BufferedImage img = CMYKJPEGImageReader.read(in);
      assertNotNull(img);
      assertEquals(BufferedImage.TYPE_INT_RGB, img.getType());
      int[] l = rgbAt(img, 4, 4);
      assertTrue(l[0] > 200 && l[1] > 200 && l[2] > 200, Arrays.toString(l));
    }
  }
}
