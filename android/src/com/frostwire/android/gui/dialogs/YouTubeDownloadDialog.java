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

package com.frostwire.android.gui.dialogs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchResult;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledStreamableSearchResult;
import com.frostwire.search.youtube.YouTubePackageSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Created on 4/30/16.
 *
 * @author gubatron
 * @author aldenml
 *
 */
@SuppressWarnings("WeakerAccess") // We need the class to be public so that the dialog can be rotated (via Reflection)
@SuppressLint("ValidFragment")
public class YouTubeDownloadDialog extends AbstractConfirmListDialog<SearchResult> {
    private static Logger LOG = Logger.getLogger(YouTubeDownloadDialog.class);
    private static WeakReference<YouTubePackageSearchResult> youTubePackageSearchResultWeakReference;
    private static Pattern FORMAT_PATTERN = Pattern.compile("(?is)(H26.*p?|VP8.*p?|AAC.*p?)");

    public YouTubeDownloadDialog() {
        super();
    }

    private YouTubeDownloadDialog(YouTubePackageSearchResult sr) {
        super();
        youTubePackageSearchResultWeakReference = Ref.weak(sr);
    }

    public static YouTubeDownloadDialog newInstance(
            Context ctx,
            YouTubePackageSearchResult sr) {
        YouTubeDownloadDialog dlg = new YouTubeDownloadDialog(sr);

        dlg.onAttach((Activity) ctx);

        dlg.prepareArguments(R.drawable.download_icon,
                sr.getDisplayName(),
                ctx.getString(R.string.pick_the_files_you_want_to_download_from_this_torrent),
                null, // use the static weak reference object
                SelectionMode.SINGLE_SELECTION);

        // dlg.getArguments().putBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS, checked);
        dlg.getArguments().putInt(BUNDLE_KEY_LAST_SELECTED_RADIO_BUTTON_INDEX, 0);
        dlg.setOnYesListener(new OnStartDownloadsClickListener(ctx, dlg));

        return dlg;
    }


    @Override
    protected View.OnClickListener createOnYesListener(AbstractConfirmListDialog dlg) {
        return new OnStartDownloadsClickListener(getActivity(), dlg);
    }

    @Override
    public List<SearchResult> deserializeData(String listDataInJSON) {
        if (!Ref.alive(youTubePackageSearchResultWeakReference)) {
            return new LinkedList();
        }
        return youTubePackageSearchResultWeakReference.get().children();
    }

    @Override
    void prepareArguments(int dialogIcon, String dialogTitle, String dialogText, String listDataInJSON, SelectionMode selectionMode) {
        super.prepareArguments(dialogIcon, dialogTitle, dialogText, listDataInJSON, selectionMode);
        onSaveInstanceState(getArguments());
    }

    @Override
    public ConfirmListDialogDefaultAdapter<SearchResult> createAdapter(Context context, List<SearchResult> listData, SelectionMode selectionMode, Bundle bundle) {
        final List<SearchResult> searchResults = filterVideoOnly(listData);
        Collections.sort(searchResults, new SizeComparator());
        return new YouTubeEntriesDialogAdapter(context, searchResults);
    }

    private List<SearchResult> filterVideoOnly(List<SearchResult> listData) {
        List<SearchResult> result = new ArrayList<>();
        for (SearchResult sr : listData) {
            String format = extractFormat(sr.getDisplayName());
            if (format.startsWith("AAC") ||
                format.startsWith("MP3") ||
                format.startsWith("H263 MP3")) {
                continue;
            }
            result.add(sr);
        }
        return result;
    }

    private static String extractFormat(String fileName) {
        final Matcher matcher = FORMAT_PATTERN.matcher(fileName);
        String format = null;
        if (matcher.find()) {
            format = matcher.group(1).replace("_"," ").trim();
        }
        return format;
    }

    static long getSearchResultSize(SearchResult data) {
        if (data instanceof YouTubeCrawledSearchResult) {
            return ((YouTubeCrawledSearchResult) data).getSize();
        } else if (data instanceof YouTubeCrawledStreamableSearchResult) {
            return ((YouTubeCrawledStreamableSearchResult) data).getSize();
        } else {
            LOG.warn("getItemSize() -> -1: unhandled instance type, class = ["+data.getClass().getSimpleName()+"]");
        }
        return -1;
    }

    private class YouTubeEntriesDialogAdapter extends ConfirmListDialogDefaultAdapter<SearchResult> {
        YouTubeEntriesDialogAdapter(Context context,
                                    List<SearchResult> list) {
            super(context, list, SelectionMode.SINGLE_SELECTION);
        }

        @Override
        public CharSequence getItemTitle(SearchResult data) {
            String result = data.getDisplayName();
            if (YouTubeDownloadDialog.this.getDialogTitle() != null) {
                result = extractFormat(data.getDisplayName());
            }
            return result;
        }

        @Override
        public long getItemSize(SearchResult data) {
            return getSearchResultSize(data);
        }

        @Override
        public CharSequence getItemThumbnailUrl(SearchResult data) {
            return data.getThumbnailUrl();
        }

        @Override
        public int getItemThumbnailResourceId(SearchResult data) {
            return -1;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            return super.getView(position, view, parent);
        }

        @Override
        public String getCheckedSum() {
            return ""; // unused for single selection mode dialog.
        }
    }

    private static class OnStartDownloadsClickListener implements View.OnClickListener {
        private final WeakReference<Context> ctxRef;
        private WeakReference<AbstractConfirmListDialog> dlgRef;

        OnStartDownloadsClickListener(Context ctx, AbstractConfirmListDialog dlg) {
            ctxRef = new WeakReference<>(ctx);
            dlgRef = new WeakReference<>(dlg);
        }

        public void setDialog(AbstractConfirmListDialog dlg) {
            dlgRef = new WeakReference<>(dlg);
        }

        @Override
        public void onClick(View v) {
            if (Ref.alive(ctxRef) && Ref.alive(dlgRef) && Ref.alive(youTubePackageSearchResultWeakReference)) {
                final AbstractConfirmListDialog dlg = dlgRef.get();
                final SearchResult searchResult = (SearchResult) dlg.getSelectedItem();
                TransferManager.instance().download(searchResult);
                dlg.dismiss();

                UIUtils.showTransfersOnDownloadStart(ctxRef.get());
                if (ctxRef.get() instanceof Activity) {
                    Offers.showInterstitialOfferIfNecessary((Activity) ctxRef.get(), Offers.PLACEMENT_INTERSTITIAL_EXIT, false, false);
                }
            }
        }
    }

    private static class SizeComparator implements Comparator<SearchResult> {
        @Override
        public int compare(SearchResult left, SearchResult right) {
            return (int) (getSearchResultSize(right) - getSearchResultSize(left));
        }
    }
}
