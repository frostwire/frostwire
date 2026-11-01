package com.frostwire.gui.theme;

import java.lang.reflect.Field;
import javax.swing.*;

public class SkinMenu extends JMenu {
  /**
   * JMenu's private popup menu, which every SkinMenu replaces with a {@link SkinPopupMenu}. The JVM
   * generates a field's reflective accessor on its first use, defining classes, and that froze the
   * first right-click menu on slow machines (GitHub #1439). Held for the whole session so the
   * accessor {@link #warmUpPopupMenuAccess()} builds off the EDT is the one every menu reuses.
   */
  private static final Field POPUP_MENU = popupMenuField();

  public SkinMenu() {
    ensurePopupMenuCreated();
  }

  public SkinMenu(String s) {
    super(s);
    ensurePopupMenuCreated();
  }

  /**
   * Builds the reflective accessor for JMenu's popup menu field, so the first SkinMenu built on the
   * EDT does not. Touches no component, so it is safe on any thread.
   */
  public static void warmUpPopupMenuAccess() {
    if (POPUP_MENU == null) {
      return;
    }
    try {
      // Reading through the accessor builds it; a null target then fails, after the work is done.
      POPUP_MENU.get(null);
    } catch (NullPointerException expected) {
      // The accessor exists now.
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
  }

  private static Field popupMenuField() {
    try {
      Field f = JMenu.class.getDeclaredField("popupMenu");
      f.setAccessible(true);
      return f;
    } catch (Throwable e) {
      e.printStackTrace();
      return null;
    }
  }

  private void ensurePopupMenuCreated() {
    SkinPopupMenu popupMenu = new SkinPopupMenu();
    popupMenu.setInvoker(this);
    popupListener = createWinListener(popupMenu);
    if (POPUP_MENU == null) {
      return;
    }
    try {
      POPUP_MENU.set(this, popupMenu);
    } catch (Throwable e) {
      e.printStackTrace();
      // ignore
    }
  }
}
