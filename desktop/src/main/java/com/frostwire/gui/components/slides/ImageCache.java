/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.components.slides;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.InMemoryImageIO;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
import com.limegroup.gnutella.settings.SharingSettings;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * Loads slide images from the local disk cache or the network, always on a background executor, and
 * writes downloaded images to the disk cache.
 *
 * @author gubatron
 * @author aldenml
 */
public class ImageCache {
  private static final Logger LOG = Logger.getLogger(ImageCache.class);
  private static ImageCache instance;

  private final Executor executor;
  private final Fetcher fetcher;
  private final Supplier<File> cacheDirectory;

  ImageCache(Executor executor, Fetcher fetcher, Supplier<File> cacheDirectory) {
    this.executor = executor;
    this.fetcher = fetcher;
    this.cacheDirectory = cacheDirectory;
  }

  public static synchronized ImageCache instance() {
    if (instance == null) {
      instance =
          new ImageCache(
              DesktopParallelExecutor::execute,
              url ->
                  HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC)
                      .getBytes(url.toString()),
              SharingSettings::getImageCacheDirectory);
    }
    return instance;
  }

  /**
   * Loads the image in the background and reports it to {@code listener} from a background thread.
   * Never touches the disk or the network on the calling thread, so it is safe on the EDT.
   */
  public void getImage(URL url, OnLoadedListener listener) {
    executor.execute(() -> load(url, listener));
  }

  private void load(URL url, OnLoadedListener listener) {
    BufferedImage image = isCached(url) ? loadFromCache(url) : null;
    boolean fromCache = image != null;
    if (image == null) {
      image = download(url);
    }
    if (listener != null) {
      listener.onLoaded(url, image, fromCache, image == null);
    }
  }

  private File getCacheFile(URL url) {
    String host = url.getHost();
    String path = url.getPath();
    if (host == null || host.isEmpty()) { // dealing with local resource images, not perfect
      host = "localhost";
      path = new File(path).getName();
    }
    return new File(cacheDirectory.get(), File.separator + host + File.separator + path);
  }

  private boolean isCached(URL url) {
    File file = getCacheFile(url);
    return file.exists();
  }

  private BufferedImage loadFromCache(URL url) {
    try {
      return ImageIO.read(getCacheFile(url));
    } catch (Throwable e) {
      LOG.error("Failed to load image from cache: " + url, e);
      return null;
    }
  }

  private BufferedImage download(URL url) {
    try {
      byte[] data = fetcher.fetch(url);
      if (data == null) {
        throw new IOException("ImageCache.download() got nothing at " + url);
      }
      BufferedImage image = InMemoryImageIO.read(data);
      if (image != null) {
        saveToCache(url, image);
      }
      return image;
    } catch (Throwable e) {
      LOG.error("Failed to load image from: " + url, e);
      return null;
    }
  }

  /**
   * Writes to a temporary file and moves it into place, so a concurrent load of the same URL never
   * reads a half-written cache file.
   */
  private void saveToCache(URL url, BufferedImage image) {
    File tmp = null;
    try {
      File file = getCacheFile(url);
      String filename = file.getName();
      int dotIndex = filename.lastIndexOf('.');
      String ext = filename.substring(dotIndex + 1);
      String formatName = ImageIO.getImageReadersBySuffix(ext).next().getFormatName();
      File dir = file.getParentFile();
      if (!dir.exists()) {
        dir.mkdirs();
      }
      tmp = File.createTempFile("slide-", ".tmp", dir);
      ImageIO.write(image, formatName, tmp);
      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      tmp = null;
    } catch (Throwable e) {
      LOG.error("Failed to save image to cache: " + url, e);
    } finally {
      if (tmp != null) {
        tmp.delete();
      }
    }
  }

  /** Fetches the raw bytes of an image URL. Blocking: only called from the background executor. */
  interface Fetcher {
    byte[] fetch(URL url) throws IOException;
  }

  public interface OnLoadedListener {
    /** Called from a background thread once the image is loaded, or with fail=true. */
    void onLoaded(URL url, BufferedImage image, boolean fromCache, boolean fail);
  }
}
