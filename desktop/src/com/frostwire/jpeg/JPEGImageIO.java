/*
 * @(#)JPEGImageIO.java  1.0.1  2010-10-10
 *
 * Copyright (c) 2008 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 *
 * Some of the code has been derived from libjpeg 6b available from
 * http://www.ijg.org/files/
 */
package com.frostwire.jpeg;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.*;
import java.io.*;

/**
 * JPEGImageIO supports reading of JPEG images with YUV, CMYK and YCCK color
 * encoding.
 *
 * @author Werner Randelshofer, Hausmatt 10, CH-6405 Immensee
 * @version 1.0.1 2010-10-10 Do not close input stream in method read(InputStream).
 * <br>1.0 2008-10-17 Created.
 */
public class JPEGImageIO {
    /**
     * Define tables for YCC->RGB colorspace conversion.
     */
    private final static int SCALEBITS = 16;
    private final static int MAXJSAMPLE = 255;
    private final static int CENTERJSAMPLE = 128;
    private final static int ONE_HALF = 1 << (SCALEBITS - 1);
    private final static int[] Cr_r_tab = new int[MAXJSAMPLE + 1];
    private final static int[] Cb_b_tab = new int[MAXJSAMPLE + 1];
    private final static int[] Cr_g_tab = new int[MAXJSAMPLE + 1];
    private final static int[] Cb_g_tab = new int[MAXJSAMPLE + 1];

    /**
     * Prevent instance creation.
     */
    private JPEGImageIO() {
    }

    /**
     * Reads a JPEG image from the provided InputStream and convert it into a
     * color space which can be handled by Java2D (that is RGB or Gray in J2SE 5).
     * The image data in the file can be in the YUV, Gray, YCCK or CMYK color space.
     *
     * @param in An InputStream in the JPEG File Interchange Format (JFIF).
     * @return a BufferedImage containing the decoded image converted into the
     * RGB color space.
     */
    public static BufferedImage read(InputStream in) throws IOException {
        return read(in, true);
    }

