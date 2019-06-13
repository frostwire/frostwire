package com.frostwire.mplayer;

public interface MetaDataListener {
    void receivedVideoResolution(int width, int height);

    void receivedDisplayResolution(int width, int height);

    void receivedDuration(float durationInSecs);

    void foundAudioTrack(Language language);

    void foundSubtitle(Language language);

    void activeAudioTrackChanged(String audioTrackId);

    void activeSubtitleChanged(String subtitleId, LanguageSource source);
}
