package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.tables.AbstractTableMediator;

/**
 * Holds the data for a search result's Source.
 * @author gubatron
 *
 */
public class SourceHolder implements Comparable<SourceHolder> {

    private final UISearchResult uiSearchResult;
    private final String sourceNameHTML;
    private final String sourceName;
    private final String sourceURL;
    
    public SourceHolder(UISearchResult uiSearchResult) {
        this.uiSearchResult = uiSearchResult;
        this.sourceName = uiSearchResult.getSource();
        this.sourceNameHTML = "<html><div width=\"1000000px\"><nobr><a href=\"#\">" + sourceName + "</a></nobr></div></html>";
        this.sourceURL  = uiSearchResult.getSearchResult().getDetailsUrl();
    }

    @Override
    public int compareTo(SourceHolder o) {
        return AbstractTableMediator.compare(sourceName, o.getSourceName());
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    public String getSourceNameHTML() {
        return sourceNameHTML;
    }
    
    public String getSourceURL() {
        return sourceURL;
    }
    
    public UISearchResult getUISearchResult() {
        return uiSearchResult;
    }
    
    @Override
    public String toString() {
        return sourceName + " ["+ sourceURL +"]";
    }
}