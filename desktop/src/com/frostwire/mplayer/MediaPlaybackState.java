package com.frostwire.mplayer;

public enum MediaPlaybackState {

    Uninitialized, Opening, Playing, Paused, Stopped, Closed, Failed;

    private String details;

    public String getDetails() {
        return (details);
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
