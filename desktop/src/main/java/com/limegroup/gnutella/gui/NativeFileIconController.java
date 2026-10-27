package com.limegroup.gnutella.gui;

import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.settings.UISettings;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.FileChooserUI;
import org.apache.commons.io.FilenameUtils;
import org.limewire.collection.FixedsizeForgetfulHashMap;
import org.limewire.collection.FixedsizeForgetfulHashSet;

/** A FileIconController that attempts to return native icons. */
public class NativeFileIconController implements FileIconController {
  /** The view that retrieves the icon from the filesystem. */
  private final SmartFileView VIEW;

  /** A mapping from String (extension) to Icon. */
  private final Map<String, Icon> EXTENSIONS = new ConcurrentHashMap<>();

  /** A marker null icon so we don't create a file everytime if the icon can't be found. */
  private final Icon NULL = new ImageIcon();

  /**
   * Runs native lookups requested from the EDT, and the preload. Windows: a background queue
   * (FileSystemView shell calls run on the JDK's own COM thread and are safe from any thread).
   * macOS: deferred EDT events, because the Aqua chooser view is confined to the EDT.
   */
  private final Executor lookups;

  /** Icon shown while the native one loads: the generic icon for the file type. */
  private final Function<String, Icon> placeholders;

  /** Files and extensions whose lookup is queued, so each is requested once. */
  private final Set<Object> pending = ConcurrentHashMap.newKeySet();

  /** Files already looked up that the view did not cache (missing files, failed lookups). */
  private final Set<File> attempted =
      Collections.synchronizedSet(new FixedsizeForgetfulHashSet<>(5000, 1000));

  /**
   * Constructs the NativeFileIconController. This constructor may block as the JFileChooser is
   * constructed.
   */
  NativeFileIconController() {
    this(
        getNativeFileView(),
        UISettings.PRELOAD_NATIVE_ICONS.getValue(),
        OSUtils.isWindows()
            ? ExecutorsHelper.newProcessingQueue("IconLoader")
            : SwingUtilities::invokeLater,
        new BasicFileIconController()::getIconForExtension);
  }

  NativeFileIconController(
      SmartFileView view,
      boolean preloadIcons,
      Executor lookups,
      Function<String, Icon> placeholders) {
    this.lookups = lookups;
    this.placeholders = placeholders;
    if (view == null) {
      VIEW = null;
    } else {
      VIEW = new DelegateFileView(view);
      if (preloadIcons) preload();
    }
  }

  /** Returns true if loading succeeded and the view hasn't screwed up. */
  public boolean isValid() {
    return VIEW != null && VIEW.isViewAvailable();
  }

  /** Returns true if we have requested this file recently. */
  public boolean isIconForFileAvailable(File f) {
    return VIEW.isIconCached(f);
  }

  /** Retrieves the native FileView. */
  private static SmartFileView getNativeFileView() {
    // Deadlocks happen on Windows when using file-chooser based view.
    if (OSUtils.isWindows()) return constructFSVView();
    // macOS uses the JFileChooser native view: FileSystemView only returns generic
    // icons on JDK 26, while the Aqua file view yields rich per-type icons.
    // Resolving it goes through ResourceManager.getNativeUI, which degrades
    // gracefully (falls back to the current UI) when com.apple.laf is not
    // accessible; the mac bundle passes --add-exports for it (see appbundler.xml).
    else return constructFileChooserView();
  }

