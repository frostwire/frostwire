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

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.content.ContentUris;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.adapters.menu.DeleteFileMenuAction;
import com.frostwire.android.gui.adapters.menu.FileInformationAction;
import com.frostwire.android.gui.adapters.menu.RenameFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsWallpaperMenuAction;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.TouchImageView;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.ImageLoader.Callback;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 * @author votaguz
 */
public final class ImageViewerFragment extends AbstractFragment {

    private static final Logger LOG = Logger.getLogger(ImageViewerFragment.class);

    public static final String EXTRA_FILE_DESCRIPTOR = "com.frostwire.android.extra.bundle.FILE_DESCRIPTOR";
    public static final String EXTRA_IN_FULL_SCREEN_MODE = "com.frostwire.android.extra.boolean.IN_FULL_SCREEN_MODE";

    private RelativeLayout containerLayout;
    private TouchImageView imageViewHighRes;

    private ProgressBar progressBar;
    private FileDescriptor fd;
    private ImageViewerActionModeCallback actionModeCallback;
    boolean inFullScreenMode = false;
    private ImageLoader imageLoader;
    private Uri fileUri;

    private boolean highResLoaded = false;

    public ImageViewerFragment() {
        super(R.layout.fragment_image_viewer);
        setHasOptionsMenu(true);
        this.fd = null;
        this.actionModeCallback = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(EXTRA_FILE_DESCRIPTOR, fd.toBundle());
        outState.putBoolean(EXTRA_IN_FULL_SCREEN_MODE, inFullScreenMode);
    }

