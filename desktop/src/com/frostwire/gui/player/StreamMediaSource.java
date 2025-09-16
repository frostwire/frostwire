/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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