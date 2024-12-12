package com.frostwire.android.gui.services;

import android.content.Context;
import android.util.LongSparseArray;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.core.player.Playlist;
import com.frostwire.android.core.player.PlaylistItem;
import com.frostwire.android.gui.Librarian;

import java.io.File;
import java.util.List;

public class ApolloMediaPlayer implements CoreMediaPlayer {

    private final LongSparseArray<FWFileDescriptor> idMap = new LongSparseArray<>();

    public ApolloMediaPlayer() {
    }

    @Override
    public void play(Playlist playlist) {
        List<PlaylistItem> items = playlist.getItems();

        idMap.clear();
        File[] files = new File[items.size()];
        long[] list = new long[items.size()];
        int position = 0;

        PlaylistItem currentItem = playlist.getCurrentItem();

        for (int i = 0; i < items.size(); i++) {
            PlaylistItem item = items.get(i);
            FWFileDescriptor fd = item.getFD();
            list[i] = fd.id;
            idMap.put((long) fd.id, fd);

            if (currentItem != null && currentItem.getFD().id == fd.id) {
                position = i;
                //do not break here, otherwise the rest of the playlist ids will be 0ed;
            }
        }

        // use media store files/file descriptor ids
        MusicUtils.playFDs(list, position, list.length > 1 && MusicUtils.isShuffleEnabled());
    }

    @Override
    public void stop() {
        try {
            if (MusicUtils.getMusicPlaybackService() != null) {
                MusicUtils.getMusicPlaybackService().stop();
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
    public FWFileDescriptor getCurrentFD(final Context context) {
        try {
            long audioId = MusicUtils.getCurrentAudioId();
            FWFileDescriptor fd = idMap.get(audioId);

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
    public FWFileDescriptor getSimplePlayerCurrentFD(final Context context) {
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