    @SuppressWarnings("unused")
    public static BufferedImage read(InputStream in, boolean inverseYCCKColors) throws IOException {
        // Read the stream into a byte array
        // --------------------------------------
        // We do this, because we need to perform multiple passes over the
        // stream in order to decode it.
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[512];
        for (int count = in.read(b); count != -1; count = in.read(b)) {
            buf.write(b, 0, count);
        }
        byte[] byteArray = buf.toByteArray();
        // Extract metadata from the JFIF stream.
        // --------------------------------------
        // In particular, we are interested into the following fields:
        int numberOfComponentsInFrame = 0;
        int app14AdobeColorTransform = 0;
        ByteArrayOutputStream app2ICCProfile = new ByteArrayOutputStream();
        // Browse for marker segments, and extract data from those
        // which are of interest.
        JFIFInputStream fifi = new JFIFInputStream(new ByteArrayInputStream(byteArray));
        for (JFIFInputStream.Segment seg = fifi.getNextSegment(); seg != null; seg = fifi.getNextSegment()) {
            if (0xffc0 <= seg.marker && seg.marker <= 0xffc3 ||
                    0xffc5 <= seg.marker && seg.marker <= 0xffc7 ||
                    0xffc9 <= seg.marker && seg.marker <= 0xffcb ||
                    0xffcd <= seg.marker && seg.marker <= 0xffcf) {
                // SOF0 - SOF15: Start of Frame Header marker segment
                DataInputStream dis = new DataInputStream(fifi);
                numberOfComponentsInFrame = dis.readUnsignedByte();
                // ...the rest of SOF header is not important to us.
                // In fact, by encounterint a SOF header, we have reached
                // the end of the metadata section we are interested in.
                // Thus we can abort here.
                break;
            } else if (seg.marker == 0xffe2) {
                // APP2: Application-specific marker segment
                if (seg.length >= 26) {
                    DataInputStream dis = new DataInputStream(fifi);
                    // Check for 12-bytes containing the null-terminated string: "ICC_PROFILE".
                    if (dis.readLong() == 0x4943435f50524f46L && dis.readInt() == 0x494c4500) {
                        // Skip 2 bytes
                        dis.skipBytes(2);
                        // Read Adobe ICC_PROFILE int buffer. The profile is split up over
                        // multiple APP2 marker segments.
                        for (int count = dis.read(b); count != -1; count = dis.read(b)) {
                            app2ICCProfile.write(b, 0, count);
                        }
                    }
                }
            } else if (seg.marker == 0xffee) {
                // APP14: Application-specific marker segment
                if (seg.length == 12) {
                    DataInputStream dis = new DataInputStream(fifi);
                    // Check for 6-bytes containing the null-terminated string: "Adobe".
                    if (dis.readInt() == 0x41646f62L && dis.readUnsignedShort() == 0x6500) {
                        int version = dis.readUnsignedByte();
                        int app14Flags0 = dis.readUnsignedShort();
                        int app14Flags1 = dis.readUnsignedShort();
                        app14AdobeColorTransform = dis.readUnsignedByte();
                    }
                }
            }
        }
        // fifi.close(); Do not close input stream
        // Read the image data
        BufferedImage img = null;
        if (numberOfComponentsInFrame != 4) {
            // Read image with YUV color encoding.
            img = readImageFromYUVorGray(new ByteArrayInputStream(byteArray));
        } else if (numberOfComponentsInFrame == 4) {
            // Try to instantiate an ICC_Profile from the app2ICCProfile
            ICC_Profile profile = null;
            if (app2ICCProfile.size() > 0) {
                try {
                    profile = ICC_Profile.getInstance(new ByteArrayInputStream(app2ICCProfile.toByteArray()));
                } catch (Throwable ex) {
                    // icc profile is corrupt
                    ex.printStackTrace();
                }
            }
            // In case of failure, use a Generic CMYK profile
            if (profile == null) {
                profile = ICC_Profile.getInstance(JPEGImageIO.class.getResourceAsStream("Generic CMYK Profile.icc"));
            }
            switch (app14AdobeColorTransform) {
                case 0:
                default:
                    // Read image with CMYK color encoding.
                    img = readRGBImageFromCMYK(new ByteArrayInputStream(byteArray), profile);
                    break;
                case 1:
                    throw new IOException("YCbCr not supported");
                case 2:
                    // Read image with inverted YCCK color encoding.
                    // FIXME - How do we determine from the JFIF file whether
                    // YCCK colors are inverted?
                    if (inverseYCCKColors) {
                        img = readRGBImageFromInvertedYCCK(new ByteArrayInputStream(byteArray), profile);
                    } else {
                        img = readRGBImageFromYCCK(new ByteArrayInputStream(byteArray), profile);
                    }
                    break;
            }
        }
        return img;
    }

    /**
     * Reads a JPEG image from the provided InputStream.
     * The image data must be in the YUV or the Gray color space.
     * <p>
     * Use this method, if you have already determined that the input stream
     * contains a YUV or Gray JPEG image.
     *
     * @param in An InputStream, preferably an ImageInputStream, in the JPEG
     *           File Interchange Format (JFIF).
     * @return a BufferedImage containing the decoded image converted into the
     * RGB color space.
     */
    private static BufferedImage readImageFromYUVorGray(InputStream in) throws IOException {
        return (in instanceof ImageInputStream) ? ImageIO.read((ImageInputStream) in) : ImageIO.read(in);
    }

    /**
     * Reads a CMYK JPEG image from the provided InputStream, converting the
     * colors to RGB using the provided CMYK ICC_Profile. The image data must
     * be in the CMYK color space.
     * <p>
     * Use this method, if you have already determined that the input stream
     * contains a CMYK JPEG image.
     *
     * @param in          An InputStream, preferably an ImageInputStream, in the JPEG
     *                    File Interchange Format (JFIF).
     * @param cmykProfile An ICC_Profile for conversion from the CMYK color space
     *                    to the RGB color space. If this parameter is null, a default profile is used.
     * @return a BufferedImage containing the decoded image converted into the
     * RGB color space.
     */
    private static BufferedImage readRGBImageFromCMYK(InputStream in, ICC_Profile cmykProfile) throws IOException {
        ImageInputStream inputStream;
        ImageReader reader = ImageIO.getImageReadersByFormatName("JPEG").next();
        inputStream = (in instanceof ImageInputStream) ? (ImageInputStream) in : ImageIO.createImageInputStream(in);
        reader.setInput(inputStream);
        Raster raster = reader.readRaster(0, null);
        return createRGBImageFromCMYK(raster, cmykProfile);
    }

