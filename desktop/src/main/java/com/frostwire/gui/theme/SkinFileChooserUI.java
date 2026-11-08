/*
 * Copyright (c) 2005-2010 Substance Kirill Grouchnikov. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  o Neither the name of Substance Kirill Grouchnikov nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.frostwire.gui.theme;

import com.limegroup.gnutella.gui.util.WarmExecutors;
import java.io.File;
import java.util.concurrent.ExecutorService;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicFileChooserUI;

/**
 * UI for file chooser in <b>Substance</b> look and feel. The {@link BasicFileChooserUI} can't be
 * used on its own (creates an empty dialog).
 *
 * @author Kirill Grouchnikov
 */
public class SkinFileChooserUI extends BaseFileChooserUI {
  /** Custom file view - for system icons on the files. */
  private final SubstanceFileView fileView = new SubstanceFileView();

  /**
   * Creates the UI delegate for the specified file chooser.
   *
   * @param filechooser File chooser.
   */
  public SkinFileChooserUI(JFileChooser filechooser) {
    super(filechooser);
  }

  public static ComponentUI createUI(JComponent comp) {
    ThemeMediator.testComponentCreationThreadingViolation();
    return new SkinFileChooserUI((JFileChooser) comp);
  }

  /*
   * (non-Javadoc)
   *
   * @seejavax.swing.plaf.basic.BasicFileChooserUI#getFileView(javax.swing.
   * JFileChooser)
   */
  @Override
  public FileView getFileView(JFileChooser fc) {
    return fileView;
  }

  /** One worker for every chooser's icon lookups: a big folder must not flood a shared pool. */
  private static final ExecutorService ICON_LOOKUPS = WarmExecutors.serialQueue("FileChooserIcons");

  /**
   * System file icons and display names, looked up off the EDT (Icebase #e51928be4469,
   * #0d182adae29c, frostwire/frostwire#1428): the list renderer asks for both per row, and on
   * Windows each lookup waits for the shell over COM.
   */
  private class SubstanceFileView extends BasicFileView {
    /** The chooser's FileSystemView, read on the EDT for the lookups that run on the worker. */
    private volatile FileSystemView lookupView;

    private final AsyncFileLookupCache<Icon> icons =
        new AsyncFileLookupCache<>(
            ICON_LOOKUPS,
            f -> {
              FileSystemView view = lookupView;
              return view != null ? view.getSystemIcon(f) : null;
            },
            f -> AsyncFileLookupCache.BLANK_ICON);

    /**
     * The shell's display name (e.g. without a hidden extension, or a drive's label); the plain
     * file name shows while it loads.
     */
    private final AsyncFileLookupCache<String> names =
        new AsyncFileLookupCache<>(
            ICON_LOOKUPS,
            f -> {
              FileSystemView view = lookupView;
              return view != null ? view.getSystemDisplayName(f) : null;
            },
            SubstanceFileView::plainName);

    /** A file's own name, or its path for a root such as C:\, whose name is empty. */
    private static String plainName(File f) {
      String name = f.getName();
      return name.isEmpty() ? f.getPath() : name;
    }

    @Override
    public String getName(File f) {
      JFileChooser fileChooser = getFileChooser();
      if (f == null || fileChooser == null) {
        return null;
      }
      lookupView = fileChooser.getFileSystemView();
      return names.get(f, fileChooser::repaint);
    }

    @Override
    public Icon getCachedIcon(File f) {
      return icons.cached(f);
    }

    @Override
    public Icon getIcon(File f) {
      JFileChooser fileChooser = getFileChooser();
      if (fileChooser == null) {
        return null;
      }
      lookupView = fileChooser.getFileSystemView();
      return icons.get(f, fileChooser::repaint);
    }

    @Override
    public void cacheIcon(File f, Icon icon) {
      icons.put(f, icon);
    }

    @Override
    public void clearIconCache() {
      icons.clear();
      names.clear();
    }
  }
}
