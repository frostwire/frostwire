package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.settings.UISettings;
import org.apache.commons.io.FilenameUtils;
import org.limewire.collection.FixedsizeForgetfulHashMap;
import org.limewire.collection.FixedsizeForgetfulHashSet;
import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.FileChooserUI;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A FileIconController that attempts to return native icons.
 */
public class NativeFileIconController implements FileIconController {
    /**
     * The view that retrieves the icon from the filesystem.
     */
    private final SmartFileView VIEW;
    /**
     * A mapping from String (extension) to Icon.
     */
    private final Map<String, Icon> EXTENSIONS = new HashMap<>();
    /**
     * A marker null icon so we don't create a file everytime
     * if the icon can't be found.
     */
    private final Icon NULL = new ImageIcon();

    /**
     * Constructs the NativeFileIconController.
     * This constructor may block as the JFileChooser
     * is constructed.
     */
    NativeFileIconController() {
        SmartFileView view = getNativeFileView();
        if (view == null) {
            VIEW = null;
        } else {
            VIEW = new DelegateFileView(view);
            if (UISettings.PRELOAD_NATIVE_ICONS.getValue())
                preload();
        }
    }

    /**
     * Returns true if loading succeeded and the view hasn't screwed up.
     */
    public boolean isValid() {
        return VIEW != null && VIEW.isViewAvailable();
    }

    /**
     * Returns true if we have requested this file recently.
     */
    public boolean isIconForFileAvailable(File f) {
        return VIEW.isIconCached(f);
    }

    /**
     * Retrieves the native FileView.
     */
    private SmartFileView getNativeFileView() {
        // Deadlocks happen on Windows when using file-chooser based view.
        if (OSUtils.isWindows())
            return constructFSVView();
        else
            return constructFileChooserView();
    }

    /**
     * Constructs a JFileChooser-based FileView.
     * This doesn't work consistently on Java 1.5.0_10 on Windows,
     * because it deadlocks if called outside of the Swing thread.
     *
     * @return
     */
    private SmartFileView constructFileChooserView() {
        // This roundabout way of getting the FileView is necessary for the
        // following reasons:
        // 1) We need the native UI's FileView to get the correct icons,
        //    because the Metal UI's icons are terrible.
        // 2) We cannot just call getFileView(chooser) once retrieving the
        //    native UI, because FileChooserUI tends to delegate calls
        //    to the JFileChooser, and it seems to require that it
        //    the UI be set on the chooser.
        // 3) setUI is a protected method of JFileChooser (of JComponent),
        //    so we need to have the anonymous class with an extended
        //    constructor.
        // 4) Even after constructing the JFileChooser, using getIcon on it
        //    doesn't work well, so we need to do it directly on the FileView.
        // 5) In order to get the correct file view, it needs to be explicitly
        //    set, otherwise it reverts to the UI's FileView, using UIManager,
        //    which may actually be a different UI.
        // 6) The NullPointerException must be caught because sometimes
        //    the Windows JFileChooser throws an NPE while constructing.
        //    The ArrayIndexOutOfBoundsException is a workaround also,
        //    as that error seems to be thrown occasionally in Java 1.6.
        JFileChooser chooser = null;
        // If after 10 times we still can't set it just give up.
        for (int i = 0; i < 10; i++) {
            try {
                chooser = new JFileChooser() {
                    {
                        FileChooserUI ui =
                                (FileChooserUI) ResourceManager.getNativeUI(this);
                        setUI(ui);
                        setFileView(ui.getFileView(this));
                    }
                };
            } catch (NullPointerException | ArrayIndexOutOfBoundsException ignored) {
            }
            if (chooser != null)
                break;
        }
        if (chooser == null) {
            return null;
        } else {
            return new SmartChooserView(chooser.getFileView());
        }
    }

    /**
     * Constructs a FileSystemView-based FileView.
     * Just to be safe, we do this on the Swing thread, since we've seen
     * deadlocks when constructing JFileChooser ones outside the Swing thread.
     */
    private SmartFileView constructFSVView() {
        final AtomicReference<SmartFileView> ref = new AtomicReference<>();
        GUIMediator.safeInvokeAndWait(() -> ref.set(new FSVFileView()));
        return ref.get();
    }

