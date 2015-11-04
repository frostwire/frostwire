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

package com.frostwire.android.gui.adapters;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.adapters.FileListAdapter.FileDescriptorItem;
import com.frostwire.android.gui.adapters.menu.*;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.*;
import com.frostwire.android.util.ImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.logging.Logger;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

/**
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 */
public class FileListAdapter extends AbstractListAdapter<FileDescriptorItem> {

    private static final Logger LOG = Logger.getLogger(FileListAdapter.class);

    private final byte fileType;
    private final ImageLoader thumbnailLoader;
    private final DownloadButtonClickListener downloadButtonClickListener;
    private final FileListFilter fileListFilter;

    public FileListAdapter(Context context, List<FileDescriptor> files, byte fileType) {
        super(context, getViewItemId(fileType), convertFiles(files));

        setShowMenuOnClick(true);

        fileListFilter = new FileListFilter();
        setAdapterFilter(fileListFilter);

        this.fileType = fileType;
        this.thumbnailLoader = ImageLoader.getInstance(context);

        this.downloadButtonClickListener = new DownloadButtonClickListener();

        checkSDStatus();
    }

    public byte getFileType() {
        return fileType;
    }

    @Override
    protected final void populateView(View view, FileDescriptorItem item) {
        if (getViewItemId() == R.layout.view_browse_thumbnail_peer_list_item) {
            populateViewThumbnail(view, item);
        } else {
            populateViewPlain(view, item);
        }
    }

    @Override
    protected MenuAdapter getMenuAdapter(View view) {
        Context context = getContext();

        List<MenuAction> items = new ArrayList<MenuAction>();

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
        ensureCorrectMimeType(fd);
        boolean canOpenFile = fd.mime != null && (fd.mime.contains("audio") || fd.mime.contains("bittorrent") || fd.filePath != null);
        int numChecked = checked.size();

        boolean showSingleOptions = showSingleOptions(checked, fd);

        if (showSingleOptions) {
            if (canOpenFile) {
                items.add(new OpenMenuAction(context, fd.filePath, fd.mime));
            }

            if ((fd.fileType == Constants.FILE_TYPE_RINGTONES || fd.fileType == Constants.FILE_TYPE_AUDIO) && numChecked <= 1) {
                items.add(new SetAsRingtoneMenuAction(context, fd));
            }

            if (fd.fileType == Constants.FILE_TYPE_PICTURES && numChecked <= 1) {
                items.add(new SetAsWallpaperMenuAction(context, fd));
            }

            if (fd.fileType != Constants.FILE_TYPE_APPLICATIONS && numChecked <= 1) {
                items.add(new RenameFileMenuAction(context, this, fd));
            }

            if (fd.mime == Constants.MIME_TYPE_BITTORRENT && numChecked <= 1) {
                items.add(new CopyToClipboardMenuAction(context,
                        R.drawable.contextmenu_icon_magnet,
                        R.string.transfers_context_menu_copy_magnet,
                        R.string.transfers_context_menu_copy_magnet_copied,
                        new MagnetUriBuilder(fd.filePath)
                ));

                items.add(new CopyToClipboardMenuAction(context,
                        R.drawable.contextmenu_icon_copy,
                        R.string.transfers_context_menu_copy_infohash,
                        R.string.transfers_context_menu_copy_infohash_copied,
                        new InfoHashBuilder(fd.filePath)
                ));
            }
        }

        List<FileDescriptor> list = checked;
        if (list.size() == 0) {
            list = Arrays.asList(fd);
        }

        if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
            items.add(new AddToPlaylistMenuAction(context, list));
        }

