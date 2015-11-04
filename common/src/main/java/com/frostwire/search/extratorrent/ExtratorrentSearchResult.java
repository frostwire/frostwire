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

package com.frostwire.search.extratorrent;

import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class ExtratorrentSearchResult extends AbstractTorrentSearchResult {

    private final ExtratorrentItem item;
    private final String filename;
    private final long creationTime;

    public ExtratorrentSearchResult(ExtratorrentItem item) {
        this.item = item;
        this.filename = buildFilename(item);
        this.creationTime = buildCreationTime(item);
    }

    @Override
    public String getFilename() {
        return this.filename;
    }

    public long getSize() {
        return item.size;
    }

    public long getCreationTime() {
        return this.creationTime;
    }

    @Override
    public String getSource() {
        return "Extratorrent";
    }

    @Override
    public String getHash() {
        return item.hash;
    }

    public String getTorrentUrl() {
        return item.torrentLink;
    }

    @Override
    public int getSeeds() {
        return item.seeds;
    }

    @Override
    public String getDisplayName() {
        return getFilename();
    }

    @Override
    public String getDetailsUrl() {
        return item.link;
    }

    private String buildFilename(ExtratorrentItem item) {
        String titleNoTags = item.title.replace("<b>", "").replace("</b>", "");
        return HtmlManipulator.replaceHtmlEntities(titleNoTags) + ".torrent";
    }

    private long buildCreationTime(ExtratorrentItem item) {
        //Wed, 09 Jun 2010 18:08:27 +0100
        SimpleDateFormat date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        long result = System.currentTimeMillis();
        try {
            result = date.parse(item.pubDate).getTime();
        } catch (ParseException e) {
        }
        return result;
    }
}