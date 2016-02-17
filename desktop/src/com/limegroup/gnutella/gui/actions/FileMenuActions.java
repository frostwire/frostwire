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

package com.limegroup.gnutella.gui.actions;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.frostwire.gui.bittorrent.CreateTorrentDialog;
import com.frostwire.gui.bittorrent.SendFileProgressDialog;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.limegroup.gnutella.gui.LimeTextField;
import com.limegroup.gnutella.gui.ButtonRow;
import com.limegroup.gnutella.gui.FileChooserHandler;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconManager;
import com.limegroup.gnutella.gui.MultiLineLabel;
import com.limegroup.gnutella.gui.TorrentFileFilter;
import com.limegroup.gnutella.gui.search.MagnetClipboardListener;

public final class FileMenuActions {

    static final int SPACE = 6;

    /** Shows the File, Open Magnet or Torrent dialog box to let the user enter a magnet or torrent. */
    public static class OpenMagnetTorrentAction extends AbstractAction {

        private JDialog dialog = null;
        private LimeTextField PATH_FIELD;

        public OpenMagnetTorrentAction() {
            super(I18n.tr("O&pen .Torrent or Magnet"));
            PATH_FIELD = new LimeTextField(34);
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Opens a magnet link or torrent file"));
        }

        public void actionPerformed(ActionEvent e) {
            if (dialog == null)
                createDialog();

            // clear input before dialog is shown
            PATH_FIELD.setText(MagnetClipboardListener.getMagnetOrTorrentURLFromClipboard());

            // display modal dialog centered
            dialog.pack();
            GUIUtils.centerOnScreen(dialog);
            dialog.setVisible(true);
        }

        private class OpenDialogWindowAdapter extends WindowAdapter {
            public void windowOpened(WindowEvent e) {
                PATH_FIELD.requestFocusInWindow();
            }
        }

        private void createDialog() {

            dialog = new JDialog(GUIMediator.getAppFrame(), I18n.tr("Download .Torrent or Magnet or YouTube video link"), true);
            dialog.addWindowListener(new OpenDialogWindowAdapter());
            JPanel panel = (JPanel) dialog.getContentPane();
            GUIUtils.addHideAction(panel);
            panel.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();

            panel.setBorder(new EmptyBorder(2 * SPACE, SPACE, SPACE, SPACE));

            // download icon
            constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.gridwidth = 1;
            constraints.gridheight = 1;
            constraints.weightx = 0.0;
            constraints.weighty = 0.0;
            constraints.anchor = GridBagConstraints.CENTER;
            constraints.insets = new Insets(0, 0, 2 * SPACE, 0);
            panel.add(new JLabel(IconManager.instance().getIconForButton("SEARCH_DOWNLOAD")), constraints);

            // instructions label
            constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = 0;
            constraints.gridwidth = GridBagConstraints.REMAINDER;
            constraints.gridheight = 1;
            constraints.weightx = 1.0;
            constraints.weighty = 0.0;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(0, SPACE, 2 * SPACE, 0);
            //            panel.add(new MultiLineLabel(I18n.tr("Type a magnet link, the file path or web address of a torrent file, or a YouTube Web address and FrostWire will start downloading it for you."),
            //                   true), constraints);

            panel.add(new MultiLineLabel(I18n.tr("Type a magnet link, the file path or web address of a torrent file and FrostWire will start downloading it for you."), true), constraints);

            // open label
            constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 1;
            panel.add(new JLabel(I18n.tr("Open:")), constraints);

            // spacer between the open label and the text field
            constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            constraints.gridwidth = GridBagConstraints.REMAINDER;
            constraints.insets = new Insets(0, SPACE, 0, 0);
            panel.add(PATH_FIELD, constraints);

            ButtonRow row = new ButtonRow(new Action[] { new PasteAction(), new BrowseAction() }, ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);

            constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = 2;
            constraints.gridwidth = GridBagConstraints.REMAINDER;
            constraints.insets = new Insets(SPACE, SPACE, 0, 0);
            constraints.anchor = GridBagConstraints.WEST;
            panel.add(row, constraints);

            // add vertical spacer/spring
            constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.BOTH;
            constraints.gridx = 0;
            constraints.gridy = 3;
            constraints.gridwidth = GridBagConstraints.REMAINDER;
            constraints.weighty = 1;
            panel.add(new JPanel(), constraints);

            row = new ButtonRow(new Action[] { new OkAction(), new CancelAction() }, ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);

            constraints = new GridBagConstraints();
            constraints.gridx = 4;
            constraints.gridy = 4;
            constraints.insets = new Insets(2 * SPACE, 0, 0, 0);
            constraints.anchor = GridBagConstraints.EAST;
            panel.add(row, constraints);

            dialog.getRootPane().setDefaultButton(row.getButtonAtIndex(0));
            dialog.setMinimumSize(new Dimension(250, 150));
        }
        
        private void dismissDialog() {
            dialog.setVisible(false);
            dialog.dispose();
        }

        private class PasteAction extends AbstractAction {

            /**
             * 
             */
            private static final long serialVersionUID = -3351075105994389491L;

            public PasteAction() {
                super(I18n.tr("Paste"));
            }

            public void actionPerformed(ActionEvent a) {
                PATH_FIELD.paste();
            }
        }

        private class BrowseAction extends AbstractAction {

            /**
             * 
             */
            private static final long serialVersionUID = 3000234847843826596L;

            public BrowseAction() {
                super(I18n.tr("Browse..."));
            }

            public void actionPerformed(ActionEvent a) {
                File file = FileChooserHandler.getInputFile(GUIMediator.getAppFrame(), TorrentFileFilter.INSTANCE);
                if (file != null)
                    PATH_FIELD.setText(file.getAbsolutePath());
            }
        }