        if (fd.fileType != Constants.FILE_TYPE_APPLICATIONS) {
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

        ensureCorrectMimeType(fd);

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
                UIUtils.openFile(ctx, fd.filePath, fd.mime);
            } else {
                // it will automatically remove the 'Open' entry.
                new MenuBuilder(getMenuAdapter(view)).show();
                UIUtils.showShortMessage(ctx, R.string.cant_open_file);
            }
        }
    }

    private void ensureCorrectMimeType(FileDescriptor fd) {
        if (fd.filePath.endsWith(".apk")) {
            fd.mime = Constants.MIME_TYPE_ANDROID_PACKAGE_ARCHIVE;
        }
        if (fd.filePath.endsWith(".torrent")) {
            fd.mime = Constants.MIME_TYPE_BITTORRENT;
        }
    }

    private void populateViewThumbnail(View view, FileDescriptorItem item) {
        FileDescriptor fd = item.fd;

        BrowseThumbnailImageButton fileThumbnail = findView(view, R.id.view_browse_peer_list_item_file_thumbnail);
        fileThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);

        if (fileType == Constants.FILE_TYPE_APPLICATIONS) {
            Uri uri = Uri.withAppendedPath(ImageLoader.APPLICATION_THUMBNAILS_URI, fd.album);
            thumbnailLoader.load(uri, fileThumbnail, 96, 96);
        } else {
            if (in(fileType, Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_VIDEOS, Constants.FILE_TYPE_RINGTONES)) {
                if (fd.equals(Engine.instance().getMediaPlayer().getCurrentFD())) {
                    fileThumbnail.setOverlayState(MediaPlaybackOverlay.MediaPlaybackState.STOP);
                } else {
                    fileThumbnail.setOverlayState(MediaPlaybackOverlay.MediaPlaybackState.PLAY);
                }
            }

            if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
                Uri uri = ContentUris.withAppendedId(ImageLoader.ALBUM_THUMBNAILS_URI, fd.albumId);
                thumbnailLoader.load(uri, fileThumbnail, 96, 96);
            } else if (fd.fileType == Constants.FILE_TYPE_VIDEOS) {
                Uri uri = ContentUris.withAppendedId(Video.Media.EXTERNAL_CONTENT_URI, fd.id);
                thumbnailLoader.load(uri, fileThumbnail, 96, 96);
            } else if (fd.fileType == Constants.FILE_TYPE_PICTURES) {
                Uri uri = ContentUris.withAppendedId(Images.Media.EXTERNAL_CONTENT_URI, fd.id);
                thumbnailLoader.load(uri, fileThumbnail, 96, 96);
            }
        }

        TextView title = findView(view, R.id.view_browse_peer_list_item_file_title);
        title.setText(fd.title);

        if (fd.fileType == Constants.FILE_TYPE_AUDIO || fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
            TextView fileExtra = findView(view, R.id.view_browse_peer_list_item_extra_text);
            fileExtra.setText(fd.artist);
        } else {
            TextView fileExtra = findView(view, R.id.view_browse_peer_list_item_extra_text);
            fileExtra.setText(R.string.empty_string);
        }

        TextView fileSize = findView(view, R.id.view_browse_peer_list_item_file_size);
        fileSize.setText(UIUtils.getBytesInHuman(fd.fileSize));

        fileThumbnail.setTag(fd);
        fileThumbnail.setOnClickListener(downloadButtonClickListener);

        populateSDState(view, item);
    }

    private void populateViewPlain(View view, FileDescriptorItem item) {
        FileDescriptor fd = item.fd;

        TextView title = findView(view, R.id.view_browse_peer_list_item_file_title);
        title.setText(fd.title);

        populateContainerAction(view);

        TextView fileExtra = findView(view, R.id.view_browse_peer_list_item_extra_text);
        if (fd.fileType == Constants.FILE_TYPE_AUDIO || fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
            fileExtra.setText(fd.artist);
        } else if (fd.fileType == Constants.FILE_TYPE_DOCUMENTS) {
            fileExtra.setText(FilenameUtils.getExtension(fd.filePath));
        } else {
            fileExtra.setText(R.string.empty_string);
        }

        TextView fileSize = findView(view, R.id.view_browse_peer_list_item_file_size);
        fileSize.setText(UIUtils.getBytesInHuman(fd.fileSize));

        BrowseThumbnailImageButton downloadButton = findView(view, R.id.view_browse_peer_list_item_download);

        if (fd.equals(Engine.instance().getMediaPlayer().getCurrentFD())) {
            downloadButton.setOverlayState(MediaPlaybackOverlay.MediaPlaybackState.STOP);
        } else {
            downloadButton.setOverlayState(MediaPlaybackOverlay.MediaPlaybackState.PLAY);
        }

        downloadButton.setTag(fd);
        downloadButton.setOnClickListener(downloadButtonClickListener);

        populateSDState(view, item);
    }

    private void populateSDState(View v, FileDescriptorItem item) {
        ImageView img = findView(v, R.id.view_browse_peer_list_item_sd);

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
        TextView title = findView(v, R.id.view_browse_peer_list_item_file_title);
        TextView text = findView(v, R.id.view_browse_peer_list_item_extra_text);
        TextView size = findView(v, R.id.view_browse_peer_list_item_file_size);

        Resources res = getContext().getResources();

        title.setTextColor(res.getColor(R.color.browse_peer_listview_item_foreground));
        text.setTextColor(res.getColor(R.color.browse_peer_listview_item_foreground));
        size.setTextColor(res.getColor(R.color.app_highlight_text));
    }

    private void setInactiveTextColors(View v) {
        TextView title = findView(v, R.id.view_browse_peer_list_item_file_title);
        TextView text = findView(v, R.id.view_browse_peer_list_item_extra_text);
        TextView size = findView(v, R.id.view_browse_peer_list_item_file_size);

        Resources res = getContext().getResources();

        title.setTextColor(res.getColor(R.color.browse_peer_listview_item_inactive_foreground));
        text.setTextColor(res.getColor(R.color.browse_peer_listview_item_inactive_foreground));
        size.setTextColor(res.getColor(R.color.browse_peer_listview_item_inactive_foreground));
    }

    private void populateContainerAction(View view) {
        ImageButton preview = findView(view, R.id.view_browse_peer_list_item_button_preview);
        preview.setVisibility(View.GONE);
    }

    private boolean showSingleOptions(List<FileDescriptor> checked, FileDescriptor fd) {
        if (checked.size() > 1) {
            return false;
        }
        return checked.size() != 1 || checked.get(0).equals(fd);
    }

    private static int getViewItemId(byte fileType) {
        if (fileType == Constants.FILE_TYPE_PICTURES || fileType == Constants.FILE_TYPE_VIDEOS || fileType == Constants.FILE_TYPE_APPLICATIONS || fileType == Constants.FILE_TYPE_AUDIO) {
            return R.layout.view_browse_thumbnail_peer_list_item;
        } else {
            return R.layout.view_browse_peer_list_item;
        }
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

        String privateSubpath = "Android" + File.separator + "data";

        File[] externalDirs = SystemUtils.getExternalFilesDirs(getContext());
        for (int i = 1; i < externalDirs.length; i++) {
            File path = externalDirs[i];
            String absolutePath = path.getAbsolutePath();
            boolean isSecondaryExternalStorageMounted = SystemUtils.isSecondaryExternalStorageMounted(path);

            sds.put(absolutePath, isSecondaryExternalStorageMounted);

            if (absolutePath.contains(privateSubpath)) {
                String prefix = absolutePath.substring(0, absolutePath.indexOf(privateSubpath) - 1);
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

    private static final class MagnetUriBuilder {

        private final String torrentFilePath;

        public MagnetUriBuilder(String torrentFilePath) {
            this.torrentFilePath = torrentFilePath;
        }

        @Override
        public String toString() {
            if (this.torrentFilePath != null) {
                try {
                    return new TorrentInfo(new File(this.torrentFilePath)).makeMagnetUri();
                } catch (Throwable e) {
                    LOG.warn("Error trying to get magnet", e);
                }
            }
            return super.toString();
        }
    }

    private static final class InfoHashBuilder {

        private final String torrentFilePath;

        public InfoHashBuilder(String torrentFilePath) {
            this.torrentFilePath = torrentFilePath;
        }

        @Override
        public String toString() {
            if (this.torrentFilePath != null) {
                try {
                    return new TorrentInfo(new File(this.torrentFilePath)).getInfoHash().toString();
                } catch (Throwable e) {
                    LOG.warn("Error trying to get infohash", e);
                }
            }
            return super.toString();
        }
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
        public boolean inSD;
        public boolean mounted;
        public boolean exists;

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FileDescriptorItem)) {
                return false;
            }

            return fd.equals(((FileDescriptorItem) o).fd);
        }

        @Override
        public int hashCode() {
            return fd.id;
        }
    }
}
