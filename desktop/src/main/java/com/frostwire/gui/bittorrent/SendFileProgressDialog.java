/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.concurrent.concurrent.ThreadExecutor;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public class SendFileProgressDialog extends JDialog {
    private static final Logger LOG = Logger.getLogger(SendFileProgressDialog.class);
    private final TorrentMakerListener torrentMakerListener;
    private JProgressBar _progressBar;
    private Container _container;
    private File _preselectedFile;

    public SendFileProgressDialog(JFrame frame, File file) {
        this(frame);
        _preselectedFile = file;
    }

    public SendFileProgressDialog(JFrame frame) {
        super(frame);
        setupUI();
        setLocationRelativeTo(frame);
        torrentMakerListener = new TorrentMakerListener();
    }

    private void setupUI() {
        setupWindow();
        initProgressBar();
        initCancelButton();
    }

    private void setupWindow() {
        String itemType = I18n.tr("Preparing selection");
        setTitle(itemType + ", " + I18n.tr("please wait..."));
        Dimension prefDimension = new Dimension(512, 100);
        setSize(prefDimension);
        setMinimumSize(prefDimension);
        setPreferredSize(prefDimension);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                this_windowOpened();
            }
        });
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);
        GUIUtils.addHideAction((JComponent) getContentPane());
        _container = getContentPane();
        _container.setLayout(new GridBagLayout());
    }

    private void initCancelButton() {
        JButton _cancelButton = new JButton(I18n.tr("Cancel"));
        _cancelButton.addActionListener(e -> onCancelButton());
        GridBagConstraints c;
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(10, 0, 10, 10);
        _container.add(_cancelButton, c);
    }

    private void onCancelButton() {
        dispose();
    }

    private void initProgressBar() {
        _progressBar = new JProgressBar(0, 100);
        _progressBar.setStringPainted(true);
        GridBagConstraints c;
        c = new GridBagConstraints();
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(10, 10, 10, 10);
        c.gridwidth = GridBagConstraints.RELATIVE;
        _container.add(_progressBar, c);
    }

    private void this_windowOpened() {
        if (_preselectedFile == null) {
            chooseFile();
        } else {
            ThreadExecutor.startThread(() -> onApprovedFileSelectionToSend(_preselectedFile.getAbsoluteFile()), "SendFileProgressDialog:onApprovedFileSelectionToSend");
        }
    }

    private void chooseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setDialogTitle(I18n.tr("Select the content you want to send"));
        fileChooser.setApproveButtonText(I18n.tr("Select"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            ThreadExecutor.startThread(() -> onApprovedFileSelectionToSend(selectedFile.getAbsoluteFile()),
                    "SendFileProgressDialog:onApprovedFileSelectionToSend");
        } else if (result == JFileChooser.CANCEL_OPTION) {
            onCancelButton();
        } else if (result == JFileChooser.ERROR_OPTION) {
            LOG.error("Error selecting the file");
        }
    }

    private void onApprovedFileSelectionToSend(File absoluteFile) {
        TorrentUtil.makeTorrentAndDownload(absoluteFile, torrentMakerListener, true);
    }

    private class TorrentMakerListener implements TorrentUtil.UITorrentMakerListener {
        @Override
        public void onCreateTorrentError(final error_code ec) {
            GUIMediator.safeInvokeLater(() -> _progressBar.setString("Error: " + ec.message()));
        }

        @Override
        public void beforeOpenForSeedInUIThread() {
            GUIMediator.safeInvokeLater(SendFileProgressDialog.this::dispose);
        }

        @Override
        public void onException() {
            GUIMediator.safeInvokeLater(() -> _progressBar.setString("There was an error. Make sure the file/folder is not empty."));
        }

        @Override
        public void onPieceProgress(int nPiece, int totalPieces) {
            if (EventQueue.isDispatchThread()) {
                _progressBar.setValue((nPiece / totalPieces) * 100);
            } else {
                GUIMediator.safeInvokeLater(() -> onPieceProgress(nPiece, totalPieces));
            }
        }
    }
}
