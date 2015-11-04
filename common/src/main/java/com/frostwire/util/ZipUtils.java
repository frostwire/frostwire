/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import com.frostwire.logging.Logger;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ZipUtils {

    private static final Logger LOG = Logger.getLogger(ZipUtils.class);

    public static boolean unzip(File zipFile, File outputDir) {
        return unzip(zipFile, outputDir, null);
    }

    public static boolean unzip(File zipFile, File outputDir, ZipListener listener) {
        boolean result = false;

        try {

            FileUtils.deleteDirectory(outputDir);

            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            try {
                unzipEntries(outputDir, zis, getItemCount(zipFile), System.currentTimeMillis(), listener);
            } finally {
                zis.close();
            }

            result = true;

        } catch (IOException e) {
            LOG.error("Unable to uncompress " + zipFile + " to " + outputDir, e);
            result = false;
        }

        return result;
    }

    private static void unzipEntries(File folder, ZipInputStream zis, int itemCount, long time, ZipListener listener) throws IOException, FileNotFoundException {
        ZipEntry ze = null;

        int item = 0;

        while ((ze = zis.getNextEntry()) != null) {
            item++;

            String fileName = ze.getName();
            File newFile = new File(folder, fileName);

            LOG.debug("unzip: " + newFile.getAbsoluteFile());

            if (ze.isDirectory()) {
                if (!newFile.mkdirs()) {
                    break;
                }
                continue;
            }

            if (listener != null) {
                int progress = (item == itemCount) ? 100 : (int) (((double) (item * 100)) / (double) (itemCount));
                listener.onUnzipping(fileName, progress);
            }

            FileOutputStream fos = new FileOutputStream(newFile);

            try {
                int n;
                byte[] buffer = new byte[1024];
                while ((n = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, n);

                    if (listener != null && listener.isCanceled()) { // not the best way
                        throw new IOException("Uncompress operation cancelled");
                    }
                }
            } finally {
                fos.close();
                zis.closeEntry();
            }

            newFile.setLastModified(time);
        }
    }

    private static int getItemCount(File file) throws IOException {
        ZipFile zip = null;
        int count = 0;
        try {
            zip = new ZipFile(file);
            count = zip.size();
        } finally {
            try {
                zip.close();
            } catch (Throwable e) {
                // ignore
            }
        }
        return count;
    }

    public interface ZipListener {
        void onUnzipping(String fileName, int progress);

        boolean isCanceled();
    }
}
