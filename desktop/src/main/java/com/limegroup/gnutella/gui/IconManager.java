package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;
import java.io.File;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.swing.*;
import org.apache.commons.io.FilenameUtils;

/** Manages finding native icons for files and file types. */
public class IconManager {
  /** The sole instance of this IconManager class. */
  private static volatile IconManager INSTANCE;

  /** The current FileIconController; the native one replaces the basic one once it is built. */
  private volatile FileIconController fileController;

  /** The current ButtonIconController. */
  private final ButtonIconController buttonController;

  /** Constructs a new IconManager. */
  private IconManager() {
    this(
        OSUtils.isMacOSX() || OSUtils.isWindows(),
        NativeFileIconController::new,
        SwingUtilities::invokeLater);
  }

  /**
   * Starts with the basic controller, whose construction never blocks, and builds the native one
   * later through {@code later}: the EDT in production, since on macOS the native controller builds
   * a JFileChooser, a Swing component. Building it no longer touches the disk
   * (NativeFileIconController.NativeUiChooser, frostwire/frostwire#1361).
   */
  IconManager(boolean nativeIcons, Supplier<FileIconController> nativeController, Executor later) {
    buttonController = new ButtonIconController();
    fileController = new BasicFileIconController();
    if (nativeIcons) {
      later.execute(
          () -> {
            FileIconController newController = nativeController.get();
            if (newController.isValid()) {
              fileController = newController;
            }
          });
    }
  }

  /** Returns the sole instance of this IconManager class. */
  public static IconManager instance() {
    if (INSTANCE == null) {
      synchronized (IconManager.class) {
        if (INSTANCE == null) INSTANCE = new IconManager();
      }
    }
    return INSTANCE;
  }

  /**
   * Returns the icon associated with this file. If the file does not exist, or no icon can be
   * found, returns the icon associated with the extension.
   */
  public Icon getIconForFile(File f) {
    validate();
    String ext = f != null ? FilenameUtils.getExtension(f.getName()) : null;
    if (f != null && ext != null && ext.toLowerCase().endsWith("torrent")) {
      return GUIMediator.getThemeImage("frosthires");
    }
    return fileController.getIconForFile(f);
  }

  /** Returns the icon associated with the extension. */
  public Icon getIconForExtension(String ext) {
    validate();
    if (ext != null && ext.toLowerCase().endsWith("torrent")) {
      return GUIMediator.getThemeImage("frosthires");
    }
    return fileController.getIconForExtension(ext);
  }

  /** Returns true if the icon can be returned immediately. */
  public boolean isIconForFileAvailable(File f) {
    validate();
    return fileController.isIconForFileAvailable(f);
  }

  /** Erases the button's history. */
  void wipeButtonIconCache() {
    buttonController.wipeButtonIconCache();
  }

  /** Retrieves the icon for the specified button name. */
  public Icon getIconForButton(String buttonName) {
    return buttonController.getIconForButton(buttonName);
  }

  /** Retrieves the rollover image for the specified button name. */
  Icon getRolloverIconForButton(String buttonName) {
    return buttonController.getRolloverIconForButton(buttonName);
  }

  /**
   * Reverts the IconController to a basic controller if at any point in time the controller becomes
   * invalid.
   *
   * <p>Returns true if the current controller is already valid.
   */
  private void validate() {
    if (!fileController.isValid()) fileController = new BasicFileIconController();
  }
}
