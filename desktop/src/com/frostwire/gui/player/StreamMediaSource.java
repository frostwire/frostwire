/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.player;

/**
 * @author gubatron
 * @author aldenml
 */
public class StreamMediaSource extends MediaSource {
    private final String title;
    private final String detailsUrl;
    private final boolean showPlayerWindow;

    public StreamMediaSource(String url, String title, String detailsUrl, boolean showPlayerWindow) {
        super(url);
        this.title = title;
        this.detailsUrl = detailsUrl;
        this.showPlayerWindow = showPlayerWindow;
        // initialize display text
        titleText = this.title;
        toolTipText = "";
    }

    public String getTitle() {
        return title;
    }

    public String getDetailsUrl() {
        return detailsUrl;
    }

    boolean showPlayerWindow() {
        return showPlayerWindow;
    }
}