    /**
     * Returns the native file icon for a file, if it exists.
     * If it doesn't exist, returns the icon for the extension of the file.
     */
    public Icon getIconForFile(File f) {
        if (f == null)
            return null;
        // We return the icon if it was previously cached,
        // or if it exists.  We cannot get a nonexistant (non-cached)
        // file from the view, otherwise they'll be spurious exceptions.
        if (VIEW.isIconCached(f) || f.exists()) {
            return VIEW.getIcon(f);
        } else {
            String extension = FilenameUtils.getExtension(f.getName());
            if (extension != null)
                return getIconForExtension(extension);
            else
                return null;
        }
    }

    /**
     * Returns the icon associated with the extension.
     * TODO: Implement better.
     */
    public Icon getIconForExtension(String ext) {
        try {
            if (ext == null) {
                return null;
            }
            ext = ext.trim().toLowerCase();
            Icon icon = EXTENSIONS.get(ext);
            // If we already had a cached icon..
            if (icon != null) {
                // So long as it wasn't the NULL marker,
                // return that icon.
                if (icon != NULL)
                    return icon;
                else
                    return null;
            }
            File file = null;
            try {
                file = File.createTempFile("dummy", "." + ext);
            } catch (Exception e1) {
                return null;
            }
            Icon iconCandidate = null;
            try {
                iconCandidate = getNativeFileView().getIcon(file);
            } catch (Exception e) {
                file.delete();
                return null;
            }
            if (iconCandidate != null) {
                icon = iconCandidate;
            }
            file.delete();
            EXTENSIONS.put(ext, icon);
            return icon;
        } catch (Throwable e) {
            // due to a NPE reported in BugManager
            // ignore
            return null;
        }
    }

    /**
     * Preloads a bunch of icons.
     */
    private void preload() {
        ExecutorService queue = ExecutorsHelper.newProcessingQueue("IconLoader");
        final MediaType[] types = MediaType.getDefaultMediaTypes();
        final AtomicBoolean continueLoading = new AtomicBoolean(true);
        for (int i = 0; i < types.length && continueLoading.get(); i++) {
            final Set<?> exts = types[i].getExtensions();
            for (Iterator<?> j = exts.iterator(); j.hasNext() && continueLoading.get(); ) {
                final String next = (String) j.next();
                queue.execute(() -> GUIMediator.safeInvokeAndWait(() -> {
                    getIconForExtension(next);
                    if (!VIEW.isViewAvailable())
                        continueLoading.set(false);
                }));
            }
        }
    }

    /**
     * A smarter FileView.
     */
    private static abstract class SmartFileView extends FileView {
        /**
         * Checks to see if the given icon is cached.
         */
        protected abstract boolean isIconCached(File f);

        /**
         * Removes a file from the cache, if possible.
         */
        protected abstract boolean removeFromCache(File f);

        /**
         * Determines if this view is working.  By default, returns true.
         */
        boolean isViewAvailable() {
            return true;
        }
    }

    /**
     * Delegates to another FileView, catching NPEs & UnsatisfiedLinkErrors.
     * <p>
     * The NPE catching is required because of poorly built methods in
     * javax.swing.filechooser.FileSystemView that print true
     * exceptions to System.err and return null, instead of
     * letting the exception propogate.
     * <p>
     * The ULE catching is required because of strange Swing errors
     * that can't find the native code to:
     * sun.awt.shell.Win32ShellFolder2.getFileSystemPath(I)Ljava/lang/String;     *
     * See: LWC-1174.
     */
    private static class DelegateFileView extends SmartFileView {
        private final SmartFileView DELEGATE;
        private boolean linkFailed = false;

        DelegateFileView(SmartFileView real) {
            DELEGATE = real;
        }

        @Override
        public boolean isViewAvailable() {
            return !linkFailed;
        }

