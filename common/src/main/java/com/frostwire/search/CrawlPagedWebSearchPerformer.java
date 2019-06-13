/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.search;

import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class CrawlPagedWebSearchPerformer<T extends CrawlableSearchResult> extends PagedWebSearchPerformer {
    protected static final Map<String, Integer> UNIT_TO_BYTES;
    private static final Logger LOG = Logger.getLogger(CrawlPagedWebSearchPerformer.class);
    private static final int DEFAULT_CRAWL_TIMEOUT = 10000; // 10 seconds.
    private static final int FAILED_CRAWL_URL_CACHE_LIFETIME = 600000; // 10 minutes.
    private static final int DEFAULT_MAGNET_DOWNLOAD_TIMEOUT_SECS = 20; // 20 seconds.
    private static CrawlCache cache = null;
    private static MagnetDownloader magnetDownloader = null;

    static {
        UNIT_TO_BYTES = new HashMap<>();
        UNIT_TO_BYTES.put("bytes", 1);
        UNIT_TO_BYTES.put("B", 1);
        UNIT_TO_BYTES.put("KB", 1024);
        UNIT_TO_BYTES.put("MB", 1024 * 1024);
        UNIT_TO_BYTES.put("GB", 1024 * 1024 * 1024);
    }

    private int numCrawls;

    public CrawlPagedWebSearchPerformer(String domainName, long token, String keywords, int timeout, int pages, int numCrawls) {
        super(domainName, token, keywords, timeout, pages);
        this.numCrawls = numCrawls;
    }

    public static void setCache(CrawlCache cache) {
        CrawlPagedWebSearchPerformer.cache = cache;
    }

    public static MagnetDownloader getMagnetDownloader() {
        return magnetDownloader;
    }

    public static void setMagnetDownloader(MagnetDownloader magnetDownloader) {
        CrawlPagedWebSearchPerformer.magnetDownloader = magnetDownloader;
    }

    private static byte[] long2array(long l) {
        byte[] arr = new byte[Long.SIZE / Byte.SIZE];
        longToByteArray(l, 0, arr, 0, arr.length);
        return arr;
    }

    private static long array2long(byte[] arr) {
        return byteArrayToLong(arr, 0, 0, 0, Long.SIZE / Byte.SIZE);
    }

    public static void clearCache() {
        if (cache != null) {
            synchronized (cache) {
                cache.clear();
            }
        }
    }

    public static long getCacheNumEntries() {
        long result = 0;
        if (cache != null) {
            synchronized (cache) {
                result = cache.numEntries();
            }
        }
        return result;
    }

    public static long getCacheSize() {
        long result = 0;
        if (cache != null) {
            synchronized (cache) {
                result = cache.sizeInBytes();
            }
        }
        return result;
    }

    private static long byteArrayToLong(final byte[] src, final int srcPos, final long dstInit, final int dstPos,
                                        final int nBytes) {
        if ((src.length == 0 && srcPos == 0) || 0 == nBytes) {
            return dstInit;
        }
        if ((nBytes - 1) * 8 + dstPos >= 64) {
            throw new IllegalArgumentException("(nBytes-1)*8+dstPos is greater or equal to than 64");
        }
        long out = dstInit;
        int shift;
        for (int i = 0; i < nBytes; i++) {
            shift = i * 8 + dstPos;
            final long bits = (0xffL & src[i + srcPos]) << shift;
            final long mask = 0xffL << shift;
            out = (out & ~mask) | bits;
        }
        return out;
    }

    private static byte[] longToByteArray(final long src, final int srcPos, final byte[] dst, final int dstPos,
                                          final int nBytes) {
        if (0 == nBytes) {
            return dst;
        }
        if ((nBytes - 1) * 8 + srcPos >= 64) {
            throw new IllegalArgumentException("(nBytes-1)*8+srcPos is greater or equal to than 64");
        }
        int shift;
        for (int i = 0; i < nBytes; i++) {
            shift = i * 8 + srcPos;
            dst[dstPos + i] = (byte) (0xff & (src >> shift));
        }
        return dst;
    }

    @Override
    public void crawl(CrawlableSearchResult sr) {
        if (numCrawls > 0) {
            numCrawls--;
            T obj = cast(sr);
            if (obj != null) {
                String url = getCrawlUrl(obj);
                if (url != null) {
                    // this block is an early check for failed in cache, quick return
                    byte[] failed = cacheGet("failed:" + url);
                    if (failed != null) {
                        long failedWhen = array2long(failed);
                        if ((System.currentTimeMillis() - failedWhen) < FAILED_CRAWL_URL_CACHE_LIFETIME) {
                            //if the failed request is still fresh we stop
                            //LOG.info("CrawlPagedWebSearchPerformer::crawl() - hit failed cache url");
                            onResults(Collections.emptyList());
                            return;
                        } else {
                            cacheRemove("failed:" + url);
                        }
                    }
                    byte[] data = cacheGet(url);
                    if (sr instanceof TorrentSearchResult) {
                        String infohash = ((TorrentSearchResult) sr).getHash();
                        if (data == null) {
                            // maybe we've already cached it by infohash (happens quite a bit)
                            data = cacheGet(infohash);
                        } else {
                            cachePut(infohash, data);
                        }
                    }
                    if (data == null) { // not a big deal about synchronization here
                        //LOG.debug("Downloading data for: " + url);
                        if (url.startsWith("magnet")) {
                            data = fetchMagnet(url);
                        } else {
                            data = fetchBytes(url, sr.getDetailsUrl(), DEFAULT_CRAWL_TIMEOUT);
                        }
                        //we put this here optimistically hoping this is actually
                        //valid data. if no data can be crawled from this we remove it
                        //from the cache. we do this because this same data may come
                        //from another search engine and this way we avoid the
                        //expense of performing another download.
                        if (data != null) {
                            cachePut(url, data);
                            if (sr instanceof TorrentSearchResult) {
                                // if the search result has an infohash we can use...
                                String infohash = ((TorrentSearchResult) sr).getHash();
                                cachePut(infohash, data);
                            }
                        } else {
                            //LOG.warn("Failed to download data: " + url);
                            cachePut("failed:" + url, long2array(System.currentTimeMillis()));
                        }
                    }
                    try {
                        if (data != null) {
                            List<? extends SearchResult> results = crawlResult(obj, data);
                            if (results != null) {
                                if (!results.isEmpty()) {
                                    onResults(results);
                                } else {
                                    cachePut("failed:" + url, long2array(System.currentTimeMillis()));
                                }
                            }
                        }
                    } catch (Throwable e) {
                        LOG.warn("Error creating crawled results from downloaded data: " + "url=" + url + ", e=" + e.getMessage());
                        cacheRemove(url); // invalidating cache data
                    }
                } else {
                    try {
                        List<? extends SearchResult> results = crawlResult(obj, null);
                        if (results != null) {
                            onResults(results);
                        }
                    } catch (Throwable e) {
                        LOG.warn("Error creating crawled results from search result alone: " + obj.getDetailsUrl() + ", e=" + e.getMessage());//,e);
                    }
                }
            }
        }
    }

    protected abstract String getCrawlUrl(T sr);

    protected abstract List<? extends SearchResult> crawlResult(T sr, byte[] data) throws Exception;

    private byte[] fetchMagnet(String magnet) {
        if (magnetDownloader != null) {
            return magnetDownloader.download(magnet, DEFAULT_MAGNET_DOWNLOAD_TIMEOUT_SECS);
        } else {
            LOG.warn("Magnet downloader not set, download not supported: " + magnet);
            return null;
        }
    }

    private byte[] cacheGet(String key) {
//        //UNCOMMENT TO TEST/DEBUG SEARCHES THAT KEEP FAILING
//        if (key.startsWith("failed:")) {
//            return null;
//        }
        if (cache != null) {
            synchronized (cache) {
                return cache.get(key);
            }
        } else {
            return null;
        }
    }

    private void cachePut(String key, byte[] data) {
        if (cache != null) {
            synchronized (cache) {
                cache.put(key, data);
            }
        }
    }

    private void cacheRemove(String key) {
        if (cache != null) {
            synchronized (cache) {
                cache.remove(key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private T cast(CrawlableSearchResult sr) {
        try {
            return (T) sr;
        } catch (ClassCastException e) {
            LOG.warn("Something wrong with the logic, need to pass a crawlable search result with the correct type");
        }
        return null;
    }
}
