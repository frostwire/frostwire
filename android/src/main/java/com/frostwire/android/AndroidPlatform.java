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

package com.frostwire.android;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;
import android.os.Looper;

import androidx.documentfile.provider.DocumentFile;

import com.frostwire.android.util.SystemUtils;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.posix_stat_t;
import com.frostwire.jlibtorrent.swig.posix_wrapper;
import com.frostwire.platform.AbstractPlatform;
import com.frostwire.platform.DefaultFileSystem;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.VPNMonitor;
import com.frostwire.util.Logger;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class AndroidPlatform extends AbstractPlatform {

    private static final Logger LOG = Logger.getLogger(AndroidPlatform.class);

    private static final int VERSION_CODE_LOLLIPOP = 21;

    public AndroidPlatform(Application app) {
        super(buildFileSystem(app), //FileSystem
              new AndroidPaths(app),  //SystemPaths
              new AndroidSettings()); //AppSettings
    }

    @Override
    public VPNMonitor vpn() {
        return null;
    }

    public boolean isUIThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static boolean saf() {
        Platform p = Platforms.get();
        return p.fileSystem() instanceof LollipopFileSystem;
    }

    /**
     * This method determines if the file {@code f} is protected by
     * the SAF framework because it's in the real external SD card.
     *
     * @param f the file
     * @return if protected by SAF
     */
    public static boolean saf(File f) {
        if (SystemUtils.hasAndroid11OrNewer()) {
            // We should have File operations back again on Android 11
            return false;
        }
        Platform p = Platforms.get();

        if (!(p.fileSystem() instanceof LollipopFileSystem)) {
            return false;
        }

        if (f.getPath().contains("/Android/data/com.frostwire.android/")) {
            // private file, FUSE give us standard POSIX operations
            return false;
        }

        LollipopFileSystem fs = (LollipopFileSystem) p.fileSystem();

        return fs.getExtSdCardFolder(f) != null;
    }

    private static FileSystem buildFileSystem(Application app) {
        FileSystem fs;

        if (Build.VERSION.SDK_INT >= VERSION_CODE_LOLLIPOP && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            LollipopFileSystem lfs = new LollipopFileSystem(app);
            PosixCalls w = new PosixCalls(lfs);
            w.swigReleaseOwnership();
            libtorrent.set_posix_wrapper(w);
            //LibTorrent.setPosixWrapper(new PosixCalls(lfs));
            fs = lfs;
        }
        else if (SystemUtils.hasAndroid10()) {
            fs = new Android10QFileSystem(app);
        }
        else {
            fs = new DefaultFileSystem();
        }

        return fs;
    }

    private static final class PosixCalls extends posix_wrapper {

        private final LollipopFileSystem fs;

        PosixCalls(LollipopFileSystem fs) {
            this.fs = fs;
        }

        @Override
        public int open(String path, int flags, int mode) {
            LOG.info("posix - open:" + path);

            int r = super.open(path, flags, mode);
            if (r >= 0) {
                return r;
            }

            r = fs.openFD(new File(path), "rw");
            if (r < 0) {
                LOG.info("posix wrapper failed to create native fd for: " + path);
            }

            return r;
        }

        @SuppressLint("SdCardPath")
        @Override
        public int stat(String path, posix_stat_t buf) {
            LOG.info("posix - stat:" + path);

            int r = super.stat(path, buf);
            if (r >= 0) {
                return r;
            }

            DocumentFile f = fs.getDocument(new File(path));
            if (f == null) {
                LOG.info("posix wrapper failed to stat file for: " + path);
                // this trick the posix layer to set the correct errno
                return super.stat("/data/data/com.frostwire.android/noexists.txt", buf);
            }

            int S_ISDIR = f.isDirectory() ? 0040000 : 0;
            int S_IFREG = 0100000;

            buf.setMode(S_ISDIR | S_IFREG);
            buf.setSize(f.length());
            int t = (int) (f.lastModified() / 1000);
            buf.setAtime(t);
            buf.setMtime(t);
            buf.setCtime(t);

            return 0;
        }

        @Override
        public int mkdir(String path, int mode) {
            LOG.info("posix - mkdir:" + path);
            int r = super.mkdir(path, mode);
            if (r >= 0) {
                return r;
            }

            r = fs.mkdirs(new File(path)) ? 0 : -1;
            if (r < 0) {
                LOG.info("posix wrapper failed to create dir: " + path);
            }

            return r;
        }

        @Override
        public int rename(String oldpath, String newpath) {
            LOG.info("posix - rename:" + oldpath + " -> " + newpath);
            int r = super.rename(oldpath, newpath);
            if (r >= 0) {
                return r;
            }

            File src = new File(oldpath);
            File dest = new File(newpath);

            if (fs.copy(src, dest)) {
                fs.delete(src);
                return 0;
            } else {
                LOG.info("posix wrapper failed to copy file: " + oldpath + " -> " + newpath);
                return -1;
            }
        }

        @Override
        public int remove(String path) {
            LOG.info("posix - remove:" + path);
            int r = super.remove(path);
            if (r >= 0) {
                return r;
            }

            r = fs.delete(new File(path)) ? 0 : -1;
            if (r < 0) {
                LOG.info("posix wrapper failed to delete file: " + path);
            }

            return r;
        }
    }
}
