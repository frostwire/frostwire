package com.andrew.apollo;

import android.graphics.Bitmap;

interface IApolloService
{
    void openFile(String path);
    void open(in long [] list, int position);
    void stop();
    void pause();
    void play();
    void playSimple(String path);
    void stopSimplePlayer();
    void prev();
    void next();
    void enqueue(in long [] list, int action);
    void setQueuePosition(int index);
    void enableShuffle(boolean on);
    void setRepeatMode(int repeatmode);
    void moveQueueItem(int from, int to);
    void toggleFavorite();
    void refresh();
    boolean isFavorite();
    boolean isPlaying();
    boolean isStopped();
    long [] getQueue();
    long duration();
    long position();
    long seek(long pos);
    long getAudioId();
    long getCurrentSimplePlayerAudioId();
    long getArtistId();
    long getAlbumId();
    String getArtistName();
    String getTrackName();
    String getAlbumName();
    String getPath();
    int getQueuePosition();
    boolean isShuffleEnabled();
    int removeTracks(int first, int last);
    int removeTrack(long id); 
    int getRepeatMode();
    int getMediaMountedCount();
    int getAudioSessionId();

    void shutdown();
}