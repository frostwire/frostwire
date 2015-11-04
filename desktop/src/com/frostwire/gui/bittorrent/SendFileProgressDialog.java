/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.logging.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SendFileProgressDialog extends JDialog {
    
    private static final Logger LOG = Logger.getLogger(SendFileProgressDialog.class);

	private JProgressBar _progressBar;
	private JButton _cancelButton;
	
    private Container _container;
	private int _percent_complete;
	private File _preselectedFile;

	public SendFileProgressDialog(JFrame frame, File file) {
		this(frame);
		_preselectedFile = file;
	}
	
    public SendFileProgressDialog(JFrame frame) {
        super(frame);

        setupUI();
        setLocationRelativeTo(frame);
    }

    protected void setupUI() {
        setupWindow();
        initProgressBar();      
        initCancelButton();
    }

	private void setupWindow() {
		String itemType = I18n.tr("Preparing selection");
		setTitle(itemType+", "+I18n.tr("please wait..."));
		
		Dimension prefDimension = new Dimension(512, 100);
        
		setSize(prefDimension);
        setMinimumSize(prefDimension);
        setPreferredSize(prefDimension);
        setResizable(false);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                this_windowOpened(e);
            }
            
            @Override
            public void windowClosing(WindowEvent e) {
            }
        });
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);
        GUIUtils.addHideAction((JComponent) getContentPane());
        
        _container = getContentPane();
        _container.setLayout(new GridBagLayout());
	}

    private void initCancelButton() {
		GridBagConstraints c;
		c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_END;
		c.insets = new Insets(10,0,10,10);
		_cancelButton = new JButton(I18n.tr("Cancel"));
		
		_cancelButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				onCancelButton();
			}
		});
		
		_container.add(_cancelButton,c);
	}

	protected void onCancelButton() {
		dispose();
	}

	private void initProgressBar() {
		GridBagConstraints c;
		c = new GridBagConstraints();
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.LINE_START;
		c.insets = new Insets(10, 10, 10, 10);
		c.gridwidth = GridBagConstraints.RELATIVE;
		_progressBar = new JProgressBar(0,100);
		_progressBar.setStringPainted(true);
		_container.add(_progressBar, c);		
	}

	protected void this_windowOpened(WindowEvent e) {
		if (_preselectedFile == null) {
			chooseFile();
		} else {
			new Thread(new Runnable() {

				@Override
				public void run() {
					makeTorrentAndDownload(_preselectedFile.getAbsoluteFile());					
				}}).start();
		}
    }

	public void chooseFile() {
		JFileChooser fileChooser = new JFileChooser();

        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setDialogTitle(I18n.tr("Select the content you want to send"));
        fileChooser.setApproveButtonText(I18n.tr("Select"));
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            new Thread(new Runnable() {

				@Override
				public void run() {
					makeTorrentAndDownload(selectedFile.getAbsoluteFile());					
				}}).start();
        } else if (result == JFileChooser.CANCEL_OPTION) {
            onCancelButton();
        } else if (result == JFileChooser.ERROR_OPTION) {
            LOG.error("Error selecting the file");
        }
	}

	private void updateProgressBarText() {
		GUIMediator.safeInvokeLater(new Runnable() {
			@Override
			public void run() {
				_progressBar.setString("Preparing files (" + _percent_complete + " %)");
				_progressBar.setValue(_percent_complete);
			}
		});
	}
	
    protected void torrentCreator_reportProgress(int percent_complete) {
    	_percent_complete = percent_complete;
    	updateProgressBarText();
    }

    private void makeTorrentAndDownload(final File file) {
        try {
            file_storage fs = new file_storage();
            libtorrent.add_files(fs, file.getAbsolutePath());
            create_torrent torrentCreator = new create_torrent(fs);
            torrentCreator.add_tracker("udp://tracker.openbittorrent.com:80");
            torrentCreator.add_tracker("udp://tracker.publicbt.com:80");
            torrentCreator.add_tracker("udp://tracker.ccc.de:80");
            torrentCreator.set_priv(false);
            torrentCreator.set_creator("FrostWire " + FrostWireUtils.getFrostWireJarPath() + " build " + FrostWireUtils.getBuildNumber());
            final File torrentFile = new File(SharingSettings.TORRENTS_DIR_SETTING.getValue(), file.getName() + ".torrent");
            final error_code ec = new error_code();
            libtorrent.set_piece_hashes(torrentCreator,file.getParentFile().getAbsolutePath(), ec);
            if (ec.value() != 0) {
                GUIMediator.safeInvokeLater(new Runnable() {
                    @Override
                    public void run() {
                          _progressBar.setString("Error: " + ec.message());
                    }
                });
                return;
            }

            final entry torrentEntry = torrentCreator.generate();
            byte[] bencoded_torrent_bytes = Vectors.char_vector2bytes(torrentEntry.bencode());
            FileOutputStream fos = new FileOutputStream(torrentFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            bos.write(bencoded_torrent_bytes);
            bos.flush();
            bos.close();

            final TorrentInfo torrent = TorrentInfo.bdecode(bencoded_torrent_bytes);

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                	dispose();
                    GUIMediator.instance().openTorrentForSeed(torrentFile, file.getParentFile());
                    new ShareTorrentDialog(torrent).setVisible(true);
                }
            });

        } catch (final Exception e) {
            e.printStackTrace();
            
            GUIMediator.safeInvokeLater(new Runnable() {

				@Override
				public void run() {
		            _progressBar.setString("There was an error. Make sure the file/folder is not empty.");
				}            	
            });

        }
    }
}
