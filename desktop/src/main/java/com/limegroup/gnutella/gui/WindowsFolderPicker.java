/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Windows' folder dialog: the shell's {@code IFileOpenDialog} with {@code FOS_PICKFOLDERS}, called
 * through JNA. The dialog runs on the calling thread, which it initializes as a single-threaded COM
 * apartment for the call; {@link NativeFolderChooser} calls it off the EDT. Loading this class
 * loads JNA and its native library, so it fails with a {@link LinkageError} where they're missing.
 */
final class WindowsFolderPicker implements NativeFolderChooser.Picker {
  private static final Logger LOG = Logger.getLogger(WindowsFolderPicker.class);

  private static final int COINIT_APARTMENTTHREADED = 0x2;
  private static final int COINIT_DISABLE_OLE1DDE = 0x4;
  private static final int CLSCTX_INPROC_SERVER = 0x1;

  private static final int FOS_NOCHANGEDIR = 0x8;
  private static final int FOS_PICKFOLDERS = 0x20;
  private static final int FOS_FORCEFILESYSTEM = 0x40;
  private static final int FOS_PATHMUSTEXIST = 0x800;

  private static final int SIGDN_FILESYSPATH = 0x80058000;

  /** HRESULT_FROM_WIN32(ERROR_CANCELLED): what {@code Show} returns when the user cancels. */
  private static final int HRESULT_CANCELLED = 0x800704C7;

  // vtable slots: IUnknown, then IModalWindow, IFileDialog (shobjidl_core.h)
  private static final int RELEASE = 2;
  private static final int SHOW = 3;
  private static final int SET_OPTIONS = 9;
  private static final int GET_OPTIONS = 10;
  private static final int SET_FOLDER = 12;
  private static final int SET_TITLE = 17;
  private static final int GET_RESULT = 20;
  // IShellItem
  private static final int GET_DISPLAY_NAME = 5;

  private static final Memory CLSID_FILE_OPEN_DIALOG = guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7");
  private static final Memory IID_IFILE_OPEN_DIALOG = guid("D57C7288-D4AD-4768-BE02-9D969532D960");
  private static final Memory IID_ISHELL_ITEM = guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");

  private final Function coInitializeEx = win32("ole32", "CoInitializeEx");
  private final Function coUninitialize = win32("ole32", "CoUninitialize");
  private final Function coCreateInstance = win32("ole32", "CoCreateInstance");
  private final Function coTaskMemFree = win32("ole32", "CoTaskMemFree");
  private final Function shCreateItemFromParsingName =
      win32("shell32", "SHCreateItemFromParsingName");

  @Override
  public Callable<File> prepare(Window owner, String title, File initialDirectory) {
    // The window handle is read here, on the EDT, where AWT expects it to be read.
    Pointer ownerHandle =
        owner != null && owner.isDisplayable() ? Native.getWindowPointer(owner) : null;
    return () -> show(ownerHandle, title, initialDirectory);
  }

  /** Shows the dialog, modal to {@code owner}; returns the chosen folder or null when cancelled. */
  private File show(Pointer owner, String title, File initialDirectory) throws IOException {
    check(
        coInitializeEx.invokeInt(
            new Object[] {null, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE}),
        "CoInitializeEx");
    try {
      PointerByReference dialogRef = new PointerByReference();
      check(
          coCreateInstance.invokeInt(
              new Object[] {
                CLSID_FILE_OPEN_DIALOG, null, CLSCTX_INPROC_SERVER, IID_IFILE_OPEN_DIALOG, dialogRef
              }),
          "CoCreateInstance(FileOpenDialog)");
      Pointer dialog = dialogRef.getValue();
      try {
        IntByReference options = new IntByReference();
        check(call(dialog, GET_OPTIONS, options), "IFileDialog::GetOptions");
        check(
            call(
                dialog,
                SET_OPTIONS,
                options.getValue()
                    | FOS_PICKFOLDERS
                    | FOS_FORCEFILESYSTEM
                    | FOS_PATHMUSTEXIST
                    | FOS_NOCHANGEDIR),
            "IFileDialog::SetOptions");
        if (title != null) {
          check(call(dialog, SET_TITLE, new WString(title)), "IFileDialog::SetTitle");
        }
        setFolder(dialog, initialDirectory);
        int shown = call(dialog, SHOW, owner);
        if (shown == HRESULT_CANCELLED) {
          return null;
        }
        check(shown, "IModalWindow::Show");
        return result(dialog);
      } finally {
        call(dialog, RELEASE);
      }
    } finally {
      coUninitialize.invokeVoid(new Object[0]);
    }
  }

  /** Opens the dialog at {@code folder}; Windows picks the folder when it doesn't exist. */
  private void setFolder(Pointer dialog, File folder) {
    if (folder == null) {
      return;
    }
    PointerByReference itemRef = new PointerByReference();
    int hr =
        shCreateItemFromParsingName.invokeInt(
            new Object[] {new WString(folder.getAbsolutePath()), null, IID_ISHELL_ITEM, itemRef});
    if (failed(hr)) {
      LOG.debug("Folder dialog can't start at " + folder + ": " + hresult(hr));
      return;
    }
    Pointer item = itemRef.getValue();
    try {
      int set = call(dialog, SET_FOLDER, item);
      if (failed(set)) {
        LOG.debug("Folder dialog can't start at " + folder + ": " + hresult(set));
      }
    } finally {
      call(item, RELEASE);
    }
  }

  private File result(Pointer dialog) throws IOException {
    PointerByReference itemRef = new PointerByReference();
    check(call(dialog, GET_RESULT, itemRef), "IFileDialog::GetResult");
    Pointer item = itemRef.getValue();
    try {
      PointerByReference pathRef = new PointerByReference();
      check(call(item, GET_DISPLAY_NAME, SIGDN_FILESYSPATH, pathRef), "IShellItem::GetDisplayName");
      Pointer path = pathRef.getValue();
      try {
        return new File(path.getWideString(0));
      } finally {
        coTaskMemFree.invokeVoid(new Object[] {path});
      }
    } finally {
      call(item, RELEASE);
    }
  }

  /** Calls the COM method in {@code slot} of {@code object}'s vtable; returns its HRESULT. */
  private static int call(Pointer object, int slot, Object... args) {
    Pointer method = object.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE);
    Object[] withThis = new Object[args.length + 1];
    withThis[0] = object;
    System.arraycopy(args, 0, withThis, 1, args.length);
    return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(withThis);
  }

  private static Function win32(String library, String name) {
    return Function.getFunction(library, name, Function.ALT_CONVENTION);
  }

  private static boolean failed(int hr) {
    return hr < 0;
  }

  private static void check(int hr, String what) throws IOException {
    if (failed(hr)) {
      throw new IOException(what + " failed: " + hresult(hr));
    }
  }

  private static String hresult(int hr) {
    return String.format("HRESULT 0x%08X", hr);
  }

  /** A GUID in memory, from its registry form. */
  private static Memory guid(String text) {
    String[] parts = text.split("-");
    Memory guid = new Memory(16);
    guid.setInt(0, (int) Long.parseLong(parts[0], 16));
    guid.setShort(4, (short) Integer.parseInt(parts[1], 16));
    guid.setShort(6, (short) Integer.parseInt(parts[2], 16));
    String tail = parts[3] + parts[4];
    for (int i = 0; i < 8; i++) {
      guid.setByte(8 + i, (byte) Integer.parseInt(tail.substring(2 * i, 2 * i + 2), 16));
    }
    return guid;
  }
}
