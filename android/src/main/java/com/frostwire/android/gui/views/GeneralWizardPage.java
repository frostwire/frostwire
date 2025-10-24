/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
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
        checkSeedFinishedTorrentsWifiOnly.setTextColor((checkSeedFinishedTorrents.isChecked()) ? Color.WHITE : getContext().getResources().getColor(R.color.app_text_wizard_dark, null));
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

        if (!AndroidPlatform.saf()) {
            titleHorizontalBreak.setVisibility(View.GONE);
            textStoragePathTitle.setVisibility(View.GONE);
            textStoragePath.setVisibility(View.GONE);
        }

        checkSeedFinishedTorrents = findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents);
        checkSeedFinishedTorrents.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkSeedFinishedTorrentsWifiOnly.setEnabled(isChecked);
            checkSeedFinishedTorrentsWifiOnly.setTextColor((isChecked) ? Color.WHITE : getContext().getResources().getColor(R.color.app_text_wizard_dark, null));
            textWifiOnly.setTextColor(getContext().getResources().getColor(checkSeedFinishedTorrents.isChecked() ? R.color.app_text_wizard : R.color.app_text_wizard_dark, null));
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
