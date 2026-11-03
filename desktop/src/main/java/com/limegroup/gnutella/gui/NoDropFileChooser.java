/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Component;
import java.awt.HeadlessException;
import java.io.File;
import javax.swing.JDialog;
import javax.swing.JFileChooser;

/**
 * A JFileChooser whose dialog registers for no drag-and-drop with the OS. Its show*Dialog methods
 * build the dialog in {@link #createDialog} and show it right away. Its file name field, like every
 * text field, carries a look and feel drop target, and a file chooser takes no drops
 * (frostwire/frostwire#1471). {@link DropTargets} explains why that matters.
 */
public class NoDropFileChooser extends JFileChooser {

  public NoDropFileChooser() {}

  public NoDropFileChooser(File currentDirectory) {
    super(currentDirectory);
  }

  @Override
  protected JDialog createDialog(Component parent) throws HeadlessException {
    JDialog dialog = super.createDialog(parent);
    DropTargets.removeAll(dialog);
    return dialog;
  }
}
