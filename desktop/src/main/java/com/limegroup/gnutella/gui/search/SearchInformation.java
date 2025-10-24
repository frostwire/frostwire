package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.MediaType;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple struct-like class containing information about a search.
 */
public class SearchInformation {
    /**
     * Key in map which holds property {@link #type}.
     */
    private static final String MAP_TYPE = "type";
    /**
     * Key in map which holds property {@link #query}.
     */
    private static final String MAP_QUERY = "query";
    /**
     * Key in map which holds property {@link #xml}.
     */
    private static final String MAP_XML = "xml";
    /**
     * Key in map which holds property {@link #media}.
     */
    private static final String MAP_MEDIA = "media";
    /**
     * Key in map which holds property {@link #title}.
     */
    private static final String MAP_TITLE = "title";
    /**
     * A keyword search.
     */
    private static final int KEYWORD = 0;
    /**
     * The kind of search this is.
     */
    private final int type;
    /**
     * The simple query string.
     */
    private final String query;
    /**
     * The XML string.
     */
    private final String xml;
    /**
     * The MediaType of the search.
     */
    private final MediaType media;
    /**
     * The title of this search as it is displayed to the user.
     */
    private final String title;

    /**
     * Private constructor -- use factory methods instead.
     *
     * @param title can be <code>null</code>, then the query is used.
     */
    private SearchInformation(int type, String query, String xml,
                              MediaType media, String title) {
        if (media == null)
            throw new NullPointerException("null media");
        if (query == null)
            throw new NullPointerException("null query");
        this.type = type;
        this.query = query.trim();
        this.xml = xml;
        this.media = media;
        this.title = title != null ? title : query;
    }

    private SearchInformation(int type, String query, String xml,
                              MediaType media) {
        this(type, query, xml, media, null);
    }

    /**
     * Creates a keyword search.
     */
    public static SearchInformation createKeywordSearch(String query,
                                                        String xml,
                                                        MediaType media) {
        return new SearchInformation(KEYWORD, query, xml, media);
    }

    /**
     * Creates a keyword search with a title different from the query string.
     */
    public static SearchInformation createTitledKeywordSearch(String query,
                                                              String xml, MediaType media, String title) {
        return new SearchInformation(KEYWORD, query, xml, media, title);
    }

    /**
     * Retrieves the basic query of the search.
     */
    public String getQuery() {
        return query;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Determines whether or not this is an XML search.
     */
    public boolean isXMLSearch() {
        return xml != null && xml.length() > 0;
    }

    /**
     * Determines if this is a keyword search.
     */
    public boolean isKeywordSearch() {
        return type == KEYWORD;
    }

    /**
     * Returns a string representation of the SearchInfo.
     */
    public String toString() {
        return toMap().toString();
    }

    /**
     * Converts state storred in the object into map. In this way, state
     * can be storred in classes unawre of this class existance.
     *
     * @return A map which holds parameters of the class.
     * @see SearchInformation(Map)
     */
    private Map<String, Serializable> toMap() {
        Map<String, Serializable> map = new HashMap<>(5);
        map.put(MAP_TYPE, type);
        map.put(MAP_QUERY, query);
        map.put(MAP_XML, xml);
        map.put(MAP_MEDIA, media);
        map.put(MAP_TITLE, title);
        return map;
    }
}
