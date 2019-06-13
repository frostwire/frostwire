package com.frostwire.mplayer;

public interface PlayerPreferences {
    int getVolume();

    void setVolume(int volume);

    void setPositionForFile(String file, float position);

    float getPositionForFile(String file);
}
