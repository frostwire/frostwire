/*
 * Copyright (c) 2012, the Last.fm Java Project and Committers All rights
 * reserved. Redistribution and use of this software in source and binary forms,
 * with or without modification, are permitted provided that the following
 * conditions are met: - Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following disclaimer. -
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution. THIS SOFTWARE IS
 * PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.andrew.apollo.lastfm;

/**
 * <code>MusicEntry</code> is the abstract superclass for {@link Track},
 * {@link Artist} and {@link Album}. It encapsulates data and provides methods
 * used in all subclasses, for example: name, playcount, images and more.
 * 
 * @author Janni Kovacs
 */
public abstract class MusicEntry extends ImageHolder {

    protected String name;

    protected String url;

    private String wikiSummary;

    protected MusicEntry(final String name, final String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getWikiSummary() {
        return wikiSummary;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + "name='" + name + '\'' + ", url='" + url
                + '\'' + ']';
    }

    /**
     * Loads all generic information from an XML <code>DomElement</code> into
     * the given <code>MusicEntry</code> instance, i.e. the following tags:<br/>
     * <ul>
     * <li>playcount/plays</li>
     * <li>listeners</li>
     * <li>streamable</li>
     * <li>name</li>
     * <li>url</li>
     * <li>mbid</li>
     * <li>image</li>
     * <li>tags</li>
     * </ul>
     * 
     * @param entry An entry
     * @param element XML source element
     */
    protected static void loadStandardInfo(final MusicEntry entry, final DomElement element) {
        // copy
        entry.name = element.getChildText("name");
        entry.url = element.getChildText("url");
        // wiki
        DomElement wiki = element.getChild("bio");
        if (wiki == null) {
            wiki = element.getChild("wiki");
        }
        if (wiki != null) {
            entry.wikiSummary = wiki.getChildText("summary");
        }
        // images
        ImageHolder.loadImages(entry, element);
    }
}
