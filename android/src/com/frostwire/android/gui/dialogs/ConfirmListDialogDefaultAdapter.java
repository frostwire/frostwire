/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Jose Molina (@votaguz)
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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.AbstractConfirmListDialog.SelectionMode;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.util.Logger;

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
abstract class ConfirmListDialogDefaultAdapter<T> extends AbstractListAdapter {
    private static final Logger LOGGER = Logger.getLogger(ConfirmListDialogDefaultAdapter.class);
    private static final int ITEM_TITLE = 0;
    private static final int ITEM_SIZE = 1;
    private static final int ITEM_ART = 2;

    private static final Map<SelectionMode, Integer> selectionModeToLayoutId = new HashMap<>();
    private static final Map<SelectionMode, SparseIntArray> layoutMapping = new HashMap<>();
    private final SelectionMode selectionMode;

    static {
        selectionModeToLayoutId.put(SelectionMode.NO_SELECTION, R.layout.confirmation_dialog_no_selection_list_item);
        selectionModeToLayoutId.put(SelectionMode.SINGLE_SELECTION, R.layout.confirmation_dialog_single_selection_list_item);
        selectionModeToLayoutId.put(SelectionMode.MULTIPLE_SELECTION,R.layout.confirmation_dialog_multiple_selection_list_item);

        layoutMapping.put(SelectionMode.NO_SELECTION, new SparseIntArray());
        layoutMapping.get(SelectionMode.NO_SELECTION).put(ITEM_TITLE, R.id.confirmation_dialog_no_selection_list_item_title);
        layoutMapping.get(SelectionMode.NO_SELECTION).put(ITEM_SIZE, R.id.confirmation_dialog_no_selection_list_item_size);
        layoutMapping.get(SelectionMode.NO_SELECTION).put(ITEM_ART, R.id.confirmation_dialog_no_selection_list_item_art);

        layoutMapping.put(SelectionMode.SINGLE_SELECTION, new SparseIntArray());
        layoutMapping.get(SelectionMode.SINGLE_SELECTION).put(ITEM_TITLE, R.id.confirmation_dialog_single_selection_list_item_title);
        layoutMapping.get(SelectionMode.SINGLE_SELECTION).put(ITEM_SIZE, R.id.confirmation_dialog_single_selection_list_item_size);
        layoutMapping.get(SelectionMode.SINGLE_SELECTION).put(ITEM_ART, R.id.confirmation_dialog_single_selection_list_item_art);

        layoutMapping.put(SelectionMode.MULTIPLE_SELECTION, new SparseIntArray());
        layoutMapping.get(SelectionMode.MULTIPLE_SELECTION).put(ITEM_TITLE, R.id.confirmation_dialog_multiple_selection_list_item_title);
        layoutMapping.get(SelectionMode.MULTIPLE_SELECTION).put(ITEM_SIZE, R.id.confirmation_dialog_multiple_selection_list_item_size);
        layoutMapping.get(SelectionMode.MULTIPLE_SELECTION).put(ITEM_ART, R.id.confirmation_dialog_multiple_selection_list_item_art);
    }

    ConfirmListDialogDefaultAdapter(Context context, List<T> list, SelectionMode selectionMode) {
        super(context, selectionModeToLayoutId.get(selectionMode), list);
        this.selectionMode = selectionMode;
        setCheckboxesVisibility(selectionMode != SelectionMode.NO_SELECTION);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        T item = (T) getItem(position);
        Context ctx = getContext();

        if (view == null && ctx != null) {
            int layoutId = selectionModeToLayoutId.get(selectionMode);
            view = View.inflate(ctx, layoutId, null);
        }

        try {
            initTouchFeedback(view, item);
            if(selectionMode == SelectionMode.MULTIPLE_SELECTION) {
                initCheckBox(view, item);
                setCheckboxesVisibility(true);
            } else if (selectionMode == SelectionMode.SINGLE_SELECTION) {
                initRadioButton(view, item, position);
            }
            populateView(view, item);
        } catch (Throwable e) {
            LOGGER.error("Fatal error getting view: " + e.getMessage(), e);
        }

        return view;
    }

    public abstract CharSequence getItemTitle(T data);
    public abstract double getItemSize(T data);
    public abstract CharSequence getItemThumbnailUrl(T data);
    public abstract int getItemThumbnailResourceId(T data);

    @Override
    protected void populateView(View view, Object data) {
        T item = (T) data;

        TextView trackTitle = (TextView) findView(view, layoutMapping.get(selectionMode).get(ITEM_TITLE));
        trackTitle.setText(getItemTitle(item));

        final double itemSize = getItemSize(item);
        TextView trackSizeInHuman = (TextView) findView(view, layoutMapping.get(selectionMode).get(ITEM_SIZE));
        if (itemSize != -1) {
            trackSizeInHuman.setText(UIUtils.getBytesInHuman(itemSize));
            trackSizeInHuman.setVisibility(View.VISIBLE);
        } else {
            trackSizeInHuman.setVisibility(View.GONE);
        }

        ImageView imageView = (ImageView) findView(view, layoutMapping.get(selectionMode).get(ITEM_ART));
        final CharSequence itemThumbnailUrl = getItemThumbnailUrl(item);
        if (itemThumbnailUrl != null && itemThumbnailUrl.length() != 0) {
            ImageLoader.getInstance(getContext()).load(Uri.parse((String) itemThumbnailUrl), imageView);
        }

        final int itemThumbnailResourceId = getItemThumbnailResourceId(item);
        if (itemThumbnailResourceId != -1) {
            Drawable thumbnail = getContext().getResources().getDrawable(itemThumbnailResourceId);
            if (thumbnail != null) {
                imageView.setImageDrawable(thumbnail);
            }
        }
    }

    @Override
    protected void onItemClicked(View v) {
        if (selectionMode != SelectionMode.NO_SELECTION) {
            final T tag = (T) v.getTag();
            int i = list.indexOf(tag);
            if (i != -1 && v.getParent() instanceof LinearLayout) {
                final LinearLayout parent = (LinearLayout) v.getParent();
                if (parent instanceof View) {
                    CompoundButton compoundButton = (CompoundButton) findView(parent,
                            selectionMode == SelectionMode.SINGLE_SELECTION ?
                                    R.id.view_selectable_list_item_radiobutton :
                                    R.id.view_selectable_list_item_checkbox);
                    if (compoundButton != null) {
                        onItemChecked(compoundButton, true);
                    }
                }
            }
        }
    }
}
