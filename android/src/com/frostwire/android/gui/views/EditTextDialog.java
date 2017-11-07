/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.views;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;

/**
 * A generic use EditText input dialog.
 *
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 11/6/17.
 * @see UIUtils showEditTextDialog for making simple use of this class
 */

public final class EditTextDialog extends AbstractDialog {

    public interface TextViewInputDialogCallback {
        void onDialogSubmitted(String value, boolean cancelled);
    }

    private int titleStringId;
    private int messageStringId;
    private int positiveButtonStringId;
    private boolean cancelable;
    private boolean multilineInput;
    private String optionalEditTextValue;
    private static TextViewInputDialogCallback callback;
    private EditText inputEditText;

    public EditTextDialog() {
        super(R.layout.dialog_default_input);
    }

    public EditTextDialog init(int titleStringId,
                               int messageStringId,
                               int positiveButtonStringId,
                               boolean cancelable,
                               boolean multilineInput,
                               String optionalEditTextValue,
                               final TextViewInputDialogCallback cb) {
        this.titleStringId = titleStringId;
        this.messageStringId = messageStringId;
        this.positiveButtonStringId = positiveButtonStringId;
        this.cancelable = cancelable;
        this.multilineInput = multilineInput;
        this.optionalEditTextValue = optionalEditTextValue;
        callback = cb;
        return this;
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            titleStringId = savedInstanceState.getInt("titleStringId");
            messageStringId = savedInstanceState.getInt("messageStringId");
            positiveButtonStringId = savedInstanceState.getInt("positiveButtonStringId");
            cancelable = savedInstanceState.getBoolean("cancelable");
            multilineInput = savedInstanceState.getBoolean("multilineInput");
            optionalEditTextValue = savedInstanceState.getString("optionalEditTextValue");
        }

        final TextView title = findView(dlg, R.id.dialog_default_input_title);
        title.setText(titleStringId);
        inputEditText = findView(dlg, R.id.dialog_default_input_text);
        if (savedInstanceState != null && savedInstanceState.getString("inputEditText") != null) {
            inputEditText.setText(savedInstanceState.getString("inputEditText"));
        }
        inputEditText.setHint(getString(messageStringId));
        inputEditText.setMaxLines(!multilineInput ? 1 : 5);
        if (optionalEditTextValue != null && optionalEditTextValue.length() > 0) {
            inputEditText.setText(optionalEditTextValue);
        }
        final Button positiveButton = findView(dlg, R.id.dialog_default_input_button_yes);
        positiveButton.setText(positiveButtonStringId);
        final Button negativeButton = findView(dlg, R.id.dialog_default_input_button_no);
        negativeButton.setText(R.string.cancel);
        negativeButton.setVisibility(cancelable ? View.VISIBLE : View.GONE);
        positiveButton.setOnClickListener(view -> {
            if (inputEditText != null && callback != null) {
                try {
                    callback.onDialogSubmitted(inputEditText.getText().toString(), false);
                    callback = null;
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            dlg.dismiss();
        });
        negativeButton.setOnClickListener(view -> {
            if (callback != null) {
                callback.onDialogSubmitted(null, true);
                callback = null;
            }
            dlg.dismiss();
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt("titleStringId", titleStringId);
        outState.putInt("messageStringId", messageStringId);
        outState.putInt("positiveButtonStringId", positiveButtonStringId);
        outState.putBoolean("cancelable", cancelable);
        outState.putBoolean("multilineInput", multilineInput);
        outState.putString("optionalEditTextValue", optionalEditTextValue);

        if (inputEditText.getText() != null && !inputEditText.getText().toString().isEmpty()) {
            outState.putString("inputEditText", inputEditText.getText().toString());
        }
        super.onSaveInstanceState(outState);
    }
}
