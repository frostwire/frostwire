/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.player.MediaSource;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.logging.Logger;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 *  This class serves as a holder for any static gui convenience
 *  methods.
 */
public final class GUIUtils {
    
    private static final Logger LOG = Logger.getLogger(GUIUtils.class);
    
	/**
     * Make sure the constructor is never called.
     */
    private GUIUtils() {}
    
    /**
     * Localizable Number Format constant for the current default locale
     * set at init time.
     */
    private static NumberFormat NUMBER_FORMAT0; // localized "#,##0"
    private static NumberFormat NUMBER_FORMAT1; // localized "#,##0.0"
    
    /** A full datetime format. */
    private static DateFormat FULL_DATETIME_FORMAT;
    
    /**
     * Localizable constants
     */
    public static String GENERAL_UNIT_KILOBYTES;
    public static String GENERAL_UNIT_MEGABYTES;
    public static String GENERAL_UNIT_GIGABYTES;
    public static String GENERAL_UNIT_TERABYTES;
    /* ambiguous name: means kilobytes/second, not kilobits/second! */
    public static String GENERAL_UNIT_KBPSEC;
    
    public static final HyperlinkListener HYPER_LISTENER;
    
    /**
     * An action that disposes the parent window.
     * Constructed lazily.
     */
    public static Action ACTION_DISPOSE;

    static {
        HYPER_LISTENER = new HyperlinkListener() {
			public void hyperlinkUpdate(HyperlinkEvent he) {
				if(he.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				    URL url = he.getURL();
				    if(url != null)
    				    GUIMediator.openURL(url.toExternalForm());
                }
			}
		};
        resetLocale();
    }
    
    static void resetLocale() {
        NUMBER_FORMAT0 = NumberFormat.getNumberInstance(GUIMediator.getLocale());
        NUMBER_FORMAT0.setMaximumFractionDigits(0);
        NUMBER_FORMAT0.setMinimumFractionDigits(0);
        NUMBER_FORMAT0.setGroupingUsed(true);
        
        NUMBER_FORMAT1 = NumberFormat.getNumberInstance(GUIMediator.getLocale());
        NUMBER_FORMAT1.setMaximumFractionDigits(1);
        NUMBER_FORMAT1.setMinimumFractionDigits(1);
        NUMBER_FORMAT1.setGroupingUsed(true);

        FULL_DATETIME_FORMAT = 
            new SimpleDateFormat("EEE, MMM. d, yyyy h:mm a", GUIMediator.getLocale());
        
        GENERAL_UNIT_KILOBYTES =
            I18n.tr("KB");
        GENERAL_UNIT_MEGABYTES =
            I18n.tr("MB");
        GENERAL_UNIT_GIGABYTES =
            I18n.tr("GB");
        GENERAL_UNIT_TERABYTES =
            I18n.tr("TB");
        GENERAL_UNIT_KBPSEC =
            I18n.tr("KB/s");
    }
    
    /**
     * This static method converts the passed in number
     * into a localizable representation of an integer, with
     * digit grouping using locale dependant separators.
     *
     * @param value the number to convert to a numeric String.
     *
     * @return a localized String representing the integer value
     */
    public static String toLocalizedInteger(long value) {
        return NUMBER_FORMAT0.format(value);
    }
    
    /**
     * This static method converts the passed in number of bytes into a
     * kilobyte string grouping digits with locale-dependant thousand separator
     * and with "KB" locale-dependant unit at the end.
     *
     * @param bytes the number of bytes to convert to a kilobyte String.
     *
     * @return a String representing the number of kilobytes that the
     *         <code>bytes</code> argument evaluates to, with "KB" appended
     *         at the end.  If the input value is negative, the string
     *         returned will be "? KB".
     */
    public static String toKilobytes(long bytes) {
        if (bytes < 0)
            return "? " + GENERAL_UNIT_KILOBYTES;
        long kbytes = bytes / 1024;
         // round to nearest multiple, or round up if size below 1024
        if ((bytes & 512) != 0 || (bytes > 0 && bytes < 1024)) kbytes++;
        // result formating, according to the current locale
        return NUMBER_FORMAT0.format(kbytes) + GENERAL_UNIT_KILOBYTES;
    }
    
