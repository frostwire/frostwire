/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.mplayer;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseMediaPlayer implements MediaPlayer, MetaDataListener, StateListener, VolumeListener, PositionListener, TaskListener, IcyInfoListener {
    private final List<MetaDataListener> metaDataListeners;
    private final List<StateListener> stateListeners;
    private final List<VolumeListener> volumeListeners;
    private final List<PositionListener> positionListeners;
    private final List<TaskListener> taskListeners;
    private final List<IcyInfoListener> icyInfoListeners;
    protected final PlayerPreferences preferences;
    private MediaPlaybackState currentState;
    private int currentVolume;
    private float currentPositionInSecs;
    private float durationInSecs;
    private final Object audioTracksLock = new Object();
    private final Object subtitlesLock = new Object();
    private List<Language> audioTracks;
    private List<Language> subtitles;
    private String openedFile;

    protected BaseMediaPlayer(PlayerPreferences preferences) {
        this.preferences = preferences;
        metaDataListeners = new ArrayList<>(1);
        stateListeners = new ArrayList<>(1);
        volumeListeners = new ArrayList<>(1);
        positionListeners = new ArrayList<>(1);
        taskListeners = new ArrayList<>(1);
        icyInfoListeners = new ArrayList<>(1);
        initialize();
        setMetaDataListener(this);
        setStateListener(this);
        setVolumeListener(this);
        setPositionListener(this);
        setIcyInfoListener(this);
    }

    private void initialize() {
        openedFile = null;
        audioTracks = new ArrayList<>();
        subtitles = new ArrayList<>();
        durationInSecs = 0;
        currentPositionInSecs = 0;
        currentState = MediaPlaybackState.Uninitialized;
    }

    public void addStateListener(StateListener listener) {
        synchronized (stateListeners) {
            stateListeners.add(listener);
        }
    }

    public void addPositionListener(PositionListener listener) {
        synchronized (positionListeners) {
            positionListeners.add(listener);
        }
    }

    public void addIcyInfoListener(IcyInfoListener listener) {
        synchronized (icyInfoListeners) {
            icyInfoListeners.add(listener);
        }
    }

    protected abstract void setStateListener(StateListener listener);

    protected abstract void setVolumeListener(VolumeListener listener);

    protected abstract void setMetaDataListener(MetaDataListener listener);

    protected abstract void setPositionListener(PositionListener listener);

    protected abstract void setIcyInfoListener(IcyInfoListener listener);

    protected abstract void doOpen(String fileOrUrl, int initialVolume);

    protected abstract void doPause();

    protected abstract void doResume();

    protected abstract void doStop();

    protected abstract void doSeek(float timeInSecs);

    protected abstract void doSetVolume(int volume);

    @SuppressWarnings("unused")
    public abstract void doLoadSubtitlesFile(String file, boolean autoPlay);

    public void open(String fileOrUrl, int initialVolume) {
        if (currentState == MediaPlaybackState.Uninitialized || currentState == MediaPlaybackState.Stopped) {
            openedFile = fileOrUrl;
            doOpen(fileOrUrl, initialVolume);
        } else {
            doStop();
            initialize();
            openedFile = fileOrUrl;
            doOpen(fileOrUrl, initialVolume);
        }
    }

    public String getOpenedFile() {
        return openedFile;
    }

    public void fastForward() {
        if (currentState == MediaPlaybackState.Playing ||
                currentState == MediaPlaybackState.Paused) {
            seek((float) (currentPositionInSecs + 10.0));
        }
    }

    public void rewind() {
        if (currentState == MediaPlaybackState.Playing ||
                currentState == MediaPlaybackState.Paused) {
            seek((float) (currentPositionInSecs - 10.0));
        }
    }

    public void pause() {
        if (currentState == MediaPlaybackState.Playing) {
            doPause();
        }
    }

    public void play() {
        if (currentState == MediaPlaybackState.Paused) {
            doResume();
        }
    }

    public void togglePause() {
        if (currentState == MediaPlaybackState.Paused) {
            doResume();
        } else if (currentState == MediaPlaybackState.Playing) {
            doPause();
        }
    }

    public synchronized void seek(float timeInSecs) {
        if (timeInSecs < 0) timeInSecs = 0;
        if (timeInSecs > durationInSecs) timeInSecs = durationInSecs;
        if (currentState == MediaPlaybackState.Playing || currentState == MediaPlaybackState.Paused) {
            doSeek(timeInSecs);
        }
    }

    @SuppressWarnings("unused")
    public void incrementVolume() {
        setVolume(getVolume() + 10);
    }

    @SuppressWarnings("unused")
    public void decrementVolume() {
        setVolume(getVolume() - 10);
    }

    public void receivedDisplayResolution(int width, int height) {
        synchronized (metaDataListeners) {
            for (MetaDataListener listener : metaDataListeners) {
                listener.receivedDisplayResolution(width, height);
            }
        }
    }

    public void receivedDuration(float durationInSecs) {
        this.durationInSecs = durationInSecs;
        synchronized (metaDataListeners) {
            for (MetaDataListener listener : metaDataListeners) {
                listener.receivedDuration(durationInSecs);
            }
        }
    }

    public void receivedVideoResolution(int width, int height) {
        synchronized (metaDataListeners) {
            for (MetaDataListener listener : metaDataListeners) {
                listener.receivedVideoResolution(width, height);
            }
        }
    }

    public void foundAudioTrack(Language language) {
        synchronized (audioTracksLock) {
            audioTracks.add(language);
        }
        synchronized (metaDataListeners) {
            for (MetaDataListener listener : metaDataListeners) {
                listener.foundAudioTrack(language);
            }
        }
    }

    public void foundSubtitle(Language language) {
        synchronized (subtitlesLock) {
            subtitles.add(language);
        }
        synchronized (metaDataListeners) {
            for (MetaDataListener listener : metaDataListeners) {
                listener.foundSubtitle(language);
            }
        }
    }

    public void activeAudioTrackChanged(String audioTrackId) {
        synchronized (metaDataListeners) {
            for (MetaDataListener listener : metaDataListeners) {
                listener.activeAudioTrackChanged(audioTrackId);
            }
        }
    }

    public void activeSubtitleChanged(String subtitleId, LanguageSource source) {
        synchronized (metaDataListeners) {
            for (MetaDataListener listener : metaDataListeners) {
                listener.activeSubtitleChanged(subtitleId, source);
            }
        }
    }

    public void stateChanged(MediaPlaybackState newState) {
        currentState = newState;
        synchronized (stateListeners) {
            for (StateListener listener : stateListeners) {
                listener.stateChanged(newState);
            }
        }
    }

    public void volumeChanged(int newVolume) {
        currentVolume = newVolume;
        synchronized (volumeListeners) {
            for (VolumeListener listener : volumeListeners) {
                listener.volumeChanged(newVolume);
            }
        }
    }

    public void positionChanged(float currentTimeInSecs) {
        if (currentPositionInSecs != currentTimeInSecs) {
            currentPositionInSecs = currentTimeInSecs;
            synchronized (positionListeners) {
                for (PositionListener listener : positionListeners) {
                    listener.positionChanged(currentTimeInSecs);
                }
            }
        }
    }

    public void newIcyInfoData(String data) {
        synchronized (icyInfoListeners) {
            for (IcyInfoListener listener : icyInfoListeners) {
                listener.newIcyInfoData(data);
            }
        }
    }

    public void taskStarted(String taskName) {
        synchronized (taskListeners) {
            for (TaskListener listener : taskListeners) {
                listener.taskStarted(taskName);
            }
        }
    }

    public void taskProgress(String taskName, int percent) {
        synchronized (taskListeners) {
            for (TaskListener listener : taskListeners) {
                listener.taskProgress(taskName, percent);
            }
        }
    }

    public void taskEnded(String taskName) {
        synchronized (taskListeners) {
            for (TaskListener listener : taskListeners) {
                listener.taskEnded(taskName);
            }
        }
    }

    public void stop() {
        if (currentState == MediaPlaybackState.Playing || currentState == MediaPlaybackState.Paused) {
            doStop();
        }
    }

    public MediaPlaybackState getCurrentState() {
        return currentState;
    }

    public int getVolume() {
        return currentVolume;
    }

    public void setVolume(int volume) {
        if (currentState == MediaPlaybackState.Playing || currentState == MediaPlaybackState.Paused) {
            doSetVolume(volume);
        }
    }

    public float getPositionInSecs() {
        return currentPositionInSecs;
    }

    public float getDurationInSecs() {
        return durationInSecs;
    }
}
