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

package com.frostwire.android.gui.activities;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ViewFlipper;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.WizardPageView;
import com.frostwire.android.gui.views.WizardPageView.OnCompleteListener;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class WizardActivity extends AbstractActivity {
    private final OnCompleteListener completeListener;
    private Button buttonPrevious;
    private Button buttonNext;
    private ViewFlipper viewFlipper;
    private View currentPageView;

    public WizardActivity() {
        super(R.layout.activity_wizard);
        completeListener = (pageView, complete) -> {
            if (pageView == currentPageView) {
                buttonNext.setEnabled(complete);
            }
        };
    }
    
    @Override
    protected void onCreate(Bundle savedInstance) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstance);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        buttonPrevious = findView(R.id.activity_wizard_button_previous);
        buttonPrevious.setOnClickListener(v -> previousPage());

        buttonNext = findView(R.id.activity_wizard_button_next);
        buttonNext.setOnClickListener(v -> nextPage());

        viewFlipper = findView(R.id.activity_wizard_view_flipper);
        int count = viewFlipper.getChildCount();
        for (int i = 0; i < count; i++) {
            View view = viewFlipper.getChildAt(i);
            if (view instanceof WizardPageView) {
                ((WizardPageView) view).setOnCompleteListener(completeListener);
            }
            view.setTag(i);
        }

        setupViewPage();
    }

    private void previousPage() {
        viewFlipper.showPrevious();
        setupViewPage();
    }

    private void nextPage() {
        View view = viewFlipper.getCurrentView();
        if (view instanceof WizardPageView) {
            WizardPageView pageView = (WizardPageView) view;
            pageView.finish();
            if (!pageView.hasNext()) {
                ConfigurationManager CM = ConfigurationManager.instance();
                CM.setBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED, true);
                CM.setBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE, true);
                CM.setLong(Constants.PREF_KEY_GUI_INSTALLATION_TIMESTAMP, System.currentTimeMillis());
            } else {
                viewFlipper.showNext();
                setupViewPage();
            }
        } else {
            viewFlipper.showNext();
            setupViewPage();
        }
    }

    private void setupViewPage() {
        View view = viewFlipper.getCurrentView();
        currentPageView = view;
        if (view instanceof WizardPageView) {
            WizardPageView pageView = (WizardPageView) view;

            buttonPrevious.setVisibility(pageView.hasPrevious() ? View.VISIBLE : View.INVISIBLE);
            buttonNext.setText(pageView.hasNext() ? R.string.wizard_next : R.string.wizard_finish);
            buttonNext.setEnabled(false);

            pageView.load();
        } else {
            buttonPrevious.setVisibility(View.VISIBLE);
            buttonNext.setEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        final View view = viewFlipper.getCurrentView();
        if (view instanceof WizardPageView && !((WizardPageView) view).hasPrevious()) {
            UIUtils.sendShutdownIntent(this);
            finish();
        } else {
            previousPage();
        }
    }
}
