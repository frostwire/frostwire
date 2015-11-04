package com.frostwire.mplayer;

import java.util.Map;

public interface MediaPlayer {

    public void open(String fileOrUrl, int initialVolume);

    public void seek(float time);

    public void setVolume(int volume);

    public void pause();

    public void play();

    public void stop();

    public void togglePause();

    public void mute(boolean on);

    public void addMetaDataListener(MetaDataListener listener);

    public void removeMetaDataListener(MetaDataListener listener);

    public void addStateListener(StateListener listener);

    public void removeStateListener(StateListener listener);

    public void addVolumeListener(VolumeListener listener);

    public void removeVolumeListener(VolumeListener listener);

    public void addPositionListener(PositionListener listener);

    public void removePositionListener(PositionListener listener);

    public void addTaskListener(TaskListener listener);

    public void removeTaskListener(TaskListener listener);

    public String getOpenedFile();

    public int getVolume();

    public float getPositionInSecs();

    public float getDurationInSecs();

    public MediaPlaybackState getCurrentState();

    public Map<String, String> getProperties(String fileOrUrl);
}
