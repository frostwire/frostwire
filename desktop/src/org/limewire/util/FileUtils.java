/*
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

package org.limewire.util;

import com.frostwire.util.Logger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Provides file manipulation methods; ensures a file exists, makes a file
 * writable, renames, saves and deletes a file.
 */
public class FileUtils {
    private static final Logger LOG = Logger.getLogger(FileUtils.class);

    /**
     * Gets the canonical path, catching buggy Windows errors
     */
    public static String getCanonicalPath(File f) throws IOException {
        try {
            return f.getCanonicalPath();
        } catch (IOException ioe) {
            String msg = ioe.getMessage();
            // windows bugs out :(
            if (OSUtils.isWindows() && msg != null && msg.contains("There are no more files"))
                return f.getAbsolutePath();
            else
                throw ioe;
        }
    }

    /**
     * Same as f.getCanonicalFile() in JDK1.3.
     */
    public static File getCanonicalFile(File f) throws IOException {
        try {
            return f.getCanonicalFile();
        } catch (IOException ioe) {
            String msg = ioe.getMessage();
            // windows bugs out :(
            if (OSUtils.isWindows() && msg != null && msg.contains("There are no more files"))
                return f.getAbsoluteFile();
            else
                throw ioe;
        }
    }

    /**
     * Determines if file 'a' is an ancestor of file 'b'.
     */
    public static boolean isAncestor(File a, File b) {
        while (b != null) {
            if (b.equals(a))
                return true;
            b = b.getParentFile();
        }
        return false;
    }

    /**
     * Detects attempts at directory traversal by testing if testDirectory
     * really is a parent of testPath.
     */
    public static boolean isReallyInParentPath(File testParent, File testChild) throws IOException {
        String testParentName = getCanonicalPath(testParent);
        File testChildParentFile = testChild.getAbsoluteFile().getParentFile();
        if (testChildParentFile == null)
            testChildParentFile = testChild.getAbsoluteFile();
        String testChildParentName = getCanonicalPath(testChildParentFile);
        return testChildParentName.startsWith(testParentName);
    }

    /**
     * Utility method to set a file as non read only.
     * If the file is already writable, does nothing.
     *
     * @param f the <tt>File</tt> instance whose read only flag should
     *          be unset.
     * @return whether or not <tt>f</tt> is writable after trying to make it
     * writeable -- note that if the file doesn't exist, then this returns
     * <tt>true</tt>
     */
    public static boolean setWriteable(File f) {
        if (!f.exists())
            return true;
        // non Windows-based systems return the wrong value
        // for canWrite when the argument is a directory --
        // writing is based on the 'x' attribute, not the 'w'
        // attribute for directories.
        if (f.canWrite()) {
            if (OSUtils.isWindows())
                return true;
            else if (!f.isDirectory())
                return true;
        }
        String fName;
        try {
            fName = f.getCanonicalPath();
        } catch (IOException ioe) {
            fName = f.getPath();
        }
        String[] cmds = null;
        if (OSUtils.isWindows() || OSUtils.isMacOSX())
            SystemUtils.setWriteable(fName);
        else if (OSUtils.isOS2())
            ;//cmds = null; // Find the right command for OS/2 and fill in
        else {
            if (f.isDirectory())
                cmds = new String[]{"chmod", "u+w+x", fName};
            else
                cmds = new String[]{"chmod", "u+w", fName};
        }
        if (cmds != null) {
            try {
                Process p = Runtime.getRuntime().exec(cmds);
                p.waitFor();
            } catch (SecurityException | InterruptedException | IOException ignored) {
            }
        }
        return f.canWrite();
    }

