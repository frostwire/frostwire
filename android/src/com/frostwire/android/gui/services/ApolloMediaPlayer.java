package com.frostwire.android.gui.services;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.core.player.Playlist;
import com.frostwire.android.core.player.PlaylistItem;
import com.frostwire.android.gui.Librarian;
import com.frostwire.util.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApolloMediaPlayer implements CoreMediaPlayer {
    private static Logger LOG = Logger.getLogger(ApolloMediaPlayer.class);
    private Map<Long, FileDescriptor> idMap = new HashMap<>();

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
            }
        }

        MusicUtils.playAll(list, position, false);
    }

    @Override
    public void stop() {
        try {
            if (MusicUtils.musicPlaybackService != null) {
                MusicUtils.musicPlaybackService.stop();
                LOG.info("ApolloMediaPlayer.stop() I could invoke stop() on the service.");
            } else {
                LOG.warn("ApolloMediaPlayer.stop() couldn't get a hold of the music playback service.");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {
        try {
            if (MusicUtils.musicPlaybackService != null) {
                MusicUtils.musicPlaybackService.shutdown();
                LOG.info("ApolloMediaPlayer.shutdown() I could invoke shutdown() on the service.");
            } else {
                LOG.warn("ApolloMediaPlayer.shutdown() couldn't get a hold of the music playback service.");
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
    public FileDescriptor getCurrentFD() {
        try {
            long audioId = MusicUtils.getCurrentAudioId();
            FileDescriptor fd = idMap.get(audioId);

            if (audioId != -1 && fd == null) {
                fd = Librarian.instance().getFileDescriptor(Constants.FILE_TYPE_AUDIO, (int) audioId);
                if (fd != null) {
                    idMap.put(audioId, fd);
                }
            }

            return fd;
        } catch (Throwable e) {
        }

        return null;
    }
}
