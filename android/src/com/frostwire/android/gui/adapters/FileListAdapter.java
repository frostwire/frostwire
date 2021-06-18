/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
package com.frostwire.android.gui.adapters;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.ImageViewerActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.adapters.FileListAdapter.FileDescriptorItem;
import com.frostwire.android.gui.adapters.menu.AddToPlaylistMenuAction;
import com.frostwire.android.gui.adapters.menu.CopyMagnetMenuAction;
import com.frostwire.android.gui.adapters.menu.DeleteAdapterFilesMenuAction;
import com.frostwire.android.gui.adapters.menu.FileInformationAction;
import com.frostwire.android.gui.adapters.menu.OpenMenuAction;
import com.frostwire.android.gui.adapters.menu.RenameFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsRingtoneMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsWallpaperMenuAction;
import com.frostwire.android.gui.fragments.ImageViewerFragment;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.android.gui.views.ListAdapterFilter;
import com.frostwire.android.gui.views.MediaPlaybackOverlayPainter;
import com.frostwire.android.gui.views.MediaPlaybackStatusOverlayView;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.MenuAdapter;
import com.frostwire.android.gui.views.MenuBuilder;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 * @author votaguz
 */
public class FileListAdapter extends AbstractListAdapter<FileDescriptorItem> {

    private static final Logger LOG = Logger.getLogger(FileListAdapter.class);
    private final byte fileType;
    private final ImageLoader thumbnailLoader;
    private final DownloadButtonClickListener downloadButtonClickListener;
    private boolean selectAllMode;

    protected FileListAdapter(Context context, List<FWFileDescriptor> files, byte fileType, boolean selectAllMode) {
        super(context, getLayoutId(fileType), convertFiles(files));
        setShowMenuOnClick(true);
        setShowMenuOnLongClick(false);

        FileListFilter fileListFilter = new FileListFilter();
        setAdapterFilter(fileListFilter);

        this.fileType = fileType;
        this.thumbnailLoader = ImageLoader.getInstance(context);
        this.downloadButtonClickListener = new DownloadButtonClickListener();
        this.selectAllMode = selectAllMode;

        checkSDStatus();
        setCheckboxesVisibility(fileType != Constants.FILE_TYPE_RINGTONES);
    }

    public void setSelectAllMode(boolean selectAllMode) {
        this.selectAllMode = selectAllMode;
    }

    private static int getLayoutId(int fileType) {
        return (fileType == Constants.FILE_TYPE_PICTURES || fileType == Constants.FILE_TYPE_VIDEOS) ?
                R.layout.view_my_files_thumbnail_grid_item :
                R.layout.view_my_files_thumbnail_list_item;
    }

    public View getView(int position, View view, ViewGroup parent) {
        int adapterLayoutId = getViewItemId();
        if (adapterLayoutId == R.layout.view_my_files_thumbnail_list_item) {
            return getListItemView(position, view, parent);
        } else {
            return getGridItemView(position, view);
        }
    }

    private View getListItemView(int position, View view, ViewGroup parent) {
        view = super.getView(position, view, parent);
        final FileDescriptorItem item = getItem(position);
        if (item != null && item.fd != null) {
            if (item.fd.fileType == Constants.FILE_TYPE_AUDIO ||
                item.fd.fileType == Constants.FILE_TYPE_RINGTONES) {
                initPlaybackStatusOverlayTouchFeedback(view, item);
            }
        }
        ImageView thumbnailView = findView(view, R.id.view_my_files_thumbnail_list_item_browse_thumbnail_image_button);
        if (thumbnailView != null) {
            thumbnailView.setTag(item);
            thumbnailView.setOnClickListener(v -> {
                if (!selectAllMode) {
                    if (getShowMenuOnClick()) {
                        if (showMenu(v)) {
                            return;
                        }
                    }
                    LOG.info("AbstractListAdapter.ViewOnClickListener.onClick()");
                    onItemClicked(v);
                } else {
                    onItemClicked(v);
                }
            });
        }
        return view;
    }

