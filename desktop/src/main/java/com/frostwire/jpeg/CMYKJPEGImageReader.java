/*
 * @(#)MJPGImageReader.java  1.2.1  2011-08-15
 *
 * Copyright (c) 2010-2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 *
 * Cleanup of unused methods and parameters, privacy refactors by FrostWire on 2018-12-18.
 */
package com.frostwire.jpeg;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.*;
import java.io.*;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Reads a JPEG image with colors in the CMYK color space.
 *
 * @author Werner Randelshofer
 * @version 1.2.1 2011-08-15 Seek to start of input stream in read() method.
 * <br>1.2 2011-02-17 Removes support for JMF.
 * <br>1.1.1 2011-02-14 If a CMYK image doesn't have a color profile, don't
 * try with a generic CMYK profile.
 * <br>1.1 2010-11-16 Fixes handling of JPEG images in the RGBA color space
 * (in prior versions, these were wrongly handled as CMYK images).
 * <br>1.0 2010-07-23 Created.
 */
public class CMYKJPEGImageReader extends ImageReader {
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
    private static final DirectColorModel RGB = new DirectColorModel(24, 0xff0000, 0xff00, 0xff, 0x0);
    /**
     * When we read the header, we read the whole image.
     */
    private BufferedImage image;

    private CMYKJPEGImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @SuppressWarnings("unused")
    public static BufferedImage read(ImageInputStream in, boolean inverseYCCKColors) throws IOException {
        // Seek to start of input stream
        in.seek(0);
        // Extract metadata from the JFIF stream.
        // --------------------------------------
        // In particular, we are interested into the following fields:
        int samplePrecision = 0;
        int numberOfLines = 0;
        int numberOfSamplesPerLine = 0;
        int numberOfComponentsInFrame = 0;
        int app14AdobeColorTransform = 0;
        ByteArrayOutputStream app2ICCProfile = new ByteArrayOutputStream();
        // Browse for marker segments, and extract data from those
        // which are of interest.
        JFIFInputStream fifi = new JFIFInputStream(new ImageInputStreamAdapter(in));
        for (JFIFInputStream.Segment seg = fifi.getNextSegment(); seg != null; seg = fifi.getNextSegment()) {
            if (0xffc0 <= seg.marker && seg.marker <= 0xffc3
                    || 0xffc5 <= seg.marker && seg.marker <= 0xffc7
                    || 0xffc9 <= seg.marker && seg.marker <= 0xffcb
                    || 0xffcd <= seg.marker && seg.marker <= 0xffcf) {
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
                        byte[] b = new byte[512];
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
        //fifi.close();
        // Read the image data
        BufferedImage img = null;
        if (numberOfComponentsInFrame != 4) {
            // Read image with YUV color encoding.
            in.seek(0);
            img = readImageFromYUVorGray(in);
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
                profile = ICC_Profile.getInstance(CMYKJPEGImageReader.class.getResourceAsStream("Generic CMYK Profile.icc"));
            }
            switch (app14AdobeColorTransform) {
                case 0:
                default:
                    // Read image with RGBA color encoding.
                    in.seek(0);
                    img = readRGBAImageFromRGBA(new ImageInputStreamAdapter(in));
                    break;
                case 1:
                    throw new IOException("YCbCr not supported");
                case 2:
                    // Read image with inverted YCCK color encoding.
                    // FIXME - How do we determine from the JFIF file whether
                    // YCCK colors are inverted?
                    in.seek(0);
                    if (inverseYCCKColors) {
                        img = readRGBImageFromInvertedYCCK(new ImageInputStreamAdapter(in), profile);
                    } else {
                        img = readRGBImageFromYCCK(new ImageInputStreamAdapter(in), profile);
                    }
                    break;
            }
        }
        return img;
    }

    private static ImageReader createNativeJPEGReader() {
        for (Iterator<ImageReader> i =
             ImageIO.getImageReadersByFormatName("jpeg"); i.hasNext(); ) {
            ImageReader r = i.next();
            if (!(r instanceof CMYKJPEGImageReader)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Reads a RGBA JPEG image from the provided InputStream, converting the
     * colors to RGBA using the provided RGBA ICC_Profile. The image data must
     * be in the RGBA color space.
     * <p>
     * Use this method, if you have already determined that the input stream
     * contains a RGBA JPEG image.
     *
     * @param in An InputStream, preferably an ImageInputStream, in the JPEG
     *           File Interchange Format (JFIF).
     * @return a BufferedImage containing the decoded image converted into the
     * RGB color space.
     */
    private static BufferedImage readRGBAImageFromRGBA(InputStream in) throws IOException {
        ImageInputStream inputStream;
        ImageReader reader = createNativeJPEGReader();
        inputStream = (in instanceof ImageInputStream) ? (ImageInputStream) in : ImageIO.createImageInputStream(in);
        reader.setInput(inputStream);
        Raster raster = reader.readRaster(0, null);
        return createRGBAImageFromRGBA(raster);
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
        ImageReader reader = createNativeJPEGReader();
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
        ImageReader reader = createNativeJPEGReader();
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
                rgb[i] = (0xff & (vr < 0.0f ? 0 : vr > 255.0f ? 0xff : (int) (vr + 0.5f))) << 16
                        | (0xff & (vg < 0.0f ? 0 : vg > 255.0f ? 0xff : (int) (vg + 0.5f))) << 8
                        | (0xff & (vb < 0.0f ? 0 : vb > 255.0f ? 0xff : (int) (vb + 0.5f)));
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
     * Creates a buffered image from a raster in the inverted YCCK color space,
     * converting the colors to RGB using the provided CMYK ICC_Profile.
     *
     * @param ycckRaster  A raster with (at least) 4 bands of samples.
     * @param cmykProfile An ICC_Profile for conversion from the CMYK color space
     *                    to the RGB color space. If this parameter is null, a default profile is used.
     * @return a BufferedImage in the RGB color space.
     */
    @SuppressWarnings("unused")
    public static BufferedImage createRGBImageFromInvertedYCCK(Raster ycckRaster, ICC_Profile cmykProfile) {
        BufferedImage image;
        if (cmykProfile != null) {
            ycckRaster = convertInvertedYCCKToCMYK(ycckRaster);
            image = createRGBImageFromCMYK(ycckRaster, cmykProfile);
        } else {
            int w = ycckRaster.getWidth(), h = ycckRaster.getHeight();
            int[] rgb = new int[w * h];
            //PixelInterleavedSampleModel pix;
            // if (Adobe_APP14 and transform==2) then YCCK else CMYK
            int[] Y = ycckRaster.getSamples(0, 0, w, h, 0, (int[]) null);
            int[] Cb = ycckRaster.getSamples(0, 0, w, h, 1, (int[]) null);
            int[] Cr = ycckRaster.getSamples(0, 0, w, h, 2, (int[]) null);
            int[] K = ycckRaster.getSamples(0, 0, w, h, 3, (int[]) null);
            float vr, vg, vb;
            for (int i = 0, imax = Y.length; i < imax; i++) {
                float k = 255 - K[i], y = 255 - Y[i], cb = 255 - Cb[i], cr = 255 - Cr[i];
                vr = y + 1.402f * (cr - 128) - k;
                vg = y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128) - k;
                vb = y + 1.772f * (cb - 128) - k;
                rgb[i] = (0xff & (vr < 0.0f ? 0 : vr > 255.0f ? 0xff : (int) (vr + 0.5f))) << 16
                        | (0xff & (vg < 0.0f ? 0 : vg > 255.0f ? 0xff : (int) (vg + 0.5f))) << 8
                        | (0xff & (vb < 0.0f ? 0 : vb > 255.0f ? 0xff : (int) (vb + 0.5f)));
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
                rgb[i] = (255 - Math.min(255, C[i] + k)) << 16
                        | (255 - Math.min(255, M[i] + k)) << 8
                        | (255 - Math.min(255, Y[i] + k));
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
     * Creates a buffered image from a raster in the RGBA color space, converting
     * the colors to RGB using the provided CMYK ICC_Profile.
     * <p>
     * As seen from a comment made by 'phelps' at
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4799903
     *
     * @param rgbaRaster A raster with (at least) 4 bands of samples.
     * @return a BufferedImage in the RGB color space.
     */
    private static BufferedImage createRGBAImageFromRGBA(Raster rgbaRaster) {
        BufferedImage image;
        int w = rgbaRaster.getWidth();
        int h = rgbaRaster.getHeight();
        // ICC_Profile currently not supported
        //        if (rgbaProfile != null) {
//            ColorSpace rgbaCS = new ICC_ColorSpace(rgbaProfile);
//            image = new BufferedImage(w, h,
//                    BufferedImage.TYPE_INT_RGB);
//            WritableRaster rgbRaster = image.getRaster();
//            ColorSpace rgbCS = image.getColorModel().getColorSpace();
//            ColorConvertOp cmykToRgb = new ColorConvertOp(rgbaCS, rgbCS, null);
//            cmykToRgb.filter(rgbaRaster, rgbRaster);
//        } else {
        {
            int[] rgb = new int[w * h];
            int[] R = rgbaRaster.getSamples(0, 0, w, h, 0, (int[]) null);
            int[] G = rgbaRaster.getSamples(0, 0, w, h, 1, (int[]) null);
            int[] B = rgbaRaster.getSamples(0, 0, w, h, 2, (int[]) null);
            int[] A = rgbaRaster.getSamples(0, 0, w, h, 3, (int[]) null);
            for (int i = 0, imax = R.length; i < imax; i++) {
                rgb[i] = A[i] << 24 | R[i] << 16 | G[i] << 8 | B[i];
            }
            WritableRaster rgbRaster = Raster.createPackedRaster(
                    new DataBufferInt(rgb, rgb.length),
                    w, h, w, new int[]{0xff0000, 0xff00, 0xff, 0xff000000}, null);
            ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
            ColorModel cm = new DirectColorModel(cs, 32, 0xff0000, 0xff00, 0xff, 0x0ff000000, false, DataBuffer.TYPE_INT);
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
                    (Cb_g_tab[cb] + Cr_g_tab[cr]
                            >> SCALEBITS));
            cmykY = MAXJSAMPLE - (y + Cb_b_tab[cb]);    // blue
            /* K passes through unchanged */
            cmyk[i] = (cmykC < 0 ? 0 : Math.min(cmykC, 255)) << 24
                    | (cmykM < 0 ? 0 : Math.min(cmykM, 255)) << 16
                    | (cmykY < 0 ? 0 : Math.min(cmykY, 255)) << 8
                    | 255 - ycckK[i];
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
                    (Cb_g_tab[cb] + Cr_g_tab[cr]
                            >> SCALEBITS));
            cmykY = MAXJSAMPLE - (y + Cb_b_tab[cb]);    // blue
            /* K passes through unchanged */
            cmyk[i] = (cmykC < 0 ? 0 : Math.min(cmykC, 255)) << 24
                    | (cmykM < 0 ? 0 : Math.min(cmykM, 255)) << 16
                    | (cmykY < 0 ? 0 : Math.min(cmykY, 255)) << 8
                    | ycckK[i];
        }
        return Raster.createPackedRaster(
                new DataBufferInt(cmyk, cmyk.length),
                w, h, w, new int[]{0xff000000, 0xff0000, 0xff00, 0xff}, null);
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
    private static BufferedImage readImageFromYUVorGray(ImageInputStream in) throws IOException {
        ImageReader r = createNativeJPEGReader();
        r.setInput(in);
        return r.read(0);
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        readHeader();
        return image.getWidth();
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        readHeader();
        return image.getHeight();
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        readHeader();
        LinkedList<ImageTypeSpecifier> l = new LinkedList<>();
        l.add(new ImageTypeSpecifier(RGB, RGB.createCompatibleSampleModel(image.getWidth(), image.getHeight())));
        return l.iterator();
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        if (imageIndex > 0) {
            throw new IndexOutOfBoundsException();
        }
        readHeader();
        return image;
    }

    /**
     * Reads the PGM header.
     * Does nothing if the header has already been loaded.
     */
    private void readHeader() throws IOException {
        if (image == null) {
            ImageInputStream iis;
            Object in = getInput();
            /* No need for JMF support in CMYKJPEGImageReader.
            if (in instanceof Buffer) {
            in = ((Buffer) in).getData();
            }*/
            if (in instanceof byte[]) {
                iis = ImageIO.createImageInputStream(in);
            } else if (in instanceof ImageInputStream) {
                iis = (ImageInputStream) in;
            } else if (in instanceof InputStream) {
                iis = new MemoryCacheImageInputStream((InputStream) in);
            } else {
                throw new IOException("Can't handle input of type " + in);
            }
            boolean isYCCKInversed = true;
            image = read(iis, isYCCKInversed);
        }
    }
}
