/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class PartialFilesDialog extends JDialog {

    private static final long serialVersionUID = 4312306965758592618L;

    private LabeledTextField _filter;
    private RowFilter<Object, Object> textBasedFilter;

    private JPanel panel;
    private JLabel labelTitle;
    private JTable _table;
    private JScrollPane _scrollPane;
    private JButton _buttonOK;
    private JButton _buttonCancel;

    private final TorrentInfo _torrent;
    private final String _name;
    private final TorrentTableModel _model;

    private boolean[] _filesSelection;
    private JCheckBox _checkBoxToggleAll;

    /** Has the table been painted at least once? */
    protected boolean tablePainted;

    public PartialFilesDialog(JFrame frame, File torrentFile) {
        this(frame, new TorrentInfo(torrentFile), torrentFile.getName());
    }

    public PartialFilesDialog(JFrame frame, byte[] bytes, String name) {
        this(frame, TorrentInfo.bdecode(bytes), name);
    }

    public PartialFilesDialog(JFrame frame, TorrentInfo torrent, String name) {
        super(frame, I18n.tr("Select files to download"));

        this._torrent = torrent;

        this._name = name;
        _model = new TorrentTableModel(_torrent);

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

    protected void setupUI() {
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
        getRootPane().setDefaultButton(_buttonOK);
        GUIUtils.addHideAction((JComponent) getContentPane());
    }

    private void setupCancelButton() {
        GridBagConstraints c;
        _buttonCancel = new JButton(I18n.tr("Cancel"));
        _buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                buttonCancel_actionPerformed(e);
            }
        });
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
        _buttonOK = new JButton(I18n.tr("Download Selected Files Only"));
        _buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                buttonOK_actionPerformed(e);
            }
        });

        c = new GridBagConstraints();
        c.insets = new Insets(4, 100, 8, 4);
        c.fill = GridBagConstraints.NONE;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.EAST;
        c.ipadx = 20;
        c.weightx = 1.0;
        c.gridy = 4;

        panel.add(_buttonOK, c);
    }

    private void setupTable() {
        GridBagConstraints c;
        _table = new JTable() {
            private static final long serialVersionUID = -4266029708016964901L;

            public void paint(java.awt.Graphics g) {
                super.paint(g);

                try {
                    if (tablePainted) {
                        return;
                    }
                    tablePainted = true;

                    GUIUtils.adjustColumnWidth(_model, 2, 620, 10, this);
                    GUIUtils.adjustColumnWidth(_model, 3, 150, 10, this);
                } catch (Exception e) {
                    tablePainted = false;
                }

            };
        };

        _table.setPreferredScrollableViewportSize(new Dimension(600, 300));
        _table.setRowSelectionAllowed(false);
        _table.setModel(_model);
        _table.getColumnModel().getColumn(0).setHeaderValue(""); //checkbox
        _table.getColumnModel().getColumn(1).setHeaderValue(""); //icon
        _table.getColumnModel().getColumn(2).setHeaderValue(I18n.tr("File"));
        _table.getColumnModel().getColumn(3).setHeaderValue(I18n.tr("Type"));
        _table.getColumnModel().getColumn(4).setHeaderValue(I18n.tr("Extension"));
        _table.getColumnModel().getColumn(5).setHeaderValue(I18n.tr("Size"));

        _table.getColumnModel().getColumn(0).setPreferredWidth(30);//checkbox
        _table.getColumnModel().getColumn(1).setPreferredWidth(30);//icon
        _table.getColumnModel().getColumn(2).setPreferredWidth(620);
        _table.getColumnModel().getColumn(3).setPreferredWidth(150);
        _table.getColumnModel().getColumn(4).setPreferredWidth(60);
        _table.getColumnModel().getColumn(5).setPreferredWidth(60);

        _scrollPane = new JScrollPane(_table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        _table.setFillsViewportHeight(true);
        _table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
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
        _checkBoxToggleAll = new JCheckBox(I18n.tr("Select/Unselect all files"), true);
        _checkBoxToggleAll.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                onCheckBoxToggleAll(e);
            }
        });

        c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(5, 5, 5, 5);
        panel.add(_checkBoxToggleAll, c);
    }

    private void setupTextFilter() {
        GridBagConstraints c;
        _filter = new LabeledTextField("Filter files", 30);
        _filter.setMinimumSize(_filter.getPreferredSize()); // fix odd behavior
        textBasedFilter = new RowFilterExtension(_filter, 2);

        _filter.addKeyListener(new KeyAdapter() {

            @Override
            public void keyReleased(KeyEvent e) {
                if (_filter.getText() == null || _filter.getText().equals("")) {
                    _table.setRowSorter(null);
                    return;
                }

                _checkBoxToggleAll.setSelected(false);

                TableRowSorter<TorrentTableModel> sorter = new TableRowSorter<TorrentTableModel>(_model);
                sorter.setRowFilter(textBasedFilter);
                _table.setRowSorter(sorter);
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
        panel.add(_filter, c);
    }

    private void setupTitle() {
        GridBagConstraints c;

        String title = _torrent.getName();
        if (title == null) {
            if (_torrent.getName() != null) {
                title = StringUtils.getUTF8String(_torrent.getName().getBytes());
            } else {
                title = _name.replace("_", " ").replace(".torrent", "").replace("&quot;", "\"");
            }
        }
        labelTitle = new JLabel(title);
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

    protected void onCheckBoxToggleAll(ItemEvent e) {
        _model.setAllSelected(_checkBoxToggleAll.isSelected());
        _buttonOK.setEnabled(_checkBoxToggleAll.isSelected());
    }

    /**
     * Change the value of the checkbox but don't trigger any events.
     * (We probably need something generic for this, this pattern keeps appearing all over)
     * @param allSelected
     */
    public void checkboxToggleAllSetSelectedNoTrigger(boolean allSelected) {
        ItemListener[] itemListeners = _checkBoxToggleAll.getItemListeners();

        for (ItemListener l : itemListeners) {
            _checkBoxToggleAll.removeItemListener(l);
        }
        _checkBoxToggleAll.setSelected(allSelected);

        for (ItemListener l : itemListeners) {
            _checkBoxToggleAll.addItemListener(l);
        }

    }

    protected void buttonOK_actionPerformed(ActionEvent e) {

        TorrentFileInfo[] fileInfos = _model.getFileInfos();
        _filesSelection = new boolean[fileInfos.length];
        for (int i = 0; i < _filesSelection.length; i++) {
            _filesSelection[i] = fileInfos[i].selected;
        }

        GUIUtils.getDisposeAction().actionPerformed(e);
    }

    protected void buttonCancel_actionPerformed(ActionEvent e) {
        GUIUtils.getDisposeAction().actionPerformed(e);
    }

    public boolean[] getFilesSelection() {
        return _filesSelection;
    }

    static final class RowFilterExtension extends RowFilter<Object, Object> {

        private final LabeledTextField labelFilter;
        private final int columnIndex;

        public RowFilterExtension(LabeledTextField labelFilter, int columnIndex) {
            this.labelFilter = labelFilter;
            this.columnIndex = columnIndex;
        }

        @Override
        public boolean include(RowFilter.Entry<? extends Object, ? extends Object> entry) {

            Object value = entry.getValue(columnIndex);

            String fileName = value != null && value instanceof String ? (String) value : "";

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

        public TorrentTableModel(TorrentInfo torrent) {
            _torrent = torrent;
            _fileInfos = new TorrentFileInfo[torrent.getNumFiles()];
            FileStorage fs = torrent.getFiles();
            for (int i = 0; i < _fileInfos.length; i++) {
                _fileInfos[i] = new TorrentFileInfo(fs.getFilePath(i), fs.getFileSize(i), true);
            }

        }

        @Override
        public int getRowCount() {
            return _torrent.getNumFiles();
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
                return String.class;
            case 3:
                return String.class;
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
            _buttonOK.setEnabled(!isNoneSelected());
        }

        public void setAllSelected(boolean selected) {
            for (int i = 0; i < _fileInfos.length; i++) {
                _fileInfos[i].selected = selected;
            }
            fireTableDataChanged();
        }

        public boolean isAllSelected() {
            for (int i = 0; i < _fileInfos.length; i++) {
                if (!_fileInfos[i].selected) {
                    return false;
                }
            }
            return true;
        }

        public boolean isNoneSelected() {
            for (int i = 0; i < _fileInfos.length; i++) {
                if (_fileInfos[i].selected) {
                    return false;
                }
            }
            return true;
        }

        public TorrentFileInfo[] getFileInfos() {
            return _fileInfos;
        }

        private String guessHumanType(String extension) {
            try {
                return NamedMediaType.getFromExtension(extension).getMediaType().getDescriptionKey();
            } catch (Throwable t) {
                return I18n.tr("Unknown");
            }
        }
    }
}
