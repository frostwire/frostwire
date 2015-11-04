package com.frostwire.gui.theme;

import java.lang.reflect.Field;

import javax.swing.JMenu;

public class SkinMenu extends JMenu {

    public SkinMenu() {
        ensurePopupMenuCreated();
    }

    public SkinMenu(String s) {
        super(s);

        ensurePopupMenuCreated();
    }

    private void ensurePopupMenuCreated() {
        SkinPopupMenu popupMenu = new SkinPopupMenu();
        popupMenu.setInvoker(this);
        popupListener = createWinListener(popupMenu);

        try {
            Field f = JMenu.class.getDeclaredField("popupMenu");
            f.setAccessible(true);
            f.set(this, popupMenu);
        } catch (Throwable e) {
            e.printStackTrace();
            // ignore
        }
    }
}