        @Override
        public Icon getIcon(final File f) {
            try {
                return DELEGATE.getIcon(f);
            } catch (NullPointerException npe) {
                return null;
            } catch (UnsatisfiedLinkError ule) {
                linkFailed = true;
                return null;
            }
        }

        @Override
        public String getDescription(File f) {
            try {
                return DELEGATE.getDescription(f);
            } catch (NullPointerException npe) {
                return null;
            } catch (UnsatisfiedLinkError ule) {
                linkFailed = true;
                return null;
            }
        }

        @Override
        public String getName(File f) {
            try {
                return DELEGATE.getName(f);
            } catch (NullPointerException npe) {
                return null;
            } catch (UnsatisfiedLinkError ule) {
                linkFailed = true;
                return null;
            }
        }

        @Override
        public String getTypeDescription(File f) {
            try {
                return DELEGATE.getTypeDescription(f);
            } catch (NullPointerException npe) {
                return null;
            } catch (UnsatisfiedLinkError ule) {
                linkFailed = true;
                return null;
            }
        }

        @Override
        public Boolean isTraversable(File f) {
            try {
                return DELEGATE.isTraversable(f);
            } catch (NullPointerException npe) {
                return null;
            } catch (UnsatisfiedLinkError ule) {
                linkFailed = true;
                return null;
            }
        }

        @Override
        public boolean isIconCached(File f) {
            return DELEGATE.isIconCached(f);
        }

        /**
         * Does nothing.
         */
        @Override
        public boolean removeFromCache(File f) {
            return DELEGATE.removeFromCache(f);
        }
    }

    /**
     * A FileSystemView FileView.
     */
    private static class FSVFileView extends SmartFileView {
        private final FileSystemView VIEW = FileSystemView.getFileSystemView();
        private final Map<File, Icon> CACHE = new FixedsizeForgetfulHashMap<>(50000);

        @Override
        public String getDescription(File f) {
            return VIEW.getSystemTypeDescription(f);
        }

        @Override
        public Icon getIcon(final File f) {
            Icon icon = CACHE.get(f);
            if (icon == null) {
                icon = VIEW.getSystemIcon(f);
                CACHE.put(f, icon);
            }
            return icon;
        }

        @Override
        public String getName(File f) {
            return VIEW.getSystemDisplayName(f);
        }

        @Override
        public String getTypeDescription(File f) {
            return VIEW.getSystemTypeDescription(f);
        }

        @Override
        public Boolean isTraversable(File f) {
            return VIEW.isTraversable(f);
        }

        @Override
        public boolean isIconCached(File f) {
            return CACHE.containsKey(f);
        }

        /**
         * Removes the given file from the cache.
         */
        @Override
        public boolean removeFromCache(File f) {
            return CACHE.remove(f) != null;
        }
    }

    /**
     * A wrapper around JFileChooser's view that returns true for caching
     * once a file has been looked up.
     */
    private static class SmartChooserView extends SmartFileView {
        /**
         * The view this uses.
         */
        private final FileView DELEGATE;
        /**
         * A set of the most recently requested Files.
         */
        private final Set<File> CACHE = new FixedsizeForgetfulHashSet<>(5000, 1000);

        SmartChooserView(FileView delegate) {
            DELEGATE = delegate;
        }

        @Override
        public String getDescription(File f) {
            return DELEGATE.getDescription(f);
        }

        @Override
        public Icon getIcon(File f) {
            CACHE.add(f);
            return DELEGATE.getIcon(f);
        }

        @Override
        public String getName(File f) {
            return DELEGATE.getName(f);
        }

        @Override
        public String getTypeDescription(File f) {
            return DELEGATE.getTypeDescription(f);
        }

        @Override
        public Boolean isTraversable(File f) {
            return DELEGATE.isTraversable(f);
        }

        @Override
        public boolean isIconCached(File f) {
            return CACHE.contains(f);
        }

        @Override
        public boolean removeFromCache(File f) {
            return CACHE.remove(f);
        }
    }
}
