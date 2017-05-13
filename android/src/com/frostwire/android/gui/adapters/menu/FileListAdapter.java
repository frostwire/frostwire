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
package com.frostwire.android.gui.adapters.menu;

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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.activities.ImageViewerActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.adapters.menu.FileListAdapter.FileDescriptorItem;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.CheckableImageView;
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
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.Logger;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

    protected FileListAdapter(Context context, List<FileDescriptor> files, byte fileType, boolean selectAllMode) {
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
                R.layout.view_browse_peer_thumbnail_grid_item :
                R.layout.view_browse_peer_thumbnail_list_item;
    }

    public View getView(int position, View view, ViewGroup parent) {
        int adapterLayoutId = getViewItemId();
        if (adapterLayoutId == R.layout.view_browse_peer_thumbnail_list_item) {
            return getListItemView(position, view, parent);
        } else {
            return getGridItemView(position, view);
        }
    }

    private View getListItemView(int position, View view, ViewGroup parent) {
        view = super.getView(position, view, parent);
        final FileDescriptorItem item = getItem(position);
        if (item.fd.fileType == Constants.FILE_TYPE_AUDIO) {
            initPlaybackStatusOverlayTouchFeedback(view, item);
        }
        ImageView thumbnailView = findView(view, R.id.view_browse_peer_thumbnail_list_item_browse_thumbnail_image_button);
        if (thumbnailView != null) {
            thumbnailView.setTag(item);
            thumbnailView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
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
                }
            });
        }
        return view;
    }

    private View getGridItemView(int position, View view) {
        final FileDescriptorItem item = getItem(position);
        Context ctx = getContext();

        if (view == null && ctx != null) {
            // every list view item is wrapped in a generic container which has a hidden checkbox on the left hand side.
            view = View.inflate(ctx, R.layout.view_browse_peer_thumbnail_grid_item, null);
        }

        try {
            initCheckableGridImageView((RelativeLayout) view, item);
            initTouchFeedback(view, item, new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // background image listener
                            if (selectAllMode) {
                                onItemClicked(v);
                            } else {
                                if (item.fd.fileType == Constants.FILE_TYPE_VIDEOS) {
                                    LOG.info("getGridItemView() Background ImageView.onClick(), show the menu");
                                    new MenuBuilder(getMenuAdapter(v)).show();
                                } else {
                                    localPlay(item.fd, v);
                                }
                            }
                        }
                    }, new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            // toggles checkbox mode.
                            return onItemLongClicked(v);
                        }
                    },
                    null);
            initPlaybackStatusOverlayTouchFeedback(view, item);
        } catch (Throwable e) {
            LOG.error("Fatal error getting view: " + e.getMessage(), e);
        }

        return view;
    }

    private void initPlaybackStatusOverlayTouchFeedback(View view, final FileDescriptorItem item) {
        MediaPlaybackStatusOverlayView playbackStatusOverlayView = findView(view,
                inGridMode() ? R.id.view_browse_peer_thumbnail_grid_item_playback_overlay_view :
                        R.id.view_browse_peer_thumbnail_list_item_playback_overlay_view);
        if (selectAllMode) {
            playbackStatusOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.NONE);
        }
        playbackStatusOverlayView.setTag(item);
        playbackStatusOverlayView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectAllMode) {
                    onItemClicked(v);
                } else {
                    localPlay(item.fd, v);
                }
            }
        });
    }

    protected void initCheckableGridImageView(ViewGroup view, final FileDescriptorItem item) throws Throwable {
        final CheckboxOnCheckedChangeListener checkboxOnCheckedChangeListener = new CheckboxOnCheckedChangeListener();
        boolean isChecked = getChecked().contains(item);
        boolean showFileSize = false;

        Uri[] uris = getFileItemThumbnailUris(item);

        MediaPlaybackStatusOverlayView playbackStatusOverlayView = findView(view, R.id.view_browse_peer_thumbnail_grid_item_playback_overlay_view);
        MediaPlaybackOverlayPainter.MediaPlaybackState overlayPlaybackState = MediaPlaybackOverlayPainter.MediaPlaybackState.NONE;

        int thumbnailResizeHeight = (item.fd.fileType == Constants.FILE_TYPE_VIDEOS) ? 96 : 196;
        if (item.fd.fileType == Constants.FILE_TYPE_VIDEOS && !selectAllMode) {
            overlayPlaybackState = MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY;
            showFileSize = true;
        }
        final CheckableImageView checkableView = new CheckableImageView(
                view.getContext(),
                view,
                playbackStatusOverlayView,
                overlayPlaybackState,
                0, // re-sizes while keeping aspect ratio based only on given height
                thumbnailResizeHeight,
                uris,
                isChecked,
                showFileSize,
                checkboxOnCheckedChangeListener);

        checkboxOnCheckedChangeListener.setEnabled(false);
            checkableView.setCheckableMode(selectAllMode);
            checkableView.setTag(item);
            checkableView.loadImages();
            if (showFileSize) {
                checkableView.setFileSize(item.fd.fileSize);
            }
            checkableView.setVisibility(View.VISIBLE);
        checkboxOnCheckedChangeListener.setEnabled(true);
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
        FileDescriptor fd = null;

        if (view.getTag() instanceof FileDescriptorItem) {
            FileDescriptorItem item = (FileDescriptorItem) view.getTag();
            fd = item.fd;
        } else if (view.getTag() instanceof FileDescriptor) {
            fd = (FileDescriptor) view.getTag();
        }

        if (checkIfNotExists(fd)) {
            return null;
        }

        List<FileDescriptor> checked = convertItems(getChecked());
        boolean canOpenFile = fd.mime != null && (fd.mime.contains("audio") || fd.mime.contains("bittorrent") || fd.filePath != null);
        int numChecked = checked.size();

        boolean showSingleOptions = showSingleOptions(checked, fd);

        if (showSingleOptions) {
            if (!AndroidPlatform.saf(new File(fd.filePath)) &&
                    fd.fileType != Constants.FILE_TYPE_RINGTONES) {
                items.add(new SeedAction(context, fd));
            }

            if (canOpenFile) {
                items.add(new OpenMenuAction(context, fd.filePath, fd.mime, fd.fileType));
            }

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
                items.add(new CopyToClipboardMenuAction(context,
                        R.drawable.contextmenu_icon_magnet,
                        R.string.transfers_context_menu_copy_magnet,
                        R.string.transfers_context_menu_copy_magnet_copied,
                        readInfoFromTorrent(fd.filePath, true)
                ));

                items.add(new CopyToClipboardMenuAction(context,
                        R.drawable.contextmenu_icon_copy,
                        R.string.transfers_context_menu_copy_infohash,
                        R.string.transfers_context_menu_copy_infohash_copied,
                        readInfoFromTorrent(fd.filePath, false)
                ));
            }
        }

        List<FileDescriptor> list = checked;
        if (list.size() == 0) {
            list = Collections.singletonList(fd);
        }

        if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
            items.add(new AddToPlaylistMenuAction(context, list));
        }

        if (fd.fileType != Constants.FILE_TYPE_APPLICATIONS &&
                fd.fileType != Constants.FILE_TYPE_RINGTONES) {
            items.add(new SendFileMenuAction(context, fd));
            items.add(new DeleteFileMenuAction(context, this, list));
        }

        return new MenuAdapter(context, fd.title, items);
    }

    protected void onLocalPlay() {
    }

    private void localPlay(FileDescriptor fd, View view) {
        if (fd == null) {
            return;
        }

        onLocalPlay();
        Context ctx = getContext();
        if (fd.mime != null && fd.mime.contains("audio")) {
            if (fd.equals(Engine.instance().getMediaPlayer().getCurrentFD())) {
                Engine.instance().getMediaPlayer().stop();
            } else {
                try {
                    UIUtils.playEphemeralPlaylist(fd);
                    UXStats.instance().log(UXAction.LIBRARY_PLAY_AUDIO_FROM_FILE);
                } catch (RuntimeException re) {
                    UIUtils.showShortMessage(ctx, R.string.media_player_failed);
                }
            }
            notifyDataSetChanged();
        } else {
            if (fd.filePath != null && fd.mime != null) {
                //special treatment of ringtones
                if (fd.fileType == Constants.FILE_TYPE_RINGTONES) {
                    playRingtone(fd);
                } else {
                    if (fd.fileType == Constants.FILE_TYPE_PICTURES && ctx instanceof MainActivity) {
                        Intent intent = new Intent(ctx, ImageViewerActivity.class);
                        intent.putExtras(fd.toBundle());
                        ctx.startActivity(intent);
                    } else {
                        UIUtils.openFile(ctx, fd.filePath, fd.mime, true);
                    }
                }
            } else {
                // it will automatically remove the 'Open' entry.
                new MenuBuilder(getMenuAdapter(view)).show();
                UIUtils.showShortMessage(ctx, R.string.cant_open_file);
            }
        }
    }

    private void playRingtone(FileDescriptor fileDescriptor) {
        //pause real music if any
        if (MusicUtils.isPlaying()) {
            MusicUtils.playOrPause();
        }
        MusicUtils.playSimple(fileDescriptor.filePath);
        notifyDataSetChanged();
    }

    private void populateViewThumbnail(View view, FileDescriptorItem item) {
        FileDescriptor fd = item.fd;

        ImageButton fileThumbnail = findView(view,
                inGridMode() ?
                        R.id.view_browse_peer_thumbnail_grid_item_browse_thumbnail_image_button :
                        R.id.view_browse_peer_thumbnail_list_item_browse_thumbnail_image_button);
        fileThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);

        MediaPlaybackStatusOverlayView mediaOverlayView = findView(view, inGridMode() ?
                R.id.view_browse_peer_thumbnail_grid_item_playback_overlay_view :
                R.id.view_browse_peer_thumbnail_list_item_playback_overlay_view);

        boolean inGridMode = inGridMode();
        int thumbnailDimensions = inGridMode ?
                128 : 96;

        if (fileType == Constants.FILE_TYPE_APPLICATIONS) {
            Uri uri = Uri.withAppendedPath(ImageLoader.APPLICATION_THUMBNAILS_URI, fd.album);
            thumbnailLoader.load(uri, fileThumbnail, thumbnailDimensions, thumbnailDimensions);
        } else {
            if (in(fileType, Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_VIDEOS)) {
                if (fd.equals(Engine.instance().getMediaPlayer().getCurrentFD())) {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.STOP);
                } else {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY);
                }
            } else if (fileType == Constants.FILE_TYPE_RINGTONES) {
                if (fd.equals(Engine.instance().getMediaPlayer().getSimplePlayerCurrentFD())) {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.STOP);
                } else {
                    mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY);
                }
            }

            if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
                Uri uri = ContentUris.withAppendedId(ImageLoader.ALBUM_THUMBNAILS_URI, fd.albumId);
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
            TextView title = findView(view, R.id.view_browse_peer_thumbnail_list_image_item_file_title);
            title.setText(fd.title);
            if (fd.fileType == Constants.FILE_TYPE_AUDIO || fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
                TextView fileExtra = findView(view, R.id.view_browse_peer_thumbnail_list_image_item_extra_text);
                fileExtra.setText(fd.artist);
            } else {
                TextView fileExtra = findView(view, R.id.view_browse_peer_thumbnail_list_image_item_extra_text);
                fileExtra.setText(R.string.empty_string);
            }
            TextView fileSize = findView(view, R.id.view_browse_peer_thumbnail_list_image_item_file_size);
            fileSize.setText(UIUtils.getBytesInHuman(fd.fileSize));
        }
        fileThumbnail.setTag(fd);
        fileThumbnail.setOnClickListener(downloadButtonClickListener);

        populateSDState(view, item);
    }

    private boolean inGridMode() {
        return getViewItemId() == R.layout.view_browse_peer_thumbnail_grid_item;
    }

    private void populateViewPlain(View view, FileDescriptorItem item) {
        FileDescriptor fd = item.fd;

        TextView title = findView(view, R.id.view_browse_peer_thumbnail_list_image_item_file_title);
        title.setText(fd.title);

        TextView fileExtra = findView(view, R.id.view_browse_peer_thumbnail_list_image_item_extra_text);
        if (fd.fileType == Constants.FILE_TYPE_AUDIO || fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
            fileExtra.setText(fd.artist);
        } else if (fd.fileType == Constants.FILE_TYPE_DOCUMENTS) {
            fileExtra.setText(FilenameUtils.getExtension(fd.filePath));
        } else {
            fileExtra.setText(R.string.empty_string);
        }

        TextView fileSize = findView(view, R.id.view_browse_peer_thumbnail_list_image_item_file_size);
        fileSize.setText(UIUtils.getBytesInHuman(fd.fileSize));

        ImageButton downloadButton = findView(view, inGridMode() ?
                R.id.view_browse_peer_thumbnail_grid_item_browse_thumbnail_image_button :
                R.id.view_browse_peer_thumbnail_list_item_browse_thumbnail_image_button);

        MediaPlaybackStatusOverlayView mediaOverlayView = findView(view, inGridMode() ?
                R.id.view_browse_peer_thumbnail_grid_item_playback_overlay_view :
                R.id.view_browse_peer_thumbnail_list_item_playback_overlay_view);

        if (fd.equals(Engine.instance().getMediaPlayer().getCurrentFD()) || fd.equals(Engine.instance().getMediaPlayer().getSimplePlayerCurrentFD())) {
            mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.STOP);
        } else {
            mediaOverlayView.setPlaybackState(MediaPlaybackOverlayPainter.MediaPlaybackState.PLAY);
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
        ImageView img = findView(v, R.id.view_browse_peer_thumbnail_list_image_item_sd);

        if (item.inSD) {
            if (item.mounted) {
                v.setBackgroundResource(R.drawable.listview_item_background_selector);
                setNormalTextColors(v);
                img.setVisibility(View.GONE);
            } else {
                v.setBackgroundResource(R.drawable.browse_peer_listview_item_inactive_background);
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
        TextView title = findView(v, R.id.view_browse_peer_thumbnail_list_image_item_file_title);
        TextView text = findView(v, R.id.view_browse_peer_thumbnail_list_image_item_extra_text);
        TextView size = findView(v, R.id.view_browse_peer_thumbnail_list_image_item_file_size);

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
        TextView title = findView(v, R.id.view_browse_peer_thumbnail_list_image_item_file_title);
        TextView text = findView(v, R.id.view_browse_peer_thumbnail_list_image_item_extra_text);
        TextView size = findView(v, R.id.view_browse_peer_thumbnail_list_image_item_file_size);

        Resources res = getContext().getResources();

        // TODO: Fix deprecation warning when we hit API 23
        title.setTextColor(res.getColor(R.color.browse_peer_listview_item_inactive_foreground));
        text.setTextColor(res.getColor(R.color.browse_peer_listview_item_inactive_foreground));
        size.setTextColor(res.getColor(R.color.browse_peer_listview_item_inactive_foreground));
    }

    private boolean showSingleOptions(List<FileDescriptor> checked, FileDescriptor fd) {
        //if ringtone - ignore other checked items
        if (fd.fileType == Constants.FILE_TYPE_RINGTONES) {
            return true;
        }
        return checked.size() <= 1 && (checked.size() != 1 || checked.get(0).equals(fd));
    }

    private static ArrayList<FileDescriptor> convertItems(Collection<FileDescriptorItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }

        ArrayList<FileDescriptor> list = new ArrayList<>(items.size());

        for (FileDescriptorItem item : items) {
            list.add(item.fd);
        }

        return list;
    }

    private static ArrayList<FileDescriptorItem> convertFiles(Collection<FileDescriptor> fds) {
        if (fds == null) {
            return new ArrayList<>();
        }

        ArrayList<FileDescriptorItem> list = new ArrayList<>(fds.size());

        for (FileDescriptor fd : fds) {
            FileDescriptorItem item = new FileDescriptorItem();
            item.fd = fd;
            list.add(item);
        }

        return list;
    }

    public void deleteItem(FileDescriptor fd) {
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

    private boolean checkIfNotExists(FileDescriptor fd) {
        if (fd == null || fd.filePath == null) {
            return true;
        }
        File f = new File(fd.filePath);
        if (!f.exists()) {
            if (SystemUtils.isSecondaryExternalStorageMounted(f.getAbsoluteFile())) {
                UIUtils.showShortMessage(getContext(), R.string.file_descriptor_sd_mounted);
                Librarian.instance().deleteFiles(fileType, Arrays.asList(fd), getContext());
                deleteItem(fd);
            } else {
                UIUtils.showShortMessage(getContext(), R.string.file_descriptor_sd_unmounted);
            }
            return true;
        } else {
            return false;
        }
    }

    // Moved here to cleanup base code.
    // Functional abstractions should be used instead
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

    public static String readInfoFromTorrent(String torrent, boolean magnet) {
        if (torrent == null) {
            return "";
        }

        String result = "";

        try {
            TorrentInfo ti = new TorrentInfo(new File(torrent));

            if (magnet) {
                result = ti.makeMagnetUri() + BTEngine.getInstance().magnetPeers();
            } else {
                result = ti.infoHash().toString();
            }
        } catch (Throwable e) {
            LOG.warn("Error trying read torrent: " + torrent, e);
        }

        return result;
    }

    public int getNumColumns() {
        if (getViewItemId() == R.layout.view_browse_peer_thumbnail_list_item) {
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

            FileDescriptor fd = obj.fd;

            if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
                return fd.album.trim().toLowerCase(Locale.US).contains(keywords) || fd.artist.trim().toLowerCase(Locale.US).contains(keywords) || fd.title.trim().toLowerCase(Locale.US).contains(keywords) || fd.filePath.trim().toLowerCase(Locale.US).contains(keywords);
            } else {
                return fd.title.trim().toLowerCase(Locale.US).contains(keywords) || fd.filePath.trim().toLowerCase(Locale.US).contains(keywords);
            }
        }
    }

    private final class DownloadButtonClickListener implements OnClickListener {
        public void onClick(View v) {
            FileDescriptor fd = (FileDescriptor) v.getTag();

            if (fd == null) {
                return;
            }

            if (checkIfNotExists(fd)) {
                return;
            }

            localPlay(fd, v);
        }
    }

    public static class FileDescriptorItem {

        public FileDescriptor fd;
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
}
