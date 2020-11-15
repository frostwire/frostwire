/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.frostwire.gui.theme;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

/**
 * @author Michael Martak
 * @since 1.4
 */
abstract class ShellFolder extends File {
    // Static
    private static ShellFolderManager shellFolderManager;

    static {
//        Class<?> managerClass = (Class<?>)Toolkit.getDefaultToolkit().
//            getDesktopProperty("Shell.shellFolderManager");
//        if (managerClass == null) {
//            managerClass = ShellFolderManager.class;
//        }
        Class<?> managerClass = ShellFolderManager.class;
        try {
            shellFolderManager =
                    (ShellFolderManager) managerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new Error("Could not instantiate Shell Folder Manager: "
                    + managerClass.getName());
        } catch (IllegalAccessException e) {
            throw new Error("Could not access Shell Folder Manager: "
                    + managerClass.getName());
        } catch (NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    protected ShellFolder parent;

    /**
     * Create a file system shell folder from a file
     */
    ShellFolder(ShellFolder parent, String pathname) {
        super((pathname != null) ? pathname : "ShellFolder");
        this.parent = parent;
    }

    /**
     * Return a shell folder from a file object
     *
     * @throws FileNotFoundException if file does not exist
     */
    static ShellFolder getShellFolder(File file) throws FileNotFoundException {
        if (file instanceof ShellFolder) {
            return (ShellFolder) file;
        }
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        return shellFolderManager.createShellFolder(file);
    }

    /**
     * @param key a <code>String</code>
     * @return An Object matching the string <code>key</code>.
     * @see ShellFolderManager#get(String)
     */
    public static Object get(String key) {
        return shellFolderManager.get(key);
    }

    /**
     * @return Whether this is a file system root directory
     */
    private static boolean isFileSystemRoot(File dir) {
        return shellFolderManager.isFileSystemRoot(dir);
    }

    /**
     * Canonicalizes files that don't have symbolic links in their path.
     * Normalizes files that do, preserving symbolic links from being resolved.
     */
    static File getNormalizedFile(File f) throws IOException {
        File canonical = f.getCanonicalFile();
        if (f.equals(canonical)) {
            // path of f doesn't contain symbolic links
            return canonical;
        }
        // preserve symbolic links from being resolved
        return new File(f.toURI().normalize());
    }

    static ShellFolderColumnInfo[] getFolderColumns(File dir) {
        return shellFolderManager.getFolderColumns(dir);
    }

    static Object getFolderColumnValue(File file, int column) {
        return shellFolderManager.getFolderColumnValue(file, column);
    }

    /**
     * @return Whether this is a file system shell folder
     */
    boolean isFileSystem() {
        return (!getPath().startsWith("ShellFolder"));
    }

    /**
     * This method must be implemented to make sure that no instances
     * of <code>ShellFolder</code> are ever serialized. If <code>isFileSystem()</code> returns
     * <code>true</code>, then the object should be representable with an instance of
     * <code>java.io.File</code> instead. If not, then the object is most likely
     * depending on some internal (native) state and cannot be serialized.
     * <p>
     * if no suitable replacement can be found.
     */
    protected abstract Object writeReplace();

    /**
     * Returns the path for this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder.
     *
     * @return the path as a String for this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder
     * @see java.io.File#getParent()
     * @since 1.4
     */
    public String getParent() {
        if (parent == null && isFileSystem()) {
            return super.getParent();
        }
        if (parent != null) {
            return (parent.getPath());
        } else {
            return null;
        }
    }

    /**
     * Returns a File object representing this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder.
     *
     * @return a File object representing this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder
     * @see java.io.File#getParentFile()
     * @since 1.4
     */
    public File getParentFile() {
        if (parent != null) {
            return parent;
        } else if (isFileSystem()) {
            return super.getParentFile();
        } else {
            return null;
        }
    }

    public File[] listFiles() {
        return listFiles(true);
    }

    public File[] listFiles(boolean includeHiddenFiles) {
        File[] files = super.listFiles();
        if (!includeHiddenFiles) {
            Vector<File> v = new Vector<>();
            int nameCount = (files == null) ? 0 : files.length;
            for (int i = 0; i < nameCount; i++) {
                if (!files[i].isHidden()) {
                    v.addElement(files[i]);
                }
            }
            files = v.toArray(new File[0]);
        }
        return files;
    }

    /**
     * @return Whether this shell folder is a link
     */
    public abstract boolean isLink();

    /**
     * @return The name used to display this shell folder
     */
    public abstract String getDisplayName();
    // Override File methods

    /**
     * Compares this ShellFolder with the specified ShellFolder for order.
     *
     * @see #compareTo(Object)
     */
    public int compareTo(File file2) {
        if (!(file2 instanceof ShellFolder)
                || ((file2 instanceof ShellFolder) && ((ShellFolder) file2).isFileSystem())) {
            if (isFileSystem()) {
                return super.compareTo(file2);
            } else {
                return -1;
            }
        } else {
            if (isFileSystem()) {
                return 1;
            } else {
                return getName().compareTo(file2.getName());
            }
        }
    }

    /**
     * @return The icon used to display this shell folder
     */
    public Image getIcon() {
        return null;
    }

    public boolean isAbsolute() {
        return (!isFileSystem() || super.isAbsolute());
    }

    public File getAbsoluteFile() {
        return (isFileSystem() ? super.getAbsoluteFile() : this);
    }

    public boolean canRead() {
        return (!isFileSystem() || super.canRead());       // ((Fix?))
    }

    /**
     * Returns true if folder allows creation of children.
     * True for the "Desktop" folder, but false for the "My Computer"
     * folder.
     */
    public boolean canWrite() {
        return (isFileSystem() && super.canWrite());     // ((Fix?))
    }

    public boolean exists() {
        // Assume top-level drives exist, because state is uncertain for
        // removable drives.
        return (!isFileSystem() || isFileSystemRoot(this) || super.exists());
    }

    public boolean isDirectory() {
        return (!isFileSystem() || super.isDirectory());   // ((Fix?))
    }

    public boolean isFile() {
        return (isFileSystem() ? super.isFile() : !isDirectory());      // ((Fix?))
    }

    public long lastModified() {
        return (isFileSystem() ? super.lastModified() : 0L);    // ((Fix?))
    }

    public long length() {
        return (isFileSystem() ? super.length() : 0L);  // ((Fix?))
    }

    public boolean createNewFile() throws IOException {
        return (isFileSystem() && super.createNewFile());
    }

    public boolean delete() {
        return (isFileSystem() && super.delete());       // ((Fix?))
    }

    public void deleteOnExit() {
        if (isFileSystem()) {
            super.deleteOnExit();
        }
    }

    public boolean mkdir() {
        return (isFileSystem() && super.mkdir());
    }

    public boolean mkdirs() {
        return (isFileSystem() && super.mkdirs());
    }

    public boolean renameTo(File dest) {
        return (isFileSystem() && super.renameTo(dest)); // ((Fix?))
    }

    public boolean setLastModified(long time) {
        return (isFileSystem() && super.setLastModified(time)); // ((Fix?))
    }

    public boolean setReadOnly() {
        return (isFileSystem() && super.setReadOnly()); // ((Fix?))
    }

    public String toString() {
        return (isFileSystem() ? super.toString() : getDisplayName());
    }

    ShellFolderColumnInfo[] getFolderColumns() {
        return null;
    }

    Object getFolderColumnValue() {
        return null;
    }
}