    /**
     * Reads a YCCK JPEG image from the provided InputStream, converting the
     * colors to RGB using the provided CMYK ICC_Profile. The image data must
     * be in the YCCK color space.
     * <p>
     * Use this method, if you have already determined that the input stream
     * contains a YCCK JPEG image.
     *
     * @param in          An InputStream, preferably an ImageInputStream, in the JPEG
     *                    File Interchange Format (JFIF).
     * @param cmykProfile An ICC_Profile for conversion from the CMYK color space
     *                    to the RGB color space. If this parameter is null, a default profile is used.
     * @return a BufferedImage containing the decoded image converted into the
     * RGB color space.
     */
    private static BufferedImage readRGBImageFromYCCK(InputStream in, ICC_Profile cmykProfile) throws IOException {
        ImageInputStream inputStream;
        ImageReader reader = ImageIO.getImageReadersByFormatName("JPEG").next();
        inputStream = (in instanceof ImageInputStream) ? (ImageInputStream) in : ImageIO.createImageInputStream(in);
        reader.setInput(inputStream);
        Raster raster = reader.readRaster(0, null);
        return createRGBImageFromYCCK(raster, cmykProfile);
    }

    /**
     * Reads an inverted-YCCK JPEG image from the provided InputStream, converting the
     * colors to RGB using the provided CMYK ICC_Profile. The image data must
     * be in the inverted-YCCK color space.
     * <p>
     * Use this method, if you have already determined that the input stream
     * contains an inverted-YCCK JPEG image.
     *
     * @param in          An InputStream, preferably an ImageInputStream, in the JPEG
     *                    File Interchange Format (JFIF).
     * @param cmykProfile An ICC_Profile for conversion from the CMYK color space
     *                    to the RGB color space. If this parameter is null, a default profile is used.
     * @return a BufferedImage containing the decoded image converted into the
     * RGB color space.
     */
    private static BufferedImage readRGBImageFromInvertedYCCK(InputStream in, ICC_Profile cmykProfile) throws IOException {
        ImageInputStream inputStream;
        ImageReader reader = ImageIO.getImageReadersByFormatName("JPEG").next();
        inputStream = (in instanceof ImageInputStream) ? (ImageInputStream) in : ImageIO.createImageInputStream(in);
        reader.setInput(inputStream);
        Raster raster = reader.readRaster(0, null);
        raster = convertInvertedYCCKToCMYK(raster);
        return createRGBImageFromCMYK(raster, cmykProfile);
    }

