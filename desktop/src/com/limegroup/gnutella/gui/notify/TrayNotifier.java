package com.limegroup.gnutella.gui.notify;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;

import javax.swing.SwingUtilities;

import org.limewire.util.OSUtils;

import com.frostwire.logging.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UISettings;

/**
 * Puts an icon and menu in the system tray.
 * Works on Windows, Linux, and any other platforms JDIC supports.
 */
public class TrayNotifier implements NotifyUser {
	
    private static final Logger LOG = Logger.getLogger(DefaultNotificationRenderer.class);
    
	private SystemTray _tray;
	private TrayIcon _icon;
	private NotificationWindow notificationWindow;
	
	private boolean _supportsTray;
	
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

		buildTrayIcon("FrostWire", iconFileName);
		buildNotificationWindow();
	}

	private void buildTrayIcon(String desc, String imageFileName) {
	    //String tip = "FrostWire: Running the Gnutella Network";

	    _icon = new TrayIcon(GUIMediator.getThemeImage(imageFileName).getImage(), 
				 desc, 
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
      _icon.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
              GUIMediator.restoreView();
          }
      });
        
	    _icon.setImageAutoSize(true);
	} //buildTrayIcon
	
	private PopupMenu buildPopupMenu() {
		PopupMenu menu = GUIMediator.getTrayMenu();
		
		// restore
		MenuItem item = new MenuItem(I18n.tr("Restore"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				GUIMediator.restoreView();
			}
		});
		menu.add(item);
		
		menu.addSeparator();
		
		// about box
		item = new MenuItem(I18n.tr("About"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				GUIMediator.showAboutWindow();
			}
		});
		menu.add(item);
		
		menu.addSeparator();
		
		// exit
		item = new MenuItem(I18n.tr("Exit"));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				GUIMediator.shutdown();
			}
		});
		menu.add(item);
		
		return menu;
	}
	
	private void buildNotificationWindow() {
		notificationWindow = new NotificationWindow(GUIMediator.getAppFrame());
		notificationWindow.setLocationOffset(new Dimension(2, 7));
		notificationWindow.setTitle("FrostWire");
		notificationWindow.setIcon(GUIMediator.getThemeImage("frosticon.gif"));
	}
	
	public boolean showTrayIcon() {
		if (_tray == null || !supportsSystemTray()) {
			return false;
		}
		
	    try {
	        _tray.add(_icon);
	    } catch(Exception iae) {
	        // Sometimes JDIC can't load the trayIcon :(
	        return false;
	    }

        // XXX use the actual icon size once the necessary call is available in JDIC 
        //notificationWindow.setParentSize(_icon.getSize());
        notificationWindow.setParentSize(new Dimension(22, 22));

        return true;
	}
	
    public boolean supportsSystemTray() {
		//gub: could be SystemTray.isSupported(), not sure how fast that is though.
	    return _supportsTray;
    }

	public void hideTrayIcon() {
		_tray.remove(_icon);
		notificationWindow.setParentLocation(null);
		notificationWindow.setParentSize(null);
	}

	public void showMessage(Notification notification) {
	    try {
	        notificationWindow.addNotification(notification);
	        try {
	            notificationWindow.setParentLocation(getTryIconLocation(_icon));
	        } catch (Exception ignore) {
	            // thrown if the native peer is not found (GUI-273)?
	        }
	        notificationWindow.showWindow();
        } catch (Exception e) {
            // see GUI-239
            LOG.error("Disabling notifications due to error", e);
            UISettings.SHOW_NOTIFICATIONS.setValue(false);
            notificationWindow.hideWindowImmediately();
        }
	}

	public void hideMessage(Notification notification) {
		notificationWindow.removeNotification(notification);
	}

    public void updateUI() {
        SwingUtilities.updateComponentTreeUI(notificationWindow);
    }

    private Point getTryIconLocation(TrayIcon icon) throws Exception {
        Field peerField = icon.getClass().getDeclaredField("peer");
        peerField.setAccessible(true);

        Object peer = peerField.get(icon);

        Field eframeField = peer.getClass().getDeclaredField("eframe");
        eframeField.setAccessible(true);

        Object eframe = eframeField.get(peer);

        Component component = (Component) eframe;

        Point p = component.getLocation();

        SwingUtilities.convertPointToScreen(p, component);

        return p;
    }
}
