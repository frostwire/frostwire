/*
 * @(#)JFIFInputStream.java  1.3  2011-01-28
 *
 * Copyright (c) 2008-2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package com.frostwire.jpeg;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

/**
 * JFIFInputStream.
 * <p>
 * This InputStream uses two special marker values which do not exist
 * in the JFIF stream:
 * <ul>
 * <li><b>-1</b>: marks junk data at the beginning of the file.</li>
 * <li><b>0</b>: marks entropy encoded image data.</li>
 * </ul>
 * <p>
 * The junk data at the beginning of the file can be accessed by calling the
 * read-methods immediately after opening the stream. Call nextSegment()
 * immediately after opening the stream if you are not interested into this
 * junk data.
 * <p>
 * Junk data at the end of the file is delivered as part of the EOI_MARKER segment.
 * Finish reading after encountering the EOI_MARKER segment if you are not interested
 * in this junk data.
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
 * @author Werner Randelshofer, Hausmatt 10, CH-6405 Immensee
 * @version 1.3 2011-01-28 Adds SOF markers.
 * <br>1.2 2011-01-23 Adds method skipFully.
 * <br>1.1.1 2010-07-21 Gracefully handle illegal markers outside of
 * SOI_MARKER and EOI_MARKER.
 * <br>1.1 2009-12-26 Added method getSegment().
 * <br>1.0 2008-10-14 Created.
 */
class JFIFInputStream extends FilterInputStream {
    /**
     * JUNK_MARKER Marker (for data which is not part of the JFIF stream.
     */
    private final static int JUNK_MARKER = -1;
    /**
     * Start of image
     */
    private final static int SOI_MARKER = 0xffd8;
    /**
     * End of image
     */
    private final static int EOI_MARKER = 0xffd9;
    /**
     * Temporary private use in arithmetic coding
     */
    private final static int TEM_MARKER = 0xff01;
    /**
     * Start of scan
     */
    private final static int SOS_MARKER = 0xffda;
    /**
     * Reserved for JPEG extensions
     */
    private final static int JPG0_MARKER = 0xfff0;
    private final static int JPG1_MARKER = 0xfff1;
    private final static int JPG2_MARKER = 0xfff2;
    private final static int JPG3_MARKER = 0xfff3;
    private final static int JPG4_MARKER = 0xfff4;
    private final static int JPG5_MARKER = 0xfff5;
    private final static int JPG6_MARKER = 0xfff6;
    private final static int JPG7_MARKER = 0xfff7;
    private final static int JPG8_MARKER = 0xfff8;
    private final static int JPG9_MARKER = 0xfff9;
    private final static int JPGA_MARKER = 0xfffA;
    private final static int JPGB_MARKER = 0xfffB;
    private final static int JPGC_MARKER = 0xfffC;
    private final static int JPGD_MARKER = 0xfffD;
    // Restart markers
    private final static int RST0_MARKER = 0xffd0;
    private final static int RST7_MARKER = 0xffd7;
    /**
     * This hash set holds the Id's of markers which stand alone,
     * respectively do no have a data segment.
     */
    private final HashSet<Integer> standaloneMarkers = new HashSet<>();
    /**
     * This hash set holds the Id's of markers which have a data
     * segment followed by a entropy-coded data segment.
     */
    private final HashSet<Integer> doubleSegMarkers = new HashSet<>();
    private Segment segment;
    /**
     * This variable is set to true, if a 0xff byte has been found in
     * entropy-code data.
     */
    private boolean markerFound;
    private int marker = JUNK_MARKER;
    private long offset = 0;
    private boolean isStuffed0xff = false;
    JFIFInputStream(InputStream in) {
        super(in);
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
        standaloneMarkers.add(0xffff); // Illegal marker
        doubleSegMarkers.add(SOS_MARKER); // SOS_MARKER Start of Scan
        // Start with a dummy entropy-coded data segment.
        segment = new Segment(-1, 0, -1);
    }

