/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
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

package com.frostwire.android.gui.util;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.FrameLayout;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.gui.views.BrowseThumbnailImageButton;
import com.frostwire.android.gui.views.MediaPlaybackOverlay;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.util.Logger;
import com.squareup.picasso.Callback;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 4/27/17.
 */


public final class CheckableImageView<T> extends View implements Checkable {
    private static final Logger LOG = Logger.getLogger(CheckableImageView.class);
    private final AbstractListAdapter<T>.CheckboxOnCheckedChangeListener onCheckedChangeListener;
    private boolean checked;
    private BrowseThumbnailImageButton backgroundView;
    private FrameLayout checkedOverlayView;
    private boolean checkableMode;

    public CheckableImageView(Context context, ViewGroup containerView, int dimensions, Uri imageUri, Uri imageRetryUri, AbstractListAdapter<T>.CheckboxOnCheckedChangeListener onCheckedChangeListener, boolean checked, MediaPlaybackOverlay.MediaPlaybackState mediaPlaybackOverlay) {
        super(context);
        setClickable(true);
        this.onCheckedChangeListener = onCheckedChangeListener;
        initComponents(context, containerView, dimensions, imageUri, imageRetryUri, checked, mediaPlaybackOverlay);
        this.onCheckedChangeListener.setEnabled(false);
        setChecked(checked);
        this.onCheckedChangeListener.setEnabled(true);
        initClickListeners();
    }

    private void initComponents(Context context, ViewGroup containerView, int dimensions, final Uri imageUri, Uri imageRetryUri, boolean checked, MediaPlaybackOverlay.MediaPlaybackState overlay) {
        if (containerView == null) {
            LOG.error("initComponents() containerView can't be null");
            return;
        }
        backgroundView = (BrowseThumbnailImageButton) containerView.findViewById(R.id.view_browse_peer_thumbnail_grid_item_browse_thumbnail_image_button);
        if (!checked) {
            backgroundView.setOverlayState(overlay);
        } else {
            backgroundView.setOverlayState(MediaPlaybackOverlay.MediaPlaybackState.NONE);
        }
        checkedOverlayView = (FrameLayout) containerView.findViewById(R.id.view_browse_peer_thumbnail_grid_overlay_checkmark_framelayout);
        ImageLoader imageLoader = ImageLoader.getInstance(context);
        imageLoader.load(imageUri, imageRetryUri, backgroundView, dimensions, dimensions);
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
        backgroundView.setVisibility(View.VISIBLE);
        checkedOverlayView.setVisibility(checked ? View.VISIBLE : View.GONE);
        if (this.onCheckedChangeListener != null && this.onCheckedChangeListener.isEnabled()) {
            this.onCheckedChangeListener.onCheckedChanged(CheckableImageView.this, checked);
        }
    }

    @Override
    public boolean isChecked() {
        return this.checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    private void initClickListeners() {
        backgroundView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackgroundViewClick(v);
            }
        });
        checkedOverlayView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onOverlayCheckedViewClick(v);
            }
        });
    }

    private void onBackgroundViewClick(View v) {
        if (checkableMode) {
            setChecked(true);
        }
    }

    private void onOverlayCheckedViewClick(View v) {
        setChecked(false);
    }

    public void setCheckableMode(boolean checkableMode) {
        this.checkableMode = checkableMode;
        if (!checkableMode) {
            setChecked(false);
        }
    }
}