    /**
     * Creates a buffered image from a raster in the YCCK color space, converting
     * the colors to RGB using the provided CMYK ICC_Profile.
     *
     * @param ycckRaster  A raster with (at least) 4 bands of samples.
     * @param cmykProfile An ICC_Profile for conversion from the CMYK color space
     *                    to the RGB color space. If this parameter is null, a default profile is used.
     * @return a BufferedImage in the RGB color space.
     */
    private static BufferedImage createRGBImageFromYCCK(Raster ycckRaster, ICC_Profile cmykProfile) {
        BufferedImage image;
        if (cmykProfile != null) {
            ycckRaster = convertYCCKtoCMYK(ycckRaster);
            image = createRGBImageFromCMYK(ycckRaster, cmykProfile);
        } else {
            int w = ycckRaster.getWidth(), h = ycckRaster.getHeight();
            int[] rgb = new int[w * h];
            int[] Y = ycckRaster.getSamples(0, 0, w, h, 0, (int[]) null);
            int[] Cb = ycckRaster.getSamples(0, 0, w, h, 1, (int[]) null);
            int[] Cr = ycckRaster.getSamples(0, 0, w, h, 2, (int[]) null);
            int[] K = ycckRaster.getSamples(0, 0, w, h, 3, (int[]) null);
            float vr, vg, vb;
            for (int i = 0, imax = Y.length; i < imax; i++) {
                float k = K[i], y = Y[i], cb = Cb[i], cr = Cr[i];
                vr = y + 1.402f * (cr - 128) - k;
                vg = y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128) - k;
                vb = y + 1.772f * (cb - 128) - k;
                rgb[i] = (0xff & (vr < 0.0f ? 0 : vr > 255.0f ? 0xff : (int) (vr + 0.5f))) << 16 |
                        (0xff & (vg < 0.0f ? 0 : vg > 255.0f ? 0xff : (int) (vg + 0.5f))) << 8 |
                        (0xff & (vb < 0.0f ? 0 : vb > 255.0f ? 0xff : (int) (vb + 0.5f)));
            }
            WritableRaster rgbRaster = Raster.createPackedRaster(
                    new DataBufferInt(rgb, rgb.length),
                    w, h, w, new int[]{0xff0000, 0xff00, 0xff}, null);
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
            ColorModel cm = new DirectColorModel(cs, 24, 0xff0000, 0xff00, 0xff, 0x0, false, DataBuffer.TYPE_INT);
            image = new BufferedImage(cm, rgbRaster, true, null);
        }
        return image;
    }

    /**
     * Creates a buffered image from a raster in the CMYK color space, converting
     * the colors to RGB using the provided CMYK ICC_Profile.
     * <p>
     * As seen from a comment made by 'phelps' at
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4799903
     *
     * @param cmykRaster  A raster with (at least) 4 bands of samples.
     * @param cmykProfile An ICC_Profile for conversion from the CMYK color space
     *                    to the RGB color space. If this parameter is null, a default profile is used.
     * @return a BufferedImage in the RGB color space.
     */
    private static BufferedImage createRGBImageFromCMYK(Raster cmykRaster, ICC_Profile cmykProfile) {
        BufferedImage image;
        int w = cmykRaster.getWidth();
        int h = cmykRaster.getHeight();
        if (cmykProfile != null) {
            ColorSpace cmykCS = new ICC_ColorSpace(cmykProfile);
            image = new BufferedImage(w, h,
                    BufferedImage.TYPE_INT_RGB);
            WritableRaster rgbRaster = image.getRaster();
            ColorSpace rgbCS = image.getColorModel().getColorSpace();
            ColorConvertOp cmykToRgb = new ColorConvertOp(cmykCS, rgbCS, null);
            cmykToRgb.filter(cmykRaster, rgbRaster);
        } else {
            int[] rgb = new int[w * h];
            int[] C = cmykRaster.getSamples(0, 0, w, h, 0, (int[]) null);
            int[] M = cmykRaster.getSamples(0, 0, w, h, 1, (int[]) null);
            int[] Y = cmykRaster.getSamples(0, 0, w, h, 2, (int[]) null);
            int[] K = cmykRaster.getSamples(0, 0, w, h, 3, (int[]) null);
            for (int i = 0, imax = C.length; i < imax; i++) {
                int k = K[i];
                rgb[i] = (255 - Math.min(255, C[i] + k)) << 16 |
                        (255 - Math.min(255, M[i] + k)) << 8 |
                        (255 - Math.min(255, Y[i] + k));
            }
            WritableRaster rgbRaster = Raster.createPackedRaster(
                    new DataBufferInt(rgb, rgb.length),
                    w, h, w, new int[]{0xff0000, 0xff00, 0xff}, null);
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
            ColorModel cm = new DirectColorModel(cs, 24, 0xff0000, 0xff00, 0xff, 0x0, false, DataBuffer.TYPE_INT);
            image = new BufferedImage(cm, rgbRaster, true, null);
        }
        return image;
    }

    /*
     * Initialize tables for YCC->RGB colorspace conversion.
     */
    private static synchronized void buildYCCtoRGBtable() {
        if (Cr_r_tab[0] == 0) {
            for (int i = 0, x = -CENTERJSAMPLE; i <= MAXJSAMPLE; i++, x++) {
                /* i is the actual input pixel value, in the range 0..MAXJSAMPLE */
                /* The Cb or Cr value we are thinking of is x = i - CENTERJSAMPLE */
                /* Cr=>R value is nearest int to 1.40200 * x */
                Cr_r_tab[i] = (int) ((1.40200 * (1 << SCALEBITS) + 0.5) * x + ONE_HALF) >> SCALEBITS;
                /* Cb=>B value is nearest int to 1.77200 * x */
                Cb_b_tab[i] = (int) ((1.77200 * (1 << SCALEBITS) + 0.5) * x + ONE_HALF) >> SCALEBITS;
                /* Cr=>G value is scaled-up -0.71414 * x */
                Cr_g_tab[i] = -(int) (0.71414 * (1 << SCALEBITS) + 0.5) * x;
                /* Cb=>G value is scaled-up -0.34414 * x */
                /* We also add in ONE_HALF so that need not do it in inner loop */
                Cb_g_tab[i] = -(int) ((0.34414) * (1 << SCALEBITS) + 0.5) * x + ONE_HALF;
            }
        }
    }

    /*
     * Adobe-style YCCK->CMYK conversion.
     * We convert YCbCr to R=1-C, G=1-M, and B=1-Y using the same
     * conversion as above, while passing K (black) unchanged.
     * We assume build_ycc_rgb_table has been called.
     */
    private static Raster convertInvertedYCCKToCMYK(Raster ycckRaster) {
        buildYCCtoRGBtable();
        int w = ycckRaster.getWidth(), h = ycckRaster.getHeight();
        int[] ycckY = ycckRaster.getSamples(0, 0, w, h, 0, (int[]) null);
        int[] ycckCb = ycckRaster.getSamples(0, 0, w, h, 1, (int[]) null);
        int[] ycckCr = ycckRaster.getSamples(0, 0, w, h, 2, (int[]) null);
        int[] ycckK = ycckRaster.getSamples(0, 0, w, h, 3, (int[]) null);
        int[] cmyk = new int[ycckY.length];
        for (int i = 0; i < ycckY.length; i++) {
            int y = 255 - ycckY[i];
            int cb = 255 - ycckCb[i];
            int cr = 255 - ycckCr[i];
            int cmykC, cmykM, cmykY;
            // Range-limiting is essential due to noise introduced by DCT losses. 
            cmykC = MAXJSAMPLE - (y + Cr_r_tab[cr]);    // red
            cmykM = MAXJSAMPLE - (y + // green 
                    (Cb_g_tab[cb] + Cr_g_tab[cr] >>
                            SCALEBITS));
            cmykY = MAXJSAMPLE - (y + Cb_b_tab[cb]);    // blue
            /* K passes through unchanged */
            cmyk[i] = (cmykC < 0 ? 0 : Math.min(cmykC, 255)) << 24 |
                    (cmykM < 0 ? 0 : Math.min(cmykM, 255)) << 16 |
                    (cmykY < 0 ? 0 : Math.min(cmykY, 255)) << 8 |
                    255 - ycckK[i];
        }
        return Raster.createPackedRaster(
                new DataBufferInt(cmyk, cmyk.length),
                w, h, w, new int[]{0xff000000, 0xff0000, 0xff00, 0xff}, null);
    }

    private static Raster convertYCCKtoCMYK(Raster ycckRaster) {
        buildYCCtoRGBtable();
        int w = ycckRaster.getWidth(), h = ycckRaster.getHeight();
        int[] ycckY = ycckRaster.getSamples(0, 0, w, h, 0, (int[]) null);
        int[] ycckCb = ycckRaster.getSamples(0, 0, w, h, 1, (int[]) null);
        int[] ycckCr = ycckRaster.getSamples(0, 0, w, h, 2, (int[]) null);
        int[] ycckK = ycckRaster.getSamples(0, 0, w, h, 3, (int[]) null);
        int[] cmyk = new int[ycckY.length];
        for (int i = 0; i < ycckY.length; i++) {
            int y = ycckY[i];
            int cb = ycckCb[i];
            int cr = ycckCr[i];
            int cmykC, cmykM, cmykY;
            // Range-limiting is essential due to noise introduced by DCT losses. 
            cmykC = MAXJSAMPLE - (y + Cr_r_tab[cr]);    // red
            cmykM = MAXJSAMPLE - (y + // green 
                    (Cb_g_tab[cb] + Cr_g_tab[cr] >>
                            SCALEBITS));
            cmykY = MAXJSAMPLE - (y + Cb_b_tab[cb]);    // blue
            /* K passes through unchanged */
            cmyk[i] = (cmykC < 0 ? 0 : Math.min(cmykC, 255)) << 24 |
                    (cmykM < 0 ? 0 : Math.min(cmykM, 255)) << 16 |
                    (cmykY < 0 ? 0 : Math.min(cmykY, 255)) << 8 |
                    ycckK[i];
        }
        return Raster.createPackedRaster(
                new DataBufferInt(cmyk, cmyk.length),
                w, h, w, new int[]{0xff000000, 0xff0000, 0xff00, 0xff}, null);
    }
}