    /**
     * @param directory Gets all files under this directory RECURSIVELY.
     * @param filter    If null, then returns all files.  Else, only returns files
     *                  extensions in the filter array.
     * @return An array of Files recursively obtained from the directory,
     * according to the filter.
     */
    public static File[] getFilesRecursive(File directory,
                                           String[] filter) {
        List<File> dirs = new ArrayList<>();
        // the return array of files...
        List<File> retFileArray = new ArrayList<>();
        File[] retArray = new File[0];
        // bootstrap the process
        if (directory.exists() && directory.isDirectory())
            dirs.add(directory);
        // while i have dirs to process
        while (dirs.size() > 0) {
            File currDir = dirs.remove(0);
            String[] listedFiles = currDir.list();
            for (int i = 0; (listedFiles != null) && (i < listedFiles.length); i++) {
                File currFile = new File(currDir, listedFiles[i]);
                if (currFile.isDirectory()) // to be dealt with later
                    dirs.add(currFile);
                else if (currFile.isFile()) { // we have a 'file'....
                    boolean shouldAdd = false;
                    if (filter == null)
                        shouldAdd = true;
                    else {
                        String ext = FilenameUtils.getExtension(currFile.getName());
                        for (int j = 0; (j < filter.length) && (ext != null); j++) {
                            if (ext.equalsIgnoreCase(filter[j])) {
                                shouldAdd = true;
                                // don't keep looping through all filters --
                                // one match is good enough
                                break;
                            }
                        }
                    }
                    if (shouldAdd)
                        retFileArray.add(currFile);
                }
            }
        }
        if (!retFileArray.isEmpty()) {
            retArray = new File[retFileArray.size()];
            for (int i = 0; i < retArray.length; i++)
                retArray[i] = retFileArray.get(i);
        }
        return retArray;
    }

    /**
     * Deletes the given file or directory, moving it to the trash can or
     * recycle bin if the platform has one and <code>moveToTrash</code> is
     * true.
     *
     * @param file        The file or directory to trash or delete
     * @param moveToTrash whether the file should be moved to the trash bin
     *                    or permanently deleted
     * @return true on success
     * @throws IllegalArgumentException if the OS does not support moving files
     *                                  to a trash bin, check with {@link OSUtils#supportsTrash()}.
     */
    public static boolean delete(File file, boolean moveToTrash) {
        if (!file.exists()) {
            return false;
        }
        if (moveToTrash) {
            if (OSUtils.isMacOSX()) {
                return moveToTrashOSX(file);
            } else if (OSUtils.isWindows()) {
                return SystemUtils.recycle(file);
            } else {
                throw new IllegalArgumentException("OS does not support trash");
            }
        } else {
            return deleteRecursive(file);
        }
    }

    /**
     * Moves the given file or directory to Trash.
     *
     * @param file The file or directory to move to Trash
     * @return true on success
     * @throws IOException if the canonical path cannot be resolved
     *                     or if the move process is interrupted
     */
    private static boolean moveToTrashOSX(File file) {
        try {
            String[] command = moveToTrashCommand(file);
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream();
            Process process = builder.start();
            consumeAllInput(process);
            process.waitFor();
        } catch (InterruptedException err) {
            LOG.error("InterruptedException", err);
        } catch (IOException err) {
            LOG.error("IOException", err);
        }
        return !file.exists();
    }

    /**
     * Consumes all input from a Process. See also
     * ProcessBuilder.redirectErrorStream()
     */
    private static void consumeAllInput(Process p) throws IOException {
        try (InputStream in = new BufferedInputStream(p.getInputStream())) {
            byte[] buf = new byte[1024];
            while (in.read(buf, 0, buf.length) >= 0) ;
        }
    }

    /**
     * Creates and returns the osascript command to move
     * a file or directory to the Trash
     *
     * @param file The file or directory to move to Trash
     * @return OSAScript command
     * @throws IOException if the canonical path cannot be resolved
     */
    private static String[] moveToTrashCommand(File file) {
        String path = null;
        try {
            path = file.getCanonicalPath();
        } catch (IOException err) {
            LOG.error("IOException", err);
            path = file.getAbsolutePath();
        }
        String fileOrFolder = (file.isFile() ? "file" : "folder");
        return new String[]{
                "osascript",
                "-e", "set unixPath to \"" + path + "\"",
                "-e", "set hfsPath to POSIX file unixPath",
                "-e", "tell application \"Finder\"",
                "-e", "if " + fileOrFolder + " hfsPath exists then",
                "-e", "move " + fileOrFolder + " hfsPath to trash",
                "-e", "end if",
                "-e", "end tell"
        };
    }

