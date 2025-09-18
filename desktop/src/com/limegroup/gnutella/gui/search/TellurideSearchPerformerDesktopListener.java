/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.telluride.TellurideSearchPerformerListener;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.apache.commons.io.FilenameUtils;

import java.util.List;

final class TellurideSearchPerformerDesktopListener implements TellurideSearchPerformerListener {
    @Override
    public void onTellurideBinaryNotFound(IllegalArgumentException e) {
        GUIMediator.showError(I18n.tr("telluride component not found.<br><br>Please check your installation or contact support@frostwire.com letting them know about this issue with your") + " FrostWire " + FrostWireUtils.getFrostWireVersion() + " " + I18n.tr("build") + " " + FrostWireUtils.getBuildNumber() + " " + I18n.tr("for") + " " + System.getProperty("os.name"));
    }

    @Override
    public void onTellurideJSONResult(final long token, final TellurideSearchPerformer.TellurideJSONResult result) {
        // Safely occurs in main thread.
        SearchMediator.instance().updateSearchPanelTitle(token, result.extractor + ": " + result.title);
    }

    @Override
    public void onError(final long token, final String errorMessage) {
        SearchMediator.instance().updateSearchPanelTitle(token, errorMessage + " (" + I18n.tr("Right click") + " > " + I18n.tr("Repeat Search") + ")");
    }

    @Override
    public void onSearchResults(long token, List<TellurideSearchResult> results) {
        int audioResults = 0;
        int videoResults = 0;
        for (TellurideSearchResult r : results) {
            NamedMediaType namedMediaType = NamedMediaType.getFromExtension(FilenameUtils.getExtension(r.getFilename()));
            if (namedMediaType != null) {
                MediaType mediaType = namedMediaType.getMediaType();
                if (mediaType == MediaType.getAudioMediaType()) {
                    audioResults++;
                } else if (mediaType == MediaType.getVideoMediaType()) {
                    videoResults++;
                }
            }
        }

        // java life...
        final int audioCount = audioResults;
        final int videoCount = videoResults;

        GUIMediator.safeInvokeLater(() -> {
            SearchResultMediator resultPanelForGUID = SearchMediator.getSearchResultDisplayer().getResultPanelForGUID(token);
            if (resultPanelForGUID != null) {
                resultPanelForGUID.showOnlyAudioVideoSchemaBox();
                if (videoCount > 0 && videoCount > audioCount) {
                    resultPanelForGUID.selectMediaType(NamedMediaType.getFromMediaType(MediaType.getVideoMediaType()));
                } else if (audioCount > 0) {
                    resultPanelForGUID.selectMediaType(NamedMediaType.getFromMediaType(MediaType.getAudioMediaType()));
                }
            }
        });
    }
}