    /**
     * Gets the next segment from the input stream.
     *
     * @return The next segment. Returns null, if we encountered
     * the end of the stream.
     */
    Segment getNextSegment() throws IOException {
        // If we are inside of a marker segment, skip the
        // marker
        if (!segment.isEntropyCoded()) {
            markerFound = false;
            do {
                long skipped = in.skip(segment.length - offset + segment.offset);
                if (skipped == -1) {
                    segment = new Segment(0, offset, -1);
                    return null;
                }
                offset += skipped;
            } while (offset < segment.length + segment.offset);
            if (doubleSegMarkers.contains(segment.marker)) {
                segment = new Segment(0, offset, -1);
                return segment;
            }
        }
        // Scan the input stream for the next marker.
        while (!markerFound) {
            while (true) {
                int b;
                if (isStuffed0xff) {
                    b = 0xff;
                    isStuffed0xff = false;
                } else {
                    b = read0();
                }
                if (b == -1) {
                    return null;
                }
                if (b == 0xff) {
                    markerFound = true;
                    break;
                }
            }
            int b = read0();
            if (b == -1) {
                return null;
            }
            if (b == 0x00) {
                markerFound = false;
            } else if (b == 0xff) {
                isStuffed0xff = true;
                markerFound = false;
            } else {
                marker = 0xff00 | b;
            }
        }
        markerFound = false;
        /*
        if (marker <= 0xff00 || marker >= 0xffff) {
        throw new IOException("JFIFInputStream found illegal marker " + Integer.toHexString(marker) + " at offset " + offset + " 0x"+Long.toHexString(offset)+".");
        }*/
        // Note: 0xffff is an illegal marker segment, we process it here
        // for robustness.
        if (standaloneMarkers.contains(marker)) {
            segment = new Segment(0xff00 | marker, offset, -1);
        } else {
            int length = (read0() << 8) | read0();
            if (length < 2 || length >= 0xffff) {
                throw new IOException("JFIFInputStream found illegal segment length " + length + " after marker " + Integer.toHexString(marker) + " at offset " + offset + ".");
            }
            segment = new Segment(0xff00 | marker, offset, length - 2);
        }
        return segment;
    }

    private int read0() throws IOException {
        int b = in.read();
        if (b != -1) {
            offset++;
        }
        return b;
    }

