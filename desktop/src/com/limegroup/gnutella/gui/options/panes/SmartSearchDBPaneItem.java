package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.SearchSettings;

import javax.swing.*;
import java.awt.*;

public final class SmartSearchDBPaneItem extends AbstractPaneItem {
    public final static String TITLE = I18n.tr("Smart Search");
    public final static String LABEL = I18n.tr("The Smart Search database is used to speed up individual file searches, it's how FrostWire remembers information about .torrent contents.");
    private final JLabel _numTorrentsLabel;
    private final JCheckBox smartSearchEnabled;
    private long _numTorrents = 0;

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     *
     */
    public SmartSearchDBPaneItem() {
        super(TITLE, LABEL);
        Font font = new Font("dialog", Font.BOLD, 12);
        _numTorrentsLabel = new JLabel();
        _numTorrentsLabel.setFont(font);
        smartSearchEnabled = new JCheckBox(I18n.tr("Enable Smart Search"), SearchSettings.SMART_SEARCH_ENABLED.getValue());
        LabeledComponent numTorrentsComp = new LabeledComponent(I18n.tr("Total torrents indexed"), _numTorrentsLabel);
        add(getVerticalSeparator());
        JButton resetButton = new JButton(I18n.tr("Reset Smart Search Database"));
        resetButton.addActionListener(e -> GUIMediator.safeInvokeLater(() -> {
            resetSmartSearchDB();
            initOptions();
        }));
        add(smartSearchEnabled);
        add(getVerticalSeparator());
        add(numTorrentsComp.getComponent());
        add(getVerticalSeparator());
        add(resetButton);
    }

    protected void resetSmartSearchDB() {
        DialogOption showConfirmDialog = GUIMediator.showYesNoMessage(I18n.tr("If you continue you will erase all the information related to\n{0} torrents that FrostWire has learned to speed up your search results.\nDo you wish to continue?", _numTorrents),
                I18n.tr("Are you sure?"), JOptionPane.QUESTION_MESSAGE);
        if (showConfirmDialog == DialogOption.YES) {
            SearchMediator.instance().clearCache();
        }
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        _numTorrentsLabel.setText("...");
        BackgroundExecutorService.schedule(() -> {
            _numTorrents = SearchMediator.instance().getTotalTorrents();
            GUIMediator.safeInvokeLater(() -> {
                _numTorrentsLabel.setText(String.valueOf(_numTorrents));
                smartSearchEnabled.setSelected(SearchSettings.SMART_SEARCH_ENABLED.getValue());
            });
        });
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        SearchSettings.SMART_SEARCH_ENABLED.setValue(smartSearchEnabled.isSelected());
        return true;
    }

    public boolean isDirty() {
        return false;
    }
}
