/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
public class IntentWizardPage extends RelativeLayout implements WizardPageView {

    private final CheckAcceptListener checkAcceptListener;

    private OnCompleteListener listener;

    private CheckBox checkAccept;

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
        UIUtils.showSocialLinksDialog(getContext(), true, new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (getContext() instanceof Activity) {
                    ((Activity) getContext()).finish();
                }
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

        checkAccept = (CheckBox) findViewById(R.id.view_intent_wizard_page_check_accept);
        checkAccept.setOnCheckedChangeListener(checkAcceptListener);
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
        boolean complete = true;

        if (!checkAccept.isChecked()) {
            complete = false;
        }

        onComplete(complete);
    }

    private static final class CheckAcceptListener extends ClickAdapter<IntentWizardPage> {

        public CheckAcceptListener(IntentWizardPage owner) {
            super(owner);
        }

        @Override
        public void onCheckedChanged(IntentWizardPage owner, CompoundButton buttonView, boolean isChecked) {
            owner.validate();
        }
    }
}
