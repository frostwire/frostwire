/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
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


package com.frostwire.android.gui.views;

import android.content.Context;
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
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Ref;
import com.google.android.material.tabs.TabLayout;

/**
 * @author gubatron
 * @author aldenml
 */
public class SearchInputView extends LinearLayout {
    private final TextInputClickListener textInputListener;
    private final SuggestionsAdapter adapter;
    private ClearableEditTextView textInput;
    private View dummyFocusView;
    private OnSearchListener onSearchListener;
    private TabLayout tabLayout;
    private final SparseArray<FileTypeTab> toFileTypeTab;
    private int selectedFileType = Constants.FILE_TYPE_TORRENTS;

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
            int mediaTypeId = ConfigurationManager.instance().getLastMediaTypeFilter();
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
            selectedFileType = mediaTypeId;
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
        selectedFileType = mediaTypeId;
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
        int nextTabPosition = (right ? ++currentTabPosition : --currentTabPosition) % 6;
        if (nextTabPosition == -1) {
            nextTabPosition = 5;
        }
        TabLayout.Tab tabAt = tabLayout.getTabAt(nextTabPosition);
        selectedFileType = FileTypeTab.at(nextTabPosition).fileType;
        if (tabAt != null) {
            tabAt.select();
        }
    }

    public int getSelectedFileType() {
        return selectedFileType;
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
            String attemptBelow1k;
            try {
                attemptBelow1k = String.valueOf(numFiles);
            } catch (Throwable t) {
                attemptBelow1k = "0";
            }
            final String numFilesStr = (numFiles > 999) ? "+1k" : attemptBelow1k;

            SystemUtils.postToUIThreadAtFront(() -> {
                if (tabLayout == null) {
                    return;
                }
                int position = toFileTypeTab.get(fileType).position;
                TabLayout.Tab tabAt = tabLayout.getTabAt(position);
                if (tabAt != null) {
                    tabAt.setText(numFilesStr);
                }
            });
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
