/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.gui.views.MediaPlaybackOverlayPainter;
import com.frostwire.android.gui.views.MediaPlaybackStatusOverlayView;
import com.frostwire.android.util.ImageLoader;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class CheckableImageView<T> extends View implements Checkable {

    private final AbstractListAdapter<T>.CheckboxOnCheckedChangeListener onCheckedChangeListener;
    private final Uri[] imageUris;
    private ImageButton backgroundView;
    private TextView fileSizeTextView;
    private FrameLayout checkedOverlayView;
    private boolean checkableMode;
    private int width;
    private int height;

    public CheckableImageView(Context context,
                              ViewGroup containerView,
                              MediaPlaybackStatusOverlayView playbackStatusOverlayView,
                              MediaPlaybackOverlayPainter.MediaPlaybackState mediaPlaybackOverlayState,
                              int width, int height,
                              Uri[] imageUris,
                              boolean checked, boolean showFileSize,
                              AbstractListAdapter<T>.CheckboxOnCheckedChangeListener onCheckedChangeListener) {
        super(context);
        setClickable(true);
        this.onCheckedChangeListener = onCheckedChangeListener;
        initComponents(containerView, playbackStatusOverlayView, mediaPlaybackOverlayState, checked, showFileSize);
        this.onCheckedChangeListener.setEnabled(false);
        setChecked(checked);
        this.onCheckedChangeListener.setEnabled(true);
        initClickListeners();
        this.imageUris = imageUris;
        this.width = width;
        this.height = height;
    }

    public void loadImages() {
        ImageLoader imageLoader = ImageLoader.getInstance(getContext());
        imageLoader.load(imageUris[0], imageUris[1], backgroundView, width, height);
    }

    public void setFileSize(long fileSize) {
        fileSizeTextView.setText(UIUtils.getBytesInHuman(fileSize));
    }

    public void setChecked(boolean checked) {
        backgroundView.setVisibility(View.VISIBLE);
        checkedOverlayView.setVisibility(checked ? View.VISIBLE : View.GONE);
        if (this.onCheckedChangeListener != null && this.onCheckedChangeListener.isEnabled()) {
            this.onCheckedChangeListener.onCheckedChanged(CheckableImageView.this, checked);
        }
    }

    @Override
    public boolean isChecked() {
        throw new UnsupportedOperationException("To be removed");
    }

    @Override
    public void toggle() {
        throw new UnsupportedOperationException("To be removed");
    }

    private void initComponents(ViewGroup containerView,
                                MediaPlaybackStatusOverlayView playbackStatusOverlayView,
                                MediaPlaybackOverlayPainter.MediaPlaybackState overlayState,
                                boolean checked,
                                boolean showFileSize) {
        backgroundView = containerView.findViewById(R.id.view_my_files_thumbnail_grid_item_browse_thumbnail_image_button);
        backgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fileSizeTextView = containerView.findViewById(R.id.view_my_files_thumbnail_grid_item_filesize);
        fileSizeTextView.setVisibility(showFileSize ? View.VISIBLE : View.GONE);
        if (playbackStatusOverlayView != null) {
            playbackStatusOverlayView.setPlaybackState(!checked ? overlayState : MediaPlaybackOverlayPainter.MediaPlaybackState.NONE);
        }
        checkedOverlayView = containerView.findViewById(R.id.view_my_files_thumbnail_grid_overlay_checkmark_framelayout);
    }

    private void initClickListeners() {
        backgroundView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackgroundViewClick();
            }
        });
        checkedOverlayView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onOverlayCheckedViewClick(v);
            }
        });
    }

    private void onBackgroundViewClick() {
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
