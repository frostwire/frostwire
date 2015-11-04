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

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.RadioButton;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;


/**
 * This class uses radio_button_background_selector.xml,
 *                 radio_button_background_selector_on.xml and
 *                 radio_button_background_selector_off.xml
 * to prepare the backgrounds and bitmaps of the radio buttons for
 * searching remote files and browsing local files.
 */
public final class FileTypeRadioButtonSelectorFactory {
    public enum RadioButtonContainerType {
        SEARCH,
        BROWSE
    }
    private byte fileType;
    private Resources r;
    private BitmapDrawable iconOn;
    private BitmapDrawable iconOff;
    private LayerDrawable selectorOn;
    private LayerDrawable selectorOff;
    private RadioButtonContainerType containerType;

    public FileTypeRadioButtonSelectorFactory(byte fileType, Resources r, RadioButtonContainerType containerType) {
        this.fileType = fileType;
        this.r = r;
        this.containerType = containerType;
        this.init();
    }

    public LayerDrawable getSelectorOn() {
        return selectorOn;
    }

    public LayerDrawable getSelectorOff() {
        return selectorOff;
    }

    public RadioButtonContainerType getContainerType() {
        return containerType;
    }

    public void updateButtonBackground(RadioButton button) {
        LayerDrawable layerDrawable = button.isChecked() ? getSelectorOn() : getSelectorOff();
        BitmapDrawable iconDrawable = button.isChecked() ? iconOn : iconOff;

        if (getContainerType() == RadioButtonContainerType.SEARCH) {
            // things are a bit different for the radio buttons on the search screen.
            button.setBackgroundDrawable(layerDrawable);

            if (button.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                // android:drawableTop
                button.setPadding(0, 0, 0, 25);
                button.setCompoundDrawablesWithIntrinsicBounds(null, iconDrawable, null, null);
                button.setCompoundDrawablePadding(-15);
            } else {
                // android:drawableLeft
                button.setPadding(40, 0, 0, 0);
                button.setCompoundDrawablesWithIntrinsicBounds(iconDrawable, null, null, null);
                button.setCompoundDrawablePadding(-10);
            }
        } else if (getContainerType() == RadioButtonContainerType.BROWSE) {
            // only the background drawable will align to the center.
            // if we use button.setButtonDrawable it will not center the drawable as it will
            button.setPadding(0, 5, 0, -25);
            button.setCompoundDrawablesWithIntrinsicBounds(null, iconDrawable, null, null);
            button.setCompoundDrawablePadding(0);
        }
    }

    private void init() {
        // Get background layer list, the selectorOn will have that light blue line at the bottom.
        selectorOn = (LayerDrawable) r.getDrawable(R.drawable.radio_button_background_selector_on);
        selectorOff = (LayerDrawable) r.getDrawable(R.drawable.radio_button_background_selector_off);

        // Load the images we want for this file type on both states.
        BitmapDrawablesForSelector bitmapsForSelector = new BitmapDrawablesForSelector(fileType);
        iconOn = (BitmapDrawable) r.getDrawable(bitmapsForSelector.selectorOnDrawableId);
        iconOff = (BitmapDrawable) r.getDrawable(bitmapsForSelector.selectorOffDrawableId);

        // Fix scaling.
        iconOn.setGravity(Gravity.CENTER);
        iconOff.setGravity(Gravity.CENTER);
    }

    private static class BitmapDrawablesForSelector {
        int selectorOnDrawableId;
        int selectorOffDrawableId;

        public BitmapDrawablesForSelector(int fileType) {
            // Load the image we want for this file type.
            selectorOnDrawableId = R.drawable.browse_peer_audio_icon_selector_on;
            selectorOffDrawableId = R.drawable.browse_peer_audio_icon_selector_off;

            switch (fileType) {
                case Constants.FILE_TYPE_AUDIO:
                    selectorOnDrawableId = R.drawable.browse_peer_audio_icon_selector_on;
                    selectorOffDrawableId = R.drawable.browse_peer_audio_icon_selector_off;
                    break;
                case Constants.FILE_TYPE_APPLICATIONS:
                    selectorOnDrawableId = R.drawable.browse_peer_application_icon_selector_on;
                    selectorOffDrawableId = R.drawable.browse_peer_application_icon_selector_off;
                    break;
                case Constants.FILE_TYPE_DOCUMENTS:
                    selectorOnDrawableId = R.drawable.browse_peer_document_icon_selector_on;
                    selectorOffDrawableId = R.drawable.browse_peer_document_icon_selector_off;
                    break;
                case Constants.FILE_TYPE_PICTURES:
                    selectorOnDrawableId = R.drawable.browse_peer_picture_icon_selector_on;
                    selectorOffDrawableId = R.drawable.browse_peer_picture_icon_selector_off;
                    break;
                case Constants.FILE_TYPE_RINGTONES:
                    selectorOnDrawableId = R.drawable.browse_peer_ringtone_icon_selector_on;
                    selectorOffDrawableId = R.drawable.browse_peer_ringtone_icon_selector_off;
                    break;
                case Constants.FILE_TYPE_TORRENTS:
                    selectorOnDrawableId = R.drawable.browse_peer_torrent_icon_selector_on;
                    selectorOffDrawableId = R.drawable.browse_peer_torrent_icon_selector_off;
                    break;
                case Constants.FILE_TYPE_VIDEOS:
                    selectorOnDrawableId = R.drawable.browse_peer_video_icon_selector_on;
                    selectorOffDrawableId = R.drawable.browse_peer_video_icon_selector_off;
                    break;
            }
        }
    }
}