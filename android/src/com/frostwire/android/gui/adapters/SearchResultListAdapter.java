/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.SystemClock;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchResultListAdapter extends AbstractListAdapter<SearchResult> {
    private static final int NO_FILE_TYPE = -1;

    private final OnLinkClickListener linkListener;
    private final PreviewClickListener previewClickListener;

    private int fileType;

    private final ImageLoader thumbLoader;
    private final List<KeywordFilter> keywordFiltersPipeline;
    private final AtomicLong lastFilterCallTimestamp = new AtomicLong();
    private FilteredSearchResults cachedFilteredSearchResults = null;

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
        cachedFilteredSearchResults = null;
        filter();
    }

    public void addResults(List<? extends SearchResult> completeList, List<? extends SearchResult> filteredList) {
        visualList.addAll(filteredList); // java, java, and type erasure
        list.addAll(completeList);
        notifyDataSetChanged();
    }

    @Override
    public void clear() {
        super.clear();
        cachedFilteredSearchResults = null;
        clearKeywordFilters();
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
        int clickedColor = getContext().getResources().getColor(R.color.my_files_listview_item_inactive_foreground);
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

    public FilteredSearchResults filter() {
        long now = SystemClock.currentThreadTimeMillis();
        long timeSinceLastFilterCall = now - lastFilterCallTimestamp.get();
        if (cachedFilteredSearchResults != null && timeSinceLastFilterCall < 250) {
            return cachedFilteredSearchResults;
        }
        lastFilterCallTimestamp.set(now);
        cachedFilteredSearchResults = filter(list);

        this.visualList = cachedFilteredSearchResults.filtered;
        notifyDataSetChanged();
        notifyDataSetInvalidated();
        return cachedFilteredSearchResults;
    }

    public FilteredSearchResults filter(List<SearchResult> results) {
        FilteredSearchResults fsr = new FilteredSearchResults();
        ArrayList<SearchResult> mediaTypedFiltered = new ArrayList<>();
        ArrayList<SearchResult> keywordFiltered = new ArrayList<>();
        List<KeywordFilter> keywordFilters = getKeywordFiltersPipeline();
        for (SearchResult sr : results) {
            String extension = FilenameUtils.getExtension(((FileSearchResult) sr).getFilename());
            MediaType mt = MediaType.getMediaTypeForExtension(extension);

            if ("youtube".equals(extension)) {
                mt = MediaType.getVideoMediaType();
            } else if (mt != null && mt.equals(MediaType.getVideoMediaType()) && sr instanceof YouTubeCrawledSearchResult) {
                // NOTE: this excludes all non .youtube youtube search results (e.g. 3gp, webm) from appearing on results
                mt = null;
            }

            boolean passedKeywordFilter = KeywordFilter.passesFilterPipeline(sr, keywordFilters);
            if (isFileSearchResultMediaTypeMatching(sr, mt)) {
                if (keywordFilters.isEmpty() || passedKeywordFilter) {
                    mediaTypedFiltered.add(sr);
                    keywordFiltered.add(sr);
                }
            } else if (mt != null && passedKeywordFilter) {
                keywordFiltered.add(sr);
            }
            fsr.increment(mt, passedKeywordFilter);
        }
        fsr.filtered = mediaTypedFiltered;
        fsr.keywordFiltered = keywordFiltered;
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

    public FilteredSearchResults setKeywordFiltersPipeline(List<KeywordFilter> keywordFiltersPipeline) {
        // if another instance is being assigned, we clear and copy its members
        if (keywordFiltersPipeline != this.keywordFiltersPipeline) {
            this.keywordFiltersPipeline.clear();
            cachedFilteredSearchResults = null;
            if (keywordFiltersPipeline != null && keywordFiltersPipeline.size() > 0) {
                this.keywordFiltersPipeline.addAll(keywordFiltersPipeline);
            }
        }
        return filter();
    }

    public FilteredSearchResults addKeywordFilter(KeywordFilter kf) {
        if (!keywordFiltersPipeline.contains(kf)) {
            this.keywordFiltersPipeline.add(kf);
            cachedFilteredSearchResults = null;
            return filter();
        }
        return null;
    }

    public FilteredSearchResults removeKeywordFilter(KeywordFilter kf) {
        this.keywordFiltersPipeline.remove(kf);
        cachedFilteredSearchResults = null;
        return filter();
    }

    public FilteredSearchResults clearKeywordFilters() {
        this.keywordFiltersPipeline.clear();
        cachedFilteredSearchResults = null;
        return filter();
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
        public List<SearchResult> keywordFiltered;

        public int numAudio;
        public int numVideo;
        public int numPictures;
        public int numApplications;
        public int numDocuments;
        public int numTorrents;

        public int numFilteredAudio;
        public int numFilteredVideo;
        public int numFilteredPictures;
        public int numFilteredApplications;
        public int numFilteredDocuments;
        public int numFilteredTorrents;

        private void increment(MediaType mt, boolean passedFilter) {
            if (mt != null) {
                switch (mt.getId()) {
                    case Constants.FILE_TYPE_AUDIO:
                        numAudio++;
                        numFilteredAudio += passedFilter ? 1 : 0;
                        break;
                    case Constants.FILE_TYPE_VIDEOS:
                        numVideo++;
                        numFilteredVideo += passedFilter ? 1 : 0;
                        break;
                    case Constants.FILE_TYPE_PICTURES:
                        numPictures++;
                        numFilteredPictures += passedFilter ? 1 : 0;
                        break;
                    case Constants.FILE_TYPE_APPLICATIONS:
                        numApplications++;
                        numFilteredApplications += passedFilter ? 1 : 0;
                        break;
                    case Constants.FILE_TYPE_DOCUMENTS:
                        numDocuments++;
                        numFilteredDocuments += passedFilter ? 1 : 0;
                        break;
                    case Constants.FILE_TYPE_TORRENTS:
                        numTorrents++;
                        numFilteredTorrents += passedFilter ? 1 : 0;
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
