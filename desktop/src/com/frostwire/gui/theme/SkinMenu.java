package com.frostwire.gui.theme;

import javax.swing.*;
import java.lang.reflect.Field;

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
