/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.StoragePicker;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;

/**
 * @author gubatron
 * @author aldenml
 */
public class GeneralWizardPage extends RelativeLayout implements WizardPageView {

    private OnCompleteListener listener;
    private TextView textStoragePath;
    private CheckBox checkSeedFinishedTorrents;
    private CheckBox checkSeedFinishedTorrentsWifiOnly;
    private CheckBox checkUXStats;

    public GeneralWizardPage(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean hasPrevious() {
        return false;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public void load() {
        textStoragePath.setText(ConfigurationManager.instance().getStoragePath());
        checkSeedFinishedTorrents.setChecked(ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS));
        checkSeedFinishedTorrentsWifiOnly.setChecked(ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY));
        checkSeedFinishedTorrentsWifiOnly.setEnabled(checkSeedFinishedTorrents.isChecked());
        checkSeedFinishedTorrentsWifiOnly.setTextColor((checkSeedFinishedTorrents.isChecked()) ? Color.WHITE : getContext().getResources().getColor(R.color.app_text_wizard_dark));
        checkUXStats.setChecked(ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_UXSTATS_ENABLED));
        validate();
    }

    @Override
    public void finish() {
        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS, checkSeedFinishedTorrents.isChecked());
        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY, checkSeedFinishedTorrentsWifiOnly.isChecked());
        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_UXSTATS_ENABLED, checkUXStats.isChecked());
    }

    @Override
    public void setOnCompleteListener(OnCompleteListener listener) {
        this.listener = listener;
    }

    public void updateStoragePathTextView(String newLocation) {
        textStoragePath.setText(newLocation);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_general_wizard_page, this);
        final TextView textWifiOnly = (TextView) findViewById(R.id.view_general_wizard_page_wifi_only_text);

        TextView textStoragePathTitle = (TextView) findViewById(R.id.view_general_wizard_page_storage_path_title);
        textStoragePath = (TextView) findViewById(R.id.view_general_wizard_page_storage_path_textview);
        TextView titleHorizontalBreak = (TextView) findViewById(R.id.view_general_wizard_page_title_horizontal_break);

        if (AndroidPlatform.saf()) {
            textStoragePath.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StoragePicker.show((Activity) getContext());
                }
            });
        } else {
            titleHorizontalBreak.setVisibility(View.GONE);
            textStoragePathTitle.setVisibility(View.GONE);
            textStoragePath.setVisibility(View.GONE);
        }

        checkSeedFinishedTorrents = (CheckBox) findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents);
        checkSeedFinishedTorrents.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                checkSeedFinishedTorrentsWifiOnly.setEnabled(isChecked);
                checkSeedFinishedTorrentsWifiOnly.setTextColor((isChecked) ? Color.WHITE : getContext().getResources().getColor(R.color.app_text_wizard_dark));
                textWifiOnly.setTextColor(getContext().getResources().getColor(checkSeedFinishedTorrents.isChecked() ? R.color.app_text_wizard : R.color.app_text_wizard_dark));
                validate();
            }
        });


        checkSeedFinishedTorrentsWifiOnly = (CheckBox) findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents_wifi_only);
        checkSeedFinishedTorrentsWifiOnly.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                validate();
            }
        });

        checkUXStats = (CheckBox) findViewById(R.id.view_general_wizard_page_check_ux_stats);
        checkUXStats.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                validate();
            }
        });

        final TextView welcome_to_frostwire = (TextView) findViewById(R.id.view_general_wizard_page_welcome_to_frostwire);
        final String basicOrPlus = getContext().getString(Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? R.string.basic : R.string.plus);
        welcome_to_frostwire.setText(getContext().getString(R.string.welcome_to_frostwire, basicOrPlus));
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
        onComplete(true);
    }
}
