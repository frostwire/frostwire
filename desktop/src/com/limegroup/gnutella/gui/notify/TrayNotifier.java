package com.limegroup.gnutella.gui.notify;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import org.limewire.util.OSUtils;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Puts an icon and menu in the system tray.
 * Works on Windows, Linux, and any other platforms JDIC supports.
 */
public final class TrayNotifier implements NotifyUser {
    private SystemTray _tray;
    private TrayIcon _icon;
    private final boolean _supportsTray;

    public TrayNotifier() {
        try {
            _tray = SystemTray.getSystemTray();
        } catch (Exception e) {
            _tray = null;
            _supportsTray = false;
            return;
        }
        _supportsTray = true;
        buildPopupMenu();
        String iconFileName = "frosticon";
        if (OSUtils.isLinux()) {
            iconFileName += "_linux";
        }
        buildTrayIcon(iconFileName);
    }

    private void buildTrayIcon(String imageFileName) {
        _icon = new TrayIcon(GUIMediator.getThemeImage(imageFileName).getImage(),
                "FrostWire",
                GUIMediator.getTrayMenu());
        _icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
                    GUIMediator.restoreView();
                }
            }
        });
        // left click restores.  This happens on the awt thread.
        _icon.addActionListener(e -> GUIMediator.restoreView());
        _icon.setImageAutoSize(true);
    }

    private void buildPopupMenu() {
        PopupMenu menu = GUIMediator.getTrayMenu();
        // restore
        MenuItem item = new MenuItem(I18n.tr("Restore"));
        item.addActionListener(e -> GUIMediator.restoreView());
        menu.add(item);
        menu.addSeparator();
        // about box
        item = new MenuItem(I18n.tr("About"));
        item.addActionListener(e -> GUIMediator.showAboutWindow());
        menu.add(item);
        menu.addSeparator();
        // exit
        item = new MenuItem(I18n.tr("Exit"));
        item.addActionListener(e -> GUIMediator.shutdown());
        menu.add(item);
    }

    @Override
    public boolean showTrayIcon() {
        if (_tray == null || !supportsSystemTray()) {
            return false;
        }
        try {
            _tray.add(_icon);
        } catch (Exception iae) {
            // Sometimes JDIC can't load the trayIcon :(
            return false;
        }
        return true;
    }

    @Override
    public boolean supportsSystemTray() {
        //gub: could be SystemTray.isSupported(), not sure how fast that is though.
        return _supportsTray;
    }

    @Override
    public void hideTrayIcon() {
        _tray.remove(_icon);
    }
}
