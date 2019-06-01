package com.frostwire.mplayer;

import java.util.Map;

public interface MediaPlayer {

    void open(String fileOrUrl, int initialVolume);

    void seek(float time);

    void setVolume(int volume);

    void pause();

    void play();

    void stop();

    void togglePause();

    void mute(boolean on);

    void addMetaDataListener(MetaDataListener listener);

    void removeMetaDataListener(MetaDataListener listener);

    void addStateListener(StateListener listener);

    void removeStateListener(StateListener listener);

    void addVolumeListener(VolumeListener listener);

    void removeVolumeListener(VolumeListener listener);

    void addPositionListener(PositionListener listener);

    void removePositionListener(PositionListener listener);

    void addTaskListener(TaskListener listener);

    void removeTaskListener(TaskListener listener);

    String getOpenedFile();

    int getVolume();

    float getPositionInSecs();

    float getDurationInSecs();

    MediaPlaybackState getCurrentState();

    Map<String, String> getProperties(String fileOrUrl);
}
