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

package com.frostwire.android.gui.transfers;

import com.frostwire.logging.Logger;
import com.frostwire.util.ByteUtils;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * @author gubatron
 * @author aldenml
 *
 */
final class Digests {
    private final static Logger LOG = Logger.getLogger(Digests.class);

    private Digests() {}

    public static byte[] sha1Bytes(File f)  {
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            if (sha1 == null) {
                LOG.error("No such algorithm: SHA1");
                return null;
            }
            FileInputStream fis = new FileInputStream(f);
            byte[] dataBytes = new byte[1024];
            int read;
            while ((read = fis.read(dataBytes)) != -1) {
                sha1.update(dataBytes, 0, read);
            }
            return sha1.digest();
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    public static String sha1(File f) {
        return ByteUtils.encodeHex(sha1Bytes(f));
    }
}
