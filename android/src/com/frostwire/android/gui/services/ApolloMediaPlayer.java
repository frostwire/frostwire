package com.frostwire.android.gui.services;

import android.content.Context;
import android.util.LongSparseArray;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.core.player.Playlist;
import com.frostwire.android.core.player.PlaylistItem;
import com.frostwire.android.gui.Librarian;

import java.util.List;

public class ApolloMediaPlayer implements CoreMediaPlayer {

    private final LongSparseArray<FileDescriptor> idMap = new LongSparseArray<>();

    public ApolloMediaPlayer() {
    }

    @Override
    public void play(Playlist playlist) {
        List<PlaylistItem> items = playlist.getItems();

        idMap.clear();
        long[] list = new long[items.size()];
        int position = 0;

        PlaylistItem currentItem = playlist.getCurrentItem();

        for (int i = 0; i < items.size(); i++) {
            PlaylistItem item = items.get(i);
            list[i] = item.getFD().id;
            idMap.put((long) item.getFD().id, item.getFD());
            if (currentItem != null && currentItem.getFD().id == item.getFD().id) {
                position = i;
                break;
            }
        }

        MusicUtils.playAll(list, position, MusicUtils.isShuffleEnabled());
    }

    @Override
    public void stop() {
        try {
            if (MusicUtils.musicPlaybackService != null) {
                MusicUtils.musicPlaybackService.stop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isPlaying() {
        return MusicUtils.isPlaying();
    }

    @Override
    public FileDescriptor getCurrentFD(final Context context) {
        try {
            long audioId = MusicUtils.getCurrentAudioId();
            FileDescriptor fd = idMap.get(audioId);

            if (audioId != -1 && fd == null && context != null) {
                fd = Librarian.instance().getFileDescriptor(context, Constants.FILE_TYPE_AUDIO, (int) audioId);
                if (fd != null) {
                    idMap.put(audioId, fd);
                }
            }

            return fd;
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Override
    public FileDescriptor getSimplePlayerCurrentFD(final Context context) {
        if (context == null) {
            return null;
        }
        try {
            long audioId = MusicUtils.getCurrentSimplePlayerAudioId();
            return Librarian.instance().getFileDescriptor(context, Constants.FILE_TYPE_RINGTONES, (int) audioId);
        } catch (Throwable ignored) {
        }
        return null;
    }
}