    /**
     * Deletes all files in 'directory'.
     * Returns true if this succesfully deleted every file recursively, including itself.
     *
     * @param directory
     * @return
     */
    private static boolean deleteRecursive(File directory) {
        // make sure we only delete canonical children of the parent file we
        // wish to delete. I have a hunch this might be an issue on OSX and
        // Linux under certain circumstances.
        // If anyone can test whether this really happens (possibly related to
        // symlinks), I would much appreciate it.
        String canonicalParent;
        try {
            canonicalParent = getCanonicalPath(directory);
        } catch (IOException ioe) {
            return false;
        }
        if (!directory.isDirectory())
            return directory.delete();
        File[] files = directory.listFiles();
        for (File file : files) {
            try {
                if (!getCanonicalPath(file).startsWith(canonicalParent))
                    continue;
            } catch (IOException ioe) {
                return false;
            }
            if (!deleteRecursive(file))
                return false;
        }
        return directory.delete();
    }

    /**
     * Attempts to copy the first 'amount' bytes of file 'src' to 'dst',
     * returning the number of bytes actually copied.  If 'dst' already exists,
     * the copy may or may not succeed.
     *
     * @param src    the source file to copy
     * @param amount the amount of src to copy, in bytes
     * @param dst    the place to copy the file
     * @return the number of bytes actually copied.  Returns 'amount' if the
     * entire requested range was copied.
     */
    private static long copy(File src, long amount, File dst) {
        final int BUFFER_SIZE = 1024;
        long amountToRead = amount;
        InputStream in = null;
        OutputStream out = null;
        try {
            //I'm not sure whether buffering is needed here.  It can't hurt.
            in = new BufferedInputStream(new FileInputStream(src));
            out = new BufferedOutputStream(new FileOutputStream(dst));
            byte[] buf = new byte[BUFFER_SIZE];
            while (amountToRead > 0) {
                int read = in.read(buf, 0, (int) Math.min(BUFFER_SIZE, amountToRead));
                if (read == -1)
                    break;
                amountToRead -= read;
                out.write(buf, 0, read);
            }
        } catch (IOException e) {
        } finally {
            IOUtils.closeQuietly(in);
            if (out != null) {
                try {
                    out.flush();
                } catch (IOException ignored) {
                }
            }
            IOUtils.closeQuietly(out);
        }
        return amount - amountToRead;
    }

    /**
     * Copies the file 'src' to 'dst', returning true iff the copy succeeded.
     * If 'dst' already exists, the copy may or may not succeed.  May also
     * fail for VERY large source files.
     */
    public static boolean copy(File src, File dst) {
        //Downcasting length can result in a sign change, causing
        //copy(File,int,File) to terminate immediately.
        long length = src.length();
        return copy(src, (int) length, dst) == length;
    }

    public static File getJarFromClasspath(ClassLoader classLoader, String markerFile) {
        if (classLoader == null) {
            throw new IllegalArgumentException();
        }
        URL messagesURL = classLoader.getResource(markerFile);
        if (messagesURL != null) {
            String url = CommonUtils.decode(messagesURL.toExternalForm());
            if (url != null && url.startsWith("jar:file:")) {
                url = url.substring("jar:file:".length());
                url = url.substring(0, url.length() - markerFile.length() - "!/".length());
                return new File(url);
            }
        }
        return null;
    }

    // aldenml: Why are we using NIO2 for this?
    public static File[] listFiles(File directoryFile) {
        List<File> files = new LinkedList<>();
        DirectoryStream<Path> dir = null;
        try {
            dir = Files.newDirectoryStream(directoryFile.toPath());
            for (Path child : dir) {
                files.add(child.toFile());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dir != null) {
                IOUtils.closeQuietly(dir);
            }
        }
        return files.toArray(new File[0]);
    }

    public static boolean hasExtension(String filename, String... extensionsWithoutDot) {
        String extension = FilenameUtils.getExtension(filename).toLowerCase();
        for (String ext : extensionsWithoutDot) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * files are saved with (1), (2),... if there's one with the same name already.
     */
    public static File buildFile(File savePath, String name) {
        String baseName = FilenameUtils.getBaseName(name);
        String ext = FilenameUtils.getExtension(name);
        File f = new File(savePath, name);
        int i = 1;
        while (f.exists() && i < Integer.MAX_VALUE) {
            f = new File(savePath, baseName + " (" + i + ")." + ext);
            i++;
        }
        return f;
    }
}
