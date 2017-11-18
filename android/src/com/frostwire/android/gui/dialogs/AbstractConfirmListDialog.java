/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *            Jose Molina (@votaguz), Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.dialogs;

import android.app.Dialog;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.util.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This dialog should evolve to allow us for reuse on a number of situations in which you
 * need a dialog that needs to display a list view control.
 * 
 * This would be the simplest version, in the future it will have a text editor to filter
 * the contents of the list, and it will also support different modes of selection.
 * 
 * For now it just uses an adapter to display the contents of the model data.
 * 
 * It's up to the user to implement the adapter (hmm, perhaps that's where the selection mode logic should be)
 * 
 * @author aldenml
 * @author gubatron
 * @author votaguz
 * @author marcelinkaaa
 *
 */
abstract class AbstractConfirmListDialog<T> extends AbstractDialog implements
        AbstractListAdapter.OnItemCheckedListener<T> {

    private static final Logger LOG = Logger.getLogger(AbstractConfirmListDialog.class);

    static final String BUNDLE_KEY_CHECKED_OFFSETS = "checkedOffsets";
    static final String BUNDLE_KEY_LAST_SELECTED_RADIO_BUTTON_INDEX = "lastSelectedRadioButtonIndex";

    private static final String BUNDLE_KEY_DIALOG_TITLE = "title";
    private static final String BUNDLE_KEY_DIALOG_TEXT = "dialogText";
    private static final String BUNDLE_KEY_LIST_DATA = "listData";
    private static final String BUNDLE_KEY_SELECTION_MODE = "selectionMode";

    /**
     * TODOS: 1. Add an optional text filter control that will be connected to the adapter.
     */

    private CompoundButton.OnCheckedChangeListener selectAllCheckboxOnCheckedChangeListener;

    enum SelectionMode {
        NO_SELECTION,
        SINGLE_SELECTION,
        MULTIPLE_SELECTION;

       public static SelectionMode fromInt(int n) {
           SelectionMode selectionMode = SelectionMode.NO_SELECTION;
           if (n == SelectionMode.MULTIPLE_SELECTION.ordinal()) {
               selectionMode = SelectionMode.MULTIPLE_SELECTION;
           } else if (n == SelectionMode.SINGLE_SELECTION.ordinal()) {
               selectionMode = SelectionMode.SINGLE_SELECTION;
           }
           return selectionMode;
       }
    }

    protected final static String TAG = "confirm_list_dialog";

    private SelectionMode selectionMode;
    private Dialog dlg;
    private OnCancelListener onCancelListener;
    private OnClickListener onYesListener;
    private ConfirmListDialogDefaultAdapter<T> adapter;

    abstract protected OnClickListener createOnYesListener();

    /** rebuilds list of objects from json and does listView.setAdapter(YourAdapter(theObjectList)) */
    abstract public List<T> deserializeData(String listDataInJSON);

    public AbstractConfirmListDialog()  {
        super(TAG, R.layout.dialog_confirm_list);
    }

    @Override
    public void show(FragmentManager manager) {
        try {
            super.show(manager);
        } catch (Throwable e) {
            // java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
            // TODO: this needs a refactor
            LOG.error("Error in show, review your logic", e);
        }
    }

    SelectionMode getSelectionMode() {
        return selectionMode;
    }

    void prepareArguments(int dialogIcon,
                          String dialogTitle,
                          String dialogText,
                          String listDataInJSON,
                          SelectionMode selectionMode) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_KEY_DIALOG_TITLE, dialogTitle);
        bundle.putString(BUNDLE_KEY_DIALOG_TEXT, dialogText);

        if (listDataInJSON != null) {
            bundle.putString(BUNDLE_KEY_LIST_DATA, listDataInJSON);
        }
        bundle.putInt(BUNDLE_KEY_SELECTION_MODE, selectionMode.ordinal());
        this.selectionMode = selectionMode;

        if (selectionMode == SelectionMode.SINGLE_SELECTION) {
            bundle.putInt(BUNDLE_KEY_LAST_SELECTED_RADIO_BUTTON_INDEX, 0);
        }

        setArguments(bundle);
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        this.dlg = dlg;
        Bundle bundle = getArguments();
        String title = bundle.getString(BUNDLE_KEY_DIALOG_TITLE);

        TextView dialogTitle = findView(dlg, R.id.dialog_confirm_list_title);
        dialogTitle.setText(R.string.confirm_download);
        TextView dialogText = findView(dlg, R.id.dialog_confirm_list_text);
        dialogText.setText(title);

        initListViewAndAdapter(bundle);
        initSelectAllCheckbox();
        initButtonListeners();
    }

    private void initButtonListeners() {
        final Dialog dialog = dlg;
        Button noButton = findView(dialog, R.id.dialog_confirm_list_button_no);
        noButton.setOnClickListener(v -> {
            if (onCancelListener != null) {
                onCancelListener.onCancel(dialog);
            }
            dialog.dismiss();
        });

        onYesListener = createOnYesListener();
        if (onYesListener != null) {
            Button yesButton = findView(dialog, R.id.dialog_confirm_list_button_yes);
            yesButton.setOnClickListener(onYesListener);
        }
    }

    private void initSelectAllCheckbox() {
        final CheckBox selectAllCheckbox = findView(dlg, R.id.dialog_confirm_list_select_all_checkbox);

        if (selectionMode != SelectionMode.MULTIPLE_SELECTION) {
            selectAllCheckbox.setVisibility(View.GONE);
            return;
        }

        selectAllCheckboxOnCheckedChangeListener = (buttonView, isChecked) -> {
            if (buttonView.isChecked()) {
                adapter.checkAll();
            } else {
                adapter.clearChecked();
            }
            updateSelectedCount();
            updateSelectedInBundle();
        };

        selectAllCheckbox.setVisibility(View.VISIBLE);
        selectAllCheckbox.setOnCheckedChangeListener(selectAllCheckboxOnCheckedChangeListener);
    }

    public String getDialogTitle() {
        Bundle bundle = getArguments();
        if (bundle != null) {
            return bundle.getString(BUNDLE_KEY_DIALOG_TITLE);
        }
        return null;
    }

    public abstract ConfirmListDialogDefaultAdapter<T> createAdapter(Context context,
                                                                     List<T> listData,
                                                                     SelectionMode selectionMode,
                                                                     Bundle bundle);

    private void initListViewAndAdapter(Bundle bundle) {
        ListView listView = findView(dlg, R.id.dialog_confirm_list_listview);

        String listDataString = bundle.getString(BUNDLE_KEY_LIST_DATA);
        List<T> listData = deserializeData(listDataString);

        if (selectionMode == null) {
            selectionMode = SelectionMode.fromInt(bundle.getInt(BUNDLE_KEY_SELECTION_MODE));
        }

        if (adapter == null &&
            listData != null  &&
            !listData.isEmpty()) {
            adapter = createAdapter(getActivity(), listData, selectionMode, bundle);
        } else if (adapter != null && adapter.getTotalCount() == 0 && !listData.isEmpty()) {
            adapter.addList(listData);
        }

        if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
            updateAdapterChecked(bundle);
        } else if (selectionMode == SelectionMode.SINGLE_SELECTION) {
            updateAdapterLastSelected(bundle);
            scrollToSelectedRadioButton();
        }

        if (adapter != null) {
            listView.setAdapter(adapter);
            updateSelectedCount();
            adapter.setOnItemCheckedListener(this);
        }
    }

    private void updateAdapterLastSelected(Bundle bundle) {
        if (adapter != null && bundle.containsKey(BUNDLE_KEY_LAST_SELECTED_RADIO_BUTTON_INDEX)) {
            int index = bundle.getInt(BUNDLE_KEY_LAST_SELECTED_RADIO_BUTTON_INDEX);
            adapter.setLastSelectedRadioButton(index);
            adapter.notifyDataSetChanged();
        }
    }

    private void updateAdapterChecked(Bundle bundle) {
        if (adapter == null) {
            return;
        }
        if (bundle.containsKey(BUNDLE_KEY_CHECKED_OFFSETS)) {
            final boolean[] checkedOffsets = bundle.getBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS);
            for (int i=0; i < checkedOffsets.length; i++) {
                adapter.setChecked(i, checkedOffsets[i]);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (adapter != null) {
            if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
                final Set checked = adapter.getChecked();
                if (outState != null && checked != null && !checked.isEmpty()) {
                    outState.putBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS, getSelected());
                }
            } else if (selectionMode == SelectionMode.SINGLE_SELECTION) {
                outState.putInt(BUNDLE_KEY_LAST_SELECTED_RADIO_BUTTON_INDEX, adapter.getLastSelectedRadioButtonIndex());
            }
        }
        super.onSaveInstanceState(outState);
    }

    void setOnYesListener(OnClickListener listener) {
        onYesListener = listener;
    }

    @SuppressWarnings("unused")
    /**
     * In case we want to do something before dismissing the dialog. (Unused at the moment)
     */
    void setOnCancelListener(OnCancelListener l) {
        onCancelListener = l;
    }

    public Set<T> getChecked() {
        Set<T> result = new HashSet<>();
        if (adapter != null) {
            if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
                result = (Set<T>) adapter.getChecked();
            } else if (selectionMode == SelectionMode.SINGLE_SELECTION) {
                result.add((T) adapter.getSelectedItem());
            } else if (selectionMode == SelectionMode.NO_SELECTION) {
                result.addAll(adapter.getList());
            }
        }
        return result;
    }

    public List<T> getList() {
        List<T> result = (List<T>) Collections.EMPTY_LIST;
        if (adapter != null) {
            result = adapter.getList();
        }
        return result;
    }

    public boolean[] getSelected() {
        boolean[] result = new boolean[0];
        if (adapter != null) {
            Set<T> checked = adapter.getChecked();
            if (checked == null || checked.isEmpty()) {
                return result;
            }
            result = new boolean[adapter.getCount()];
            List<T> all = adapter.getList();
            for (T item : checked) {
                int i = all.indexOf(item);
                if (i >= 0) {
                    result[i] = true;
                } else {
                    LOG.warn("getSelected() is not finding the checked items on the list. Verify that [" + item.getClass().getSimpleName() + "] implements equals() and hashCode()");
                }
            }
        }
        return result;
    }

    T getSelectedItem() {
        return (T) adapter.getSelectedItem();
    }

    private int getLastSelectedIndex() {
        return adapter.getLastSelectedRadioButtonIndex();
    }

    private void updateSelectedInBundle() {
        final Bundle arguments = getArguments();
        if (arguments != null) {
            if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
                arguments.putBooleanArray(BUNDLE_KEY_CHECKED_OFFSETS, getSelected());
            } else if (selectionMode == SelectionMode.SINGLE_SELECTION) {
                arguments.putInt(BUNDLE_KEY_LAST_SELECTED_RADIO_BUTTON_INDEX, getLastSelectedIndex());
            }
        }
    }

    /**
     * If the selection mode is single selection mode it will hide the summary layout.
     */
    private void updateSelectedCount() {
        if (adapter == null || selectionMode == SelectionMode.SINGLE_SELECTION) {
            final LinearLayout summaryLayout = findView(dlg, R.id.dialog_confirm_list_selection_summary);
            summaryLayout.setVisibility(View.GONE );
            return;
        }

        int selected = adapter.getCheckedCount();
        String selectedSum = adapter.getCheckedSum();
        updatedSelectedCount(selected, selectedSum);
        autoToggleSelectAllCheckbox(selected);
    }


    private void updatedSelectedCount(int selected, String selectedSum) {
        final LinearLayout summaryLayout = findView(dlg, R.id.dialog_confirm_list_selection_summary);
        final TextView numCheckedTextView = findView(dlg, R.id.dialog_confirm_list_num_checked_textview);

        boolean summaryVisible = selected > 0 &&
                                 selectionMode == SelectionMode.MULTIPLE_SELECTION &&
                                 summaryLayout != null &&
                                 numCheckedTextView != null;

        if (summaryLayout != null) {
            summaryLayout.setVisibility(summaryVisible ? View.VISIBLE : View.GONE);
            numCheckedTextView.setText(selected + " " + getString(R.string.selected));
            numCheckedTextView.setVisibility(View.VISIBLE);

            final TextView sumCheckedTextView = findView(dlg, R.id.dialog_confirm_list_sum_checked_textview);
            if (sumCheckedTextView != null) {
                sumCheckedTextView.setVisibility(selectedSum != null && !selectedSum.equals("") ? View.VISIBLE : View.GONE);
                if (selectedSum != null && !selectedSum.equals("")) {
                    sumCheckedTextView.setText(selectedSum);
                }
            }
        }
    }

    private void autoToggleSelectAllCheckbox(int selected) {
        // Change the state of the "Select All" checkbox only when necessary.
        final CheckBox selectAllCheckbox = findView(dlg, R.id.dialog_confirm_list_select_all_checkbox);
        selectAllCheckbox.setOnCheckedChangeListener(null);
        boolean wasChecked = selectAllCheckbox.isChecked();
        int total = adapter.getTotalCount();
        if (wasChecked && selected < total) {
            selectAllCheckbox.setChecked(false);
        } else if (!wasChecked && selected == total) {
            selectAllCheckbox.setChecked(true);
        }
        selectAllCheckbox.setOnCheckedChangeListener(selectAllCheckboxOnCheckedChangeListener);
    }

    // AbstractListAdapter.OnItemCheckedListener.onItemChecked(View v, boolean checked)
    @Override
    public void onItemChecked(View v, T item, boolean checked) {
        if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
            updateSelectedCount();
        }
        updateSelectedInBundle();
    }

    private void scrollToSelectedRadioButton() {
        if (dlg != null && selectionMode == SelectionMode.SINGLE_SELECTION ) {
            ListView listView = findView(dlg, R.id.dialog_confirm_list_listview);
            if (listView == null) {
                return;
            }

            // TODO: Fix for dialog rotation, as it won't scroll if the element selected
            //       has't been painted yet. Works fine if the element would be painted from the get go
            //       then it can scroll as getChildAt() does return a view.
            // I've tried:
            // - Calculating the offset by multiplying the first visible element's height x lastSelectedIndex.
            //   it does scroll, but it doesn't populate the views, so you see blank unless you then
            //   manually scroll up and down.
            // - Populating the views with adapter.getView(), but I ended up in an infinite loop and this dialog has taken too long now.
            //   this is probably the path to fixing it. maybe you gotta turn off some listener that ends up calling
            //   this method again.

            if (listView.getAdapter() != null && listView.getChildCount() > 0) {
                View selectedView = listView.getChildAt(adapter.getLastSelectedRadioButtonIndex());

                if (selectedView == null) {
                    selectedView = adapter.getView(getLastSelectedIndex(),null,listView);
                }

                if (selectedView != null) {
                    listView.scrollTo(0, Math.max(0, (int) selectedView.getY()-(selectedView.getHeight()/2)));
                }
            }
        }
    }
}
