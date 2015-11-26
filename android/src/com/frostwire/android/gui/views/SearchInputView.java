/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.views.ClearableEditTextView.OnActionListener;
import com.frostwire.util.Ref;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

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
    
    private final SparseIntArray mediaTypeToRadioButtonMap;

    public SearchInputView(Context context, AttributeSet set) {
        super(context, set);

        this.textInputListener = new TextInputClickListener(this);
        this.adapter = new SuggestionsAdapter(context);
        
        mediaTypeToRadioButtonMap = new SparseIntArray(6);
    }

    public void setShowKeyboardOnPaste(boolean show) {
        textInput.setShowKeyboardOnPaste(show);
    }
    
    public boolean isShowKeyboardOnPaste() {
        return textInput.isShowKeyboardOnPaste();
    }
    
    public OnSearchListener getOnSearchListener() {
        return onSearchListener;
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

    public void updateHint(String newHint) {
        textInput.setHint(newHint);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_searchinput, this);

        if (isInEditMode()) {
            return;
        }

        mediaTypeId = ConfigurationManager.instance().getLastMediaTypeFilter();

        textInput = (ClearableEditTextView) findViewById(R.id.view_search_input_text_input);

        textInput.setOnKeyListener(textInputListener);
        textInput.setOnActionListener(textInputListener);
        textInput.setOnItemClickListener(textInputListener);

        if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            textInput.setAdapter(adapter);
        }

        updateHint(mediaTypeId);

        initRadioButton(R.id.view_search_input_radio_audio, Constants.FILE_TYPE_AUDIO);
        initRadioButton(R.id.view_search_input_radio_videos, Constants.FILE_TYPE_VIDEOS);
        initRadioButton(R.id.view_search_input_radio_pictures, Constants.FILE_TYPE_PICTURES);
        initRadioButton(R.id.view_search_input_radio_applications, Constants.FILE_TYPE_APPLICATIONS);
        initRadioButton(R.id.view_search_input_radio_documents, Constants.FILE_TYPE_DOCUMENTS);
        initRadioButton(R.id.view_search_input_radio_torrents, Constants.FILE_TYPE_TORRENTS);

        setFileTypeCountersVisible(false);

        dummyFocusView = findViewById(R.id.view_search_input_linearlayout_dummy);
    }

    private void startSearch(View v) {
        hideSoftInput(v);
        textInput.setListSelection(-1);
        textInput.dismissDropDown();
        adapter.discardLastResult();

        String query = textInput.getText().toString().trim();
        if (query.length() > 0) {
            onSearch(query, mediaTypeId);
        }

        dummyFocusView.requestFocus();
    }

    private void onSearch(String query, int mediaTypeId) {
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

    private void updateHint(int fileType) {
        final String searchFiles = getContext().getString(R.string.search_label) + " " + getContext().getString(R.string.files);
        final String orEnterYTorSCUrl = getContext().getString(R.string.or_enter_url);
        textInput.setHint(searchFiles + " " + orEnterYTorSCUrl);
    }

    private RadioButton initRadioButton(int viewId, final byte fileType) {
        mediaTypeToRadioButtonMap.put(fileType, viewId);
        final RadioButton button = (RadioButton) findViewById(viewId);
        final Resources r = getResources();
        final FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory =
                new FileTypeRadioButtonSelectorFactory(fileType,
                        r,
                        FileTypeRadioButtonSelectorFactory.RadioButtonContainerType.SEARCH);
        fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        button.setOnClickListener(new RadioButtonListener(this, fileType, button, fileTypeRadioButtonSelectorFactory));
        button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
            }
        });

        if (mediaTypeId == fileType) {
            button.setChecked(true);
        }
        return button;
    }
    
    public void performClickOnRadioButton(final int mediaTypeId) {
        int viewId = mediaTypeToRadioButtonMap.get(mediaTypeId);
        final RadioButton button = (RadioButton) findViewById(viewId);
        button.performClick();
    }

    private void radioButtonFileTypeClick(final int mediaTypeId) {
        updateHint(mediaTypeId);
        onMediaTypeSelected(mediaTypeId);
        SearchInputView.this.mediaTypeId = mediaTypeId;
        ConfigurationManager.instance().setLastMediaTypeFilter(mediaTypeId);
    }

    public interface OnSearchListener {

        void onSearch(View v, String query, int mediaTypeId);

        void onMediaTypeSelected(View v, int mediaTypeId);

        void onClear(View v);
    }

    public void updateFileTypeCounter(byte fileType, int numFiles) {
        try {
            int radioId = Constants.FILE_TYPE_AUDIO;
            switch (fileType) {
            case Constants.FILE_TYPE_AUDIO:
                radioId = R.id.view_search_input_radio_audio;
                break;
            case Constants.FILE_TYPE_VIDEOS:
                radioId = R.id.view_search_input_radio_videos;
                break;
            case Constants.FILE_TYPE_PICTURES:
                radioId = R.id.view_search_input_radio_pictures;
                break;
            case Constants.FILE_TYPE_APPLICATIONS:
                radioId = R.id.view_search_input_radio_applications;
                break;
            case Constants.FILE_TYPE_DOCUMENTS:
                radioId = R.id.view_search_input_radio_documents;
                break;
            case Constants.FILE_TYPE_TORRENTS:
                radioId = R.id.view_search_input_radio_torrents;
                break;

            }

            RadioButton rButton = (RadioButton) findViewById(radioId);
            String numFilesStr = String.valueOf(numFiles);
            if (numFiles > 9999) {
                numFilesStr = "+1k";
            }
            rButton.setText(numFilesStr);
        } catch (Throwable e) {
            // NPE
        }
    }

    public void setFileTypeCountersVisible(boolean fileTypeCountersVisible) {
        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.view_search_input_radiogroup_file_type);
        radioGroup.setVisibility(fileTypeCountersVisible ? View.VISIBLE : View.GONE);
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

        @Override
        public void onTextChanged(View v, String str) {
        }

        @Override
        public void onClear(View v) {
            if (Ref.alive(ownerRef)) {
                ownerRef.get().onClear();
            }
        }
    }

    private static final class RadioButtonListener extends ClickAdapter<SearchInputView> {

        private final byte fileType;
        private final RadioButton button;
        private final FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory;

        public RadioButtonListener(SearchInputView owner,
                                   byte fileType,
                                   RadioButton button,
                                   FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory) {
            super(owner);
            this.fileType = fileType;
            this.button = button;
            this.fileTypeRadioButtonSelectorFactory = fileTypeRadioButtonSelectorFactory;
        }

        @Override
        public void onClick(SearchInputView owner, View v) {
            fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
            owner.radioButtonFileTypeClick(fileType);
            UXStats.instance().log(UXAction.SEARCH_RESULT_FILE_TYPE_CLICK);
        }
    }
}
