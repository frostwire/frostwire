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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.components.slides.Slide;
import com.frostwire.util.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SlideDownload extends HttpDownload {
    private static final Logger LOG = Logger.getLogger(SlideDownload.class);
    private static final int BUFFER_SIZE = 8192;

    public SlideDownload(Slide slide) {
        super(slide.httpDownloadURL, slide.title, slide.saveFileAs, slide.size, slide.md5, true, true);
    }

    @Override
    protected void onComplete() {
        File file = getSaveLocation();
        if (file == null || !file.getName().toLowerCase().endsWith(".zip")) {
            return;
        }
        File destDir = file.getParentFile();
        try {
            unzip(file, destDir);
            if (!file.delete()) {
                file.deleteOnExit();
            }
        } catch (IOException e) {
            LOG.error("Error unzipping slide download: " + file.getAbsolutePath(), e);
        }
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Could not create directory: " + destDir.getAbsolutePath());
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                String destDirPath = destDir.getCanonicalPath();
                String outFilePath = outFile.getCanonicalPath();
                if (!outFilePath.startsWith(destDirPath + File.separator)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Could not create directory: " + outFile.getAbsolutePath());
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create directory: " + parent.getAbsolutePath());
                    }
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
