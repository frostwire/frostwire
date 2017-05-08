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

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.adapters.menu.DeleteFileMenuAction;
import com.frostwire.android.gui.adapters.menu.OpenMenuAction;
import com.frostwire.android.gui.adapters.menu.RenameFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsWallpaperMenuAction;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.TouchImageView;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.util.Logger;
import com.squareup.picasso.Callback;
import com.squareup.picasso.RequestCreator;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 * @author votaguz
 */
public class ImageViewerFragment extends AbstractFragment {
    private static final Logger LOG = Logger.getLogger(ImageViewerFragment.class);
    private static int EXPANDED_METADATA_LAYOUT_HEIGHT;
    private static final int CONTRACTED_METADATA_LAYOUT_HEIGHT = 100;
    private ImageView preloadImageView; // tried doing this with a single imageviewer, didn't work.
    private TouchImageView imageView;
    private ProgressBar progressBar;
    private FileDescriptor fd;
    private ImageViewerActionModeCallback actionModeCallback;
    private ImageButton infoButton;
    private RelativeLayout metadataLayout;
    private ViewGroup.LayoutParams metadataLayoutParams;
    private TextView fileNameTextView;
    private TextView fileSizeTextView;
    private TextView fileDateTextView;

    public ImageViewerFragment() {
        super(R.layout.fragment_image_viewer);
        setHasOptionsMenu(true);
        actionModeCallback = null;
    }

