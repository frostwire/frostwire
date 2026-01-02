/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters;

import static com.frostwire.android.util.SystemUtils.ensureBackgroundThreadOrCrash;
import static com.frostwire.android.util.SystemUtils.ensureUIThreadOrCrash;
import static com.frostwire.android.util.SystemUtils.postToHandler;

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
import com.frostwire.android.gui.SearchMediator;
import com.frostwire.android.gui.activities.PreviewPlayerActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.SearchResultUtils;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.MediaPlaybackOverlayPainter;
import com.frostwire.android.gui.views.MediaPlaybackStatusOverlayView;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchResultListAdapter extends AbstractListAdapter<SearchResult> {
    private static final int NO_FILE_TYPE = -1;
    private final OnLinkClickListener linkListener;
    private final PreviewClickListener previewClickListener;
    private final FWImageLoader thumbLoader;
    private int fileType;
    private FilteredSearchResults filteredSearchResults;

    protected SearchResultListAdapter(Context context) {
        super(context, R.layout.view_bittorrent_search_result_list_item);
        this.linkListener = new OnLinkClickListener();
        this.previewClickListener = new PreviewClickListener(context, this);
        this.fileType = NO_FILE_TYPE;
        this.thumbLoader = FWImageLoader.getInstance(context);
    }

    public int getFileType() {
        return fileType;
    }

    /**
     * Changes the fileType, extracts all the file search results, then filters them by type
     * and updates (FilteredSearchResults) filteredSearchResults (see getFilteredSearchResults())
     */
    public void setFileType(final int fileType, boolean appendToPreviouslyFilteredResults, Runnable callback) {
        boolean differentFileType = this.fileType != fileType;
        this.fileType = fileType;
        postToHandler(
                SystemUtils.HandlerThreadName.SEARCH_PERFORMER,
                () -> {
                    // Extract all FileSearchResult from full list
                    List<FileSearchResult> fileSearchResults;
                    synchronized (listLock) {
                        fileSearchResults = extractFileSearchResults(getFullList());
                    }
                    // Filter the entire list by file type
                    if (!appendToPreviouslyFilteredResults || filteredSearchResults == null) {
                        if (filteredSearchResults != null) {
                            filteredSearchResults.clear();
                            filteredSearchResults = null;
                        }
                        filteredSearchResults = newFilteredSearchResults(fileSearchResults, fileType);
                    } else {
                        appendFilteredSearchResults(filteredSearchResults, fileSearchResults, fileType);
                    }
                    if (filteredSearchResults != null) {
                        SystemUtils.postToUIThreadAtFront(() -> {
                            if (filteredSearchResults != null) {
                                updateVisualListWithAllMediaTypeFilteredSearchResults(filteredSearchResults.mediaTypeFiltered, differentFileType);
                            }
                        });
                    }

                    if (callback != null) {
                        try {
                            callback.run();
                        } catch (Throwable ignored) {
                        }
                    }
                });
    }

    public void setFileType(final int fileType, Runnable callback) {
        setFileType(fileType, false, callback);
    }

    public FilteredSearchResults getFilteredSearchResults() {
        return filteredSearchResults;
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
        title.setTextColor(SearchMediator.instance().hasBeenOpened(sr) ? clickedColor : unclickedColor);
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
        String license = "";
        if (sr.getLicense() != null) {
            license = sr.getLicense().equals(Licenses.UNKNOWN) ? "" : " - " + sr.getLicense();
        }
        TextView sourceLink = findView(view, R.id.view_bittorrent_search_result_list_item_text_source);
        sourceLink.setText(sr.getSource() + license); // TODO: ask for design
        boolean hasDetailsUrl = sr.getDetailsUrl() != null && !sr.getDetailsUrl().isEmpty();
        if (hasDetailsUrl) {
            sourceLink.setTag(sr.getDetailsUrl());
        } else {
            sourceLink.setTag(null);
        }
        sourceLink.setClickable(hasDetailsUrl);
        int underlineFlag = hasDetailsUrl ? Paint.UNDERLINE_TEXT_FLAG : 0;
        sourceLink.setPaintFlags(sourceLink.getPaintFlags() | underlineFlag);
        sourceLink.setOnClickListener(linkListener);
    }

    private void populateThumbnail(View view, SearchResult sr) {
        ImageView fileTypeIcon = findView(view, R.id.view_bittorrent_search_result_list_item_filetype_icon);
        if (sr.getThumbnailUrl() != null) {
            thumbLoader.load(Uri.parse(sr.getThumbnailUrl()), fileTypeIcon, 96, 96, getFileTypeIconId());
        }
        MediaPlaybackStatusOverlayView overlayView = findView(view, R.id.view_bittorrent_search_result_list_item_filetype_icon_media_playback_overlay_view);
        fileTypeIcon.setOnClickListener(previewClickListener);
        if (isAudio(sr) || isTelluridePartialSearchResult(sr)) {
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

    public void updateVisualListWithAllMediaTypeFilteredSearchResults(List<FileSearchResult> mediaTypeFiltered, boolean clearBeforeAdding) {
        ensureUIThreadOrCrash("SearchResultListAdapter::updateVisualListWithFilteredSearchResults");
        try {
            synchronized (listLock) {
                if (clearBeforeAdding) {
                    this.visualList.clear();
                }
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
        return appendFilteredSearchResults(fsr, allFileSearchResults, fileType);
    }

    public FilteredSearchResults appendFilteredSearchResults(FilteredSearchResults fsr, List<FileSearchResult> allFileSearchResults, int fileType) {
        ensureBackgroundThreadOrCrash("SearchResultListAdapter::appendFilteredSearchResults(results, fileType)");
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

    private static boolean isTelluridePartialSearchResult(SearchResult sr) {
        // YouTube results are "Telluride Preliminary Search Results" that need format/quality selection
        return SearchResultUtils.isYouTubeSearchResult(sr);
    }

    private int getFileTypeIconId() {
        return switch (fileType) {
            case Constants.FILE_TYPE_APPLICATIONS -> R.drawable.list_item_application_icon;
            case Constants.FILE_TYPE_AUDIO -> R.drawable.list_item_audio_icon;
            case Constants.FILE_TYPE_DOCUMENTS -> R.drawable.list_item_document_icon;
            case Constants.FILE_TYPE_PICTURES -> R.drawable.list_item_picture_icon;
            case Constants.FILE_TYPE_VIDEOS -> R.drawable.list_item_video_icon;
            case Constants.FILE_TYPE_TORRENTS -> R.drawable.list_item_torrent_icon;
            default -> R.drawable.list_item_question_mark;
        };
    }

    public void sortByKeywordsRelevance(String currentQuery) {
        if (currentQuery == null || currentQuery.isEmpty()) {
            return;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            // atomically snapshot to get size for pre-allocation
            List<SearchResult> snapshot;
            synchronized (listLock) {
                snapshot = new ArrayList<>(fullList);
            }

            // Pre-size maps to avoid rehashing (account for load factor 0.75)
            // This eliminates 3-5 array copies during growth
            final int capacity = (int) (snapshot.size() / 0.75f) + 1;

            List<String> tokens = PerformersHelper.tokenizeSearchKeywords(currentQuery.toLowerCase());
            tokens.removeIf(PerformersHelper.stopwords::contains);

            // PRE-COMPUTE sorting keys once: O(n × k × m)
            // This is the key optimization - previously we recomputed these in every comparison!
            Map<SearchResult, SortKey> sortKeys = new HashMap<>(capacity);

            for (SearchResult r : snapshot) {
                String normalized = PerformersHelper.searchResultAsNormalizedString(r).toLowerCase();
                int matchedTokens = PerformersHelper.countMatchedTokens(normalized, tokens);
                int levenshteinDist = PerformersHelper.levenshteinDistance(normalized, currentQuery);
                sortKeys.put(r, new SortKey(matchedTokens, levenshteinDist));
            }

            // Sort using pre-computed keys: O(n log n) with O(1) key lookups
            // Previously this was O(n² log n) due to recomputing countMatchedTokens in comparator
            snapshot.sort((a, b) -> {
                SortKey ka = sortKeys.get(a);
                SortKey kb = sortKeys.get(b);
                // Sort by matched tokens (descending), then by Levenshtein distance (ascending)
                if (ka.matchedTokens != kb.matchedTokens) {
                    return Integer.compare(kb.matchedTokens, ka.matchedTokens); // best → first
                }
                return Integer.compare(ka.levenshteinDistance, kb.levenshteinDistance);
            });

            SystemUtils.postToUIThread(() -> {
                synchronized (listLock) {
                    fullList.clear();          // replace the list atomically
                    fullList.addAll(snapshot);
                }
            });
        });
    }

    /**
     * Pre-computed sorting key for O(n log n) sort instead of O(n² log n).
     * Stores the expensive-to-compute values that were previously recalculated
     * on every comparison during the sort.
     */
    private static final class SortKey {
        final int matchedTokens;
        final int levenshteinDistance;

        SortKey(int matchedTokens, int levenshteinDistance) {
            this.matchedTokens = matchedTokens;
            this.levenshteinDistance = levenshteinDistance;
        }
    }

    private static class OnLinkClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (!v.isClickable() || v.getTag() == null) {
                return;
            }
            Object tag = v.getTag();
            String url = null;
            
            if (tag instanceof String) {
                url = (String) tag;
            } else if (tag instanceof SearchResult) {
                SearchResult sr = (SearchResult) tag;
                url = sr.getDetailsUrl();
            }
            
            if (url != null && !url.isEmpty()) {
                UIUtils.openURL(v.getContext(), url);
            }
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
            Object tag = v.getTag();
            if (isTelluridePartialSearchResult((SearchResult) tag)) {
                UIUtils.openURL(ctx, ((SearchResult) tag).getDetailsUrl());
            } else if (tag instanceof StreamableSearchResult) {
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
                                SearchMediator.instance().markOpened(sr, (Ref.alive(adapterRef)) ? adapterRef.get() : null);
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
}
