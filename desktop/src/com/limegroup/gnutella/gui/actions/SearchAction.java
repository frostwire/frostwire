package com.limegroup.gnutella.gui.actions;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchInformation;
import com.limegroup.gnutella.gui.search.SearchMediator;

import javax.swing.AbstractAction;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;

/**
 * Provides a search action.
 * <p>
 * After the search has been sent the search panel is focused.
 */
public class SearchAction extends AbstractAction {
    private final SearchInformation info;

    /**
     * Constructs an action that searches a space separated list of keywords.
     *
     * @throws IllegalArgumentException if the constructed search information
     *                                  is not valid.
     */
    public SearchAction(String keywords) {
        this(SearchInformation.createKeywordSearch
                        (keywords, null, MediaType.getTorrentMediaType()),
                I18n.tr("Search for Keywords: {0}"));
    }

    /**
     * Constructs an action that triggers a search for the given search
     * information.
     *
     * @param messageKey the key that surrounds the title of the search information, e.g
     *                   "Search for: {0}", {0} is replaced by the title of the search information
     * @throws IllegalArgumentException if the search information is not
     *                                  {@link SearchMediator#validateInfo(SearchInformation) valid}
     */
    public SearchAction(SearchInformation info, String messageKey) {
        this.info = info;
        putValue(Action.NAME, MessageFormat.format
                (I18n.tr(messageKey),
                        info.getTitle()));
        if (SearchMediator.validateInfo(info) != SearchMediator.QUERY_VALID) {
            throw new IllegalArgumentException("invalid search info: " + info);
        }
    }

    public void actionPerformed(ActionEvent e) {
        SearchMediator.instance().triggerSearch(info);
        GUIMediator.instance().setWindow(GUIMediator.Tabs.SEARCH);
    }
}