        private class OkAction extends AbstractAction {

            public OkAction() {
                super(I18n.tr("OK"));
            }

            public void actionPerformed(ActionEvent a) {
                String str = PATH_FIELD.getText();
                if (openMagnetOrTorrent(str, FileMenuActions.ActionInvocationSource.FROM_FILE_MENU)) {
                    dismissDialog();
                } else if (str.contains("youtube.com/watch?") || str.contains("http://youtu.be/")) {
                    GUIMediator.instance().startSearch(str);
                    dismissDialog();
                } else {
                    GUIMediator.showError(I18n.tr("FrostWire cannot download this address. Make sure you typed it correctly, and then try again."));
                }
            }
        }

        private class CancelAction extends AbstractAction {

            /**
             * 
             */
            private static final long serialVersionUID = 3350673081539434959L;

            public CancelAction() {
                super(I18n.tr("Cancel"));
            }

            public void actionPerformed(ActionEvent a) {
                GUIUtils.getDisposeAction().actionPerformed(a);
            }
        }
    }

    /**
     * Opens a magnet link, a Web address to a torrent file, or a path to a
     * torrent file on the disk.
     * 
     * Note that DNDUtils performs similar steps when the user drops a magnet
     * or torrent on the window.
     * 
     * @param userText The text of the path, link, or address the user entered
     * @return true if it was valid and we opened it
     */
    public static boolean openMagnetOrTorrent(final String userText, ActionInvocationSource invokedFrom) {

        if (userText.startsWith("magnet:?xt=urn:btih")) {
            GUIMediator.instance().openTorrentURI(userText, true);
            UXStats.instance().log(invokedFrom == ActionInvocationSource.FROM_FILE_MENU ? UXAction.DOWNLOAD_MAGNET_URL_FROM_FILE_ACTION : UXAction.DOWNLOAD_MAGNET_URL_FROM_SEARCH_FIELD);
            return true;
        } else if (userText.matches(".*youtube.com.*") || userText.matches(".*youtu.be.*") || userText.matches(".*y2u.be.*")) {
            UXStats.instance().log(invokedFrom == ActionInvocationSource.FROM_FILE_MENU ? UXAction.DOWNLOAD_CLOUD_URL_FROM_FILE_ACTION : UXAction.DOWNLOAD_CLOUD_URL_FROM_SEARCH_FIELD);
            return false;
        } else if (userText.matches(".*soundcloud.com.*")) {
            //the new soundcloud redirects to what seems to be an ajax page
            String soundCloudURL = userText.replace("soundcloud.com/#", "soundcloud.com/");
            GUIMediator.instance().openSoundcloudTrackUrl(soundCloudURL, null);
            UXStats.instance().log(invokedFrom == ActionInvocationSource.FROM_FILE_MENU ? UXAction.DOWNLOAD_CLOUD_URL_FROM_FILE_ACTION : UXAction.DOWNLOAD_CLOUD_URL_FROM_SEARCH_FIELD);
            return true;
        } else if (userText.startsWith("http://") || (userText.startsWith("https://"))) {
            GUIMediator.instance().openTorrentURI(userText, true);
            UXStats.instance().log(invokedFrom == ActionInvocationSource.FROM_FILE_MENU ? UXAction.DOWNLOAD_TORRENT_URL_FROM_FILE_ACTION : UXAction.DOWNLOAD_TORRENT_URL_FROM_SEARCH_FIELD);
            return true;
        } else {

            // See if it's a path to a file on the disk
            File file = new File(userText);
            if (isFileSystemPath(file)) {
                if (file.exists()) {
                    GUIMediator.instance().openTorrentFile(file, true); // Open the torrent file
                    return true;
                } else {
                    // TODO show error dialog telling
                    // user that they entered a bad file name    
                }
                // Not a file
            }
        }

        // Invalid text, nothing opened
        return false;
    }

    private static boolean isFileSystemPath(File file) {
        return file.isAbsolute();
    }

    /**
     * Exits the application.
     */
    public static class CloseAction extends AbstractAction {

        /**
         * 
         */
        private static final long serialVersionUID = -456007457702576349L;

        public CloseAction() {
            super(I18n.tr("&Close"));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Close the program's main window"));
        }

        public void actionPerformed(ActionEvent e) {
            GUIMediator.close(false);
        }
    }

    public static class ExitAction extends AbstractAction {

        public ExitAction() {
            super(I18n.tr("E&xit"));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Close and exit the program"));
        }

        public void actionPerformed(ActionEvent e) {
            GUIMediator.shutdown();
        }
    }

    public static class CreateTorrentAction extends AbstractAction {
        private static final long serialVersionUID = 1494672346951877693L;

        public CreateTorrentAction() {
            super(I18n.tr("Create New Torrent"));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Create a new .torrent file"));
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            CreateTorrentDialog dlg = new CreateTorrentDialog(GUIMediator.getAppFrame());
            dlg.setVisible(true);            
        }

    }

    public static class SendFileAction extends AbstractAction {

        public SendFileAction() {
            super(I18n.tr("Send File or Folder..."));
            putValue(Action.LONG_DESCRIPTION, I18n.tr("Send a file or a folder to a friend"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            SendFileProgressDialog dlg = new SendFileProgressDialog(GUIMediator.getAppFrame());
            dlg.setVisible(true);
            UXStats.instance().log(UXAction.SHARING_TORRENT_CREATED_WITH_SEND_TO_FRIEND_FROM_MENU);
        }
    }
    
    public enum ActionInvocationSource {
        FROM_FILE_MENU,
        FROM_SEARCH_FIELD
    }
}
