/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
public class IntentWizardPage extends RelativeLayout implements WizardPageView {
    private final CheckAcceptListener checkAcceptListener;
    private CheckBox checkCopyrightAccept;
    private CheckBox checkTOUAccept;
    private OnCompleteListener listener;

    public IntentWizardPage(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.checkAcceptListener = new CheckAcceptListener(this);
    }

    @Override
    public boolean hasPrevious() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public void load() {
        validate();
    }

    @Override
    public void finish() {
        UIUtils.showSocialLinksDialog(getContext(), true, dialog -> {
            if (getContext() instanceof Activity) {
                ((Activity) getContext()).finish();
            }
        }, "wizard");
    }

    @Override
    public void setOnCompleteListener(OnCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_intent_wizard_page, this);
        checkCopyrightAccept = findViewById(R.id.view_intent_wizard_page_check_accept_copyright);
        checkCopyrightAccept.setOnCheckedChangeListener(checkAcceptListener);
        checkTOUAccept = findViewById(R.id.view_intent_wizard_page_check_accept_tou);
        checkTOUAccept.setOnCheckedChangeListener(checkAcceptListener);
        Resources r = getResources();
        TextView tosTextView = findViewById(R.id.view_intent_wizard_page_text_tos);
        tosTextView.setMovementMethod(LinkMovementMethod.getInstance());
        final String tou = r.getString(R.string.terms_of_use);
        tosTextView.setText(Html.fromHtml("<a href='" + Constants.TERMS_OF_USE_URL + "'>" + tou + "</a>"));
    }

    protected void onComplete(boolean complete) {
        if (listener != null) {
            listener.onComplete(this, complete);
        }
    }

    /**
     * Put more complete/validation logic here.
     */
    private void validate() {
        onComplete(checkCopyrightAccept.isChecked() && checkTOUAccept.isChecked());
    }

    private static final class CheckAcceptListener extends ClickAdapter<IntentWizardPage> {
        CheckAcceptListener(IntentWizardPage owner) {
            super(owner);
        }

        @Override
        public void onCheckedChanged(IntentWizardPage owner, CompoundButton buttonView, boolean isChecked) {
            owner.validate();
        }
    }
}
