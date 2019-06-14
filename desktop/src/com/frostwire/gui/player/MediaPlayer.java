/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.player;

import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.tags.TagsReader;
import com.frostwire.gui.mplayer.MPlayer;
import com.frostwire.mp4.IsoFile;
import com.frostwire.mp4.MovieHeaderBox;
import com.frostwire.mplayer.MediaPlaybackState;
import com.frostwire.util.StringUtils;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.MPlayerMediator;
import com.limegroup.gnutella.gui.RefreshListener;
import com.limegroup.gnutella.settings.PlayerSettings;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.FileUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * An media player to play compressed and uncompressed media.
 *
 * @author gubatron
 * @author aldenml
 */
public abstract class MediaPlayer implements RefreshListener, MPlayerUIEventListener {
    private static final String[] PLAYABLE_EXTENSIONS = new String[]{"mp3", "ogg", "wav", "wma", "wmv", "m4a", "aac", "flac", "mp4", "flv", "avi", "mov", "mkv", "mpg", "mpeg", "3gp", "m4v", "webm"};
    private static MediaPlayer instance;
    private final ExecutorService playExecutor;
    /**
     * Our list of MediaPlayerListeners that are currently listening for events
     * from this player
     */
    private final List<MediaPlayerListener> listenerList = new CopyOnWriteArrayList<>();
    private final MPlayer mplayer;
    private MediaSource currentMedia;
    private Playlist currentPlaylist;
    private MediaSource[] playlistFilesView;
    private RepeatMode repeatMode;
    private boolean shuffle;
    private boolean playNextMedia;
    private double volume;
    private final Queue<MediaSource> lastRandomFiles;
    private long durationInSeconds;
    private boolean isPlayPausedForSliding = false;
    private boolean stateNotificationsEnabled = true;