    private View getGridItemView(final int position, View view) {
        final FileDescriptorItem item = getItem(position);
        Context ctx = getContext();

        if (view == null && ctx != null) {
            // every list view item is wrapped in a generic container which has a hidden checkbox on the left hand side.
            view = View.inflate(ctx, R.layout.view_my_files_thumbnail_grid_item, null);
        }

        try {
            initCheckableGridImageView((RelativeLayout) view, item);
            // toggles checkbox mode.
            initTouchFeedback(view, item, v -> {
                // background image listener
                if (selectAllMode) {
                    onItemClicked(v);
                } else {
                    if (item.fd.fileType == Constants.FILE_TYPE_VIDEOS) {
                        LOG.info("getGridItemView() Background ImageView.onClick(), show the menu");
                        MenuAdapter menuAdapter = getMenuAdapter(v);
                        if (menuAdapter != null) {
                            new MenuBuilder(menuAdapter).show();
                        }
                    } else {
                        localPlay(item.fd, v, position);
                    }
                }
            }, this::onItemLongClicked,
                    null);
            initPlaybackStatusOverlayTouchFeedback(view, item);
        } catch (Throwable e) {
            LOG.error("Fatal error getting view: " + e.getMessage(), e);
        }

        return view;
    }

    private void initPlaybackStatusOverlayTouchFeedback(View view, final FileDescriptorItem item) {
        MediaPlaybackStatusOverlayView playbackStatusOverlayView = findView(view,
                inGridMode() ? R.id.view_my_files_thumbnail_grid_item_playback_overlay_view :
                        R.id.view_my_files_thumbnail_list_item_playback_overlay_view);
        if (selectAllMode) {
            playbackStatusOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.NONE);
        }
        playbackStatusOverlayView.setTag(item);
        playbackStatusOverlayView.setOnClickListener(v -> {
            if (selectAllMode) {
                onItemClicked(v);
            } else {
                localPlay(item.fd, v, getViewPosition(v));
            }
        });
    }

    private void initCheckableGridImageView(ViewGroup view, final FileDescriptorItem item) {
        boolean isChecked = getChecked().contains(item);
        boolean showFileSize = false;

        Uri[] uris = getFileItemThumbnailUris(item);

        MediaPlaybackStatusOverlayView playbackStatusOverlayView = findView(view, R.id.view_my_files_thumbnail_grid_item_playback_overlay_view);
        MediaPlaybackOverlayPainter.MediaPlaybackState overlayPlaybackState = MediaPlaybackOverlayPainter.MediaPlaybackState.NONE;

        int thumbnailResizeWidth = (item.fd.fileType == Constants.FILE_TYPE_VIDEOS) ? 512 : 196;
        if (item.fd.fileType == Constants.FILE_TYPE_VIDEOS && !selectAllMode) {
            overlayPlaybackState = MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY;
            showFileSize = true;
        }
        // TODO: why CheckableImageView needs to exist is hard to explain, I (aldenml) think that
        // it's better to move the logic to use in place and improve code locality
        final CheckableImageView checkableView = new CheckableImageView(
                view.getContext(),
                view,
                playbackStatusOverlayView,
                overlayPlaybackState,
                thumbnailResizeWidth, // re-sizes while keeping aspect ratio based only on given height
                0,
                uris,
                isChecked,
                showFileSize);

        checkableView.setCheckableMode(selectAllMode);
        checkableView.loadImages();
        if (showFileSize) {
            checkableView.setFileSize(item.fd.fileSize);
        }
    }

    private Uri[] getFileItemThumbnailUris(FileDescriptorItem item) {
        Uri[] uris = new Uri[2];
        if (item.fd.fileType == Constants.FILE_TYPE_VIDEOS) {
            uris[0] = ContentUris.withAppendedId(Video.Media.EXTERNAL_CONTENT_URI, item.fd.id);
            uris[1] = ImageLoader.getMetadataArtUri(uris[0]);
        } else if (item.fd.fileType == Constants.FILE_TYPE_PICTURES) {
            Uri uri = ContentUris.withAppendedId(Images.Media.EXTERNAL_CONTENT_URI, item.fd.id);
            uris[0] = uri;
            uris[1] = null;
        }
        return uris;
    }

    public byte getFileType() {
        return fileType;
    }

    @Override
    protected final void populateView(View view, FileDescriptorItem item) {
        if (hasThumbnailView()) {
            populateViewThumbnail(view, item);
        } else {
            populateViewPlain(view, item);
        }
    }

    private boolean hasThumbnailView() {
        return !in(fileType, Constants.FILE_TYPE_DOCUMENTS, Constants.FILE_TYPE_TORRENTS);
    }

    @Override
    protected MenuAdapter getMenuAdapter(View view) {
        Context context = getContext();

        List<MenuAction> items = new ArrayList<>();

        // due to long click generic handle
        FWFileDescriptor fd = null;

        if (view.getTag() instanceof FileDescriptorItem) {
            FileDescriptorItem item = (FileDescriptorItem) view.getTag();
            fd = item.fd;
        } else if (view.getTag() instanceof FWFileDescriptor) {
            fd = (FWFileDescriptor) view.getTag();
        }

        if (checkIfNotExists(fd)) {
            notifyDataSetInvalidated();
            return null;
        }

        List<FWFileDescriptor> checked = convertItems(getChecked());
        boolean canOpenFile = fd.mime != null && (fd.mime.contains("audio") || fd.mime.contains("bittorrent") || fd.filePath != null);
        int numChecked = checked.size();

        boolean showSingleOptions = showSingleOptions(checked, fd);

        if (showSingleOptions) {
            if (!AndroidPlatform.saf(new File(fd.filePath)) &&
                    fd.fileType != Constants.FILE_TYPE_RINGTONES) {
                items.add(new SeedAction(context, fd));
            }

            if (canOpenFile) {
                items.add(new OpenMenuAction(context, fd, getViewPosition(view)));
            }

            items.add(new FileInformationAction(context, fd));

            if ((fd.fileType == Constants.FILE_TYPE_AUDIO && numChecked <= 1) || fd.fileType == Constants.FILE_TYPE_RINGTONES) {
                items.add(new SetAsRingtoneMenuAction(context, fd));
            }

            if (fd.fileType == Constants.FILE_TYPE_PICTURES && numChecked <= 1) {
                items.add(new SetAsWallpaperMenuAction(context, fd));
            }

            if (fd.fileType != Constants.FILE_TYPE_APPLICATIONS && numChecked <= 1 &&
                    fd.fileType != Constants.FILE_TYPE_RINGTONES) {
                items.add(new RenameFileMenuAction(context, this, fd));
            }

            if (fd.mime != null && fd.mime.equals(Constants.MIME_TYPE_BITTORRENT) && numChecked <= 1) {
                items.add(new CopyMagnetMenuAction(context,
                        R.drawable.contextmenu_icon_magnet,
                        R.string.transfers_context_menu_copy_magnet,
                        R.string.transfers_context_menu_copy_magnet_copied,
                        fd.filePath));

                items.add(new CopyMagnetMenuAction(context,
                        R.drawable.contextmenu_icon_copy,
                        R.string.transfers_context_menu_copy_infohash,
                        R.string.transfers_context_menu_copy_infohash_copied,
                        fd.filePath, false));
            }
        }

        List<FWFileDescriptor> list = checked;
        if (list.size() == 0) {
            list = Collections.singletonList(fd);
        }

        if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
            items.add(new AddToPlaylistMenuAction(context, list));
        }

        if (fd.fileType != Constants.FILE_TYPE_APPLICATIONS &&
                fd.fileType != Constants.FILE_TYPE_RINGTONES) {
            items.add(new SendFileMenuAction(context, fd));

            if (fd.deletable) {
                items.add(new DeleteAdapterFilesMenuAction(context, this, list, null));
            }
        }

        return new MenuAdapter(context, fd.title, items);
    }

    protected void onLocalPlay() {
    }

    private void localPlay(FWFileDescriptor fd, View view, int position) {
        if (fd == null) {
            return;
        }

        onLocalPlay();
        Context ctx = getContext();
        if (fd.mime != null && fd.mime.contains("audio")) {
            CoreMediaPlayer coreMediaPlayer = Engine.instance().getMediaPlayer();
            if (coreMediaPlayer != null && fd.equals(coreMediaPlayer.getCurrentFD(getContext()))) {
                coreMediaPlayer.stop();
            } else {
                try {
                    UIUtils.playEphemeralPlaylist(ctx, fd);
                } catch (RuntimeException re) {
                    re.printStackTrace();
                    UIUtils.showShortMessage(ctx, R.string.media_player_failed);
                }
            }
            notifyDataSetChanged();
        } else {
            if (fd.filePath != null && fd.mime != null) {
                //special treatment of ringtones
                if (fd.fileType == Constants.FILE_TYPE_RINGTONES) {
                    playRingtone(fd);
                } else if (fd.fileType == Constants.FILE_TYPE_PICTURES && ctx instanceof MainActivity) {
                    Intent intent = new Intent(ctx, ImageViewerActivity.class);
                    intent.putExtra(ImageViewerFragment.EXTRA_FILE_DESCRIPTOR_BUNDLE, fd.toBundle());
                    intent.putExtra(ImageViewerFragment.EXTRA_ADAPTER_FILE_OFFSET, position);
                    ctx.startActivity(intent);
                } else if ("application/x-bittorrent".equals(fd.mime)) {
                    // torrents are often DOCUMENT typed
                    TransferManager.instance().downloadTorrent(UIUtils.getFileUri(ctx, fd.filePath, false).toString());
                    UIUtils.showTransfersOnDownloadStart(ctx);
                } else {
                    UIUtils.openFile(ctx, fd.filePath, fd.mime, true);
                }
            } else {
                // it will automatically remove the 'Open' entry.
                MenuAdapter menuAdapter = getMenuAdapter(view);
                if (menuAdapter != null) {
                    new MenuBuilder(menuAdapter).show();
                    UIUtils.showShortMessage(ctx, R.string.cant_open_file);
                }
            }
        }
    }

    private void playRingtone(FWFileDescriptor FWFileDescriptor) {
        //pause real music if any
        if (MusicUtils.isPlaying()) {
            MusicUtils.playPauseOrResume();
        }
        MusicUtils.playSimple(FWFileDescriptor.filePath);
        notifyDataSetChanged();
    }

    private void populateViewThumbnail(View view, FileDescriptorItem item) {
        FWFileDescriptor fd = item.fd;

        final ImageButton fileThumbnail = findView(view,
                inGridMode() ?
                        R.id.view_my_files_thumbnail_grid_item_browse_thumbnail_image_button :
                        R.id.view_my_files_thumbnail_list_item_browse_thumbnail_image_button);
        fileThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);

        boolean inGridMode = inGridMode();

        MediaPlaybackStatusOverlayView mediaOverlayView = findView(view, inGridMode ?
                R.id.view_my_files_thumbnail_grid_item_playback_overlay_view :
                R.id.view_my_files_thumbnail_list_item_playback_overlay_view);

        final int thumbnailDimensions = inGridMode ?
                256 : 96;

        if (fileType == Constants.FILE_TYPE_APPLICATIONS) {
            Uri uri = ImageLoader.getApplicationArtUri(fd.album);
            thumbnailLoader.load(uri, fileThumbnail, thumbnailDimensions, thumbnailDimensions);
        } else {
            CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
            if (in(fileType, Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_VIDEOS)) {
                if (mediaPlayer != null && fd.equals(mediaPlayer.getCurrentFD(getContext()))) {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.STOP);
                } else {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY);
                }
            } else if (fileType == Constants.FILE_TYPE_RINGTONES) {
                if (mediaPlayer != null && fd.equals(mediaPlayer.getSimplePlayerCurrentFD(getContext()))) {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.STOP);
                } else {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY);
                }
            }

            if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
                Uri uri = ImageLoader.getAlbumArtUri(fd.albumId);
                Uri uriRetry = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, fd.id);
                uriRetry = ImageLoader.getMetadataArtUri(uriRetry);
                thumbnailLoader.load(uri, uriRetry, fileThumbnail, thumbnailDimensions, thumbnailDimensions);
            } else if (fd.fileType == Constants.FILE_TYPE_VIDEOS) {
                Uri uri = ContentUris.withAppendedId(Video.Media.EXTERNAL_CONTENT_URI, fd.id);
                Uri uriRetry = ImageLoader.getMetadataArtUri(uri);
                thumbnailLoader.load(uri, uriRetry, fileThumbnail, thumbnailDimensions, thumbnailDimensions);
            } else if (fd.fileType == Constants.FILE_TYPE_PICTURES) {
                Uri uri = ContentUris.withAppendedId(Images.Media.EXTERNAL_CONTENT_URI, fd.id);
                thumbnailLoader.load(uri, fileThumbnail, thumbnailDimensions, thumbnailDimensions);
            }
        }

        if (!inGridMode) {
            TextView title = findView(view, R.id.view_my_files_thumbnail_list_image_item_file_title);
            title.setText(fd.title);
            if (fd.fileType == Constants.FILE_TYPE_AUDIO || fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
                TextView fileExtra = findView(view, R.id.view_my_files_thumbnail_list_image_item_extra_text);
                fileExtra.setText(fd.artist);
            } else {
                TextView fileExtra = findView(view, R.id.view_my_files_thumbnail_list_image_item_extra_text);
                fileExtra.setText(R.string.empty_string);
            }
            TextView fileSize = findView(view, R.id.view_my_files_thumbnail_list_image_item_file_size);
            fileSize.setText(UIUtils.getBytesInHuman(fd.fileSize));
        }
        fileThumbnail.setTag(fd);
        fileThumbnail.setOnClickListener(downloadButtonClickListener);

        populateSDState(view, item);
    }

    private boolean inGridMode() {
        return getViewItemId() == R.layout.view_my_files_thumbnail_grid_item;
    }

    private void populateViewPlain(View view, FileDescriptorItem item) {
        FWFileDescriptor fd = item.fd;

        TextView title = findView(view, R.id.view_my_files_thumbnail_list_image_item_file_title);
        title.setText(fd.title);

        TextView fileExtra = findView(view, R.id.view_my_files_thumbnail_list_image_item_extra_text);
        if (fd.fileType == Constants.FILE_TYPE_AUDIO || fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
            fileExtra.setText(fd.artist);
        } else if (fd.fileType == Constants.FILE_TYPE_DOCUMENTS) {
            fileExtra.setText(FilenameUtils.getExtension(fd.filePath));
        } else {
            fileExtra.setText(R.string.empty_string);
        }

        TextView fileSize = findView(view, R.id.view_my_files_thumbnail_list_image_item_file_size);
        fileSize.setText(UIUtils.getBytesInHuman(fd.fileSize));

        ImageButton downloadButton = findView(view, inGridMode() ?
                R.id.view_my_files_thumbnail_grid_item_browse_thumbnail_image_button :
                R.id.view_my_files_thumbnail_list_item_browse_thumbnail_image_button);

        MediaPlaybackStatusOverlayView mediaOverlayView = findView(view, inGridMode() ?
                R.id.view_my_files_thumbnail_grid_item_playback_overlay_view :
                R.id.view_my_files_thumbnail_list_item_playback_overlay_view);

        CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
        if (mediaPlayer != null) {
            if (fd.equals(mediaPlayer.getCurrentFD(getContext())) || fd.equals(mediaPlayer.getSimplePlayerCurrentFD(getContext()))) {
                mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.STOP);
            } else {
                mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY);
            }
        }

        downloadButton.setTag(fd);
        downloadButton.setOnClickListener(downloadButtonClickListener);

        populateSDState(view, item);
    }

    private void populateSDState(View v, FileDescriptorItem item) {
        if (inGridMode()) {
            // gotta see what to do here
            return;
        }
        ImageView img = findView(v, R.id.view_my_files_thumbnail_list_image_item_sd);

        if (item.inSD) {
            if (item.mounted) {
                v.setBackgroundResource(R.drawable.listview_item_background_selector);
                setNormalTextColors(v);
                img.setVisibility(View.GONE);
            } else {
                v.setBackgroundResource(R.drawable.my_files_listview_item_inactive_background);
                setInactiveTextColors(v);
                img.setVisibility(View.VISIBLE);
            }
        } else {
            v.setBackgroundResource(R.drawable.listview_item_background_selector);
            setNormalTextColors(v);
            img.setVisibility(View.GONE);
        }
    }

    private void setNormalTextColors(View v) {
        if (inGridMode()) {
            return;
        }
        TextView title = findView(v, R.id.view_my_files_thumbnail_list_image_item_file_title);
        TextView text = findView(v, R.id.view_my_files_thumbnail_list_image_item_extra_text);
        TextView size = findView(v, R.id.view_my_files_thumbnail_list_image_item_file_size);

        Resources res = getContext().getResources();

        // TODO: Fix deprecation warning when we hit API 23
        title.setTextColor(res.getColor(R.color.app_text_primary));
        text.setTextColor(res.getColor(R.color.app_text_primary));
        size.setTextColor(res.getColor(R.color.basic_blue_highlight_dark));
    }

    private void setInactiveTextColors(View v) {
        if (inGridMode()) {
            return;
        }
        TextView title = findView(v, R.id.view_my_files_thumbnail_list_image_item_file_title);
        TextView text = findView(v, R.id.view_my_files_thumbnail_list_image_item_extra_text);
        TextView size = findView(v, R.id.view_my_files_thumbnail_list_image_item_file_size);

        Resources res = getContext().getResources();

        // TODO: Fix deprecation warning when we hit API 23
        title.setTextColor(res.getColor(R.color.my_files_listview_item_inactive_foreground));
        text.setTextColor(res.getColor(R.color.my_files_listview_item_inactive_foreground));
        size.setTextColor(res.getColor(R.color.my_files_listview_item_inactive_foreground));
    }

    private boolean showSingleOptions(List<FWFileDescriptor> checked, FWFileDescriptor fd) {
        //if ringtone - ignore other checked items
        if (fd.fileType == Constants.FILE_TYPE_RINGTONES) {
            return true;
        }
        return checked.size() <= 1 && (checked.size() != 1 || checked.get(0).equals(fd));
    }

    private static ArrayList<FWFileDescriptor> convertItems(Collection<FileDescriptorItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }

        ArrayList<FWFileDescriptor> list = new ArrayList<>(items.size());

        for (FileDescriptorItem item : items) {
            list.add(item.fd);
        }

        return list;
    }

    private static ArrayList<FileDescriptorItem> convertFiles(Collection<FWFileDescriptor> fds) {
        if (fds == null) {
            return new ArrayList<>();
        }

        ArrayList<FileDescriptorItem> list = new ArrayList<>(fds.size());

        for (FWFileDescriptor fd : fds) {
            FileDescriptorItem item = new FileDescriptorItem();
            item.fd = fd;
            list.add(item);
        }

        return list;
    }

    public void deleteItem(FWFileDescriptor fd) {
        FileDescriptorItem item = new FileDescriptorItem();
        item.fd = fd;
        super.deleteItem(item);
    }

    private void checkSDStatus() {
        Map<String, Boolean> sds = new HashMap<>();

        String privateSubPath = "Android" + File.separator + "data";

        File[] externalDirs = SystemUtils.getExternalFilesDirs(getContext());
        for (int i = 1; i < externalDirs.length; i++) {
            File path = externalDirs[i];
            String absolutePath = path.getAbsolutePath();
            boolean isSecondaryExternalStorageMounted = SystemUtils.isSecondaryExternalStorageMounted(path);

            sds.put(absolutePath, isSecondaryExternalStorageMounted);

            if (absolutePath.contains(privateSubPath)) {
                String prefix = absolutePath.substring(0, absolutePath.indexOf(privateSubPath) - 1);
                sds.put(prefix, isSecondaryExternalStorageMounted);
            }
        }

        if (sds.isEmpty()) {
            return; // yes, fast return (for now)
        }

        for (FileDescriptorItem item : getList()) {
            item.inSD = false;
            for (Entry<String, Boolean> e : sds.entrySet()) {
                if (item.fd.filePath.contains(e.getKey())) {
                    item.inSD = true;
                    item.mounted = e.getValue();
                }
            }
            item.exists = true;
        }
    }

    private boolean checkIfNotExists(FWFileDescriptor fd) {
        if (fd == null || fd.filePath == null) {
            return true;
        }
        File f = new File(fd.filePath);
        if (!f.exists()) {
            if (SystemUtils.isSecondaryExternalStorageMounted(f.getAbsoluteFile())) {
                UIUtils.showShortMessage(getContext(), R.string.file_descriptor_sd_mounted);
                Librarian.instance().deleteFiles(getContext(), fileType, Collections.singletonList(fd));
                deleteItem(fd);
            } else {
                UIUtils.showShortMessage(getContext(), R.string.file_descriptor_sd_unmounted);
                deleteItem(fd);
            }
            return true;
        } else {
            return false;
        }
    }

    // Moved here to cleanup base code.
    // Functional abstractions should be used instead
    @SafeVarargs
    private static <T> boolean in(T needle, T... args) {
        if (args == null) {
            throw new IllegalArgumentException("args on in operation can't be null");
        }

        for (T t : args) {
            if (t != null && t.equals(needle)) {
                return true;
            }
        }

        return false;
    }

    public int getNumColumns() {
        if (getViewItemId() == R.layout.view_my_files_thumbnail_list_item) {
            return 1;
        }
        int rotation = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
        boolean vertical = Surface.ROTATION_0 == rotation || Surface.ROTATION_180 == rotation;
        if (vertical) {
            return 3;
        }
        return (int) UIUtils.getScreenInches((Activity) getContext());
    }

    private static class FileListFilter implements ListAdapterFilter<FileDescriptorItem> {
        public boolean accept(FileDescriptorItem obj, CharSequence constraint) {
            String keywords = constraint.toString();

            if (keywords == null || keywords.length() == 0) {
                return true;
            }

            keywords = keywords.toLowerCase(Locale.US);

            FWFileDescriptor fd = obj.fd;

            if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
                return fd.album.trim().toLowerCase(Locale.US).contains(keywords) || fd.artist.trim().toLowerCase(Locale.US).contains(keywords) || fd.title.trim().toLowerCase(Locale.US).contains(keywords) || fd.filePath.trim().toLowerCase(Locale.US).contains(keywords);
            } else {
                return fd.title.trim().toLowerCase(Locale.US).contains(keywords) || fd.filePath.trim().toLowerCase(Locale.US).contains(keywords);
            }
        }
    }

    private final class DownloadButtonClickListener implements OnClickListener {
        public void onClick(View v) {
            FWFileDescriptor fd = (FWFileDescriptor) v.getTag();

            if (fd == null) {
                return;
            }

            if (checkIfNotExists(fd)) {
                return;
            }

            localPlay(fd, v, getViewPosition(v));
        }
    }

    public static class FileDescriptorItem {

        public FWFileDescriptor fd;
        boolean inSD;
        boolean mounted;
        public boolean exists;

        @Override
        public boolean equals(Object o) {
            return o instanceof FileDescriptorItem && fd.equals(((FileDescriptorItem) o).fd);
        }

        @Override
        public int hashCode() {
            return fd.id;
        }
    }

    private static final class CheckableImageView {

        private final Context context;
        private final Uri[] imageUris;
        private ImageButton backgroundView;
        private TextView fileSizeTextView;
        private FrameLayout checkedOverlayView;
        private final int width;
        private final int height;

        public CheckableImageView(Context context,
                                  ViewGroup containerView,
                                  MediaPlaybackStatusOverlayView playbackStatusOverlayView,
                                  MediaPlaybackOverlayPainter.MediaPlaybackState mediaPlaybackOverlayState,
                                  int width, int height,
                                  Uri[] imageUris,
                                  boolean checked, boolean showFileSize) {
            this.context = context;
            initComponents(containerView, playbackStatusOverlayView, mediaPlaybackOverlayState, checked, showFileSize);
            setChecked(checked);
            this.imageUris = imageUris;
            this.width = width;
            this.height = height;
        }

        public void loadImages() {
            ImageLoader imageLoader = ImageLoader.getInstance(context);
            imageLoader.load(imageUris[0], imageUris[1], backgroundView, width, height);
        }

        public void setFileSize(long fileSize) {
            fileSizeTextView.setText(UIUtils.getBytesInHuman(fileSize));
        }

        public void setChecked(boolean checked) {
            backgroundView.setVisibility(View.VISIBLE);
            checkedOverlayView.setVisibility(checked ? View.VISIBLE : View.GONE);
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

        public void setCheckableMode(boolean checkableMode) {
            if (!checkableMode) {
                setChecked(false);
            }
        }
    }
}