    /**
     * Converts the passed in number of bytes into a byte-size string.
     * Group digits with locale-dependant thousand separator if needed, but
     * with "KB", or "MB" or "GB" or "TB" locale-dependant unit at the end,
     * and a limited precision of 4 significant digits.
     *
     * @param bytes the number of bytes to convert to a size String.
     * @return a String representing the number of kilobytes that the
     *         <code>bytes</code> argument evaluates to, with
     *         "KB"/"MB"/"GB"/TB" appended at the end. If the input value is
     *         negative, the string returned will be "? KB".
     */
    public static String toUnitbytes(long bytes) {
        if (bytes < 0) {
            return "? " + GENERAL_UNIT_KILOBYTES;
        }
        long   unitValue; // the multiple associated with the unit
        String unitName;  // one of localizable units
        if (bytes < 0xA00000) {                // below 10MB, use KB
            unitValue = 0x400;
            unitName = GENERAL_UNIT_KILOBYTES;
        } else if (bytes < 0x280000000L) {     // below 10GB, use MB
            unitValue = 0x100000;
            unitName = GENERAL_UNIT_MEGABYTES;
        } else if (bytes < 0xA0000000000L) {   // below 10TB, use GB
            unitValue = 0x40000000;
            unitName = GENERAL_UNIT_GIGABYTES;
        } else {                                // at least 10TB, use TB
            unitValue = 0x10000000000L;
            unitName = GENERAL_UNIT_TERABYTES;
        }
        NumberFormat numberFormat; // one of localizable formats
        if ((double)bytes * 100 / unitValue < 99995)
            // return a minimum "100.0xB", and maximum "999.9xB"
            numberFormat = NUMBER_FORMAT1; // localized "#,##0.0"
        else
            // return a minimum "1,000xB"
            numberFormat = NUMBER_FORMAT0; // localized "#,##0"
        try {
            return numberFormat.format((double)bytes / unitValue) + " " + unitName;
        } catch(ArithmeticException ae) {
            return "0 " + unitName;
            // internal java error, just return 0.
        }
    }
    
    /**
     * Returns a label with multiple lines that is sized according to
     * the string parameter.
     *
     * @param msg the string that will be contained in the label.
     *
     * @return a MultiLineLabel sized according to the passed
     *  in string.
     */
    public static MultiLineLabel getSizedLabel(String msg) {
        Dimension dim = new Dimension();
        MultiLineLabel label = new MultiLineLabel(msg);
        FontMetrics fm = label.getFontMetrics(label.getFont());
        int width = fm.stringWidth(msg);
        dim.setSize(Integer.MAX_VALUE, width / 9); //what's this magic?
        label.setPreferredSize(dim);
        return label;
    }
    
    /**
     * Converts an rate into a human readable and localized KB/s speed.
     */
    public static String rate2speed(double rate) {
        return NUMBER_FORMAT0.format(rate) + " " + GENERAL_UNIT_KBPSEC;
    }
    
    /** Gets the full datetime formatter. */
    public static DateFormat getFullDateTimeFormat() {
        return FULL_DATETIME_FORMAT;
    }
    