    @Override
    protected void initComponents(View v) {
        fileNameTextView = findView(v, R.id.fragment_image_viewer_metadata_filename);
        fileSizeTextView = findView(v, R.id.fragment_image_viewer_metadata_filesize);
        fileDateTextView = findView(v, R.id.fragment_image_viewer_metadata_date_created);
        progressBar = findView(v, R.id.fragment_image_viewer_progress_bar);
        preloadImageView = findView(v, R.id.fragment_image_viewer_preload_image);
        imageView = findView(v, R.id.fragment_image_viewer_image);
        infoButton = findView(v, R.id.fragment_image_viewer_info_button);
        metadataLayout = findView(v, R.id.fragment_image_viewer_metadata_layout);
        progressBar.setVisibility(View.VISIBLE);
        preloadImageView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        metadataLayout.setVisibility(View.VISIBLE);
        metadataLayoutParams = metadataLayout.getLayoutParams();
        EXPANDED_METADATA_LAYOUT_HEIGHT = metadataLayoutParams.height;

        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInfoButtonClick();
            }
        });

        metadataLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onInfoButtonClick();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateData(fd);
    }

    public void updateData(FileDescriptor fd) {
        this.fd = fd;
        if (actionModeCallback == null) {
            actionModeCallback = new ImageViewerActionModeCallback(this.fd);
            startActionMode(actionModeCallback);
        }

        updateFileMetadata(fd);

        Uri fileUri = UIUtils.getFileUri(getActivity(), fd.filePath, false);
        progressBar.setVisibility(View.VISIBLE);
        preloadImageView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        ImageLoader imageLoader = ImageLoader.getInstance(getActivity());
        // get screen dimensions and orientation once
        int[] dimsAndRot = UIUtils.getScreenDimensionsAndRotation(getActivity());
        final int screenWidth = dimsAndRot[0];
        final int screenHeight = dimsAndRot[1];
        int screenRotation = dimsAndRot[2];
        final boolean screenIsVertical = screenRotation == Surface.ROTATION_0 || screenRotation == Surface.ROTATION_180;
        // downsize to bad quality for responsive UI when opening fragment
        int preloadingWidth = screenIsVertical ? 0 : 32;
        int preloadingHeight = screenIsVertical ? 32 : 0;
        imageLoader.load(fileUri, preloadImageView, preloadingWidth, preloadingHeight);

        imageLoader.loadBitmapAsync(fileUri, new ImageLoader.OnBitmapLoadedCallbackRunner() {
            @Override
            public void onBitmapLoaded(Bitmap bitmap, RequestCreator requestCreator) {
                ImageViewerFragment.this.onBitmapLoaded(bitmap, requestCreator, screenWidth, screenHeight, screenIsVertical);
            }

            @Override
            public void onError() {
                getActivity().finish();
            }
        });
    }

    private void updateFileMetadata(FileDescriptor fd) {
        fileNameTextView.setText(FilenameUtils.getName(fd.filePath));
        fileSizeTextView.setText(UIUtils.getBytesInHuman(fd.fileSize));
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fd.dateAdded*1000);
        int numMonth = cal.get(Calendar.MONTH) + 1;
        int numDay = cal.get(Calendar.DAY_OF_MONTH) + 1;
        String month = numMonth >= 10 ? String.valueOf(numMonth) : "0" + numMonth;
        String day = numDay >= 10 ? String.valueOf(numDay) : "0" + numDay;
        fileDateTextView.setText(cal.get(Calendar.YEAR) + "-" + month + "-" + day);
    }

    private void onBitmapLoaded(final Bitmap bitmap,
                                final RequestCreator requestCreator,
                                int screenWidth,
                                int screenHeight, boolean screenIsVertical) {
        // this should happen in background thread
        if (UIUtils.isMain()) {
            LOG.warn("onBitmapLoaded() -> check your logic. You shouldn't be loading this bitmap on the main thread.");
            return;
        }
        int finalHeight = (int) (screenHeight / 3.0);
        int finalWidth = (int) (screenWidth / 3.0);
        // downsize it if you have to
        if (screenIsVertical && bitmap.getHeight() > finalHeight) {
            requestCreator.resize(0, finalHeight);
        } else if (!screenIsVertical && bitmap.getWidth() > screenWidth) {
            requestCreator.resize(finalWidth, 0);
        }
        // final image loading in UI thread
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                requestCreator.into(imageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        progressBar.setVisibility(View.GONE);
                        preloadImageView.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onError() {
                    }
                });

            }
        });
    }

    private void onInfoButtonClick() {
        ViewGroup.LayoutParams currentParams = metadataLayout.getLayoutParams();
        if (currentParams.height == EXPANDED_METADATA_LAYOUT_HEIGHT) {
            currentParams.height = CONTRACTED_METADATA_LAYOUT_HEIGHT;
        } else {
            currentParams.height = EXPANDED_METADATA_LAYOUT_HEIGHT;
        }
        metadataLayout.setLayoutParams(currentParams);
    }


    private class ImageViewerActionModeCallback implements android.support.v7.view.ActionMode.Callback {
        private final FileDescriptor fd;
        private ActionMode mode;
        private Menu menu;

        public ImageViewerActionModeCallback(FileDescriptor fd) {
            this.fd = fd;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            this.mode = mode;
            this.menu = menu;
            mode.getMenuInflater().inflate(R.menu.fragment_browse_peer_action_mode_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            mode.setTitle(FilenameUtils.getName(fd.filePath));
            updateMenuActionsVisibility(fd);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Activity context = getActivity();
            switch (item.getItemId()) {
                case R.id.fragment_browse_peer_action_mode_menu_delete:
                    ArrayList<FileDescriptor> fdList = new ArrayList<>(1);
                    fdList.add(fd);
                    new DeleteFileMenuAction(context, null, fdList, new AbstractDialog.OnDialogClickListener() {
                        @Override
                        public void onDialogClick(String tag, int which) {
                            if (which == 1) {
                                getActivity().finish();
                            }
                        }
                    }).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_seed:
                    new SeedAction(context, fd, null).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_open:
                    new OpenMenuAction(context, fd.filePath, fd.mime, fd.fileType).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_use_as_wallpaper:
                    new SetAsWallpaperMenuAction(context, fd).onClick();
                    mode.finish();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_rename:
                    final ActionMode fMode = mode;
                    new RenameFileMenuAction(context, null, fd, new AbstractDialog.OnDialogClickListener() {
                        @Override
                        public void onDialogClick(String tag, int which) {
                            if (which == 1 && tag != null) {
                                onRenameFileMenuDialogOk(tag, fMode);
                            }
                        }
                    }).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_share:
                    new SendFileMenuAction(context, fd).onClick();
                    break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            this.mode.finish();
            getActivity().finish();
        }

        private void updateMenuActionsVisibility(FileDescriptor fd) {
            List<Integer> actionsToHide = new ArrayList<>();
            actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_use_as_ringtone);
            actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_copy_magnet);
            actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_copy_info_hash);
            actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_add_to_playlist);
            if (fd.filePath != null && AndroidPlatform.saf(new File(fd.filePath))) {
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_seed);
            }
            if (menu != null && menu.size() > 0) {
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem item = menu.getItem(i);
                    item.setVisible(!actionsToHide.contains(item.getItemId()));
                }
            }
        }

        private void onRenameFileMenuDialogOk(String tag, ActionMode mode) {
            String newFileName = tag.trim();
            String oldFileName = FilenameUtils.getName(fd.filePath);
            String fileExtension = FilenameUtils.getExtension(fd.filePath);
            fd.filePath = fd.filePath.replace(oldFileName, newFileName) + "." + fileExtension;
            mode.setTitle(FilenameUtils.getName(fd.filePath));
        }

    }
}
