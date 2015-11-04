/*
 * @(#)JFIFOutputStream.java  1.0  2011-02-27
 * 
 * Copyright (c) 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * 
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package com.frostwire.jpeg;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Stack;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * {@code JFIFOutputStream}.
 *
 * This OutputStream supports writing of a JFIF stream.
 *
 * <p>
 * References:<br>
 * JPEG File Interchange Format Version 1.02<br>
 * <a href="http://www.jpeg.org/public/jfif.pdf">http://www.jpeg.org/public/jfif.pdf</a>
 * <p>
 *   Pennebaker, W., Mitchell, J. (1993).<br>
 *   JPEG Still Image Data Compression Standard.<br>
 *   Chapmann & Hall, New York.<br>
 *   ISBN 0-442-01272-1<br>
 *
 * @author Werner Randelshofer
 * @version 1.0 2011-02-27 Created.
 */
public class JFIFOutputStream extends OutputStream {

    /**
     * This hash set holds the Id's of markers which stand alone,
     * respectively do no have a data segment.
     */
    private final HashSet<Integer> standaloneMarkers = new HashSet<Integer>();
    /**
     * This hash set holds the Id's of markers which have a data
     * segment followed by a entropy-coded data segment.
     */
    private final HashSet<Integer> doubleSegMarkers = new HashSet<Integer>();
    /** Start of image */
    public final static int SOI_MARKER = 0xffd8;
    /** End of image */
    public final static int EOI_MARKER = 0xffd9;
    /** Temporary private use in arithmetic coding */
    public final static int TEM_MARKER = 0xff01;
    /** Start of scan */
    public final static int SOS_MARKER = 0xffda;
    /** APP1_MARKER Reserved for application use */
    public final static int APP1_MARKER = 0xffe1;
    /** APP2_MARKER Reserved for application use */
    public final static int APP2_MARKER = 0xffe2;
    /** Reserved for JPEG extensions */
    public final static int JPG0_MARKER = 0xfff0;
    public final static int JPG1_MARKER = 0xfff1;
    public final static int JPG2_MARKER = 0xfff2;
    public final static int JPG3_MARKER = 0xfff3;
    public final static int JPG4_MARKER = 0xfff4;
    public final static int JPG5_MARKER = 0xfff5;
    public final static int JPG6_MARKER = 0xfff6;
    public final static int JPG7_MARKER = 0xfff7;
    public final static int JPG8_MARKER = 0xfff8;
    public final static int JPG9_MARKER = 0xfff9;
    public final static int JPGA_MARKER = 0xfffA;
    public final static int JPGB_MARKER = 0xfffB;
    public final static int JPGC_MARKER = 0xfffC;
    public final static int JPGD_MARKER = 0xfffD;
    /** Start of frame markers */
    public final static int SOF0_MARKER = 0xffc0;//nondifferential Huffman-coding frames with baseline DCT.
    public final static int SOF1_MARKER = 0xffc1;//nondifferential Huffman-coding frames with extended sequential DCT.
    public final static int SOF2_MARKER = 0xffc2;//nondifferential Huffman-coding frames with progressive DCT.
    public final static int SOF3_MARKER = 0xffc3;//nondifferential Huffman-coding frames with lossless (sequential) data.
    //public final static int SOF4_MARKER = 0xffc4;//
    public final static int SOF5_MARKER = 0xffc5;//differential Huffman-coding frames with differential sequential DCT.
    public final static int SOF6_MARKER = 0xffc6;//differential Huffman-coding frames with differential progressive DCT.
    public final static int SOF7_MARKER = 0xffc7;//differential Huffman-coding frames with differential lossless data.
    //public final static int SOF8_MARKER = 0xffc8;//
    public final static int SOF9_MARKER = 0xffc9;//nondifferential Arithmetic-coding frames with extended sequential DCT.
    public final static int SOFA_MARKER = 0xffcA;//nondifferential Arithmetic-coding frames with progressive DCT.
    public final static int SOFB_MARKER = 0xffcB;//nondifferential Arithmetic-coding frames with lossless (sequential) data.
    //public final static int SOFC_MARKER = 0xffcC;//
    public final static int SOFD_MARKER = 0xffcD;//differential Arithmetic-coding frames with differential sequential DCT.
    public final static int SOFE_MARKER = 0xffcE;//differential Arithmetic-coding frames with differential progressive DCT.
    public final static int SOFF_MARKER = 0xffcF;//differential Arithmetic-coding frames with differential lossless DCT.
    // Restart markers
    public final static int RST0_MARKER = 0xffd0;
    public final static int RST1_MARKER = 0xffd1;
    public final static int RST2_MARKER = 0xffd2;
    public final static int RST3_MARKER = 0xffd3;
    public final static int RST4_MARKER = 0xffd4;
    public final static int RST5_MARKER = 0xffd5;
    public final static int RST6_MARKER = 0xffd6;
    public final static int RST7_MARKER = 0xffd7;
    private ImageOutputStream out;
    private long streamOffset;
    private Stack<Segment> stack = new Stack<Segment>();

