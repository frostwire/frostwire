/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RelativeLayout;

import com.frostwire.android.R;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class BrowsePeerSearchBarView extends RelativeLayout {

    private final OnCheckedChangeListener checkAllListener;
    private final ClearableEditTextView.OnActionListener inputSearchListener;

    private CheckBox checkAll;
    private ClearableEditTextView inputSearch;

    private OnActionListener listener;

    public BrowsePeerSearchBarView(Context context, AttributeSet set) {
        super(context, set);

        checkAllListener = new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onCheckAll(isChecked);
            }
        };

        inputSearchListener = new ClearableEditTextView.OnActionListener() {
            public void onTextChanged(View v, String str) {
                onFilter(str);
            }

            public void onClear(View v) {
                clearSearch();
            }
        };
    }

    public OnActionListener getListener() {
        return listener;
    }

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    public void clearCheckAll() {
        checkAll.setOnCheckedChangeListener(null);
        checkAll.setChecked(false);
        checkAll.setOnCheckedChangeListener(checkAllListener);
    }

    public void clearSearch() {
        inputSearch.setOnActionListener(null);
        inputSearch.setText("");
        inputSearch.setOnActionListener(inputSearchListener);
        listener.onClear();
    }
    
    public String getText() {
        return inputSearch.getText();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_browse_peer_search_bar, this);

        checkAll = (CheckBox) findViewById(R.id.view_files_bar_check_all);
        checkAll.setOnCheckedChangeListener(checkAllListener);
        inputSearch = (ClearableEditTextView) findViewById(R.id.view_files_bar_input_search);
        inputSearch.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                    hideSoftInput(v);
                    return true;
                }
                return false;
            }
        });
        inputSearch.setOnActionListener(inputSearchListener);
    }

    protected void onCheckAll(boolean isChecked) {
        if (listener != null) {
            listener.onCheckAll(this, isChecked);
        }
    }

    protected void onFilter(String str) {
        if (listener != null) {
            listener.onFilter(this, str);
        }
    }

    private void hideSoftInput(View v) {
        InputMethodManager manager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    public void setText(String text) {
        inputSearch.setText(text);
    }

    public interface OnActionListener {

        void onCheckAll(View v, boolean isChecked);

        void onFilter(View v, String str);

        void onClear();
    }
}
