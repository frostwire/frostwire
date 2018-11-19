/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.app.Dialog;
import android.content.Context;
import android.os.Looper;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import com.frostwire.android.R;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * We extend from ListAdapter to populate our ListViews.
 * This one allows us to click and long click on the elements of our ListViews.
 * Supports checkbox and radio button selection.
 *
 * @param <T>
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractListAdapter<T> extends BaseAdapter implements Filterable {

    private static final Logger LOG = Logger.getLogger(AbstractListAdapter.class);

    private final Context context;
    private final int viewItemId;

    private final OnClickListener viewOnClickListener;
    private final ViewOnLongClickListener viewOnLongClickListener;
    private final ViewOnKeyListener viewOnKeyListener;

    private final CheckboxOnCheckedChangeListener checkboxOnCheckedChangeListener;
    private int lastSelectedRadioButtonIndex = -1;
    private final RadioButtonOnCheckedChangeListener radioButtonCheckedChangeListener;
    private OnItemCheckedListener<T> onItemCheckedListener;

    private ListAdapterFilter<T> filter;
    private boolean checkboxesVisibility;
    private boolean showMenuOnClick;
    private boolean showMenuOnLongClick;

    private final List<Dialog> dialogs;

    protected List<T> list;
    protected Set<T> checked;
    protected List<T> visualList;

    private AbstractListAdapter(Context context,
                                int viewItemId,
                                List<T> list,
                                Set<T> checked) {
        this.context = context;
        this.viewItemId = viewItemId;
        this.viewOnClickListener = new ViewOnClickListener();
        this.viewOnLongClickListener = new ViewOnLongClickListener();
        this.viewOnKeyListener = new ViewOnKeyListener();
        this.checkboxOnCheckedChangeListener = new CheckboxOnCheckedChangeListener();
        this.radioButtonCheckedChangeListener = new RadioButtonOnCheckedChangeListener();
        this.dialogs = new ArrayList<>();
        this.list = (list == null || list.equals(Collections.emptyList())) ? new ArrayList<>() : list;
        this.checked = checked;
        this.visualList = list;
    }

    public AbstractListAdapter(Context context, int viewItemId, List<T> list) {
        this(context, viewItemId, list, new HashSet<>());
    }

    public AbstractListAdapter(Context context, int viewItemId) {
        this(context, viewItemId, new ArrayList<>(), new HashSet<>());
    }

    protected int getViewItemId() {
        return viewItemId;
    }

    public boolean hasStableIds() {
        return true;
    }

    public boolean areAllItemsEnabled() {
        return true;
    }

    public boolean isEnabled(int position) {
        return true;
    }

    public int getItemViewType(int position) {
        return 0;
    }

    public int getViewTypeCount() {
        return 1;
    }

    public boolean isEmpty() {
        return getCount() == 0;
    }

    /**
     * @return List of items chosen with checkboxes.
     */
    public Set<T> getChecked() {
        return checked;
    }

    /**
     * @return The last item selected on a radio button.
     */
    public T getSelectedItem() {
        return getItem(lastSelectedRadioButtonIndex);
    }

    public void setChecked(Set<T> newChecked) {
        checked = newChecked;
    }

    public int getCheckedCount() {
        return (checked == null || checked.isEmpty()) ? 0 : checked.size();
    }

    public void clearChecked() {
        if (checked != null && checked.size() > 0) {
            checked.clear();
            notifyDataSetChanged();
        }
    }

    public void checkAll() {
        checked.clear();
        if (visualList != null) {
            checked.addAll(visualList);
        }
        notifyDataSetChanged();
    }

    public Context getContext() {
        return context;
    }

    /**
     * This will return the count for the current file type
     */
    public int getCount() {
        return visualList == null ? 0 : visualList.size();
    }

    /**
     * Should return the total count for all file types.
     */
    public int getTotalCount() {
        return list == null ? 0 : list.size();
    }

    public T getItem(int position) {
        try {
            return visualList.get(position);
        } catch (Throwable e) {
            if (e instanceof IndexOutOfBoundsException) {
                IndexOutOfBoundsException ioobe = new IndexOutOfBoundsException(getClass().getName() + ": " + position + " of " + getCount());
                ioobe.setStackTrace(e.getStackTrace());
                throw ioobe;
            }
        }
        return null;
    }

    public int getItemPosition(T item) {
        int position = -1;
        try {
            position = visualList.indexOf(item);
        } catch (Throwable ignored) {
        }
        return position;
    }

    public int getViewPosition(View view) {
        T tag = (T) view.getTag();
        int result = -1;
        int i = 0;
        for (T t : visualList) {
            if (t.equals(tag)) {
                result = i;
                break;
            }
            i++;
        }
        return result;
    }

    public long getItemId(int position) {
        return position;
    }

    public void setList(List<T> list) {
        this.list = list.equals(Collections.emptyList()) ? new ArrayList<>() : list;
        this.visualList = this.list;
        this.checked.clear();
        notifyDataSetInvalidated();
    }

    private void addList(List<T> g, boolean checked) {
        visualList.addAll(g);
        if (visualList != list) {
            list.addAll(g);
        }
        if (checked) {
            this.checked.addAll(g);
        }
        notifyDataSetChanged();
    }

    /**
     * Adds new results to the existing list.
     */
    public void addList(List<T> g) {
        addList(g, false);
    }

    public void addItem(T item) {
        addItem(item, true);
    }

    public void addItem(T item, boolean visible) {
        if (visible) {
            visualList.add(item);
            if (visualList != list) {
                list.add(item);
            }
        } else {
            if (visualList == list) {
                visualList = new ArrayList<>(list);
            }
            list.add(item);
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyDataSetChanged();
        }
    }

    /**
     * Note: only calls notifyDataSetChanged if called from the main thread
     * @param item
     */
    public void deleteItem(T item) {
        visualList.remove(item);
        if (visualList != list) {
            list.remove(item);
        }
        checked.remove(item);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyDataSetChanged();
        }
    }

    public void updateList(List<T> g) {
        list = g;
        visualList = g;
        checked.clear();
        notifyDataSetChanged();
    }

    public void clear() {
        if (list != null) {
            list.clear();
        }
        if (visualList != null) {
            visualList.clear();
        }
        if (checked != null) {
            checked.clear();
        }
        notifyDataSetInvalidated();
    }

    public List<T> getList() {
        return list;
    }

    /**
     * Inflates the view out of the XML.
     * <p/>
     * Sets click and long click listeners in case you need them. (Override onItemClicked and onItemLongClicked)
     * <p/>
     * Let's the adapter know that the view has been created, in case you need to go deeper and create
     * more advanced click behavior or even add new Views during runtime.
     * <p/>
     * It will also bind the data to the view, you can refer to it if you need it by doing a .getTag()
     */
    public View getView(int position, View view, ViewGroup parent) {
        T item = getItem(position);
        Context ctx = getContext();
        if (view == null && ctx != null) {
            // every list view item is wrapped in a generic container which has a hidden checkbox on the left hand side.
            view = View.inflate(ctx, R.layout.view_selectable_list_item, null);
            LinearLayout container = findView(view, R.id.view_selectable_list_item_container);
            View.inflate(ctx, viewItemId, container);
        }
        try {
            initTouchFeedback(view, item);
            initCheckBox(view, item);
            initRadioButton(view, item, position);
            populateView(view, item);
        } catch (Throwable e) {
            LOG.error("Fatal error getting view: " + e.getMessage(), e);
        }
        return view;
    }

    public Filter getFilter() {
        return new AbstractListAdapterFilter(this, filter);
    }

    /**
     * So that results can be filtered. This discriminator should define which fields of T are the ones eligible for filtering.
     */
    public void setAdapterFilter(ListAdapterFilter<T> filter) {
        this.filter = filter;
    }

    public boolean getCheckboxesVisibility() {
        return checkboxesVisibility;
    }

    public void setCheckboxesVisibility(boolean checkboxesVisibility) {
        this.checkboxesVisibility = checkboxesVisibility;
        notifyDataSetChanged();
    }

    public boolean getShowMenuOnClick() {
        return showMenuOnClick;
    }

    public void setShowMenuOnClick(boolean showMenuOnClick) {
        this.showMenuOnClick = showMenuOnClick;
    }

    public void setShowMenuOnLongClick(boolean showMenuOnLongClick) {
        this.showMenuOnLongClick = showMenuOnLongClick;
    }

    /**
     * Implement this method to refresh the UI contents of the List Item with the data.
     */
    protected abstract void populateView(View view, T data);

    /**
     * Override this method if you want to do something when the overall List Item is clicked.
     */
    protected void onItemClicked(View v) {
    }

    /**
     * Override this method if you want to do something when the overall List Item is long clicked.
     */
    protected boolean onItemLongClicked(View v) {
        return false;
    }

    /**
     * Override this method if you want to do something when the DPAD or ENTER key is pressed and released.
     * This is some sort of master click.
     *
     * @param v
     * @return if handled
     */
    private boolean onItemKeyMaster(View v) {
        return false;
    }

    protected void onItemChecked(View v, boolean isChecked) {
        if (v instanceof RadioButton) {
            onRadioButtonItemChecked((RadioButton) v, isChecked);
        } else if (v instanceof Checkable) {
            onCheckboxItemChecked(v, isChecked);
        }
        notifyDataSetInvalidated();
        if (onItemCheckedListener != null) {
            onItemCheckedListener.onItemChecked(v, (T) v.getTag(), isChecked);
        }
    }

    private void onCheckboxItemChecked(View v, boolean isChecked) {
        T item = (T) v.getTag();
        if (item != null) {
            if (isChecked && !checked.contains(item)) {
                checked.add(item);
            } else {
                checked.remove(item);
            }
        }
    }


    private void updateLastRadioButtonChecked(int position) {
        lastSelectedRadioButtonIndex = position;
    }

    private void onRadioButtonItemChecked(RadioButton radioButton, boolean isChecked) {
        if (isChecked) {
            T item = (T) radioButton.getTag();
            int position = (item == null) ? 0 : getList().indexOf(item);
            updateLastRadioButtonChecked(position);
        }
    }

    /**
     * Helper function.
     */
    @SuppressWarnings("unchecked")
    protected final <TView extends View> TView findView(View view, int id) {
        return (TView) getView(view, getHolder(view), id);
    }

    private SparseArray<View> getHolder(View view) {
        @SuppressWarnings("unchecked")
        SparseArray<View> h = (SparseArray<View>) view.getTag(R.id.abstract_list_adapter_holder_tag_id);
        if (h == null) {
            h = new SparseArray<>();
            view.setTag(R.id.abstract_list_adapter_holder_tag_id, h);
        }
        return h;
    }

    private View getView(View view, SparseArray<View> h, int id) {
        View v;
        int index = h.indexOfKey(id);
        if (index < 0) {
            v = view.findViewById(id);
            h.put(id, v);
        } else {
            v = h.valueAt(index);
        }
        return v;
    }

    /**
     * If you want to create a menu per item, return here the menu adapter.
     * The menu will be created automatically and the vent long click will be eaten.
     */
    protected MenuAdapter getMenuAdapter(View view) {
        return null;
    }

    protected Dialog trackDialog(Dialog dialog) {
        dialogs.add(dialog);
        return dialog;
    }

    /**
     * Sets up the behavior of a possible checkbox to check this item.
     * <p/>
     * Takes in consideration:
     * - Only so many views are created and reused by the ListView
     * - Setting the correct checked/unchecked value without triggering the onCheckedChanged event.
     *
     * @see #getChecked()
     */
    protected void initCheckBox(View view, T item) {
        CheckBox checkbox = findView(view, R.id.view_selectable_list_item_checkbox);
        if (checkbox != null) {
            checkbox.setVisibility((checkboxesVisibility) ? View.VISIBLE : View.GONE);
            if (checkbox.getVisibility() == View.VISIBLE) {
                checkbox.setOnCheckedChangeListener(null);
                checkbox.setChecked(checked.contains(item));
                checkbox.setTag(item);
                checkbox.setOnCheckedChangeListener(checkboxOnCheckedChangeListener);
            }
        }
        if (view instanceof Checkable) {
            ((Checkable) view).setChecked(checked.contains(item));
        }
    }

    protected void initTouchFeedback(View v, T item) {
        initTouchFeedback(v, item, viewOnClickListener, viewOnLongClickListener, viewOnKeyListener);
    }

    protected void initTouchFeedback(View v,
                                     T item,
                                     OnClickListener clickListener,
                                     OnLongClickListener longClickListener,
                                     OnKeyListener keyListener) {
        if (v == null || v instanceof CheckBox) {
            return;
        }
        v.setOnClickListener(clickListener);
        v.setOnLongClickListener(longClickListener);
        v.setOnKeyListener(keyListener);
        v.setTag(item);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = vg.getChildAt(i);
                if (child != null) {
                    initTouchFeedback(child, item, clickListener, longClickListener, keyListener);
                }
            }
        }
    }

    public void setChecked(int checkedOffset, boolean checked) {
        final T item = getItem(checkedOffset);
        if (item != null) {
            if (getChecked().contains(item) && !checked) {
                getChecked().remove(item);
            } else if (!getChecked().contains(item) && checked) {
                getChecked().add(item);
            }
        }
    }

    /**
     * Meant to be overridden. Here you must return a String that shows the sum of all the checked elements
     * and some significant unit. For files, this would be the amount of total bytes if we summed all the selected
     * files. If you had a list of items to purchase, this could be total amount of money and a currency symbol.
     */
    public String getCheckedSum() {
        return null;
    }

    public interface OnItemCheckedListener<T> {
        void onItemChecked(View v, T item, boolean checked);
    }

    public void setOnItemCheckedListener(OnItemCheckedListener<T> onItemCheckedListener) {
        this.onItemCheckedListener = onItemCheckedListener;
    }

    public boolean showMenu(View v) {
        MenuAdapter adapter = getMenuAdapter(v);
        if (adapter != null && adapter.getCount() > 0) {
            trackDialog(new MenuBuilder(adapter).show());
            return true;
        }
        return false;
    }


    private final class ViewOnClickListener implements OnClickListener {
        public void onClick(View v) {
            if (showMenuOnClick) {
                showMenu(v);
                return;
            }
            LOG.info("AbstractListAdapter.ViewOnClickListener.onClick()");
            onItemClicked(v);
        }
    }

    private final class ViewOnLongClickListener implements OnLongClickListener {
        public boolean onLongClick(View v) {
            if (showMenuOnLongClick) {
                if (showMenu(v)) {
                    return true;
                }
            }
            return onItemLongClicked(v);
        }
    }

    private final class ViewOnKeyListener implements OnKeyListener {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        return onItemKeyMaster(v);
                    }
            }
            return false;
        }
    }

    private final class CheckboxOnCheckedChangeListener implements OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            onItemChecked(buttonView, isChecked);
        }
    }

    private final class AbstractListAdapterFilter extends Filter {

        private final AbstractListAdapter<T> adapter;
        private final ListAdapterFilter<T> filter;

        AbstractListAdapterFilter(AbstractListAdapter<T> adapter, ListAdapterFilter<T> filter) {
            this.adapter = adapter;
            this.filter = filter;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<T> list = adapter.getList();
            FilterResults result = new FilterResults();
            if (filter == null) {
                result.values = list;
                result.count = list.size();
            } else {
                List<T> filtered = new ArrayList<>();
                int size = list.size();
                for (int i = 0; i < size; i++) {
                    T obj = list.get(i);
                    if (filter.accept(obj, constraint)) {
                        filtered.add(obj);
                    }
                }
                result.values = filtered;
                result.count = filtered.size();
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            adapter.visualList = (List<T>) results.values;
            notifyDataSetInvalidated();
        }
    }

    private final class RadioButtonOnCheckedChangeListener implements OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            onItemChecked(buttonView, isChecked);
        }
    }

    public void setLastSelectedRadioButton(int index) {
        lastSelectedRadioButtonIndex = index;
    }

    protected void initRadioButton(View view, T tag, int position) {
        final RadioButton radioButton = findView(view, R.id.view_selectable_list_item_radiobutton);
        if (radioButton != null) {
            radioButton.setVisibility(View.VISIBLE);
            radioButton.setTag(tag);
            radioButton.setOnCheckedChangeListener(null);
            radioButton.setChecked(position == lastSelectedRadioButtonIndex);
            radioButton.setOnCheckedChangeListener(radioButtonCheckedChangeListener);
            if (position == lastSelectedRadioButtonIndex) {
                updateLastRadioButtonChecked(position);
            }
        }
    }

    public int getLastSelectedRadioButtonIndex() {
        return lastSelectedRadioButtonIndex;
    }
}