    public JFIFOutputStream(ImageOutputStream out) throws IOException {
        this.out = out;
        out.setByteOrder(ByteOrder.BIG_ENDIAN);
        streamOffset = out.getStreamPosition();

        for (int i = RST0_MARKER; i <= RST7_MARKER; i++) {
            standaloneMarkers.add(i); // RST(i) Restart interval termination
        }
        standaloneMarkers.add(SOI_MARKER); // SOI_MARKER Start of image
        standaloneMarkers.add(EOI_MARKER); // EOI_MARKER End of image
        standaloneMarkers.add(TEM_MARKER); // TEM_MARKER Temporary private use in arithmetic coding
        standaloneMarkers.add(JPG0_MARKER); // JPEG Extensions
        standaloneMarkers.add(JPG1_MARKER);
        standaloneMarkers.add(JPG2_MARKER);
        standaloneMarkers.add(JPG3_MARKER);
        standaloneMarkers.add(JPG4_MARKER);
        standaloneMarkers.add(JPG5_MARKER);
        standaloneMarkers.add(JPG6_MARKER);
        standaloneMarkers.add(JPG7_MARKER);
        standaloneMarkers.add(JPG8_MARKER);
        standaloneMarkers.add(JPG9_MARKER);
        standaloneMarkers.add(JPGA_MARKER);
        standaloneMarkers.add(JPGB_MARKER);
        standaloneMarkers.add(JPGC_MARKER);
        standaloneMarkers.add(JPGD_MARKER);

        // JFIFInputStream returns segments with marker 0 for the data
        // which follows the SOS segment.
        standaloneMarkers.add(0);

        doubleSegMarkers.add(SOS_MARKER); // SOS_MARKER Start of Scan

    }

    public JFIFOutputStream(File imgFile) throws IOException {
        this(new FileImageOutputStream(imgFile));
    }

    /** Gets the position relative to the beginning of the IFF output stream.
     * <p>
     * Usually this value is equal to the stream position of the underlying
     * ImageOutputStream, but can be larger if the underlying stream already
     * contained data.
     *
     * @return The relative stream position.
     * @throws IOException
     */
    public long getStreamPosition() throws IOException {
        return out.getStreamPosition() - streamOffset;
    }

    /** Seeks relative to the beginning of the IFF output stream.
     * <p>
     * Usually this equal to seeking in the underlying ImageOutputStream, but
     * can be different if the underlying stream already contained data.
     *
     */
    public void seek(long newPosition) throws IOException {
        out.seek(newPosition + streamOffset);
    }

    public void pushSegment(int marker) throws IOException {
        stack.push(new Segment(marker));
    }

    public void popSegment() throws IOException {
        Segment seg = stack.pop();
        seg.finish();
    }

    /** Returns the offset of the current segment or -1 if none has been pushed. */
    public long getSegmentOffset() throws IOException {
        if (stack.peek() == null) {
            return -1;
        } else {
            return stack.peek().offset;
        }
    }

    /** Returns the length of the current segment or -1 if none has been pushed. */
    public long getSegmentLength() throws IOException {
        if (stack.peek() == null) {
            return -1;
        } else {
            return getStreamPosition() - stack.peek().offset - 2;
        }
    }

    public void finish() throws IOException {
        while (!stack.empty()) {
            popSegment();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            finish();
        } finally {
            out.close();
        }
    }

    /** Writes stuffed or non-stuffed bytes to the underlying output stream.
     * Bytes are stuffed, if the stream is not currently in a segment.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (stack.size() == 0 || standaloneMarkers.contains(stack.peek().marker)) {
            writeStuffed(b, off, len);
        } else {
            writeNonstuffed(b, off, len);
        }
    }

    /** Writes a stuffed or non-stuffed byte to the underlying output stream.
     * Bytes are stuffed, if the stream is not currently in a segment.
     */
    @Override
    public void write(int b) throws IOException {
        if (stack.size() == 0 || standaloneMarkers.contains(stack.peek().marker)) {
            writeStuffed(b);
        } else {
            writeNonstuffed(b);
        }
    }

    /** Writes non-stuffed bytes to the underlying output stream.
     */
    private void writeNonstuffed(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    /** Writes non-stuffed byte to the underlying output stream.
     * Bytes should be stuffed, if the stream is not currently in a segment.
     */
    private void writeNonstuffed(int b) throws IOException {
        out.write(b);
    }

    /** Writes stuffed bytes to the underlying output stream.
     */
    private void writeStuffed(byte[] b, int off, int len) throws IOException {
        int n = off + len;
        for (int i = off; i < n; i++) {
            if (b[i] == -1) {
                out.write(b, off, i - off + 1);
                out.write(0);
                off = i + 1;
            }
        }
        if (n - off > 0) {
            out.write(b, off, n - off);
        }
    }

    /** Writes stuffed byte to the underlying output stream.
     * Bytes should be stuffed, if the stream is not currently in a segment.
     */
    private void writeStuffed(int b) throws IOException {
        out.write(0xff);
        if (b == 0xff) {
            out.write(0);
        }
    }

    /**
     * Segment base class.
     */
    private class Segment {

        /**
         * The marker of the segment.
         */
        protected int marker;
        /**
         * The offset of the segment relative to the start of the
         * ImageOutputStream.
         */
        protected long offset;
        protected boolean finished;

        /**
         * Creates a new Chunk at the current position of the ImageOutputStream.
         * @param chunkType The chunkType of the chunk. A string with a length of 4 characters.
         */
        public Segment(int marker) throws IOException {
            this.marker = marker;
            if (marker != 0) {
                out.writeShort(marker);
                offset = getStreamPosition();
                if (!standaloneMarkers.contains(marker)) {
                    out.writeShort(0); // make room for the size field
                }
            }
        }

        /**
         * Writes the segment to the ImageOutputStream and disposes it.
         */
        public void finish() throws IOException {
            if (!finished) {
                if (!standaloneMarkers.contains(marker)) {
                    long size = getStreamPosition() - offset;
                    if (size > 0xffffL) {
                        throw new IOException("Segment \"" + marker + "\" is too large: " + size);
                    }

                    long pointer = getStreamPosition();
                    seek(offset);
                    out.writeShort((short) size);

                    seek(pointer);
                }

                finished = true;
            }
        }
    }
}
