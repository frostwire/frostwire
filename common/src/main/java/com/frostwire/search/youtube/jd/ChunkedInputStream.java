/**
 * Copyright (c) 2009 - 2011 AppWork UG(haftungsbeschränkt) <e-mail@appwork.org>
 * 
 * This file is part of org.appwork.utils.net
 * 
 * This software is licensed under the Artistic License 2.0,
 * see the LICENSE file or http://www.opensource.org/licenses/artistic-license-2.0.php
 * for details
 */
package com.frostwire.search.youtube.jd;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author daniel, ChunkedInputStream, see rfc2616#section-3.6
 * 
 */
public class ChunkedInputStream extends InputStream {

    private final InputStream is;
    private int               nextChunkSize = 0;
    private int               nextChunkLeft = 0;
    private long              completeSize  = 0;

    public ChunkedInputStream(final InputStream is) {
        this.is = is;
    }

    @Override
    public int available() throws IOException {
        if (this.nextChunkLeft > 0) { return this.nextChunkLeft; }
        return this.is.available();
    }

    /**
     * returns available bytes in current Chunk or reads next Chunk and parses
     * it
     * 
     * @return
     * @throws IOException
     */
    private int availableChunkData() throws IOException {
        if (this.nextChunkLeft == -1) { return -1; }
        if (this.nextChunkLeft > 0) { return this.nextChunkLeft; }
        final StringBuilder sb = new StringBuilder();
        boolean chunkExt = false;
        final byte[] b = { (byte) 0x00 };
        int read = 0;
        if (this.nextChunkSize > 0) {
            /* finish LF/CRLF from previous chunk */
            read = this.is.read();
            if (read == 13) {
                read = this.is.read();
            }
        }
        read = this.is.read();
        while (read > -1 && read != 10 && read != 13) {
            if (read == 59) {
                /* ignore chunkExtensions */
                // System.out.println("chunkedExtension found");
                chunkExt = true;
            }
            if (chunkExt == false) {
                b[0] = (byte) (read & 0xFF);
                sb.append(new String(b, 0, 1));
            }
            read = this.is.read();
        }
        if (read == -1 && sb.length() == 0) { return -1; }
        if (read == 13) {
            /* finish CRLF here */
            read = this.is.read();
        }
        this.nextChunkSize = 0;
        if (sb.length() > 0) {
            this.nextChunkSize = Integer.parseInt(sb.toString().trim(), 16);
        }
        if (this.nextChunkSize == 0) {
            // System.out.println("lastChunk");
            this.nextChunkLeft = -1;
            this.readTrailers();
        } else {
            // System.out.println("nextchunkSize: " + this.nextChunkSize);
            this.completeSize += this.nextChunkSize;
            this.nextChunkLeft = this.nextChunkSize;
        }
        return this.nextChunkLeft;
    }

    @Override
    public void close() throws IOException {
        this.is.close();
    }

    /**
     * Exhaust an input stream, reading until EOF has been encountered.
     * 
     * @throws IOException
     */
    private void exhaustInputStream() throws IOException {
        while (this.is.read() > 0) {
        }
    }

    /**
     * @return the completeSize
     */
    public long getCompleteSize() {
        return this.completeSize;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() throws IOException {
        final int left = this.availableChunkData();
        if (left > 0) {
            final int ret = this.is.read();
            if (ret != -1) {
                this.nextChunkLeft--;
                return ret;
            }
            throw new IOException("premature EOF");
        }
        return -1;
    }

    @Override
    public int read(final byte b[], final int off, final int len) throws IOException {
        final int left = this.availableChunkData();
        if (left > 0) {
            final int ret = this.is.read(b, off, Math.min(left, len));
            if (ret != -1) {
                this.nextChunkLeft -= ret;
                return ret;
            }
            throw new IOException("premature EOF");
        }
        return -1;
    }

    /**
     * TODO: read the Trailers we read until EOF at the moment;
     * 
     * @throws IOException
     */
    private void readTrailers() throws IOException {
        this.exhaustInputStream();
    }

}
