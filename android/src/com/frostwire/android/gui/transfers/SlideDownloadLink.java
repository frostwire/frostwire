/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.transfers;

import com.frostwire.logging.Logger;
import org.apache.commons.io.FilenameUtils;

import com.frostwire.frostclick.Slide;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class SlideDownloadLink extends HttpDownloadLink {

    private final Slide slide;

    public SlideDownloadLink(Slide slide) {
        super(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL,
              FilenameUtils.getName(slide.httpDownloadURL == null ? slide.torrent : slide.httpDownloadURL),
              slide.title,
              slide.size,
              slide.uncompress);
        this.slide = slide;
    }

    public Slide getSlide() {
        return slide;
    }

    @Override
    public String getUrl() { return slide.torrent; }
}
