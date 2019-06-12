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

    void addStateListener(StateListener listener);

    void addPositionListener(PositionListener listener);

    String getOpenedFile();

    int getVolume();

    float getPositionInSecs();

    float getDurationInSecs();

    MediaPlaybackState getCurrentState();

    Map<String, String> getProperties(String fileOrUrl);
}