    /**
     * Reads the next byte of data from this input stream. The value
     * byte is returned as an <code>int</code> in the range
     * <code>0</code> to <code>255</code>. If no byte is available
     * because the end of the stream has been reached, the value
     * <code>-1</code> is returned. This method blocks until input data
     * is available, the end of the stream is detected, or an exception
     * is thrown.
     * <p>
     * This method
     * simply performs <code>in.read()</code> and returns the result.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the
     * stream is reached.
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    @Override
    public int read() throws IOException {
        if (markerFound) {
            return -1;
        }
        int b;
        if (isStuffed0xff) {
            isStuffed0xff = false;
            b = 0xff;
        } else {
            b = read0();
        }
        if (segment.isEntropyCoded()) {
            if (b == 0xff) {
                b = read0();
                if (b == 0x00) {
                    // found a stuffed 0xff byte
                    return 0xff;
                } else if (b == 0xff) {
                    // found an invalid sequence of two 0xff bytes
                    isStuffed0xff = true;
                    return 0xff;
                }
                markerFound = true;
                marker = 0xff00 | b;
                return -1;
            }
        }
        return b;
    }

    /**
     * Reads up to <code>len</code> b of data from this input stream
     * into an array of b. This method blocks until some input is
     * available.
     * <p>
     * This method simply performs <code>in.read(b, off, len)</code>
     * and returns the result.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset of the data.
     * @param len the maximum number of b read.
     * @return the total number of b read into the buffer, or
     * <code>-1</code> if there is no more data because the end of
     * the stream has been reached.
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (markerFound) {
            return -1;
        }
        int count = 0;
        if (segment.isEntropyCoded()) {
            for (; count < len; count++) {
                int data = read();
                if (data == -1) {
                    if (count == 0) return -1;
                    break;
                }
                b[off + count] = (byte) data;
            }
        } else {
            long available = segment.length - offset + segment.offset;
            if (available <= 0) {
                return -1;
            }
            if (available < len) {
                len = (int) available;
            }
            count = in.read(b, off, len);
            if (count != -1) {
                offset += count;
            }
        }
        return count;
    }

    /**
     * Skips over and discards <code>n</code> b of data from the
     * input stream. The <code>skip</code> method may, for a variety of
     * reasons, end up skipping over some smaller number of b,
     * possibly <code>0</code>. The actual number of b skipped is
     * returned.
     * <p>
     * This method
     * simply performs <code>in.skip(n)</code>.
     *
     * @param n the number of b to be skipped.
     * @return the actual number of b skipped.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public long skip(long n) throws IOException {
        if (markerFound) {
            return -1;
        }
        long count = 0;
        if (segment.isEntropyCoded()) {
            for (; count < n; count++) {
                int data = read();
                if (data == -1) {
                    break;
                }
            }
        } else {
            long available = segment.length - offset + segment.offset;
            if (available < n) {
                n = (int) available;
            }
            count = in.skip(n);
            if (count != -1) {
                offset += count;
            }
        }
        return count;
    }

    /**
     * Marks the current position in this input stream. A subsequent
     * call to the <code>reset</code> method repositions this stream at
     * the last marked position so that subsequent reads re-read the same b.
     * <p>
     * The <code>readlimit</code> argument tells this input stream to
     * allow that many b to be read before the mark position gets
     * invalidated.
     * <p>
     * This method simply performs <code>in.mark(readlimit)</code>.
     *
     * @param readlimit the maximum limit of b that can be read before
     *                  the mark position becomes invalid.
     * @see java.io.FilterInputStream#in
     * @see java.io.FilterInputStream#reset()
     */
    @Override
    public synchronized void mark(int readlimit) {
        // do nothing, since we don't support marking
    }

    /**
     * Repositions this stream to the position at the time the
     * <code>mark</code> method was last called on this input stream.
     * <p>
     * This method
     * simply performs <code>in.reset()</code>.
     * <p>
     * Stream marks are intended to be used in
     * situations where you need to read ahead a little to see what's in
     * the stream. Often this is most easily done by invoking some
     * general parser. If the stream is of the type handled by the
     * parse, it just chugs along happily. If the stream is not of
     * that type, the parser should toss an exception when it fails.
     * If this happens within readlimit b, it allows the outer
     * code to reset the stream and try another parser.
     *
     * @throws IOException if the stream has not been marked or if the
     *                     mark has been invalidated.
     * @see java.io.FilterInputStream#in
     * @see java.io.FilterInputStream#mark(int)
     */
    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("Reset not supported");
    }

    /**
     * Tests if this input stream supports the <code>mark</code>
     * and <code>reset</code> methods.
     * This method
     * simply performs <code>in.markSupported()</code>.
     *
     * @return <code>true</code> if this stream type supports the
     * <code>mark</code> and <code>reset</code> method;
     * <code>false</code> otherwise.
     * @see java.io.FilterInputStream#in
     * @see java.io.InputStream#mark(int)
     * @see java.io.InputStream#reset()
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Represents a segment within a JFIF File.
     */
    public static class Segment {
        /**
         * Holds the offset of the first data byte to the beginning
         * of the stream.
         */
        final long offset;
        /**
         * If the marker starts a marker segment, holds the length
         * of the data in the data segment.
         * If the marker starts a entropy-coded data segment, holds
         * the value -1.
         */
        public final int length;
        /**
         * Holds the marker code.
         * A marker is an unsigned short between 0xff01 and 0xfffe.
         */
        final int marker;

        Segment(int marker, long offset, int length) {
            this.marker = marker;
            this.offset = offset;
            this.length = length;
        }

        boolean isEntropyCoded() {
            return length == -1;
        }

        @Override
        public String toString() {
            return "Segment marker=0x" + Integer.toHexString(marker) + " offset=" + offset + "=0x" + Long.toHexString(offset);
        }
    }
}
