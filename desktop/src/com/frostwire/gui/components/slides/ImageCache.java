/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.components.slides;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.imageio.ImageIO;

import org.limewire.concurrent.ThreadExecutor;

import com.frostwire.logging.Logger;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;
import com.limegroup.gnutella.settings.SharingSettings;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class ImageCache {

    private static final Logger LOG = Logger.getLogger(ImageCache.class);

    private static ImageCache instance;

    public synchronized static ImageCache instance() {
        if (instance == null) {
            instance = new ImageCache();
        }
        return instance;
    }

    private ImageCache() {
    }

    public BufferedImage getImage(URL url, OnLoadedListener listener) {
        if (isCached(url)) {
            return loadFromCache(url, listener);
        } else if (!url.getProtocol().equals("http")) {
            return loadFromResource(url, listener);
        } else {
            loadFromUrl(url, listener);
            return null;
        }
    }

    private File getCacheFile(URL url) {
        String host = url.getHost();
        String path = url.getPath();
        if (host == null || host.length() == 0) { // dealing with local resource images, not perfect
            host = "localhost";
            path = new File(path).getName();
        }

        return new File(SharingSettings.getImageCacheDirectory(), File.separator + host + File.separator + path);
    }

    /**
     * Given the remote URL if the image has been cached this will return the local URL of the cached image on disk.
     * 
     * @param remoteURL
     * @return The URL of the cached file. null if it's not been cached yet.
     */
    public URL getCachedFileURL(URL remoteURL) {
        if (isCached(remoteURL)) {
            try {
                return getCacheFile(remoteURL).toURI().toURL();
            } catch (MalformedURLException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isCached(URL url) {
        File file = getCacheFile(url);
        return file.exists();
    }

    private BufferedImage loadFromCache(URL url, OnLoadedListener listener) {
        try {
            File file = getCacheFile(url);
            BufferedImage image = ImageIO.read(file);
            listener.onLoaded(url, image, true, false);
            return image;
        } catch (Throwable e) {
            LOG.error("Failed to load image from cache: " + url, e);
            if (e instanceof OutOfMemoryError) {
                e.printStackTrace(); // this is a special condition
            }
            listener.onLoaded(url, null, false, true);
            return null;
        }
    }

    private BufferedImage loadFromResource(URL url, OnLoadedListener listener) {
        try {
            BufferedImage image = ImageIO.read(url);
            saveToCache(url, image, 0);
            listener.onLoaded(url, image, false, false);
            return image;
        } catch (Throwable e) {
            LOG.error("Failed to load image from resource: " + url, e);
            listener.onLoaded(url, null, false, true);
            return null;
        }
    }

    private void loadFromUrl(final URL url, final OnLoadedListener listener) {
        ThreadExecutor.startThread(new Thread(new Runnable() {
            public void run() {
                try {
                    BufferedImage image = null;
                    HttpClient newInstance = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
                    byte[] data = newInstance.getBytes(url.toString());
                    
                    if (data == null) {
                        throw new IOException("ImageCache.loadUrl() got nothing at " + url.toString());

                    }
                    
                    if (data != null) {
                        image = ImageIO.read(new ByteArrayInputStream(data));
                        saveToCache(url, image, System.currentTimeMillis());
                    }
                    
                    if (listener != null && image != null) {
                        listener.onLoaded(url, image, false, false);
                    }
                } catch (Throwable e) {
                    LOG.error("Failed to load image from: " + url, e);
                    listener.onLoaded(url, null, false, true);
                }
            }
        }),"ImageCache.loadFromUrl");
    }

    private void saveToCache(URL url, BufferedImage image, long date) {
        try {
            File file = getCacheFile(url);

            if (file.exists()) {
                file.delete();
            }

            String filename = file.getName();
            int dotIndex = filename.lastIndexOf('.');
            String ext = filename.substring(dotIndex + 1);

            String formatName = ImageIO.getImageReadersBySuffix(ext).next().getFormatName();

            if (!file.getParentFile().exists()) {
                file.mkdirs();
            }
            ImageIO.write(image, formatName, file);
            file.setLastModified(date);
        } catch (Throwable e) {
            LOG.error("Failed to save image to cache: " + url, e);
        }
    }

    public interface OnLoadedListener {

        /**
         * This is called in the event that the image was downloaded and cached
         */
        public void onLoaded(URL url, BufferedImage image, boolean fromCache, boolean fail);
    }
}