    MediaPlayer() {
        lastRandomFiles = new LinkedList<>();
        playExecutor = ExecutorsHelper.newProcessingQueue("AudioPlayer-PlayExecutor");
        String playerPath;
        playerPath = getPlayerPath();
        MPlayer.initialise(new File(playerPath));
        mplayer = new MPlayer();
        mplayer.addPositionListener(this::notifyProgress);
        mplayer.addStateListener(newState -> {
            if (newState == MediaPlaybackState.Closed) { // This is the case
                // mplayer is
                // done with the
                // current file
                playNextMedia();
            }
        });
        mplayer.addIcyInfoListener(this::notifyIcyInfo);
        repeatMode = RepeatMode.values()[PlayerSettings.LOOP_PLAYLIST.getValue()];
        shuffle = PlayerSettings.SHUFFLE_PLAYLIST.getValue();
        playNextMedia = true;
        volume = PlayerSettings.PLAYER_VOLUME.getValue();
        notifyVolumeChanged();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SPACE) {
                Object s = e.getComponent();
                if (!(s instanceof JTextField) && !(s instanceof JCheckBox) && !(s instanceof JTable && ((JTable) s).isEditing())) {
                    togglePause();
                    return true;
                }
            }
            return false;
        });
        // prepare to receive UI events
        MPlayerUIEventHandler.instance().addListener(this);
    }

    public static MediaPlayer instance() {
        if (instance == null) {
            if (OSUtils.isWindows()) {
                instance = new MediaPlayerWindows();
            } else if (OSUtils.isMacOSX()) {
                instance = new MediaPlayerOSX();
            } else if (OSUtils.isLinux()) {
                instance = new MediaPlayerLinux();
            }
        }
        return instance;
    }

    public static boolean isPlayableFile(File file) {
        return file.exists() && !file.isDirectory() && isPlayableFile(file.getAbsolutePath());
    }

    private static boolean isPlayableFile(String filename) {
        return FileUtils.hasExtension(filename, getPlayableExtensions());
    }

    public static String[] getPlayableExtensions() {
        return PLAYABLE_EXTENSIONS;
    }

    public static boolean isPlayableFile(MediaSource mediaSource) {
        if (mediaSource == null) {
            return false;
        } else if (mediaSource.getFile() != null) {
            return mediaSource.getFile().exists() && isPlayableFile(mediaSource.getFile());
        } else if (mediaSource.getPlaylistItem() != null) {
            return new File(mediaSource.getPlaylistItem().getFilePath()).exists() && isPlayableFile(mediaSource.getPlaylistItem().getFilePath());
        } else return mediaSource instanceof StreamMediaSource;
    }

    protected abstract String getPlayerPath();

    float getVolumeGainFactor() {
        return 100.0f;
    }

    public Dimension getCurrentVideoSize() {
        if (mplayer != null) {
            return mplayer.getVideoSize();
        } else {
            return null;
        }
    }

    public MediaSource getCurrentMedia() {
        return currentMedia;
    }

    public Playlist getCurrentPlaylist() {
        return currentPlaylist;
    }

    public MediaSource[] getPlaylistFilesView() {
        return playlistFilesView;
    }

    public synchronized void setPlaylistFilesView(List<MediaSource> playlistFilesView) {
        this.playlistFilesView = playlistFilesView.toArray(new MediaSource[0]);
    }

    RepeatMode getRepeatMode() {
        return repeatMode;
    }

    void setRepeatMode(RepeatMode repeatMode) {
        this.repeatMode = repeatMode;
        PlayerSettings.LOOP_PLAYLIST.setValue(repeatMode.getValue());
    }

    boolean isShuffle() {
        return shuffle;
    }

    void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
        PlayerSettings.SHUFFLE_PLAYLIST.setValue(shuffle);
    }

    /**
     * Adds the specified MediaPlayer listener to the list
     */
    public void addMediaPlayerListener(MediaPlayerListener listener) {
        listenerList.add(listener);
    }

    public MediaPlaybackState getState() {
        return mplayer.getCurrentState();
    }

    /**
     * Loads a MediaSource into the player to play next
     */
    private void loadMedia(MediaSource source, boolean isPreview, boolean playNextSong, Playlist currentPlaylist, List<MediaSource> playlistFilesView) {
        try {
            if (source == null) {
                return;
            }
            if (!isPreview && PlayerSettings.USE_OS_DEFAULT_PLAYER.getValue()) {
                GUIMediator.instance().playInOS(source);
                return;
            }
            currentMedia = source;
            this.playNextMedia = playNextSong;
            this.currentPlaylist = currentPlaylist;
            if (playlistFilesView != null) {
                this.playlistFilesView = playlistFilesView.toArray(new MediaSource[0]);
            } else {
                this.playlistFilesView = null;
            }
            if (currentMedia != null) {
                durationInSeconds = -1;
                if (currentMedia.getFile() != null) {
                    TagsReader tagsReader = new TagsReader(currentMedia.getFile());
                    LibraryMediator.instance().getLibraryCoverArtPanel().setTagsReader(tagsReader).asyncRetrieveImage();
                    calculateDurationInSecs(currentMedia.getFile());
                    playMedia();
                } else if (currentMedia.getPlaylistItem() != null && currentMedia.getPlaylistItem().getFilePath() != null) {
                    TagsReader tagsReader = new TagsReader(new File(currentMedia.getPlaylistItem().getFilePath()));
                    LibraryMediator.instance().getLibraryCoverArtPanel().setTagsReader(tagsReader).asyncRetrieveImage();
                    playMedia();
                    durationInSeconds = (long) currentMedia.getPlaylistItem().getTrackDurationInSecs();
                } else if (currentMedia instanceof StreamMediaSource) {
                    LibraryMediator.instance().getLibraryCoverArtPanel().setDefault();
                    playMedia(((StreamMediaSource) currentMedia).showPlayerWindow());
                }
                notifyOpened(source);
            }
        } catch (Throwable e) {
            // NPE from bug report
            e.printStackTrace();
        }
    }

    private void calculateDurationInSecs(File f) {
        String ext = FilenameUtils.getExtension(f.getName());
        if (ext == null || !ext.toLowerCase().endsWith("mp3") || !ext.toLowerCase().endsWith("m4a")) {
            durationInSeconds = -1;
            return;
        }
        if (ext.toLowerCase().endsWith("mp3")) {
            durationInSeconds = getDurationFromMP3(f);
        } else if (ext.toLowerCase().endsWith("m4a")) {
            durationInSeconds = getDurationFromM4A(f);
        }
    }

    private long getDurationFromMP3(File f) {
        try {
            return new TagsReader(f).parse().getDuration();
        } catch (Throwable e) {
            return -1;
        }
    }

    private long getDurationFromM4A(File f) {
        try {
            RandomAccessFile isoFile = new RandomAccessFile(f, "r");
            LinkedList<com.frostwire.mp4.Box> boxes = IsoFile.head(isoFile, ByteBuffer.allocate(100 * 1024));
            try {
                MovieHeaderBox mvhd = com.frostwire.mp4.Box.findFirst(boxes, com.frostwire.mp4.Box.mvhd);
                if (mvhd != null) {
                    return mvhd.duration() / mvhd.timescale();
                }
            } finally {
                IOUtils.closeQuietly(isoFile);
            }
        } catch (Throwable e) {
            return -1;
        }
        return -1;
    }

    public void asyncLoadMedia(final MediaSource source, final boolean playNextSong, final Playlist currentPlaylist, final List<MediaSource> playlistFilesView) {
        playExecutor.execute(() -> loadMedia(source, false, playNextSong, currentPlaylist, playlistFilesView));
    }

    public void loadMedia(MediaSource source, boolean isPreview, boolean playNextSong) {
        loadMedia(source, isPreview, playNextSong, currentPlaylist, (playlistFilesView != null) ? Arrays.asList(playlistFilesView) : null);
    }

    public void asyncLoadMedia(final MediaSource source, final boolean isPreview, final boolean playNextSong) {
        playExecutor.execute(() -> loadMedia(source, isPreview, playNextSong));
    }

    private String stopAndPrepareFilename() {
        String filename = "";
        try {
            mplayer.stop();
            setVolume(volume);
            if (currentMedia != null) {
                if (currentMedia.getFile() != null) {
                    filename = currentMedia.getFile().getAbsolutePath();
                } else if (currentMedia.getURL() != null) {
                    filename = currentMedia.getURL();
                } else if (currentMedia.getPlaylistItem() != null) {
                    filename = currentMedia.getPlaylistItem().getFilePath();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace(); // one more NPE
        }
        return filename;
    }

    /**
     * Force showing or not the media player window
     */
    private void playMedia(boolean showPlayerWindow) {
        String filename = stopAndPrepareFilename();
        if (filename.length() > 0) {
            MPlayerMediator mplayerMediator = MPlayerMediator.instance();
            if (mplayerMediator != null) {
                mplayerMediator.showPlayerWindow(showPlayerWindow);
            }
            mplayer.open(filename, getAdjustedVolume());
        }
        notifyState(getState());
    }

    /**
     * Plays a file and determines whether or not to show the player window based on the MediaType of the file.
     */
    private void playMedia() {
        String filename = stopAndPrepareFilename();
        if (filename.length() > 0) {
            boolean isVideoFile = MediaType.getVideoMediaType().matches(filename);
            MPlayerMediator mplayerMediator = MPlayerMediator.instance();
            if (mplayerMediator != null) {
                mplayerMediator.showPlayerWindow(isVideoFile);
            }
            mplayer.open(filename, getAdjustedVolume());
        }
        notifyState(getState());
    }

    /**
     * Toggle pause the current song
     */
    void togglePause() {
        mplayer.togglePause();
        notifyState(getState());
    }

    /**
     * Stops the current song
     */
    public void stop() {
        mplayer.stop();
        currentMedia = null;
        notifyState(getState());
    }

    private void fastForward() {
        mplayer.fastForward();
    }

    private void rewind() {
        mplayer.rewind();
    }

    /**
     * Seeks to a new location in the current song
     */
    public void seek(float timeInSecs) {
        mplayer.seek(timeInSecs);
        notifyState(getState());
    }

    private int getAdjustedVolume() {
        return (int) (volume * getVolumeGainFactor());
    }

    private double getVolume() {
        return volume;
    }

    /**
     * Sets the gain(volume) for the output line.
     *
     * @param fGain - [0.0 <-> 1.0]
     */
    public void setVolume(double fGain) {
        volume = Math.max(Math.min(fGain, 1.0), 0.0);
        mplayer.setVolume(getAdjustedVolume());
        PlayerSettings.PLAYER_VOLUME.setValue((float) volume);
        notifyVolumeChanged();
    }

    private void incrementVolume() {
        setVolume(getVolume() + 0.1);
    }

    private void decrementVolume() {
        setVolume(getVolume() - 0.1);
    }

    private void notifyVolumeChanged() {
        SwingUtilities.invokeLater(() -> fireVolumeChanged(volume));
    }

    /**
     * Notify listeners when a new audio source has been opened.
     */
    private void notifyOpened(final MediaSource mediaSource) {
        SwingUtilities.invokeLater(() -> fireOpened(mediaSource));
    }

    /**
     * Notify listeners about an AudioPlayerEvent. This creates general state
     * modifications to the player such as the transition from opened to playing
     * to paused to end of song.
     */
    private void notifyState(final MediaPlaybackState state) {
        if (stateNotificationsEnabled) {
            SwingUtilities.invokeLater(() -> fireState(state));
        }
    }

    /**
     * fires a progress event off a new thread. This lets us safely fire events
     * off of the player thread while using a lock on the input stream
     */
    private void notifyProgress(final float currentTimeInSecs) {
        SwingUtilities.invokeLater(() -> fireProgress(currentTimeInSecs));
    }

    private void notifyIcyInfo(final String data) {
        SwingUtilities.invokeLater(() -> fireIcyInfo(data));
    }

    /**
     * This is fired every time a new song is loaded and ready to play. The
     * properties map contains information about the type of song such as bit
     * rate, sample rate, media type(MPEG, Streaming,etc..), etc..
     */
    private void fireOpened(MediaSource mediaSource) {
        for (MediaPlayerListener listener : listenerList) {
            listener.mediaOpened(this, mediaSource);
        }
    }

    /**
     * Fired every time a byte stream is written to the sound card. This lets
     * listeners be aware of what point in the entire file is song is currently
     * playing. This also returns a copy of the written byte[] so it can get
     * passed along to objects such as a FFT for visual feedback of the song
     */
    private void fireProgress(float currentTimeInSecs) {
        for (MediaPlayerListener listener : listenerList) {
            listener.progressChange(this, currentTimeInSecs);
        }
    }

    private void fireVolumeChanged(double currentVolume) {
        for (MediaPlayerListener listener : listenerList) {
            listener.volumeChange(this, currentVolume);
        }
    }

    /**
     * Fired every time the state of the player changes. This allows a listener
     * to be aware of state transitions such as from OPENED -> PLAYING ->
     * STOPPED -> EOF
     */
    private void fireState(MediaPlaybackState state) {
        for (MediaPlayerListener listener : listenerList) {
            listener.stateChange(this, state);
        }
    }

    private void fireIcyInfo(String data) {
        for (MediaPlayerListener listener : listenerList) {
            listener.icyInfo(data);
        }
    }

    /**
     * returns the current state of the player and position of the song being
     * played
     */
    public void refresh() {
        notifyState(getState());
    }

    void playNextMedia() {
        if (!playNextMedia) {
            return;
        }
        if (currentPlaylist != null && currentPlaylist.isDeleted()) {
            return;
        }
        MediaSource media;
        if (getRepeatMode() == RepeatMode.SONG) {
            media = currentMedia;
        } else if (isShuffle()) {
            media = getNextRandomSong(currentMedia);
        } else if (getRepeatMode() == RepeatMode.ALL) {
            media = getNextContinuousMedia(currentMedia);
        } else {
            media = getNextMedia(currentMedia);
        }
        if (media != null) {
            //System.out.println(song.getFile());
            asyncLoadMedia(media, true, currentPlaylist, Arrays.asList(playlistFilesView));
        }
    }

    private boolean isPlayerStoppedClosedFailed() {
        MediaPlaybackState state = getState();
        return state == MediaPlaybackState.Stopped || state == MediaPlaybackState.Closed || state == MediaPlaybackState.Failed;
    }

    public boolean isThisBeingPlayed(File file) {
        if (isPlayerStoppedClosedFailed()) {
            return false;
        }
        MediaSource currentMedia = getCurrentMedia();
        if (currentMedia == null) {
            return false;
        }
        File currentMediaFile = currentMedia.getFile();
        if (currentMediaFile != null && file.equals(currentMediaFile))
            return true;
        PlaylistItem playlistItem = currentMedia.getPlaylistItem();
        return playlistItem != null && new File(playlistItem.getFilePath()).equals(file);
    }

    public boolean isThisBeingPlayed(String file) {
        if (StringUtils.isNullOrEmpty(file) || isPlayerStoppedClosedFailed()) {
            return false;
        }
        MediaSource currentMedia = getCurrentMedia();
        if (currentMedia == null) {
            return false;
        }
        String currentMediaUrl = currentMedia.getURL();
        return (currentMediaUrl != null) && file.toLowerCase().equals(currentMediaUrl.toLowerCase());
    }

    public boolean isThisBeingPlayed(PlaylistItem playlistItem) {
        if (isPlayerStoppedClosedFailed()) {
            return false;
        }
        MediaSource currentMedia = getCurrentMedia();
        if (currentMedia == null) {
            return false;
        }
        PlaylistItem currentMediaFile = currentMedia.getPlaylistItem();
        return currentMediaFile != null && playlistItem.equals(currentMediaFile);
    }

    private MediaSource getNextRandomSong(MediaSource currentMedia) {
        if (playlistFilesView == null) {
            return null;
        }
        MediaSource songFile;
        int count = 4;
        //noinspection StatementWithEmptyBody
        while ((songFile = findRandomMediaFile(currentMedia)) == null && count-- > 0) {
        }
        if (songFile != null) {
            if (count > 0) {
                lastRandomFiles.add(songFile);
                if (lastRandomFiles.size() > 3) {
                    lastRandomFiles.poll();
                }
            } else {
                songFile = currentMedia;
                lastRandomFiles.clear();
                lastRandomFiles.add(songFile);
            }
        }
        return songFile;
    }

    private MediaSource getNextContinuousMedia(MediaSource currentMedia) {
        if (playlistFilesView == null) {
            return null;
        }
        int n = playlistFilesView.length;
        if (n == 1) {
            return playlistFilesView[0];
        }
        for (int i = 0; i < n; i++) {
            try {
                MediaSource f1 = playlistFilesView[i];
                if (currentMedia.equals(f1)) {
                    for (int j = 1; j < n; j++) {
                        MediaSource file = playlistFilesView[(j + i) % n];
                        if (isPlayableFile(file)) {
                            return file;
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private MediaSource getNextMedia(MediaSource currentMedia) {
        if (playlistFilesView == null) {
            return null;
        }
        int n = playlistFilesView.length;
        //        if (n == 1) {
        //            return playlistFilesView.get(0);
        //        }
        //PlaylistFilesView should probably have a HashTable<AudioSource,Integer>
        //Where the integer is the index of the AudioSource on playlistFilesView.
        //This way we could easily find the current song and know the index of the
        //next or previous song.
        //When you have lots of files, I think the search below might
        //be too slow.
        for (int i = 0; i < n; i++) {
            try {
                MediaSource f1 = playlistFilesView[i];
                if (currentMedia.equals(f1)) {
                    for (int j = i + 1; j < n; j++) {
                        MediaSource file = playlistFilesView[j];
                        if (isPlayableFile(file)) {
                            return file;
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    MediaSource getPreviousMedia(MediaSource currentMedia) {
        if (playlistFilesView == null) {
            return null;
        }
        int n = playlistFilesView.length;
        for (int i = 0; i < n; i++) {
            try {
                MediaSource f1 = playlistFilesView[i];
                if (currentMedia.equals(f1)) {
                    for (int j = i - 1; j >= 0; j--) {
                        MediaSource file = playlistFilesView[j];
                        if (isPlayableFile(file)) {
                            return file;
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private MediaSource findRandomMediaFile(MediaSource excludeFile) {
        if (playlistFilesView == null) {
            return null;
        }
        int n = playlistFilesView.length;
        if (n == 0) {
            return null;
        } else if (n == 1) {
            return playlistFilesView[0];
        }
        int index = new Random(System.currentTimeMillis()).nextInt(n);
        for (int i = index; i < n; i++) {
            try {
                MediaSource file = playlistFilesView[i];
                if (!lastRandomFiles.contains(file) && !file.equals(excludeFile) && isPlayableFile(file)) {
                    return file;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    boolean canSeek() {
        if (durationInSeconds != -1) {
            return durationInSeconds > 0;
        }
        return mplayer.getDurationInSecs() > 0;
    }

    public float getDurationInSecs() {
        if (durationInSeconds != -1) {
            return durationInSeconds;
        }
        return mplayer.getDurationInSecs();
    }

    @Override
    public void onUIVolumeChanged(float volume) {
        setVolume(volume);
    }

    @Override
    public void onUISeekToTime(float seconds) {
        seek(seconds);
    }

    @Override
    public void onUIPlayPressed() {
        MediaPlaybackState curState = mplayer.getCurrentState();
        if (curState == MediaPlaybackState.Playing || curState == MediaPlaybackState.Paused) {
            togglePause();
        } else if (curState == MediaPlaybackState.Closed) {
            //playMedia();
            LibraryMediator.instance().playCurrentSelection();
        }
    }

    @Override
    public void onUIPausePressed() {
        togglePause();
    }

    @Override
    public void onUIFastForwardPressed() {
        fastForward();
    }

    @Override
    public void onUIRewindPressed() {
        rewind();
    }

    @Override
    public void onUIToggleFullscreenPressed() {
        MPlayerMediator.instance().toggleFullScreen();
    }

    @Override
    public void onUIProgressSlideStart() {
        stateNotificationsEnabled = false;
        if (mplayer.getCurrentState() == MediaPlaybackState.Playing) {
            isPlayPausedForSliding = true;
            mplayer.pause();
        }
    }

    @Override
    public void onUIProgressSlideEnd() {
        if (isPlayPausedForSliding) {
            isPlayPausedForSliding = false;
            mplayer.play();
        }
        stateNotificationsEnabled = true;
    }

    @Override
    public void onUIVolumeIncremented() {
        incrementVolume();
    }

    @Override
    public void onUIVolumeDecremented() {
        decrementVolume();
    }

    @Override
    public void onUITogglePlayPausePressed() {
        togglePause();
    }
}
