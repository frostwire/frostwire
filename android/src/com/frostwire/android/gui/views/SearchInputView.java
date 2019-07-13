/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.views;

import android.content.Context;
import com.google.android.material.tabs.TabLayout;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.views.ClearableEditTextView.OnActionListener;
import com.frostwire.util.Ref;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SearchInputView extends LinearLayout {
    private final TextInputClickListener textInputListener;
    private final SuggestionsAdapter adapter;
    private ClearableEditTextView textInput;
    private View dummyFocusView;
    private OnSearchListener onSearchListener;
    private int mediaTypeId;
    private TabLayout tabLayout;
    private final SparseArray<FileTypeTab> toFileTypeTab;

    private enum FileTypeTab {
        TAB_AUDIO(Constants.FILE_TYPE_AUDIO, 0),
        TAB_VIDEOS(Constants.FILE_TYPE_VIDEOS, 1),
        TAB_PICTURES(Constants.FILE_TYPE_PICTURES, 2),
        TAB_APPLICATIONS(Constants.FILE_TYPE_APPLICATIONS, 3),
        TAB_DOCUMENTS(Constants.FILE_TYPE_DOCUMENTS, 4),
        TAB_TORRENTS(Constants.FILE_TYPE_TORRENTS, 5);

        final byte fileType;
        final int position;


        FileTypeTab(byte fileType, int position) {
            this.fileType = fileType;
            this.position = position;
        }

        static FileTypeTab at(int position) {
            return FileTypeTab.values()[position];
        }
    }

    public SearchInputView(Context context, AttributeSet set) {
        super(context, set);
        this.textInputListener = new TextInputClickListener(this);
        this.adapter = new SuggestionsAdapter(context);
        toFileTypeTab = new SparseArray<>();
        toFileTypeTab.put(Constants.FILE_TYPE_AUDIO, FileTypeTab.TAB_AUDIO);
        toFileTypeTab.put(Constants.FILE_TYPE_VIDEOS, FileTypeTab.TAB_VIDEOS);
        toFileTypeTab.put(Constants.FILE_TYPE_PICTURES, FileTypeTab.TAB_PICTURES);
        toFileTypeTab.put(Constants.FILE_TYPE_APPLICATIONS, FileTypeTab.TAB_APPLICATIONS);
        toFileTypeTab.put(Constants.FILE_TYPE_DOCUMENTS, FileTypeTab.TAB_DOCUMENTS);
        toFileTypeTab.put(Constants.FILE_TYPE_TORRENTS, FileTypeTab.TAB_TORRENTS);
    }

    public void setShowKeyboardOnPaste(boolean show) {
        textInput.setShowKeyboardOnPaste(show);
    }
    
    public void setOnSearchListener(OnSearchListener listener) {
        this.onSearchListener = listener;
    }

    public boolean isEmpty() {
        return textInput.getText().length() == 0;
    }

    public String getText() {
        return textInput.getText();
    }
    
    public void setHint(String hint) {
        textInput.setHint(hint);
    }
    
    public void setText(String text) {
        textInput.setText(text);
    }

    public void showTextInput() {
        textInput.setVisibility(View.VISIBLE);
    }

    public void hideTextInput() {
        textInput.setVisibility(View.GONE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_searchinput, this);

        if (isInEditMode()) {
            return;
        }

        textInput = findViewById(R.id.view_search_input_text_input);
        textInput.setOnKeyListener(textInputListener);
        textInput.setOnActionListener(textInputListener);
        textInput.setOnItemClickListener(textInputListener);

        if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG) {
            textInput.setAdapter(adapter);
        }

        updateHint();

        tabLayout = findViewById(R.id.view_search_input_tab_layout_file_type);
        TabLayout.OnTabSelectedListener tabSelectedListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                tabItemFileTypeClick(FileTypeTab.at(tab.getPosition()).fileType);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                tabItemFileTypeClick(FileTypeTab.at(tab.getPosition()).fileType);
            }
        };
        tabLayout.addOnTabSelectedListener(tabSelectedListener);
        setFileTypeCountersVisible(false);
        dummyFocusView = findViewById(R.id.view_search_input_linearlayout_dummy);
    }

    private void startSearch(View v) {
        hideSoftInput(v);
        textInput.setListSelection(-1);
        textInput.dismissDropDown();
        adapter.discardLastResult();
        String query = textInput.getText().trim();
        if (query.length() > 0) {
            mediaTypeId = ConfigurationManager.instance().getLastMediaTypeFilter();
            tabItemFileTypeClick(mediaTypeId);
            onSearch(query, mediaTypeId);
        }
        dummyFocusView.requestFocus();
    }

    private void onSearch(String query, int mediaTypeId) {
        selectTabByMediaType((byte) mediaTypeId);
        if (onSearchListener != null) {
            onSearchListener.onSearch(this, query, mediaTypeId);
        }
    }

    private void onMediaTypeSelected(int mediaTypeId) {
        if (onSearchListener != null) {
            onSearchListener.onMediaTypeSelected(this, mediaTypeId);
        }
    }

    private void onClear() {
        if (onSearchListener != null) {
            onSearchListener.onClear(this);
        }
    }

    private void hideSoftInput(View v) {
        InputMethodManager manager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private void updateHint() {
        final String searchFiles = getContext().getString(R.string.search_label) + " " + getContext().getString(R.string.files);
        final String orEnterYTorSCUrl = getContext().getString(R.string.or_enter_url);
        textInput.setHint(searchFiles + " " + orEnterYTorSCUrl);
    }

    public void selectTabByMediaType(final byte mediaTypeId) {
        if (toFileTypeTab != null) {
            FileTypeTab fileTypeTab = toFileTypeTab.get(mediaTypeId);
            if (fileTypeTab != null && tabLayout != null) {
                TabLayout.Tab tab = tabLayout.getTabAt(fileTypeTab.position);
                if (tab != null) {
                    tab.select();
                }
            }
        }
    }

    public void switchToThe(boolean right) {
        int currentTabPosition = tabLayout.getSelectedTabPosition();
        int nextTabPosition = (right ? ++currentTabPosition : --currentTabPosition ) % 6;
        if (nextTabPosition == -1) {
            nextTabPosition = 5;
        }
        tabLayout.getTabAt(nextTabPosition).select();
    }

    private void tabItemFileTypeClick(final int fileType) {
        updateHint();
        onMediaTypeSelected(fileType);
    }

    public interface OnSearchListener {
        void onSearch(View v, String query, int mediaTypeId);
        void onMediaTypeSelected(View v, int mediaTypeId);
        void onClear(View v);
    }

    public void updateFileTypeCounter(byte fileType, int numFiles) {
        try {
            String numFilesStr = String.valueOf(numFiles);
            if (numFiles > 999) {
                numFilesStr = "+1k";
            }
            tabLayout.getTabAt(toFileTypeTab.get(fileType).position).setText(numFilesStr);
        } catch (Throwable e) {
            // NPE
        }
    }

    public void setFileTypeCountersVisible(boolean fileTypeCountersVisible) {
        TabLayout tabLayout = findViewById(R.id.view_search_input_tab_layout_file_type);
        tabLayout.setVisibility(fileTypeCountersVisible ? View.VISIBLE : View.GONE);
    }

    private static final class TextInputClickListener extends ClickAdapter<SearchInputView> implements OnItemClickListener, OnActionListener {

        public TextInputClickListener(SearchInputView owner) {
            super(owner);
        }

        @Override
        public boolean onKey(SearchInputView owner, View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                owner.startSearch(v);
                return true;
            }
            return false;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (Ref.alive(ownerRef)) {
                SearchInputView owner = ownerRef.get();
                owner.startSearch(owner.textInput);
            }
        }

//        @Override
//        public void onTextChanged(View v, String str) {
//        }

        @Override
        public void onClear(View v) {
            if (Ref.alive(ownerRef)) {
                ownerRef.get().onClear();
            }
        }
    }
}