    @Override
    protected void initComponents(View v, Bundle savedInstanceState) {
        imageLoader = ImageLoader.getInstance(getActivity());
        containerLayout = findView(v, R.id.fragment_image_viewer_container_layout);
        progressBar = findView(v, R.id.fragment_image_viewer_progress_bar);
        progressBar.setVisibility(View.VISIBLE);
        imageViewHighRes = findView(v, R.id.fragment_image_viewer_image_highres);
        View.OnClickListener toggleFullScreenClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!highResLoaded && !inFullScreenMode) {
                    LOG.info("Not going into full screen, images not loaded");
                    return;
                }
                toggleFullScreen();
            }
        };
        imageViewHighRes.setOnClickListener(toggleFullScreenClickListener);
        if (savedInstanceState != null) {
            Bundle data = savedInstanceState.getBundle(EXTRA_FILE_DESCRIPTOR);
            if (data != null) {
                inFullScreenMode = savedInstanceState.getBoolean(EXTRA_IN_FULL_SCREEN_MODE);
                updateData(new FileDescriptor(data));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (inFullScreenMode) {
            inFullScreenMode = !inFullScreenMode;
            toggleFullScreen();
        }
    }

    private void toggleFullScreen() {
        int newFlag = inFullScreenMode ? WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN : WindowManager.LayoutParams.FLAG_FULLSCREEN;
        int clearFlag = inFullScreenMode ? WindowManager.LayoutParams.FLAG_FULLSCREEN : WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
        getActivity().getWindow().clearFlags(clearFlag);
        getActivity().getWindow().addFlags(newFlag);
        int backgroundColor = getActivity().getResources().getColor(!inFullScreenMode ? R.color.black : R.color.app_background_body_light);
        containerLayout.setBackgroundColor(backgroundColor);
        if (!inFullScreenMode) {
            actionModeCallback.setInFullScreenMode(true); // so it doesn't finish the activity when destroyed
            actionModeCallback.getActionMode().finish();
        } else {
            actionModeCallback.setInFullScreenMode(false);
            startActionMode(actionModeCallback);
        }
        Toolbar actionBar = ((AbstractActivity) getActivity()).findToolbar();
        if (actionBar != null) {
            actionBar.setVisibility(!inFullScreenMode ? View.GONE : View.VISIBLE);
        }
        inFullScreenMode = !inFullScreenMode;
    }

    public void updateData(final FileDescriptor fd) {
        this.fd = fd;
        if (actionModeCallback == null) {
            actionModeCallback = new ImageViewerActionModeCallback(this.fd);
            startActionMode(actionModeCallback);
        }
        actionModeCallback.getActionMode().setTitle(FilenameUtils.getName(fd.filePath));
        fileUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, fd.id);
        progressBar.setVisibility(View.VISIBLE);
        highResLoaded = false;

        // under the assumption that the main image view to render the
        // picture will cover the main screen, or even go to full screen,
        // then using the display size as a target is a good idea
        Point size = displaySize();

        // load high res version
        ImageLoader.Params params = new ImageLoader.Params();
        params.targetWidth = size.x;
        params.targetHeight = size.y;
        params.placeholderResId = R.drawable.picture_placeholder;
        params.centerInside = true;
        params.noCache = true;
        params.callback = new LoadImageCallback(this);
        imageLoader.load(fileUri, imageViewHighRes, params);
    }

    private final class ImageViewerActionModeCallback implements android.support.v7.view.ActionMode.Callback {
        private final FileDescriptor fd;
        private ActionMode mode;
        private Menu menu;
        private boolean inFullScreenMode;

        ImageViewerActionModeCallback(FileDescriptor fd) {
            this.fd = fd;
        }

        ActionMode getActionMode() {
            return this.mode;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            this.mode = mode;
            this.menu = menu;
            mode.getMenuInflater().inflate(R.menu.fragment_my_files_action_mode_menu, menu);
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
                case R.id.fragment_my_files_action_mode_menu_delete:
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
                case R.id.fragment_my_files_action_mode_menu_seed:
                    new SeedAction(context, fd, null).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_open:
                    boolean useFileProvider = SystemUtils.hasNougatOrNewer();
                    UIUtils.openFile(context, fd.filePath, fd.mime, useFileProvider);
                    break;
                case R.id.fragment_my_files_action_mode_menu_file_information:
                    new FileInformationAction(context, fd).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_use_as_wallpaper:
                    new SetAsWallpaperMenuAction(context, fd).onClick();
                    mode.finish();
                    break;
                case R.id.fragment_my_files_action_mode_menu_rename:
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
                case R.id.fragment_my_files_action_mode_menu_share:
                    new SendFileMenuAction(context, fd).onClick();
                    break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            this.mode.finish();
            if (!inFullScreenMode) {
                getActivity().finish();
            }
        }

        private void updateMenuActionsVisibility(FileDescriptor fd) {
            List<Integer> actionsToHide = new ArrayList<>();
            actionsToHide.add(R.id.fragment_my_files_action_mode_menu_use_as_ringtone);
            actionsToHide.add(R.id.fragment_my_files_action_mode_menu_copy_magnet);
            actionsToHide.add(R.id.fragment_my_files_action_mode_menu_copy_info_hash);
            actionsToHide.add(R.id.fragment_my_files_action_mode_menu_add_to_playlist);
            if (fd.filePath != null && AndroidPlatform.saf(new File(fd.filePath))) {
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_seed);
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
            updateData(fd);
        }

        public void setInFullScreenMode(boolean inFullScreenMode) {
            this.inFullScreenMode = inFullScreenMode;
        }
    }

    private Point displaySize() {
        Point size = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(size);
        return size;
    }

    private static final class LoadImageCallback implements Callback {

        private final WeakReference<ImageViewerFragment> ref;

        LoadImageCallback(ImageViewerFragment f) {
            this.ref = Ref.weak(f);
        }

        @Override
        public void onSuccess() {
            if (!Ref.alive(ref)) {
                return;
            }
            ImageViewerFragment f = ref.get();

            LOG.info("Loaded imageViewHighRes from " + f.fileUri);
            f.highResLoaded = true;
            f.progressBar.setVisibility(View.GONE);
            f.imageViewHighRes.postInvalidate();
        }

        @Override
        public void onError(Exception e) {
            if (!Ref.alive(ref)) {
                return;
            }
            ImageViewerFragment f = ref.get();

            LOG.info("Could not load imageViewHighRes from " + f.fileUri);
            f.highResLoaded = false;
            f.progressBar.setVisibility(View.GONE);
            f.imageViewHighRes.setImageDrawable(null);
            UIUtils.showShortMessage(f.getActivity(), "Could not load image");
            f.getActivity().finish();
        }
    }
}
