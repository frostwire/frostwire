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
        ConfigurationManager CM = ConfigurationManager.instance();
        textStoragePath.setText(CM.getStoragePath());
        checkSeedFinishedTorrents.setChecked(CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS));
        checkSeedFinishedTorrentsWifiOnly.setChecked(CM.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY));
        checkSeedFinishedTorrentsWifiOnly.setEnabled(checkSeedFinishedTorrents.isChecked());
        checkSeedFinishedTorrentsWifiOnly.setTextColor((checkSeedFinishedTorrents.isChecked()) ? Color.WHITE : getContext().getResources().getColor(R.color.app_text_wizard_dark));
        validate();
    }

    @Override
    public void finish() {
        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS, checkSeedFinishedTorrents.isChecked());
        CM.setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY, checkSeedFinishedTorrentsWifiOnly.isChecked());
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
        final TextView textWifiOnly = findViewById(R.id.view_general_wizard_page_wifi_only_text);

        TextView textStoragePathTitle = findViewById(R.id.view_general_wizard_page_storage_path_title);
        textStoragePath = findViewById(R.id.view_general_wizard_page_storage_path_textview);
        TextView titleHorizontalBreak = findViewById(R.id.view_general_wizard_page_title_horizontal_break);

        if (AndroidPlatform.saf()) {
            textStoragePath.setOnClickListener(v -> StoragePicker.show((Activity) getContext()));
        } else {
            titleHorizontalBreak.setVisibility(View.GONE);
            textStoragePathTitle.setVisibility(View.GONE);
            textStoragePath.setVisibility(View.GONE);
        }

        checkSeedFinishedTorrents = findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents);
        checkSeedFinishedTorrents.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkSeedFinishedTorrentsWifiOnly.setEnabled(isChecked);
            checkSeedFinishedTorrentsWifiOnly.setTextColor((isChecked) ? Color.WHITE : getContext().getResources().getColor(R.color.app_text_wizard_dark));
            textWifiOnly.setTextColor(getContext().getResources().getColor(checkSeedFinishedTorrents.isChecked() ? R.color.app_text_wizard : R.color.app_text_wizard_dark));
            validate();
        });

        checkSeedFinishedTorrentsWifiOnly = findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents_wifi_only);
        checkSeedFinishedTorrentsWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> validate());

        final TextView welcome_to_frostwire = findViewById(R.id.view_general_wizard_page_welcome_to_frostwire);
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
