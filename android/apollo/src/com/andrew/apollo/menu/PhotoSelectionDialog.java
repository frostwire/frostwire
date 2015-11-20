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
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

import com.andrew.apollo.Config;
import com.frostwire.android.R;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.Lists;

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

    private static final int GOOGLE_SEARCH = 2;

    private static final int FETCH_IMAGE = 3;

    private final ArrayList<String> mChoices = Lists.newArrayList();

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
        final String title = getTitle();
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
        final ListAdapter adapter = new ArrayAdapter<String>(activity,
                android.R.layout.select_dialog_item, mChoices);
        return new AlertDialog.Builder(activity).setTitle(title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        switch (which) {
                            case NEW_PHOTO:
                                activity.selectNewPhoto();
                                break;
                            case OLD_PHOTO:
                                activity.selectOldPhoto();
                                break;
                            case FETCH_IMAGE:
                                activity.fetchAlbumArt();
                                break;
                            case GOOGLE_SEARCH:
                                activity.googleSearch();
                                break;
                            default:
                                break;
                        }
                    }
                }).create();
    }

    private String getTitle() {
        String title = null;
        final Bundle arguments = getArguments();
        if (arguments != null) {
            title = arguments.getString(Config.NAME);
        }
        if (title == null) {
            title = getActivity().getString(R.string.unknown);
        }
        return title;
    }

    /**
     * Adds the choices for the artist profile image.
     */
    private void setArtistChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
        if (ApolloUtils.isOnline(getActivity())) {
            // Option to fetch the old artist image
            mChoices.add(OLD_PHOTO, getString(R.string.context_menu_fetch_artist_image));
            // Search Google for the artist name
            mChoices.add(GOOGLE_SEARCH, getString(R.string.google_search));
        }
    }

    /**
     * Adds the choices for the album profile image.
     */
    private void setAlbumChoices() {
        // Select a photo from the gallery
        mChoices.add(NEW_PHOTO, getString(R.string.new_photo));
        // Option to fetch the old album image
        mChoices.add(OLD_PHOTO, getString(R.string.old_photo));
        if (ApolloUtils.isOnline(getActivity())) {
            // Search Google for the album name
            mChoices.add(GOOGLE_SEARCH, getString(R.string.google_search));
            // Option to fetch the album image
            mChoices.add(FETCH_IMAGE, getString(R.string.context_menu_fetch_album_art));
        }
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
