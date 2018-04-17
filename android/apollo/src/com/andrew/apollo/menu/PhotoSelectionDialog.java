/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.menu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

import com.andrew.apollo.Config;
import com.frostwire.android.R;
import com.andrew.apollo.ui.activities.ProfileActivity;

import java.util.ArrayList;

/**
 * Used when the user touches the image in the header in {@link ProfileActivity}
 * . It provides an easy interface for them to choose a new image, use the old
 * image, or search Google for one.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PhotoSelectionDialog extends DialogFragment {

    private static final int NEW_PHOTO = 0;

    private static final int OLD_PHOTO = 1;

    private final ArrayList<String> mChoices = new ArrayList<>();

    private static ProfileType mProfileType;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public PhotoSelectionDialog() {
    }

    /**
     * @param title The dialog title.
     * @param value The MIME type
     * @return A new instance of the dialog.
     */
    public static PhotoSelectionDialog newInstance(final String title, final ProfileType type) {
        final PhotoSelectionDialog frag = new PhotoSelectionDialog();
        final Bundle args = new Bundle();
        args.putString(Config.NAME, title);
        frag.setArguments(args);
        mProfileType = type;
        return frag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        if (mProfileType != null) {
            switch (mProfileType) {
                case ARTIST:
                    setArtistChoices();
                    break;
                case ALBUM:
                    setAlbumChoices();
                    break;
                case OTHER:
                    setOtherChoices();
                    break;
                default:
                    break;
            }
        }
        // Dialog item Adapter
        final ProfileActivity activity = (ProfileActivity) getActivity();
        final ListAdapter adapter = new ArrayAdapter<>(activity,
                R.layout.dialog_select_item, mChoices);
        return new AlertDialog.Builder(activity)//.setTitle(title)
                .setAdapter(adapter, (dialog, which) -> {
                    switch (which) {
                        case NEW_PHOTO:
                            activity.selectNewPhoto();
                            break;
                        case OLD_PHOTO:
                            activity.selectOldPhoto();
                            break;
                    }
                }).create();
    }

    /**
     * Adds the choices for the artist profile image.
     */
    private void setArtistChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
    }

    /**
     * Adds the choices for the album profile image.
     */
    private void setAlbumChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
        // Option to fetch the old album image
        mChoices.add(OLD_PHOTO, getString(R.string.old_photo));
    }

    /**
     * Adds the choices for the genre and playlist images.
     */
    private void setOtherChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
        // Option to use the default image
        mChoices.add(OLD_PHOTO, getString(R.string.use_default));
    }

    /**
     * Easily detect the MIME type
     */
    public enum ProfileType {
        ARTIST, ALBUM, OTHER
    }
}
