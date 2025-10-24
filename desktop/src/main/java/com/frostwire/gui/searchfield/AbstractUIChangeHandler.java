package com.frostwire.gui.searchfield;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;

abstract class AbstractUIChangeHandler implements PropertyChangeListener {
    //prevent double installation.
    private final Set<JComponent> installed = new HashSet<>();

    public void install(JComponent c) {
        if (isInstalled(c)) {
            return;
        }
        c.addPropertyChangeListener("UI", this);
        installed.add(c);
    }

    private boolean isInstalled(JComponent c) {
        return installed.contains(c);
    }

    void uninstall(JComponent c) {
        c.removePropertyChangeListener("UI", this);
        installed.remove(c);
    }
}