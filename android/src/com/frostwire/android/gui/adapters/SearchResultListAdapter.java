/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.android.gui.adapters;

import static com.frostwire.android.util.SystemUtils.ensureBackgroundThreadOrCrash;
import static com.frostwire.android.util.SystemUtils.ensureUIThreadOrCrash;
import static com.frostwire.android.util.SystemUtils.postToUIThread;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Paint;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.activities.PreviewPlayerActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.MediaPlaybackOverlayPainter;
import com.frostwire.android.gui.views.MediaPlaybackStatusOverlayView;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchResultListAdapter extends AbstractListAdapter<SearchResult> {
    private static final int NO_FILE_TYPE = -1;
    private static final Logger LOG = Logger.getLogger(SearchResultListAdapter.class);
    private final OnLinkClickListener linkListener;
    private final PreviewClickListener previewClickListener;
    private final ImageLoader thumbLoader;
    private int fileType;

    protected SearchResultListAdapter(Context context) {
        super(context, R.layout.view_bittorrent_search_result_list_item);
        this.linkListener = new OnLinkClickListener();
        this.previewClickListener = new PreviewClickListener(context, this);
        this.fileType = NO_FILE_TYPE;
        this.thumbLoader = ImageLoader.getInstance(context);
    }

    public int getFileType() {
        return fileType;
    }

    public void setFileType(final int fileType) {
        this.fileType = fileType;
        SystemUtils.HandlerFactory.postTo(
                SystemUtils.HandlerThreadName.SEARCH_PERFORMER,
                () -> {
                    List<FileSearchResult> fileSearchResults;
                    synchronized (listLock) {
                        fileSearchResults = extractFileSearchResults(getFullList());
                    }
                    FilteredSearchResults filteredSearchResults =
                            newFilteredSearchResults(fileSearchResults, fileType);
                    postToUIThread(() ->
                            updateVisualListWithAllMediaTypeFilteredSearchResults(
                                    filteredSearchResults.mediaTypeFiltered));
                });
    }

    public void addResults(List<? extends SearchResult> allNewResults) {
        synchronized (listLock) {
            fullList.addAll(allNewResults);
        }
    }

    @Override
    protected void populateView(View view, SearchResult sr) {
        if (sr instanceof FileSearchResult) {
            populateFilePart(view, (FileSearchResult) sr);
        }
        if (sr instanceof TorrentSearchResult) {
            populateTorrentPart(view, (TorrentSearchResult) sr);
        }
        maybeMarkTitleOpened(view, sr);
        populateThumbnail(view, sr);
    }

    private void maybeMarkTitleOpened(View view, SearchResult sr) {
        int clickedColor = getContext().getResources().getColor(R.color.my_files_listview_item_inactive_foreground, null);
        int unclickedColor = getContext().getResources().getColor(R.color.app_text_primary, null);
        TextView title = findView(view, R.id.view_bittorrent_search_result_list_item_title);
        title.setTextColor(LocalSearchEngine.instance().hasBeenOpened(sr) ? clickedColor : unclickedColor);
    }

    @SuppressLint("SetTextI18n")
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
        TextView age = findView(view, R.id.view_bittorrent_ssearch_result_list_item_text_age);
        age.setText("");
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
        if (isAudio(sr)) {
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

    private void populateTorrentPart(View view, TorrentSearchResult sr) {
        TextView seeds = findView(view, R.id.view_bittorrent_search_result_list_item_text_seeds);
        if (sr.getSeeds() > 0) {
            seeds.setText(getContext().getResources().getQuantityString(R.plurals.count_seeds_source, sr.getSeeds(), sr.getSeeds()));
        } else {
            seeds.setText("");
        }
        TextView age = findView(view, R.id.view_bittorrent_ssearch_result_list_item_text_age);
        age.setText(SearchResultListAdapter.formatElapsedTime(view.getResources(), sr.getCreationTime()));
    }

    /**
     * Human friendly time elapsed, in minutes, hours, days, months and years.
     * If we ever need this elsewhere move it to a new common/.../DateUtils.java file
     * For now it's overkill.
     */
    private static String formatElapsedTime(Resources res, long creationTimeInMs) {
        long secondsElapsed = (System.currentTimeMillis() - creationTimeInMs) / 1000;
        if (secondsElapsed <= 1) {
            return res.getString(R.string.one_second);
        }
        if (secondsElapsed < 60) {
            return res.getString(R.string.n_seconds, String.valueOf(secondsElapsed));
        }
        int minutesElapsed = (int) (secondsElapsed / 60);
        if (minutesElapsed <= 1) {
            return res.getString(R.string.one_minute);
        }
        if (minutesElapsed < 60) {
            return res.getString(R.string.n_minutes, String.valueOf(minutesElapsed));
        }
        int hoursElapsed = minutesElapsed / 60;
        if (hoursElapsed <= 1) {
            return res.getString(R.string.one_hour);
        }
        if (hoursElapsed < 24) {
            return res.getString(R.string.n_hours, String.valueOf(hoursElapsed));
        }
        int daysElapsed = hoursElapsed / 24;
        if (daysElapsed <= 1) {
            return res.getString(R.string.one_day);
        }
        if (daysElapsed < 30) {
            return res.getString(R.string.n_days, String.valueOf(daysElapsed));
        }
        int monthsElapsed = daysElapsed / 30;
        if (monthsElapsed <= 1) {
            return res.getString(R.string.one_month);
        }
        if (monthsElapsed < 12) {
            return res.getString(R.string.n_months, String.valueOf(monthsElapsed));
        }
        int yearsElapsed = monthsElapsed / 12;
        if (yearsElapsed <= 1) {
            return res.getString(R.string.one_year);
        }
        if (yearsElapsed > 20) {
            return "";
        }
        return res.getString(R.string.n_years, String.valueOf(yearsElapsed));
    }

    @Override
    protected void onItemClicked(View v) {
        SearchResult sr = (SearchResult) v.getTag();
        searchResultClicked(sr);
    }

    abstract protected void searchResultClicked(SearchResult sr);

    public void updateVisualListWithAllMediaTypeFilteredSearchResults(List<FileSearchResult> mediaTypeFiltered) {
        ensureUIThreadOrCrash("SearchResultListAdapter::updateVisualListWithFilteredSearchResults");
        try {
            synchronized (listLock) {
                this.visualList.clear();
                this.visualList.addAll(mediaTypeFiltered);
            }
            notifyDataSetChanged();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public FilteredSearchResults newFilteredSearchResults(List<FileSearchResult> allFileSearchResults, int fileType) {
        ensureBackgroundThreadOrCrash("SearchResultListAdapter::newFilteredResults(results, fileType)");
        FilteredSearchResults fsr = new FilteredSearchResults();
        allFileSearchResults.forEach(fileSearchResult -> {
                    String fileExtension = FilenameUtils.getExtension(fileSearchResult.getFilename());
                    MediaType mediaTypeForExtension = MediaType.getMediaTypeForExtension(fileExtension);
                    if (mediaTypeForExtension == null || mediaTypeForExtension == MediaType.TYPE_UNKNOWN) {
                        return;
                    }
                    if (fileType == mediaTypeForExtension.getId()) {
                        fsr.increment(mediaTypeForExtension, true);
                        fsr.mediaTypeFiltered.add(fileSearchResult);
                        return;
                    }
                    fsr.increment(mediaTypeForExtension, false);
                }
        );
        return fsr;
    }

    /**
     * From all search results, returns a list with only those that are FileSearchResult
     * (so we can do .getFilename())
     */
    public static List<FileSearchResult> extractFileSearchResults(List<? extends SearchResult> allSearchResults) {
        return (List<FileSearchResult>) allSearchResults.stream().
                filter(r -> r instanceof FileSearchResult).
                collect(Collectors.toList());
    }

    private static boolean isAudio(SearchResult sr) {
        return sr instanceof SoundcloudSearchResult;
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

    private static class OnLinkClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            String url = (String) v.getTag();
            UIUtils.openURL(v.getContext(), url);
        }
    }

    public static class FilteredSearchResults {
        public final List<FileSearchResult> mediaTypeFiltered = new ArrayList<>();
        // Maybe this comes back in a simpler form
        //public List<SearchResult> keywordFiltered;

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

        public void clear() {
            mediaTypeFiltered.clear();
        }
    }

    private static final class PreviewClickListener extends ClickAdapter<Context> {
        final WeakReference<SearchResultListAdapter> adapterRef;

        private static final Logger LOG = Logger.getLogger(PreviewClickListener.class);

        PreviewClickListener(Context ctx, SearchResultListAdapter adapter) {
            super(ctx);
            adapterRef = Ref.weak(adapter);
        }

        @Override
        public void onClick(Context ctx, View v) {
            if (v == null) {
                return;
            }
            final StreamableSearchResult sr = (StreamableSearchResult) v.getTag();
            if (sr != null && ctx != null) {
                WeakReference<Context> ctxRef = Ref.weak(ctx);

                Engine.instance().getThreadPool().execute(() -> {
                    if (!Ref.alive(ctxRef)) {
                        return;
                    }
                    Activity activity = (Activity) ctxRef.get();
                    final Intent i = new Intent(activity, PreviewPlayerActivity.class);
                    PreviewPlayerActivity.srRef = Ref.weak((FileSearchResult) sr);
                    i.putExtra("displayName", sr.getDisplayName());
                    i.putExtra("source", sr.getSource());
                    i.putExtra("thumbnailUrl", sr.getThumbnailUrl());
                    i.putExtra("streamUrl", sr.getStreamUrl());
                    i.putExtra("audio", isAudio(sr));
                    i.putExtra("hasVideo", false);

                    activity.runOnUiThread(() -> {
                        if (!Ref.alive(ctxRef)) {
                            return;
                        }
                        try {
                            LocalSearchEngine.instance().markOpened(sr, (Ref.alive(adapterRef)) ? adapterRef.get() : null);
                            ctxRef.get().startActivity(i);
                        } catch (Throwable t) {
                            if (BuildConfig.DEBUG) {
                                throw t;
                            }
                            LOG.error("SearchResultListAdapter::PreviewClickListener::onClick() " + t.getMessage(), t);
                        }
                    });
                });
            }

        }
    }
}
