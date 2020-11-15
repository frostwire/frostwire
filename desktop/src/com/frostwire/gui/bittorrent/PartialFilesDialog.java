/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.bittorrent;

import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.search.NamedMediaType;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.StringUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
class PartialFilesDialog extends JDialog {
    private final TorrentInfo torrent;
    private final String name;
    private final TorrentTableModel model;
    private LabeledTextField filter;
    private RowFilter<Object, Object> textBasedFilter;
    private JPanel panel;
    private JTable table;
    private JButton buttonOK;
    private boolean[] filesSelection;
    private JCheckBox checkBoxToggleAll;
    /**
     * Has the table been painted at least once?
     */
    private boolean tablePainted;

    PartialFilesDialog(JFrame frame, File torrentFile) {
        this(frame, new TorrentInfo(torrentFile), torrentFile.getName());
    }

    PartialFilesDialog(JFrame frame, byte[] bytes, String name) {
        this(frame, TorrentInfo.bdecode(bytes), name);
    }

    private PartialFilesDialog(JFrame frame, TorrentInfo torrent, String name) {
        super(frame, I18n.tr("Select files to download"));
        this.torrent = torrent;
        this.name = name;
        model = new TorrentTableModel(this.torrent);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                GUIMediator.instance().setRemoteDownloadsAllowed(false);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                GUIMediator.instance().setRemoteDownloadsAllowed(true);
            }
        });
        setupUI();
        setLocationRelativeTo(frame);
    }

    private void setupUI() {
        setResizable(true);
        setMinimumSize(new Dimension(400, 300));
        panel = new JPanel(new GridBagLayout());
        // title
        setupTitle();
        // filter
        setupTextFilter();
        setupToggleAllSelectionCheckbox();
        // table
        setupTable();
        // ok button
        setupOkButton();
        // cancel button
        setupCancelButton();
        getContentPane().add(panel);
        pack();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);
        getRootPane().setDefaultButton(buttonOK);
        GUIUtils.addHideAction((JComponent) getContentPane());
    }

    private void setupCancelButton() {
        GridBagConstraints c;
        JButton _buttonCancel = new JButton(I18n.tr("Cancel"));
        _buttonCancel.addActionListener(this::buttonCancel_actionPerformed);
        c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 8, 6);
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.EAST;
        c.ipadx = 18;
        c.gridy = 4;
        panel.add(_buttonCancel, c);
    }

    private void setupOkButton() {
        GridBagConstraints c;
        buttonOK = new JButton(I18n.tr("Download Selected Files Only"));
        buttonOK.addActionListener(this::buttonOK_actionPerformed);
        c = new GridBagConstraints();
        c.insets = new Insets(4, 100, 8, 4);
        c.fill = GridBagConstraints.NONE;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.EAST;
        c.ipadx = 20;
        c.weightx = 1.0;
        c.gridy = 4;
        panel.add(buttonOK, c);
    }

    private void setupTable() {
        GridBagConstraints c;
        table = new JTable() {
            public void paint(java.awt.Graphics g) {
                super.paint(g);
                try {
                    if (tablePainted) {
                        return;
                    }
                    tablePainted = true;
                    GUIUtils.adjustColumnWidth(model, 3, 150, 10, this);
                    GUIUtils.adjustColumnWidth(model, 2, 620, 10, this);
                } catch (Exception e) {
                    tablePainted = false;
                }
            }
        };
        table.setPreferredScrollableViewportSize(new Dimension(600, 300));
        table.setRowSelectionAllowed(false);
        table.setModel(model);
        table.getColumnModel().getColumn(0).setHeaderValue(""); //checkbox
        table.getColumnModel().getColumn(1).setHeaderValue(""); //icon
        table.getColumnModel().getColumn(2).setHeaderValue(I18n.tr("File"));
        table.getColumnModel().getColumn(3).setHeaderValue(I18n.tr("Type"));
        table.getColumnModel().getColumn(4).setHeaderValue(I18n.tr("Extension"));
        table.getColumnModel().getColumn(5).setHeaderValue(I18n.tr("Size"));
        table.getColumnModel().getColumn(0).setPreferredWidth(30);//checkbox
        table.getColumnModel().getColumn(1).setPreferredWidth(30);//icon
        table.getColumnModel().getColumn(2).setMinWidth(380); // File
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(60);
        JScrollPane _scrollPane = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        panel.add(_scrollPane, c);
    }

    private void setupToggleAllSelectionCheckbox() {
        GridBagConstraints c;
        checkBoxToggleAll = new JCheckBox(I18n.tr("Select/Unselect all files"), true);
        checkBoxToggleAll.addItemListener(e -> onCheckBoxToggleAll());
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(5, 5, 5, 5);
        panel.add(checkBoxToggleAll, c);
    }

    private void setupTextFilter() {
        GridBagConstraints c;
        filter = new LabeledTextField("Filter files", 30);
        filter.setMinimumSize(filter.getPreferredSize()); // fix odd behavior
        textBasedFilter = new RowFilterExtension(filter);
        filter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (filter.getText() == null || filter.getText().equals("")) {
                    table.setRowSorter(null);
                    return;
                }
                checkBoxToggleAll.setSelected(false);
                TableRowSorter<TorrentTableModel> sorter = new TableRowSorter<>(model);
                sorter.setRowFilter(textBasedFilter);
                table.setRowSorter(sorter);
            }
        });
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1.0;
        c.insets = new Insets(5, 5, 5, 5);
        panel.add(filter, c);
    }

    private void setupTitle() {
        GridBagConstraints c;
        String title = torrent.name();
        if (title == null) {
            if (torrent.name() != null) {
                title = StringUtils.getUTF8String(torrent.name().getBytes());
            } else {
                title = name.replace("_", " ").replace(".torrent", "").replace("&quot;", "\"");
            }
        }
        JLabel labelTitle = new JLabel(title);
        labelTitle.setFont(new Font("Dialog", Font.BOLD, 18));
        labelTitle.setHorizontalAlignment(SwingConstants.LEFT);
        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(5, 5, 5, 5);
        panel.add(labelTitle, c);
    }

    private void onCheckBoxToggleAll() {
        model.setAllSelected(checkBoxToggleAll.isSelected());
        buttonOK.setEnabled(checkBoxToggleAll.isSelected());
    }

    /**
     * Change the value of the checkbox but don't trigger any events.
     * (We probably need something generic for this, this pattern keeps appearing all over)
     */
    private void checkboxToggleAllSetSelectedNoTrigger(boolean allSelected) {
        ItemListener[] itemListeners = checkBoxToggleAll.getItemListeners();
        for (ItemListener l : itemListeners) {
            checkBoxToggleAll.removeItemListener(l);
        }
        checkBoxToggleAll.setSelected(allSelected);
        for (ItemListener l : itemListeners) {
            checkBoxToggleAll.addItemListener(l);
        }
    }

    private void buttonOK_actionPerformed(ActionEvent e) {
        TorrentFileInfo[] fileInfos = model.getFileInfos();
        filesSelection = new boolean[fileInfos.length];
        for (int i = 0; i < filesSelection.length; i++) {
            filesSelection[i] = fileInfos[i].selected;
        }
        GUIUtils.getDisposeAction().actionPerformed(e);
    }

    private void buttonCancel_actionPerformed(ActionEvent e) {
        GUIUtils.getDisposeAction().actionPerformed(e);
    }

    boolean[] getFilesSelection() {
        return filesSelection;
    }

    static final class RowFilterExtension extends RowFilter<Object, Object> {
        private final LabeledTextField labelFilter;
        private final int columnIndex;

        RowFilterExtension(LabeledTextField labelFilter) {
            this.labelFilter = labelFilter;
            this.columnIndex = 2;
        }

        @Override
        public boolean include(RowFilter.Entry<?, ?> entry) {
            Object value = entry.getValue(columnIndex);
            String fileName = value instanceof String ? (String) value : "";
            String[] tokens = labelFilter.getText().split(" ");
            for (String t : tokens) {
                if (!fileName.toLowerCase().contains(t.toLowerCase())) {
                    return false;
                }
            }
            return true;
        }
    }

    private final class TorrentTableModel extends AbstractTableModel {
        private static final long serialVersionUID = -8689494570949104116L;
        private final TorrentInfo _torrent;
        private final TorrentFileInfo[] _fileInfos;

        TorrentTableModel(TorrentInfo torrent) {
            _torrent = torrent;
            _fileInfos = new TorrentFileInfo[torrent.numFiles()];
            FileStorage fs = torrent.files();
            for (int i = 0; i < _fileInfos.length; i++) {
                _fileInfos[i] = new TorrentFileInfo(fs.filePath(i), fs.fileSize(i), true);
            }
        }

        @Override
        public int getRowCount() {
            return _torrent.numFiles();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return Boolean.class;
                case 1:
                    return Icon.class;
                case 2:
                case 3:
                case 4:
                    return String.class;
                case 5:
                    return SizeHolder.class;
                default:
                    return null;
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String filePath = _fileInfos[rowIndex].path;
            String extension = FilenameUtils.getExtension(filePath);
            switch (columnIndex) {
                case 0:
                    //checkbox
                    return _fileInfos[rowIndex].selected;
                case 1:
                    //icon
                    return IconManager.instance().getIconForExtension(extension);
                case 2:
                    //path
                    return filePath;
                case 3:
                    //human type
                    return guessHumanType(extension);
                case 4:
                    //extension
                    return extension;
                case 5:
                    //file size
                    return new SizeHolder(_fileInfos[rowIndex].size);
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                _fileInfos[rowIndex].selected = (Boolean) aValue;
                fireTableDataChanged();
            }
            checkboxToggleAllSetSelectedNoTrigger(isAllSelected());
            buttonOK.setEnabled(!isNoneSelected());
        }

        boolean isAllSelected() {
            for (TorrentFileInfo _fileInfo : _fileInfos) {
                if (!_fileInfo.selected) {
                    return false;
                }
            }
            return true;
        }

        void setAllSelected(boolean selected) {
            for (TorrentFileInfo _fileInfo : _fileInfos) {
                _fileInfo.selected = selected;
            }
            fireTableDataChanged();
        }

        boolean isNoneSelected() {
            for (TorrentFileInfo _fileInfo : _fileInfos) {
                if (_fileInfo.selected) {
                    return false;
                }
            }
            return true;
        }

        TorrentFileInfo[] getFileInfos() {
            return _fileInfos;
        }

        private String guessHumanType(String extension) {
            try {
                //noinspection ConstantConditions
                return NamedMediaType.getFromExtension(extension).getMediaType().getDescriptionKey();
            } catch (Throwable t) {
                return I18n.tr("Unknown");
            }
        }
    }
}
