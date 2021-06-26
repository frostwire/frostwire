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

package com.andrew.apollo.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.BitmapUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.util.ImageLoader;

/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class CarouselTab extends FrameLayoutWithOverlay {

    private ImageView mPhoto;

    private ImageView mAlbumArt;

    private TextView mLabelView;

    private View mAlphaLayer;

    private View mColorstrip;

    private final ImageFetcher mFetcher;

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public CarouselTab(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mFetcher = ApolloUtils.getImageFetcher((Activity) context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPhoto = findViewById(R.id.profile_tab_photo);
        mAlbumArt = findViewById(R.id.profile_tab_album_art);
        mLabelView = findViewById(R.id.profile_tab_label);
        mAlphaLayer = findViewById(R.id.profile_tab_alpha_overlay);
        mColorstrip = findViewById(R.id.profile_tab_colorstrip);
        // Set the alpha layer
        setAlphaLayer(mAlphaLayer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSelected(final boolean selected) {
        super.setSelected(selected);
        if (selected) {
            mColorstrip.setVisibility(View.VISIBLE);
        } else {
            mColorstrip.setVisibility(View.GONE);
        }
    }

    /**
     * Used to set the artist image in the artist profile.
     *
     * @param context The {@link Context} to use.
     * @param artist The name of the artist in the profile the user is viewing.
     */
    public void setArtistPhoto(final Activity context, final String artist) {
        if (!TextUtils.isEmpty(artist)) {
            mFetcher.loadArtistImage(artist, mPhoto);
        } else {
            setDefault(context);
        }
    }

    /**
     * Filter used to blur image in ImageLoader
     */
    private static class BlurFilter implements ImageLoader.Filter {
        @Override
        public Bitmap filter(Bitmap source) {
            // scale down image to operate in less pixels
            final int origW = source.getWidth();
            final int origH = source.getHeight();
            final int scaledW = (int) (origW * 0.5f);
            final int scaledH = (int) (origH * 0.5f);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(source, scaledW, scaledH, true);
            source.recycle();
            Bitmap blurredBitmap = BitmapUtils.stackBlur(scaledBitmap, 16);
            scaledBitmap.recycle();
            return blurredBitmap;
        }

        @Override
        public String params() {
            return "default_blur";
        }
    }

    /**
     * Used to blur the artist image in the album profile.
     *
     * @param context The {@link Context} to use.
     * @param artist The artist nmae used to fetch the cached artist image.
     * @param album The album name used to fetch the album art in case the
     *            artist image is missing.
     */
    public void blurPhoto(final Activity context, final String artist, final String album) {
        final ImageLoader loader = ImageLoader.getInstance(context.getApplicationContext());
        ImageLoader.Filter filter = new BlurFilter();
        loader.load(ImageLoader.getArtistArtUri(artist),
                    ImageLoader.getAlbumArtUri(MusicUtils.getIdForAlbum(context, album, artist)),
                    filter,
                    mPhoto,
                    true);
    }

    /**
     * Used to set the album art in the album profile.
     *
     * @param context The {@link Context} to use.
     * @param album The name of the album in the profile the user is viewing.
     */
    public void setAlbumPhoto(final Activity context, final String album, final String artist) {
        if (!TextUtils.isEmpty(album)) {
            mAlbumArt.setVisibility(View.VISIBLE);
            mFetcher.loadAlbumImage(artist, album,
                    MusicUtils.getIdForAlbum(context, album, artist), mAlbumArt);
        } else {
            setDefault(context);
        }
    }

    /**
     * Used to set the album art in the artist profile.
     *
     * @param context The {@link Context} to use.
     * @param artist The name of the artist in the profile the user is viewing.
     */
    public void setArtistAlbumPhoto(final Activity context, final String artist) {
        final String lastAlbum = MusicUtils.getLastAlbumForArtist(context, artist);
        if (!TextUtils.isEmpty(lastAlbum)) {
            // Set the last album the artist played
            mFetcher.loadAlbumImage(artist, lastAlbum,
                    MusicUtils.getIdForAlbum(context, lastAlbum, artist), mPhoto);
            // Play the album
            mPhoto.setOnClickListener(v -> {
                final long[] albumList = MusicUtils.getSongListForAlbum(getContext(),
                        MusicUtils.getIdForAlbum(context, lastAlbum, artist));
                MusicUtils.playFDs(albumList, 0, MusicUtils.isShuffleEnabled());
            });
        } else {
            setDefault(context);
        }
    }

    /**
     * Used to set the header image for playlists and genres.
     *
     * @param context The {@link Context} to use.
     * @param profileName The key used to fetch the image.
     */
    public void setPlaylistOrGenrePhoto(final Activity context,
            final String profileName) {
        if (!TextUtils.isEmpty(profileName)) {
            final Bitmap image = mFetcher.getCachedBitmap(profileName);
            if (image != null) {
                mPhoto.setImageBitmap(image);
            } else {
                setDefault(context);
            }
        } else {
            setDefault(context);
        }
    }

    /**
     * @param context The {@link Context} to use.
     */
    public void setDefault(final Context context) {
        mPhoto.setImageDrawable(context.getResources().getDrawable(R.drawable.header_temp));
    }

    /**
     * @param label The string to set as the labe.
     */
    public void setLabel(final String label) {
        mLabelView.setText(label);
    }

    /**
     * Selects the label view.
     */
    public void showSelectedState() {
        mLabelView.setSelected(true);
    }

    /**
     * Deselects the label view.
     */
    public void showDeselectedState() {
        mLabelView.setSelected(false);
    }
}
