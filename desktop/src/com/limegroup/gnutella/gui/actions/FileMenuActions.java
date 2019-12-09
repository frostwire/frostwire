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

import com.frostwire.gui.bittorrent.CreateTorrentDialog;
import com.frostwire.gui.bittorrent.SendFileProgressDialog;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.search.MagnetClipboardListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

public final class FileMenuActions {
    private static final int SPACE = 6;

    /**
     * Opens a magnet link, a Web address to a torrent file, or a path to a
     * torrent file on the disk.
     * <p>
     * Note that DNDUtils performs similar steps when the user drops a magnet
     * or torrent on the window.
     *
     * @param userText The text of the path, link, or address the user entered
     * @return true if it was valid and we opened it
     */
    public static boolean openMagnetOrTorrent(final String userText) {
        if (userText.startsWith("magnet:?xt=urn:btih")) {
            GUIMediator.instance().openTorrentURI(userText, true);
            return true;
        } else if (userText.matches(".*soundcloud.com.*")) {
            //the new soundcloud redirects to what seems to be an ajax page
            String soundCloudURL = userText.replace("soundcloud.com/#", "soundcloud.com/");
            GUIMediator.instance().openSoundcloudTrackUrl(soundCloudURL, null, true);
            return true;
        } else if (userText.startsWith("http://") || (userText.startsWith("https://"))) {
            GUIMediator.instance().openTorrentURI(userText, true);
            return true;
        } else {
            // See if it's a path to a file on the disk
            File file = new File(userText);
            if (isFileSystemPath(file)) {
                if (file.exists()) {
                    GUIMediator.instance().openTorrentFile(file, true); // Open the torrent file
                    return true;
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
     * Shows the File, Open Magnet or Torrent dialog box to let the user enter a magnet or torrent.
     */
    public static class OpenMagnetTorrentAction extends AbstractAction {
        private JDialog dialog = null;
        private final LimeTextField PATH_FIELD;

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

        private void createDialog() {
            dialog = new JDialog(GUIMediator.getAppFrame(), I18n.tr("Download .Torrent or Magnet link"), true);
            dialog.addWindowListener(new OpenDialogWindowAdapter());
            JPanel panel = (JPanel) dialog.getContentPane();
            GUIUtils.addHideAction(panel);
            panel.setLayout(new GridBagLayout());
            panel.setBorder(new EmptyBorder(2 * SPACE, SPACE, SPACE, SPACE));
            // download icon
            GridBagConstraints constraints = new GridBagConstraints();
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
            ButtonRow row = new ButtonRow(new Action[]{new PasteAction(), new BrowseAction()}, ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);
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
            row = new ButtonRow(new Action[]{new OkAction(), new CancelAction()}, ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);
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

        private class OpenDialogWindowAdapter extends WindowAdapter {
            public void windowOpened(WindowEvent e) {
                PATH_FIELD.requestFocusInWindow();
            }
        }

        private class PasteAction extends AbstractAction {
            PasteAction() {
                super(I18n.tr("Paste"));
            }

            public void actionPerformed(ActionEvent a) {
                PATH_FIELD.paste();
            }
        }

        private class BrowseAction extends AbstractAction {
            BrowseAction() {
                super(I18n.tr("Browse..."));
            }

            public void actionPerformed(ActionEvent a) {
                File file = FileChooserHandler.getInputFile(GUIMediator.getAppFrame(), TorrentFileFilter.INSTANCE);
                if (file != null)
                    PATH_FIELD.setText(file.getAbsolutePath());
            }
        }

        private class OkAction extends AbstractAction {
            OkAction() {
                super(I18n.tr("OK"));
            }

            public void actionPerformed(ActionEvent a) {
                String str = PATH_FIELD.getText();
                if (openMagnetOrTorrent(str)) {
                    dismissDialog();
                } else {
                    GUIMediator.showError(I18n.tr("FrostWire cannot download this address. Make sure you typed it correctly, and then try again."));
                }
            }
        }

        private static class CancelAction extends AbstractAction {
            CancelAction() {
                super(I18n.tr("Cancel"));
            }

            public void actionPerformed(ActionEvent a) {
                GUIUtils.getDisposeAction().actionPerformed(a);
            }
        }
    }

    /**
     * Exits the application.
     */
    public static class CloseAction extends AbstractAction {
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
        }
    }
}
