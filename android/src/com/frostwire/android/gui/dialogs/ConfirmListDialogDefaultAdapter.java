/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Jose Molina (votaguz)
 * Copyright (c) 2011, 2014, FrostWire(TM). All rights reserved.
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

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.AbstractConfirmListDialog.SelectionMode;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.logging.Logger;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Depending on the selection mode of your AbstractConfirmListDialog, you can give it this
 * default adapter implementation to use the out of the box list view items for
 * single selection mode (with radio buttons), multiple selection mode (with checkboxes),
 * or no selection mode which would by default accept all the presented items.
 *
 * @author gubatron
 * @author aldenml
 * @author votaguz
 *
 */
public class ConfirmListDialogDefaultAdapter<T extends SearchResult> extends AbstractListAdapter {
    private static final Logger LOGGER = Logger.getLogger(ConfirmListDialogDefaultAdapter.class);
    private static final int ITEM_TITLE = 0;
    private static final int ITEM_SIZE = 1;
    private static final int ITEM_ART = 2;

    private static final Map<SelectionMode, Integer> selectionModeToLayoutId = new HashMap<>();
    private static final Map<SelectionMode, Map<Integer,Integer>> layoutMapping = new HashMap<>();
    private final SelectionMode selectionMode;

    static {
        selectionModeToLayoutId.put(SelectionMode.NO_SELECTION, R.layout.confirmation_dialog_no_selection_list_item);
        selectionModeToLayoutId.put(SelectionMode.SINGLE_SELECTION, R.layout.confirmation_dialog_single_selection_list_item);
        selectionModeToLayoutId.put(SelectionMode.MULTIPLE_SELECTION,R.layout.confirmation_dialog_multiple_selection_list_item);

        layoutMapping.put(SelectionMode.NO_SELECTION, new HashMap<Integer, Integer>());
        layoutMapping.get(SelectionMode.NO_SELECTION).put(ITEM_TITLE, R.id.confirmation_dialog_no_selection_list_item_title);
        layoutMapping.get(SelectionMode.NO_SELECTION).put(ITEM_SIZE, R.id.confirmation_dialog_no_selection_list_item_size);
        layoutMapping.get(SelectionMode.NO_SELECTION).put(ITEM_ART, R.id.confirmation_dialog_no_selection_list_item_art);

        layoutMapping.put(SelectionMode.SINGLE_SELECTION, new HashMap<Integer, Integer>());
        layoutMapping.get(SelectionMode.SINGLE_SELECTION).put(ITEM_TITLE, R.id.confirmation_dialog_single_selection_list_item_title);
        layoutMapping.get(SelectionMode.SINGLE_SELECTION).put(ITEM_SIZE, R.id.confirmation_dialog_single_selection_list_item_size);
        layoutMapping.get(SelectionMode.SINGLE_SELECTION).put(ITEM_ART, R.id.confirmation_dialog_single_selection_list_item_art);

        layoutMapping.put(SelectionMode.MULTIPLE_SELECTION, new HashMap<Integer, Integer>());
        layoutMapping.get(SelectionMode.MULTIPLE_SELECTION).put(ITEM_TITLE, R.id.confirmation_dialog_multiple_selection_list_item_title);
        layoutMapping.get(SelectionMode.MULTIPLE_SELECTION).put(ITEM_SIZE, R.id.confirmation_dialog_multiple_selection_list_item_size);
        layoutMapping.get(SelectionMode.MULTIPLE_SELECTION).put(ITEM_ART, R.id.confirmation_dialog_multiple_selection_list_item_art);

    }

    public ConfirmListDialogDefaultAdapter(Context context, List<T> list, SelectionMode selectionMode) {
        super(context, selectionModeToLayoutId.get(selectionMode).intValue(), list);
        this.selectionMode = selectionMode;
    }

    @Override
    protected void populateView(View view, Object data) {
        if (data instanceof  SearchResult) {
            SearchResult sr = (SearchResult) data;
            TextView trackTitle = (TextView) findView(view, layoutMapping.get(selectionMode).get(ITEM_TITLE));
            trackTitle.setText(sr.getDisplayName());

            TextView trackSizeInHuman = (TextView) findView(view, layoutMapping.get(selectionMode).get(ITEM_SIZE));
            if (sr instanceof FileSearchResult){
                trackSizeInHuman.setText(UIUtils.getBytesInHuman(((FileSearchResult) sr).getSize()));
            } else {
                trackSizeInHuman.setVisibility(View.GONE);
            }

            if (!StringUtils.isNullOrEmpty(sr.getThumbnailUrl())) {
                ImageView imageView = (ImageView) findView(view, layoutMapping.get(selectionMode).get(ITEM_ART));
                ImageLoader.getInstance(getContext()).load(Uri.parse(sr.getThumbnailUrl()), imageView);
            }
        } else {
            LOGGER.warn("populateView(data): data is not a SearchResult instance ("+data.getClass().getSimpleName()+")");
        }
    }
}