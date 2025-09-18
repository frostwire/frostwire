/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.theme;

import javax.swing.*;
import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @author Michael Martak
 * @since 1.4
 */
class ShellFolderManager {
    private static final String COLUMN_NAME = "FileChooser.fileNameHeaderText";
    private static final String COLUMN_SIZE = "FileChooser.fileSizeHeaderText";
    private static final String COLUMN_DATE = "FileChooser.fileDateHeaderText";
    @SuppressWarnings("Convert2Diamond")
    private final Comparator<Object> fileComparator = new Comparator<Object>() {
        public int compare(Object a, Object b) {
            return cmp((File) a, (File) b);
        }

        int cmp(File f1, File f2) {
            ShellFolder sf1 = null;
            ShellFolder sf2 = null;
            if (f1 instanceof ShellFolder) {
                sf1 = (ShellFolder) f1;
                if (sf1.isFileSystem()) {
                    sf1 = null;
                }
            }
            if (f2 instanceof ShellFolder) {
                sf2 = (ShellFolder) f2;
                if (sf2.isFileSystem()) {
                    sf2 = null;
                }
            }
            if (sf1 != null && sf2 != null) {
                return sf1.compareTo(sf2);
            } else if (sf1 != null) {
                return -1;      // Non-file shellfolders sort before files
            } else if (sf2 != null) {
                return 1;
            } else {
                String name1 = f1.getName();
                String name2 = f2.getName();
                // First ignore case when comparing
                int diff = name1.toLowerCase().compareTo(name2.toLowerCase());
                if (diff != 0) {
                    return diff;
                } else {
                    // May differ in case (e.g. "mail" vs. "Mail")
                    // We need this test for consistent sorting
                    return name1.compareTo(name2);
                }
            }
        }
    };

    /**
     * Create a shell folder from a file.
     * Override to return machine-dependent behavior.
     */
    ShellFolder createShellFolder(File file) {
        return new DefaultShellFolder(null, file);
    }

    /**
     * @param key a <code>String</code>
     *            "fileChooserDefaultFolder":
     *            Returns a <code>File</code> - the default shellfolder for a new filechooser
     *            "roots":
     *            Returns a <code>File[]</code> - containing the root(s) of the displayable hieararchy
     *            "fileChooserComboBoxFolders":
     *            Returns a <code>File[]</code> - an array of shellfolders representing the list to
     *            show by default in the file chooser's combobox
     *            "fileChooserShortcutPanelFolders":
     *            Returns a <code>File[]</code> - an array of shellfolders representing well-known
     *            folders, such as Desktop, Documents, History, Network, Home, etc.
     *            This is used in the shortcut panel of the filechooser on Windows 2000
     *            and Windows Me.
     *            "fileChooserIcon nn":
     *            Returns an <code>Image</code> - icon nn from resource 124 in comctl32.dll (Windows only).
     * @return An Object matching the key string.
     */
    public Object get(String key) {
        switch (key) {
            case "fileChooserDefaultFolder":
                // Return the default shellfolder for a new filechooser
                File homeDir = new File(System.getProperty("user.home"));
                return createShellFolder(homeDir);
            case "roots":
                // The root(s) of the displayable hieararchy
                return File.listRoots();
            case "fileChooserComboBoxFolders":
                // Return an array of ShellFolders representing the list to
                // show by default in the file chooser's combobox
                return get("roots");
            case "fileChooserShortcutPanelFolders":
                // Return an array of ShellFolders representing well-known
                // folders, such as Desktop, Documents, History, Network, Home, etc.
                // This is used in the shortcut panel of the filechooser on Windows 2000
                // and Windows Me
                return new File[]{(File) get("fileChooserDefaultFolder")};
        }
        return null;
    }

    /**
     * Does <code>dir</code> represent a "computer" such as a node on the network, or
     * "My Computer" on the desktop.
     */
    boolean isComputerNode() {
        return false;
    }

    boolean isFileSystemRoot(File dir) {
        if (dir instanceof ShellFolder && !((ShellFolder) dir).isFileSystem()) {
            return false;
        }
        return (dir.getParentFile() == null);
    }

    void sortFiles(List<Object> files) {
        files.sort(fileComparator);
    }

    ShellFolderColumnInfo[] getFolderColumns(File dir) {
        ShellFolderColumnInfo[] columns = null;
        if (dir instanceof ShellFolder) {
            columns = ((ShellFolder) dir).getFolderColumns();
        }
        if (columns == null) {
            columns = new ShellFolderColumnInfo[]{
                    new ShellFolderColumnInfo(COLUMN_NAME, 150,
                            SwingConstants.LEADING, true,
                            fileComparator),
                    new ShellFolderColumnInfo(COLUMN_SIZE, 75,
                            SwingConstants.RIGHT, true,
                            ComparableComparator.getInstance(), true),
                    new ShellFolderColumnInfo(COLUMN_DATE, 130,
                            SwingConstants.LEADING, true,
                            ComparableComparator.getInstance(), true)
            };
        }
        return columns;
    }

    Object getFolderColumnValue(File file, int column) {
        if (file instanceof ShellFolder) {
            Object value = ((ShellFolder) file).getFolderColumnValue();
            if (value != null) {
                return value;
            }
        }
        if (file == null || !file.exists()) {
            return null;
        }
        switch (column) {
            case 0:
                // By default, file name will be rendered using getSystemDisplayName()
                return file;
            case 1: // size
                return file.isDirectory() ? null : file.length();
            case 2: // date
                if (isFileSystemRoot(file)) {
                    return null;
                }
                long time = file.lastModified();
                return (time == 0L) ? null : new Date(time);
            default:
                return null;
        }
    }

    /**
     * This class provides a default comparator for the default column set
     */
    private static class ComparableComparator implements Comparator<Object> {
        private static Comparator<Object> instance;

        public static Comparator<Object> getInstance() {
            if (instance == null) {
                instance = new ComparableComparator();
            }
            return instance;
        }

        @SuppressWarnings({"unchecked"})
        public int compare(Object o1, Object o2) {
            int gt;
            if (o1 == null && o2 == null) {
                gt = 0;
            } else if (o1 != null && o2 == null) {
                gt = 1;
            } else if (o1 == null && o2 != null) {
                gt = -1;
            } else if (o1 instanceof Comparable) {
                gt = ((Comparable<Object>) o1).compareTo(o2);
            } else {
                gt = 0;
            }
            return gt;
        }
    }
}