    /**
     * Sets the child components of a component to all be either
     * opaque or not opaque.
     */
    public static void setOpaque(boolean op, JComponent c) {
        c.setOpaque(op);
        Component[] cs = c.getComponents();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] instanceof JComponent && !(cs[i] instanceof JTextField) && (!(cs[i] instanceof JButton))) {
                ((JComponent) cs[i]).setOpaque(op);
                setOpaque(op, (JComponent) cs[i]);
            }
        }
    }
    
    /**
     * Centers the given component.
     */
    public static JPanel center(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.add(c);
        return p;
    }  
    
    /**
     * Left flushes the given component.
     */
    public static JPanel left(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.add(c);
        return p;
    }

    /**
     * Gets the width of a given label.
     */
    public static int width(JLabel c) {
        FontMetrics fm = c.getFontMetrics(c.getFont());
        return fm.stringWidth(c.getText()) + 3;
    }
    
    /**
     * Determines if a font can display up to a point in the string.
     *
     * Returns -1 if it can display the whole string.
     */
    public static boolean canDisplay(Font f, String s) {
        int upTo = f.canDisplayUpTo(s);
        if(upTo >= s.length() || upTo == -1)
            return true;
        else
            return false;
    }
    
    /**
     * Adds a hide action to a JDialog.
     */
    public static void addHideAction(JDialog jd) {
        addHideAction((JComponent)jd.getContentPane());
    }
    
    /**
     * Adds an action to hide a window / dialog.
     *
     * On OSX, this is done by typing 'Command-W'.
     * On all other platforms, this is done by hitting 'ESC'.
     */
    public static void addHideAction(JComponent jc) {
        InputMap map = jc.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        map.put(getHideKeystroke(), "limewire.hideWindow");
        jc.getActionMap().put("limewire.hideWindow", getDisposeAction());
    }
    
    /**
     * Gets the keystroke for hiding a window according to the platform.
     */
    public static KeyStroke getHideKeystroke() {
        if(OSUtils.isMacOSX())
            return KeyStroke.getKeyStroke(KeyEvent.VK_W,
                            Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
        else
            return KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    }
    
    /**
     * Binds a key stroke to the given action for the component. The action is 
     * triggered when the key is pressed and the keyboard focus is withing the
     * specifiedd scope.
     *  
     * @param c component for which the keybinding is installed
     * @param key the key that triggers the action
     * @param a the action
     */
    public static void bindKeyToAction(JComponent c, KeyStroke key, Action a,
    		int focusScope) {
    	InputMap inputMap = c.getInputMap(focusScope);
        ActionMap actionMap = c.getActionMap();
        if (inputMap != null && actionMap != null) {
        	inputMap.put(key, a);
        	actionMap.put(a, a);
        }
    }
    
    /**
     * Convenience wrapper for {@link #bindKeyToAction(JComponent, KeyStroke,
     * Action, int) bindKeyToAction(c, key, a, JComponentn.WHEN_FOCUSED)}.
     */
    public static void bindKeyToAction(JComponent c, KeyStroke key, Action a) {
    	bindKeyToAction(c, key, a, JComponent.WHEN_FOCUSED);
    }
    
    
    /**
     * Returns (possibly constructing) the ESC action.
     */
    public static Action getDisposeAction() {
        if(ACTION_DISPOSE == null) {
            ACTION_DISPOSE = new AbstractAction() {
                
                /**
                 * 
                 */
                private static final long serialVersionUID = 3219036624812939826L;

                public void actionPerformed(ActionEvent ae) {
                    Window parent;
                    if(ae.getSource() instanceof Window)
                        parent = (Window)ae.getSource();
                    else
                        parent = SwingUtilities.getWindowAncestor((Component)ae.getSource());

                    if(parent != null)
                        parent.dispatchEvent(new WindowEvent(parent, WindowEvent.WINDOW_CLOSING));
                }
            };
        }
        return ACTION_DISPOSE;
    }
    
    /**
     * Fixes the InputMap to have the correct KeyStrokes registered for
     * actions on various OS's.
     *
     * Currently, this fixes OSX to use the 'meta' key instead of hard-coding
     * it to use the 'control' key for actions such as 'select all', etc..
     */
    public static void fixInputMap(JComponent jc) {
        InputMap map =
            jc.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        if(OSUtils.isMacOSX()) {
            replaceAction(map, 'A'); // select all
            replaceAction(map, 'C'); // copy
            replaceAction(map, 'V'); // paste
            replaceAction(map, 'X'); // cut
        }
    }
    
    /**
     * Moves the action for the specified character from the 'ctrl' mask
     * to the 'meta' mask.
     */
    private static void replaceAction(InputMap map, char c) {
        KeyStroke ctrl = KeyStroke.getKeyStroke("control pressed " + c);
        KeyStroke meta = KeyStroke.getKeyStroke("meta pressed " + c);
        if(ctrl == null || meta == null)
            return;
        Object action = map.get(ctrl);
        if(action != null) {
            map.remove(ctrl);
            map.put(meta, action);
        }
	}
	
	/**
	 * Returns the sole hyperlink listener.
	 */
	public static HyperlinkListener getHyperlinkListener() {
	    return HYPER_LISTENER;
    }
	
	/**
	 * Returns a <code>MouseListener</code> that changes the cursor and
	 * notifies <code>actionListener</code> on click.
	 */
	public static MouseListener getURLInputListener(final ActionListener actionListener) {
	    return new MouseAdapter() {
	        public void mouseEntered(MouseEvent e) {
	            JComponent comp = (JComponent)e.getComponent();
	            comp.getTopLevelAncestor().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	        }
	        public void mouseExited(MouseEvent e) {
	            JComponent comp = (JComponent)e.getComponent();
	            comp.getTopLevelAncestor().setCursor(Cursor.getDefaultCursor());
	        }
	        public void mouseClicked(MouseEvent e) {
	            actionListener.actionPerformed(new ActionEvent(e.getComponent(), 0, null));
	        }
	    };
	}

    /**
     * Returns a <code>MouseListener</code> that changes the cursor and opens
     * <code>url</code> on click.
     */
    public static MouseListener getURLInputListener(final String url) {
        return getURLInputListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                GUIMediator.openURL(url);
            }            
        });
    }
    
    /**
     * Determines if the Start On Startup option is availble.
     */
    public static boolean shouldShowStartOnStartupWindow() {
	//System.out.println("********START UP DEBUG: GUIUtils is going to verify mac or windows");
        return !CommonUtils.isPortable() && (OSUtils.isMacOSX() || WindowsUtils.isLoginStatusAvailable());
    }
    
    /**
     * Converts all spaces in the string to non-breaking spaces.
     *
     * Adds 'preSpaces' number of non-breaking spaces prior to the string.
     */
    public static String convertToNonBreakingSpaces(int preSpaces, String s) {
        StringBuilder b = new StringBuilder(preSpaces + s.length());
        for(int i = 0; i < preSpaces; i++)
            b.append('\u00a0');
        b.append(s.replace(' ', '\u00a0'));
        return b.toString();
    }
    
    /**
     * Convert a color object to a hex string
     **/
    public static String colorToHex(Color colorCode){
        int r = colorCode.getRed();
        int g = colorCode.getGreen();
        int b = colorCode.getBlue();
        
        return toHex(r) + toHex(g) + toHex(b);   
    }
    
    /**
     * Returns the int as a hex string.
     **/
    private static String toHex(int i) {
        String hex = Integer.toHexString(i).toUpperCase();
        if (hex.length() == 1)
            return "0" + hex;
        else
            return hex;
    }
    /**
     * Convert a hex string to a color object
     **/
    public static Color hexToColor(String hexString){
        int decimalColor;
        decimalColor = Integer.parseInt(hexString, 16);
        return new Color(decimalColor);
    }
    
    
    /**
     * Launches file or enqueues it.
     * If <code>audioLaunched</code> is true and the playlist is visible the 
     * file is enqueued instead of played.
     * @param file	
     * @param audioLaunched
     * @return if audio has been launched in limewire's player
     */
    public static boolean launchOrEnqueueFile(final File file, boolean audioLaunched) {
        return launchFile(file, false, audioLaunched);
    }
    
    /**
     * Launches a file to be played once and only once. If the playlist is currently playing and/or
     * set to continous this will preemept the current song and stop the player after completion
     */
    public static boolean launchOneTimeFile(final File file) {       
        return launchFile(file, true, false);
    }
    
    /**
     * Internal launch of a song to play. 
     * @param file - song to play
     * @param playOneTime - if true, begin playing song immediately even if other songs are currently playing
     *          after completing the song stops the player regardless of the continous setting
     * @param isPlaying - if true and playOneTime != true, will enqueue the song to the playlist rather
     *          than immediately playing it, otherwise will play immediately
     */
    private static boolean launchFile(final File file, boolean playOneTime, boolean isPlaying ) {
        String extension = FilenameUtils.getExtension(file.getName());
        if(extension != null && extension.equals("torrent")) {
            GUIMediator.instance().openTorrentFile(file, true);
            return false;
        }
        
        if (GUIMediator.isPlaylistVisible()) {
//            if (PlaylistMediator.getInstance().openIfPlaylist(file))
//                return false;
            
            if(MediaPlayer.isPlayableFile(file)) {
                if( playOneTime ) {
                    BackgroundExecutorService.schedule(new Runnable() {
                        public void run(){
                            GUIMediator.safeInvokeAndWait(new Runnable() {
                                public void run() {
                                    GUIMediator.instance().launchMedia(new MediaSource(file), false);
                                }
                            });
                        }
                    });
                }
                else if (!isPlaying) { 
                    BackgroundExecutorService.schedule(new Runnable() {
                        public void run(){
                            GUIMediator.safeInvokeAndWait(new Runnable() {
                                public void run() {
                                    GUIMediator.instance().launchMedia(new MediaSource(file), false);
                                }
                            });
                        }
                    });
                }
                else {
                    //PlaylistMediator.getInstance().addFileToPlaylist(file);
                }
                return true;
            }
        }
        
        GUIMediator.launchFile(file);
        return false;
    }

 /**
     * Launches an audio file.
     * If the FrostWire media player is enabled it will enqueue the song on the
     * playlist. It won't take into consideration if the song is complete or not.
     * If the frostwire player isn't enabled it will just use whatever is configured
     * as an external player to launch the file.
     * 
     * Note, this wont take in consideration if the song is being played or not.
     * It will launch it no matter what (maybe this causes problems)
     * 
     * @param file
     * @param audioLaunched
     * @return True if the song was launched with frostplayer
     */        
    public static boolean launchAndEnqueueFile(File file, boolean audioLaunched) {        
    	if (MediaPlayer.isPlayableFile(file) && GUIMediator.isPlaylistVisible()) {
    		GUIMediator.instance().attemptStopAudio();
			GUIMediator.instance().launchMedia(new MediaSource(file), false);
			return true;
    	}
    	else {
    		//use external player to launch file
    		return launchFile(file, false, audioLaunched);
    	}    	
    	//return false;
    }

    
	/**
	 * Sets the location of <tt>dialog</tt> so it appears centered regarding
	 * the main application or centered on the screen if the main application is
	 * not visible.
	 */
    public static void centerOnScreen(JDialog dialog) {
        if (GUIMediator.isAppVisible()) {
            dialog.setLocationRelativeTo(GUIMediator.getAppFrame());
        } else { 
            dialog.setLocation(GUIMediator.getScreenCenterPoint(dialog));
        }
    }
    
    /**
     * Returns <code>text</code> wrapped by an HTML table tag that is set to a
     * fixed width.
     * <p>
     * Note: It seems to be a possible to trigger a NullPointerException in
     * Swing when this is used in a JLabel: GUI-239.
     */
    public static String restrictWidth(String text, int width) {
        return "<html><table width=\"" + width + "\"><tr><td>" + text
                + "</td></tr></table></html>";
    }

    /**
     * Restricts the size of a component by setting its minimum size and 
     * maximum size to the value of its preferred size.
     *
     */
    public static void restrictSize(JComponent component, SizePolicy sizePolicy) {
        restrictSize(component, sizePolicy, false);
    }
    
    public static void restrictSize(JComponent component, SizePolicy sizePolicy, boolean addClientProperty) {
		switch (sizePolicy) {
		case RESTRICT_HEIGHT:
			int height = component.getPreferredSize().height;
			int width = component.getPreferredSize().width;
			component.setMinimumSize(new Dimension(width, height));
			//component.setPreferredSize(new Dimension(width, height));
			component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
			break;
		case RESTRICT_BOTH:
			height = component.getPreferredSize().height;
			width = component.getPreferredSize().width;
			component.setMinimumSize(new Dimension(width, height));
			//component.setPreferredSize(STANDARD_DIMENSION);
			component.setMaximumSize(new Dimension(width, height));
			break;
		case RESTRICT_NONE:
		    component.setMinimumSize(null);
		    component.setMaximumSize(null);
		}
		if (addClientProperty) {
		    component.putClientProperty(SizePolicy.class, sizePolicy);
		}
    }
    
    public static class EmptyIcon implements Icon {
	    private final String name;
	    private final int width;
	    private final int height;
	    
	    public EmptyIcon(String name, int width, int height) {
	        this.name = name;
	        this.width = width;
	        this.height = height;
	    }
	    
	    public void paintIcon(Component c, Graphics g, int x, int y) {}
	    public int getIconWidth() { return width; }
	    public int getIconHeight() { return height; }
	    public String toString() { return name; }
	}

	public enum SizePolicy { RESTRICT_NONE, RESTRICT_HEIGHT, RESTRICT_BOTH }
	
	/**
     * Using a little reflection here for a lack of any better way 
     * to access locale-specific char codes for menu mnemonics.
     * We could at least defer this in the future.
     *
     * @param str the key for the locale-specific char resource to
     *  look up -- the key as it appears in the locale-specific
     *  properties file
     * @return the code for the passed-in key as defined in 
     *  <tt>java.awt.event.KeyEvent</tt>, or -1 if no key code
     *  could be found
     */
    public static int getCodeForCharKey(String str) {
        int charCode = -1;
        String charStr = str.toUpperCase(Locale.US);
        if(charStr.length()>1) return -1;
        try {
            Field charField = KeyEvent.class.getField("VK_"+charStr);
            charCode = charField.getInt(KeyEvent.class);
        } catch (NoSuchFieldException e) {
            LOG.error("can't get key for: " + charStr, e);
        } catch (SecurityException e) {
            LOG.error("can't get key for: " + charStr, e);
        } catch (IllegalAccessException e) {
            LOG.error("can't get key for: " + charStr, e);
        }
        return charCode;
    }
    
    private static int getAmpersandPosition(String text) {
    	int index = -1;
    	while ((index = text.indexOf('&', index + 1)) != -1) {
    		if (index < text.length() - 1 && Character.isLetterOrDigit(text.charAt(index + 1))) {
    			break;
    		}
    	}
    	return index;
    }
    
    public static String stripAmpersand(String text) {
    	int index = getAmpersandPosition(text);
    	if (index >= 0) {
    		return text.substring(0, index) + text.substring(index + 1);
    	}
    	return text;
    }
    
    public static int getMnemonicKeyCode(String text) {
    	// parse out mnemonic key
    	int index = getAmpersandPosition(text);
    	if (index >= 0) {
    		return GUIUtils.getCodeForCharKey(text.substring(index + 1, index + 2));
    	}
    	return -1;
    }
    
    /**
     * It will adjust the column width to match the widest element.
     * (You might not want to use this for every column, consider some columns might be really long)
     * @param model
     * @param columnIndex
     * @param table
     * @return
     */
    public static void adjustColumnWidth(TableModel model, int columnIndex, int maxWidth, int rightPadding, JTable table) {
        if (columnIndex > model.getColumnCount() - 1) {
            //invalid column index
            return;
        }

        if (!model.getColumnClass(columnIndex).equals(String.class)) {
            return;
        }

        String longestValue = "";
        for (int row = 0; row < model.getRowCount(); row++) {
            String strValue = (String) model.getValueAt(row, columnIndex);
            if (strValue != null && strValue.length() > longestValue.length()) {
                longestValue = strValue;
            }
        }

        Graphics g = table.getGraphics();

        try {
            int suggestedWidth = (int) g.getFontMetrics(table.getFont()).getStringBounds(longestValue, g).getWidth();
            table.getColumnModel().getColumn(columnIndex).setPreferredWidth(((suggestedWidth > maxWidth) ? maxWidth : suggestedWidth) + rightPadding);
        } catch (Exception e) {
            table.getColumnModel().getColumn(columnIndex).setPreferredWidth(maxWidth);
            e.printStackTrace();
        }
    }
    
    public static void setTitledBorderOnPanel(JPanel panel, String title) {
        Border titleBorder = BorderFactory.createTitledBorder(title);
        Border lineBorder = BorderFactory.createLineBorder(ThemeMediator.LIGHT_BORDER_COLOR);
        Border border = BorderFactory.createCompoundBorder(lineBorder, titleBorder);
        panel.setBorder(border);
        panel.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
    }
}
