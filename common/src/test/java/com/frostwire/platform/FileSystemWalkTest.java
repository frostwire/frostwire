/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.platform;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class FileSystemWalkTest {

    DefaultFileSystem fs;

    @BeforeEach
    public void setUp() throws Exception {
        fs = new DefaultFileSystem();
    }


    @Test
    public void testAnyFile() {
        final AtomicBoolean b = new AtomicBoolean(false);

        fs.walk(new File("any.txt"), new FileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }

            @Override
            public void file(File file) {
                b.set(true);
            }
        });

        assertFalse(b.get());

        b.set(false);

        fs.walk(new File("any.txt"), new FileFilter() {
            @Override
            public boolean accept(File file) {
                return !file.isDirectory();
            }

            @Override
            public void file(File file) {
                b.set(true);
            }
        });

        assertFalse(b.get());
    }

    @Test
    public void testDir() throws IOException {
        File f1 = File.createTempFile("aaa", null);
        File d1 = f1.getParentFile();
        final File d2 = new File(d1, "d2");
        if (d2.exists()) {
            FileUtils.deleteDirectory(d2);
        }
        assertTrue(d2.mkdir());
        File f2 = new File(d2, "bbb");
        assertTrue(f2.createNewFile());

        final LinkedList<File> l = new LinkedList<>();

        fs.walk(d1, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }

            @Override
            public void file(File file) {
                l.add(file);
            }
        });

        Set<File> set = new LinkedHashSet<>(l);
        assertEquals(set.size(), l.size());

        assertFalse(l.contains(d1));
        assertTrue(l.contains(f1));
        assertTrue(l.contains(d2));
        assertTrue(l.contains(f2));

        l.clear();
        fs.walk(d1, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return !file.equals(d2);
            }

            @Override
            public void file(File file) {
                l.add(file);
            }
        });

        assertFalse(l.contains(d1));
        assertTrue(l.contains(f1));
        assertFalse(l.contains(d2));
        assertFalse(l.contains(f2));

        assertTrue(f2.delete());
        assertTrue(d2.delete());
        assertTrue(f1.delete());
    }
}
