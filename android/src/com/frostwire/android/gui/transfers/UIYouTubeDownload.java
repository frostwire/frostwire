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

package com.frostwire.android.gui.transfers;

import android.net.Uri;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.transfers.YoutubeDownload;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UIYouTubeDownload extends YoutubeDownload {

    private final TransferManager manager;

    UIYouTubeDownload(TransferManager manager, YouTubeCrawledSearchResult sr) {
        super(sr);
        this.manager = manager;
    }

    @Override
    protected void onComplete() throws Throwable {
        manager.incrementDownloadsToReview();
        Engine.instance().notifyDownloadFinished(getDisplayName(), savePath);
        Librarian.instance().scan(Uri.fromFile(savePath));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UIYouTubeDownload)) {
            return false;
        }

        return getName().equals(((UIYouTubeDownload) obj).getName());
    }
}
