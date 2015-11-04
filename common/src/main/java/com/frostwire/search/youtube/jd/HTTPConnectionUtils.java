package com.frostwire.search.youtube.jd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class HTTPConnectionUtils {

    private static byte R = (byte) 13;
    private static byte N = (byte) 10;

    public static ByteBuffer readheader(final InputStream in, final boolean readSingleLine) throws IOException {
        ByteBuffer bigbuffer = ByteBuffer.wrap(new byte[4096]);
        final byte[] minibuffer = new byte[1];
        int position;
        int c;
        boolean complete = false;
        while ((c = in.read(minibuffer)) >= 0) {
            if (bigbuffer.remaining() < 1) {
                final ByteBuffer newbuffer = ByteBuffer.wrap(new byte[bigbuffer.capacity() * 2]);
                bigbuffer.flip();
                newbuffer.put(bigbuffer);
                bigbuffer = newbuffer;
            }
            if (c > 0) {
                bigbuffer.put(minibuffer);
            }
            if (readSingleLine) {
                if (bigbuffer.position() >= 1) {
                    /*
                     * \n only line termination, for fucking buggy non rfc
                     * servers
                     */
                    position = bigbuffer.position();
                    if (bigbuffer.get(position - 1) == HTTPConnectionUtils.N) {
                        break;
                    }
                }
                if (bigbuffer.position() >= 2) {
                    /* \r\n, correct line termination */
                    position = bigbuffer.position();
                    if (bigbuffer.get(position - 2) == HTTPConnectionUtils.R && bigbuffer.get(position - 1) == HTTPConnectionUtils.N) {
                        break;
                    }
                }
            } else {
                if (bigbuffer.position() >= 4) {
                    /* RNRN for header<->content divider */
                    position = bigbuffer.position();
                    complete = bigbuffer.get(position - 4) == HTTPConnectionUtils.R;
                    complete &= bigbuffer.get(position - 3) == HTTPConnectionUtils.N;
                    complete &= bigbuffer.get(position - 2) == HTTPConnectionUtils.R;
                    complete &= bigbuffer.get(position - 1) == HTTPConnectionUtils.N;
                    if (complete) {
                        break;
                    }
                }
            }
        }
        bigbuffer.flip();
        return bigbuffer;
    }
}
