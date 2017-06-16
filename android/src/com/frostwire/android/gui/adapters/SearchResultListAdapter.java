/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.activities.PreviewPlayerActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.MediaPlaybackOverlayPainter;
import com.frostwire.android.gui.views.MediaPlaybackStatusOverlayView;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.KeywordFilter;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledStreamableSearchResult;
import com.frostwire.search.youtube.YouTubePackageSearchResult;
import com.frostwire.util.Ref;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import org.apache.commons.io.FilenameUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchResultListAdapter extends AbstractListAdapter<SearchResult> {

    private static final int NO_FILE_TYPE = -1;

    private final OnLinkClickListener linkListener;
    private final PreviewClickListener previewClickListener;

    private int fileType;

    private ImageLoader thumbLoader;
    private final List<KeywordFilter> keywordFiltersPipeline;

    protected SearchResultListAdapter(Context context) {
        super(context, R.layout.view_bittorrent_search_result_list_item);
        this.linkListener = new OnLinkClickListener();
        this.previewClickListener = new PreviewClickListener(context, this);
        this.fileType = NO_FILE_TYPE;
        this.thumbLoader = ImageLoader.getInstance(context);
        this.keywordFiltersPipeline = new LinkedList<>();
    }

    public int getFileType() {
        return fileType;
    }

    public void setFileType(int fileType) {
        this.fileType = fileType;
        filter();
    }

    public void addResults(List<? extends SearchResult> completeList, List<? extends SearchResult> filteredList) {
        visualList.addAll(filteredList); // java, java, and type erasure
        list.addAll(completeList);
        notifyDataSetChanged();
    }

    @Override
    protected void populateView(View view, SearchResult sr) {
        if (sr instanceof FileSearchResult) {
            populateFilePart(view, (FileSearchResult) sr);
        }
        if (sr instanceof TorrentSearchResult) {
            populateTorrentPart(view, (TorrentSearchResult) sr);
        }
        if (sr instanceof YouTubeCrawledSearchResult) {
            populateYouTubePart(view, (YouTubeCrawledSearchResult) sr);
        }

        maybeMarkTitleOpened(view, sr);
        populateThumbnail(view, sr);
    }

    private void maybeMarkTitleOpened(View view, SearchResult sr) {
        int clickedColor = getContext().getResources().getColor(R.color.browse_peer_listview_item_inactive_foreground);
        int unclickedColor = getContext().getResources().getColor(R.color.app_text_primary);
        TextView title = findView(view, R.id.view_bittorrent_search_result_list_item_title);
        title.setTextColor(LocalSearchEngine.instance().hasBeenOpened(sr) ? clickedColor : unclickedColor);
    }

    private void populateFilePart(View view, FileSearchResult sr) {
        ImageView fileTypeIcon = findView(view, R.id.view_bittorrent_search_result_list_item_filetype_icon);
        fileTypeIcon.setImageResource(getFileTypeIconId());

        TextView adIndicator = findView(view, R.id.view_bittorrent_search_result_list_item_ad_indicator);
        adIndicator.setVisibility(View.GONE);

        TextView title = findView(view, R.id.view_bittorrent_search_result_list_item_title);
        title.setText(sr.getDisplayName());

        TextView fileSize = findView(view, R.id.view_bittorrent_search_result_list_item_file_size);
        if (sr.getSize() > 0) {
            fileSize.setText(UIUtils.getBytesInHuman(sr.getSize()));
        } else {
            fileSize.setText("...");
        }

        TextView extra = findView(view, R.id.view_bittorrent_search_result_list_item_text_extra);
        extra.setText(FilenameUtils.getExtension(sr.getFilename()));

        TextView seeds = findView(view, R.id.view_bittorrent_search_result_list_item_text_seeds);
        seeds.setText("");

        String license = sr.getLicense().equals(Licenses.UNKNOWN) ? "" : " - " + sr.getLicense();

        TextView sourceLink = findView(view, R.id.view_bittorrent_search_result_list_item_text_source);
        sourceLink.setText(sr.getSource() + license); // TODO: ask for design
        sourceLink.setTag(sr.getDetailsUrl());
        sourceLink.setPaintFlags(sourceLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        sourceLink.setOnClickListener(linkListener);
    }

    private void populateThumbnail(View view, SearchResult sr) {
        ImageView fileTypeIcon = findView(view, R.id.view_bittorrent_search_result_list_item_filetype_icon);
        if (sr.getThumbnailUrl() != null) {
            thumbLoader.load(Uri.parse(sr.getThumbnailUrl()), fileTypeIcon, 96, 96, getFileTypeIconId());
        }

        MediaPlaybackStatusOverlayView overlayView = findView(view, R.id.view_bittorrent_search_result_list_item_filetype_icon_media_playback_overlay_view);
        fileTypeIcon.setOnClickListener(previewClickListener);
        if (isAudio(sr) || sr instanceof YouTubePackageSearchResult) {
            fileTypeIcon.setTag(sr);
            overlayView.setTag(sr);
            overlayView.setVisibility(View.VISIBLE);
            overlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.PREVIEW);
            overlayView.setOnClickListener(previewClickListener);
        } else {
            fileTypeIcon.setTag(null);
            overlayView.setTag(null);
            overlayView.setVisibility(View.GONE);
            overlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.NONE);
        }
    }

    private void populateYouTubePart(View view, YouTubeCrawledSearchResult sr) {
        TextView extra = findView(view, R.id.view_bittorrent_search_result_list_item_text_extra);
        extra.setText(FilenameUtils.getExtension(sr.getFilename()));
    }

    private void populateTorrentPart(View view, TorrentSearchResult sr) {
        TextView seeds = findView(view, R.id.view_bittorrent_search_result_list_item_text_seeds);
        if (sr.getSeeds() > 0) {
            seeds.setText(getContext().getResources().getQuantityString(R.plurals.count_seeds_source, sr.getSeeds(), sr.getSeeds()));
        } else {
            seeds.setText("");
        }
    }

    @Override
    protected void onItemClicked(View v) {
        SearchResult sr = (SearchResult) v.getTag();
        searchResultClicked(sr);
    }

    abstract protected void searchResultClicked(SearchResult sr);

    private void filter() {
        this.visualList = filter(list).filtered;
        notifyDataSetInvalidated();
    }

    public FilteredSearchResults filter(List<SearchResult> results) {
        FilteredSearchResults fsr = new FilteredSearchResults();
        ArrayList<SearchResult> l = new ArrayList<>();
        List<KeywordFilter> keywordFilters = getKeywordFiltersPipeline();

        for (SearchResult sr : results) {
            MediaType mt;
            String extension = FilenameUtils.getExtension(((FileSearchResult) sr).getFilename());

            mt = MediaType.getMediaTypeForExtension(extension);

            if ("youtube".equals(extension)) {
                mt = MediaType.getVideoMediaType();
            } else if (mt != null && mt.equals(MediaType.getVideoMediaType()) && sr instanceof YouTubeCrawledSearchResult) {
                mt = null;
            }

            if (isFileSearchResultMediaTypeMatching(sr, mt)) {
                if (keywordFilters.isEmpty()) {
                    l.add(sr);
                } else if (KeywordFilter.passesFilterPipeline(sr, keywordFilters)) {
                    l.add(sr);
                }
            }
            fsr.increment(mt);
        }
        fsr.filtered = l;
        return fsr;
    }

    private boolean isFileSearchResultMediaTypeMatching(SearchResult sr, MediaType mt) {
        return sr instanceof FileSearchResult && mt != null && mt.getId() == fileType;
    }

    private static boolean isAudio(SearchResult sr) {
        if (sr instanceof SoundcloudSearchResult) {
            return true;
        }

        if (sr instanceof YouTubeCrawledStreamableSearchResult) {
            YouTubeCrawledStreamableSearchResult ytsr = (YouTubeCrawledStreamableSearchResult) sr;
            return ytsr.getVideo() == null;
        }

        return false;
    }

    private int getFileTypeIconId() {
        switch (fileType) {
            case Constants.FILE_TYPE_APPLICATIONS:
                return R.drawable.list_item_application_icon;
            case Constants.FILE_TYPE_AUDIO:
                return R.drawable.list_item_audio_icon;
            case Constants.FILE_TYPE_DOCUMENTS:
                return R.drawable.list_item_document_icon;
            case Constants.FILE_TYPE_PICTURES:
                return R.drawable.list_item_picture_icon;
            case Constants.FILE_TYPE_VIDEOS:
                return R.drawable.list_item_video_icon;
            case Constants.FILE_TYPE_TORRENTS:
                return R.drawable.list_item_torrent_icon;
            default:
                return R.drawable.list_item_question_mark;
        }
    }

    public List<KeywordFilter> getKeywordFiltersPipeline() {
        return keywordFiltersPipeline;
    }

    public void setKeywordFiltersPipeline(List<KeywordFilter> keywordFiltersPipeline) {
        if (keywordFiltersPipeline != this.keywordFiltersPipeline) {
            this.keywordFiltersPipeline.clear();
            this.keywordFiltersPipeline.addAll(keywordFiltersPipeline);
        }

        filter();
    }

    public void addKeywordFilter(KeywordFilter kf) {
        if (!keywordFiltersPipeline.contains(kf)) {
            this.keywordFiltersPipeline.add(kf);
            filter();
        }
    }

    public void removeKeywordFilter(KeywordFilter kf) {
        this.keywordFiltersPipeline.remove(kf);
        filter();
    }

    public void clearKeywordFilters() {
        this.keywordFiltersPipeline.clear();
        filter();
    }

    private static class OnLinkClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            String url = (String) v.getTag();
            UIUtils.openURL(v.getContext(), url);
            UXStats.instance().log(UXAction.SEARCH_RESULT_SOURCE_VIEW);
        }
    }

    public static class FilteredSearchResults {
        public List<SearchResult> filtered;
        public int numAudio;
        public int numVideo;
        public int numPictures;
        public int numApplications;
        public int numDocuments;
        public int numTorrents;

        private void increment(MediaType mt) {
            if (mt != null) {
                switch (mt.getId()) {
                    case Constants.FILE_TYPE_AUDIO:
                        numAudio++;
                        break;
                    case Constants.FILE_TYPE_VIDEOS:
                        numVideo++;
                        break;
                    case Constants.FILE_TYPE_PICTURES:
                        numPictures++;
                        break;
                    case Constants.FILE_TYPE_APPLICATIONS:
                        numApplications++;
                        break;
                    case Constants.FILE_TYPE_DOCUMENTS:
                        numDocuments++;
                        break;
                    case Constants.FILE_TYPE_TORRENTS:
                        numTorrents++;
                        break;
                }
            }
        }
    }

    private static final class PreviewClickListener extends ClickAdapter<Context> {
        final WeakReference<SearchResultListAdapter> adapterRef;

        PreviewClickListener(Context ctx, SearchResultListAdapter adapter) {
            super(ctx);
            adapterRef = Ref.weak(adapter);
        }

        @Override
        public void onClick(Context ctx, View v) {
            if (v == null) {
                return;
            }

            StreamableSearchResult sr = (StreamableSearchResult) v.getTag();

            if (sr != null) {
                LocalSearchEngine.instance().markOpened(sr, (Ref.alive(adapterRef)) ? adapterRef.get() : null);
                PreviewPlayerActivity.srRef = Ref.weak((FileSearchResult) sr);
                Intent i = new Intent(ctx, PreviewPlayerActivity.class);
                i.putExtra("displayName", sr.getDisplayName());
                i.putExtra("source", sr.getSource());
                i.putExtra("thumbnailUrl", sr.getThumbnailUrl());
                i.putExtra("streamUrl", sr.getStreamUrl());
                i.putExtra("audio", isAudio(sr));
                i.putExtra("hasVideo", hasVideo(sr));
                ctx.startActivity(i);
            }
        }

        private boolean hasVideo(StreamableSearchResult sr) {
            return sr instanceof YouTubePackageSearchResult;
        }
    }
}
