package com.frostwire.search.youtube.jd;

import java.io.ByteArrayOutputStream;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedList;

public class ReusableByteArrayOutputStreamPool {
    public static class ReusableByteArrayOutputStream extends ByteArrayOutputStream {

        protected ReusableByteArrayOutputStream(final int size) {
            super(size);
        }

        public int bufferSize() {
            return this.buf.length;
        }

        public synchronized int free() {
            return this.buf.length - this.count;
        }

        public byte[] getInternalBuffer() {
            return this.buf;
        }

        public synchronized void increaseUsed(final int increase) {
            this.count = this.count + increase;
        }

        public synchronized void setUsed(final int used) {
            this.count = used;
        }

    }

    private static final LinkedList<SoftReference<ReusableByteArrayOutputStream>> pool = new LinkedList<SoftReference<ReusableByteArrayOutputStream>>();

    public static ReusableByteArrayOutputStream getReusableByteArrayOutputStream() {
        return ReusableByteArrayOutputStreamPool.getReusableByteArrayOutputStream(32);
    }

    public static ReusableByteArrayOutputStream getReusableByteArrayOutputStream(final int wishedMinimumSize) {
        return ReusableByteArrayOutputStreamPool.getReusableByteArrayOutputStream(wishedMinimumSize, true);
    }

    public static ReusableByteArrayOutputStream getReusableByteArrayOutputStream(final int wishedMinimumSize, final boolean allowSmaller) {
        final int wished = Math.max(32, wishedMinimumSize);
        synchronized (ReusableByteArrayOutputStreamPool.pool) {
            ReusableByteArrayOutputStream ret = null;
            ReusableByteArrayOutputStream best = null;
            if (!ReusableByteArrayOutputStreamPool.pool.isEmpty()) {
                Iterator<SoftReference<ReusableByteArrayOutputStream>> it = ReusableByteArrayOutputStreamPool.pool.iterator();
                while (it.hasNext()) {
                    final SoftReference<ReusableByteArrayOutputStream> next = it.next();
                    ret = next.get();
                    if (ret == null) {
                        /* buffer already gced, remove it from pool */
                        it.remove();
                    } else {
                        if (ret.bufferSize() >= wishedMinimumSize) {
                            /*
                             * hit with >= desired Size, remove it from pool and
                             * return it
                             */
                            it.remove();
                            return ret;
                        } else if (best == null) {
                            /* first best hit */
                            best = ret;
                        } else if (ret.bufferSize() > best.bufferSize()) {
                            /* a better hit */
                            best = ret;
                        }
                    }
                }
                if (best != null && allowSmaller) {
                    /* return best hit from previous search */
                    it = ReusableByteArrayOutputStreamPool.pool.iterator();
                    while (it.hasNext()) {
                        final SoftReference<ReusableByteArrayOutputStream> next = it.next();
                        ret = next.get();
                        if (ret == null) {
                            /* buffer already gced, remove it from pool */
                            it.remove();
                        } else if (ret == best) {
                            /* we reuse best hit */
                            it.remove();
                            return ret;
                        }
                    }
                }
            }
            /* create new buffer */
            return new ReusableByteArrayOutputStream(wished);

        }
    }

    public static void reuseReusableByteArrayOutputStream(final ReusableByteArrayOutputStream buf) {
        if (buf == null) { return; }
        synchronized (ReusableByteArrayOutputStreamPool.pool) {
            /* TODO: this cannot work!, fix it by using iterator and compare */
            if (ReusableByteArrayOutputStreamPool.pool.contains(buf)) { return; }
            ReusableByteArrayOutputStreamPool.pool.add(new SoftReference<ReusableByteArrayOutputStream>(buf));
            buf.reset();
        }
    }
}