  /**
   * Constructs a JFileChooser-based FileView. This doesn't work consistently on Java 1.5.0_10 on
   * Windows, because it deadlocks if called outside of the Swing thread.
   *
   * @return
   */
  private static SmartFileView constructFileChooserView() {
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
        chooser =
            new JFileChooser() {
              {
                FileChooserUI ui = (FileChooserUI) ResourceManager.getNativeUI(this);
                setUI(ui);
                setFileView(ui.getFileView(this));
              }
            };
      } catch (NullPointerException | ArrayIndexOutOfBoundsException ignored) {
      }
      if (chooser != null) break;
    }
    if (chooser == null) {
      return null;
    } else {
      return new SmartChooserView(chooser.getFileView());
    }
  }

  /**
   * Constructs a FileSystemView-based FileView. Just to be safe, we do this on the Swing thread,
   * since we've seen deadlocks when constructing JFileChooser ones outside the Swing thread.
   */
  private static SmartFileView constructFSVView() {
    final AtomicReference<SmartFileView> ref = new AtomicReference<>();
    GUIMediator.safeInvokeAndWait(() -> ref.set(new FSVFileView()));
    return ref.get();
  }

  /**
   * Returns the native file icon for a file, if it exists. If it doesn't exist, returns the icon
   * for the extension of the file.
   *
   * <p>Never does a native lookup on the EDT: tables ask for icons from getValueAt() and while
   * painting, and on Windows an uncached icon is a COM round-trip into the shell
   * (Win32ShellFolder2.getIcon) that can block for seconds. An uncached icon requested from the EDT
   * returns a placeholder and loads in the background; tables pick it up on their next refresh.
   */
  public Icon getIconForFile(File f) {
    if (f == null) return null;
    if (VIEW.isIconCached(f)) {
      return VIEW.getIcon(f);
    }
    if (!SwingUtilities.isEventDispatchThread()) {
      return lookUpIconForFile(f);
    }
    String extension = FilenameUtils.getExtension(f.getName());
    if (!attempted.contains(f)) {
      requestLookup(
          f,
          () -> {
            lookUpIconForFile(f);
            attempted.add(f);
          });
    }
    Icon icon = cachedIconForExtension(extension);
    return icon != null ? icon : placeholder(extension);
  }

  /** The native lookup itself; f.exists() is disk I/O too, so this never runs on the EDT. */
  private Icon lookUpIconForFile(File f) {
    // We cannot get a nonexistent (non-cached) file from the view,
    // otherwise there'll be spurious exceptions.
    if (VIEW.isIconCached(f) || f.exists()) {
      return VIEW.getIcon(f);
    }
    String extension = FilenameUtils.getExtension(f.getName());
    return extension != null ? lookUpIconForExtension(extension) : null;
  }

  /**
   * Returns the icon associated with the extension. From the EDT, an uncached extension returns a
   * placeholder and loads in the background (see {@link #getIconForFile(File)}).
   */
  public Icon getIconForExtension(String ext) {
    if (ext == null) {
      return null;
    }
    String key = ext.trim().toLowerCase();
    if (EXTENSIONS.containsKey(key) || !SwingUtilities.isEventDispatchThread()) {
      return lookUpIconForExtension(key);
    }
    requestLookup(key, () -> lookUpIconForExtension(key));
    return placeholder(key);
  }

  /** The cached icon for the extension, without a native lookup; null when not cached yet. */
  private Icon cachedIconForExtension(String ext) {
    Icon icon = ext == null ? null : EXTENSIONS.get(ext.trim().toLowerCase());
    return icon == NULL ? null : icon;
  }

  private Icon placeholder(String ext) {
    try {
      return placeholders.apply(ext);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Queues one lookup per key; requests for a key whose lookup is already queued are dropped. */
  private void requestLookup(Object key, Runnable lookup) {
    if (!pending.add(key)) {
      return;
    }
    try {
      lookups.execute(
          () -> {
            try {
              lookup.run();
            } catch (Throwable ignored) {
              // a failed lookup leaves the placeholder; see lookUpIconForExtension
            } finally {
              pending.remove(key);
            }
          });
    } catch (RuntimeException rejected) {
      pending.remove(key);
    }
  }

  /** Looks up (once) and caches the native icon for the extension. Never call on the EDT. */
  private Icon lookUpIconForExtension(String ext) {
    try {
      ext = ext.trim().toLowerCase();
      Icon icon = EXTENSIONS.get(ext);
      // If we already had a cached icon..
      if (icon != null) {
        // So long as it wasn't the NULL marker,
        // return that icon.
        if (icon != NULL) return icon;
        else return null;
      }
      File file = null;
      try {
        file = File.createTempFile("dummy", "." + ext);
      } catch (Exception e1) {
        EXTENSIONS.put(ext, NULL);
        return null;
      }
      try {
        // The native FileView is constructed once with this controller. Creating a new
        // JFileChooser for every extension repeatedly initializes Aqua on the EDT and
        // can stall startup while preload walks the media types.
        icon = VIEW.getIcon(file);
      } catch (Exception e) {
        icon = null;
      } finally {
        file.delete();
      }
      // Remember failures too (NULL marker): an EDT request would otherwise queue
      // the same failing lookup again on every paint.
      EXTENSIONS.put(ext, icon != null ? icon : NULL);
      return icon;
    } catch (Throwable e) {
      // due to a NPE reported in BugManager
      // ignore
      return null;
    }
  }

  /** Preloads the icons of the default media types' extensions, never on the EDT on Windows. */
  private void preload() {
    final AtomicBoolean continueLoading = new AtomicBoolean(true);
    for (MediaType type : MediaType.getDefaultMediaTypes()) {
      for (Object ext : type.getExtensions()) {
        final String next = (String) ext;
        requestLookup(
            next,
            () -> {
              if (continueLoading.get()) {
                lookUpIconForExtension(next);
                if (!VIEW.isViewAvailable()) continueLoading.set(false);
              }
            });
      }
    }
  }

  /** A smarter FileView. */
  abstract static class SmartFileView extends FileView {
    /** Checks to see if the given icon is cached. */
    protected abstract boolean isIconCached(File f);

    /** Removes a file from the cache, if possible. */
    protected abstract boolean removeFromCache(File f);

    /** Determines if this view is working. By default, returns true. */
    boolean isViewAvailable() {
      return true;
    }
  }

  /**
   * Delegates to another FileView, catching NPEs & UnsatisfiedLinkErrors.
   *
   * <p>The NPE catching is required because of poorly built methods in
   * javax.swing.filechooser.FileSystemView that print true exceptions to System.err and return
   * null, instead of letting the exception propogate.
   *
   * <p>The ULE catching is required because of strange Swing errors that can't find the native code
   * to: sun.awt.shell.Win32ShellFolder2.getFileSystemPath(I)Ljava/lang/String; * See: LWC-1174.
   */
  private static class DelegateFileView extends SmartFileView {
    private final SmartFileView DELEGATE;
    private volatile boolean linkFailed = false;

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

    /** Does nothing. */
    @Override
    public boolean removeFromCache(File f) {
      return DELEGATE.removeFromCache(f);
    }
  }

  /** A FileSystemView FileView. */
  private static class FSVFileView extends SmartFileView {
    private final FileSystemView VIEW = FileSystemView.getFileSystemView();
    // Synchronized: icons are looked up on the IconLoader thread and read from the EDT.
    private final Map<File, Icon> CACHE =
        Collections.synchronizedMap(new FixedsizeForgetfulHashMap<>(50000));

    @Override
    public String getDescription(File f) {
      return VIEW.getSystemTypeDescription(f);
    }

    @Override
    public Icon getIcon(final File f) {
      // containsKey, not a null check: a file the shell has no icon for is cached as null and
      // must not trigger another COM lookup on every call.
      synchronized (CACHE) {
        if (CACHE.containsKey(f)) {
          return CACHE.get(f);
        }
      }
      Icon icon = VIEW.getSystemIcon(f);
      CACHE.put(f, icon);
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

    /** Removes the given file from the cache. */
    @Override
    public boolean removeFromCache(File f) {
      return CACHE.remove(f) != null;
    }
  }

  /**
   * A wrapper around JFileChooser's view that returns true for caching once a file has been looked
   * up.
   */
  private static class SmartChooserView extends SmartFileView {
    /** The view this uses. */
    private final FileView DELEGATE;

    /** A set of the most recently requested Files. */
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
