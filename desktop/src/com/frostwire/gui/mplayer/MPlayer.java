/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Erich Pleny (erichpleny)
 * Copyright (c) 2012-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.mplayer;

import com.frostwire.mplayer.*;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MPlayer extends BaseMediaPlayer {
    private static final String ANS_LENGTH = "ANS_LENGTH=";
    private static final String ANS_POSITION = "ANS_TIME_POSITION=";
    private static final String ANS_VOLUME = "ANS_VOLUME=";
    private static final String ANS_SUB = "ANS_SUB=";
    private static final String ANS_WIDTH = "ANS_WIDTH=";
    private static final String ANS_HEIGHT = "ANS_HEIGHT=";
    private static final String ANS_ASPECT = "ANS_ASPECT=";
    private static final String ID_AUDIO_ID = "ID_AUDIO_ID=";
    private static final String ID_SUBTITLE_ID = "ID_SUBTITLE_ID=";
    private static final String ID_AUDIO_TRACK = "ID_AUDIO_TRACK=";
    private static final String ID_SUBTITLE_TRACK = "ID_SUBTITLE_TRACK=";
    private static final String ID_FILE_SUB_ID = "ID_FILE_SUB_ID=";
    private static final String ID_FILE_SUB_FILENAME = "ID_FILE_SUB_FILENAME=";
    private static final String ID_EXIT = "ID_EXIT=";
    private static final String ICY_INFO = "ICY Info:";
    private static final Pattern v_timeInfo = Pattern
            .compile("A:\\s*([0-9\\.]+) V:\\s*[0-9\\.]* .*");
    private static final Pattern a_timeInfo = Pattern
            .compile("A:\\s*([0-9\\.]+) .*");
    private static final Logger LOG = Logger.getLogger(MPlayer.class);
    private final List<String> output;
    private volatile boolean disposed = false;
    private Dimension videoSize = null;
    private boolean firstLengthReceived = false;
    private boolean firstVolumeReceived = false;
    private MPlayerInstance current_instance;
    private boolean parsingLanguage;
    private boolean isAudioTrack;
    private Language language;
    private int width;
    private float aspect;
    private MetaDataListener metaDataListener;
    private StateListener stateListener;
    private VolumeListener volumeListener;
    private PositionListener positionListener;
    private IcyInfoListener icyInfoListener;

    public MPlayer() {
        this(null);
    }

    private MPlayer(PlayerPreferences preferences) {
        super(preferences);
        output = new LinkedList<>();
        // System.out.println(line);
        Thread outputParser = new Thread("MPlayer output parser") {
            public void run() {
                try {
                    while (!disposed) {
                        String line = null;
                        synchronized (output) {
                            if (!output.isEmpty()) {
                                line = output.remove(0);
                            } else {
                                output.wait();
                            }
                        }
                        if (line != null) {
                            // System.out.println(line);
                            try {
                                parseOutput(line);
                            } catch (Throwable e) {
                                LOG.error(e.getMessage(), e);
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        };
        outputParser.setDaemon(true);
        outputParser.start();
    }

    public static void initialise(File path) {
        MPlayerInstance.initialise(path);
    }

    private void parseOutput(String line) {
        boolean stillParsing = false;
        // if ( !line.startsWith( "A:")){
        // System.out.println(line);
        // }
        Matcher v_matcher = v_timeInfo.matcher(line);
        Matcher a_matcher = a_timeInfo.matcher(line);
        if (v_matcher.matches()) {
            float time = Float.parseFloat(v_matcher.group(1));
            MPlayerInstance instance = getCurrentInstance();
            if (instance != null) {
                instance.positioned(time);
            }
            reportPosition(time);
        } else if (a_matcher.matches()) {
            float time = Float.parseFloat(a_matcher.group(1));
            MPlayerInstance instance = getCurrentInstance();
            if (instance != null) {
                instance.positioned(time);
            }
            reportPosition(time);
        } else if (line.startsWith("Starting playback...")) {
            // Ok, so the file is initialized, let's gather information
            stateListener.stateChanged(MediaPlaybackState.Playing);
            MPlayerInstance instance = getCurrentInstance();
            if (instance != null) {
                instance.initialised();
            }
            reportNewState(MediaPlaybackState.Playing);
        } else if (line.startsWith(ANS_POSITION)) {
            try {
                MPlayerInstance instance = getCurrentInstance();
                if (instance != null) {
                    instance.positioned();
                }
                float position = Float.parseFloat(line.substring(ANS_POSITION
                        .length()));
                reportPosition(position);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ANS_LENGTH)) {
            try {
                float duration = Float.parseFloat(line.substring(ANS_LENGTH
                        .length()));
                if (!firstLengthReceived) {
                    firstLengthReceived = true;
                    if (preferences != null) {
                        float seekTo = preferences
                                .getPositionForFile(getOpenedFile()) - 2f;
                        if (seekTo > 0 && seekTo < 0.99 * duration
                                && seekTo < duration - 20f) {
                            doSeek(seekTo);
                        }
                    }
                }
                reportDuration(duration);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ANS_VOLUME)) {
            try {
                int volume = (int) Float.parseFloat(line.substring(ANS_VOLUME
                        .length()));
                reportVolume(volume);
                if (!firstVolumeReceived) {
                    firstVolumeReceived = true;
                    if (preferences != null
                            && preferences.getVolume() != volume) {
                        setVolume(preferences.getVolume());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ANS_WIDTH)) {
            try {
                width = Integer.parseInt(line.substring(ANS_WIDTH.length()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ANS_HEIGHT)) {
            try {
                int videoWidth = width;
                int videoHeight = Integer.parseInt(line.substring(ANS_HEIGHT
                        .length()));
                int displayWidth = videoWidth;
                if (aspect > 0
                        && abs(aspect - (float) videoWidth
                        / (float) videoHeight) > 0.1) {
                    displayWidth = (int) (videoHeight * aspect);
                }
                if (metaDataListener != null) {
                    metaDataListener.receivedVideoResolution(videoWidth,
                            videoHeight);
                    metaDataListener.receivedDisplayResolution(displayWidth,
                            videoHeight);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ANS_ASPECT)) {
            try {
                aspect = Float.parseFloat(line.substring(ANS_ASPECT.length()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ID_AUDIO_ID)) {
            reportParsingDone();
            try {
                String audioId = line.substring(ID_AUDIO_ID.length());
                language = new Language(LanguageSource.STREAM, "" + audioId);
                parsingLanguage = true;
                isAudioTrack = true;
                stillParsing = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ID_SUBTITLE_ID)) {
            reportParsingDone();
            try {
                String audioId = line.substring(ID_SUBTITLE_ID.length());
                language = new Language(LanguageSource.STREAM, "" + audioId);
                parsingLanguage = true;
                isAudioTrack = false;
                stillParsing = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ID_FILE_SUB_ID)) {
            reportParsingDone();
            try {
                String subId = line.substring(ID_FILE_SUB_ID.length());
                language = new Language(LanguageSource.FILE, "" + subId);
                parsingLanguage = true;
                isAudioTrack = false;
                stillParsing = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (parsingLanguage && (line.startsWith(ID_FILE_SUB_FILENAME))) {
            try {
                String fileName = line.substring(ID_FILE_SUB_FILENAME.length());
                try {
                    File f = new File(fileName);
                    language.setSourceInfo(f.getAbsolutePath());
                    fileName = f.getName();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                language.setName(fileName);
                // Locale locale =
                // OpenSubtitlesAPI.getLocalSubtitleLanguage(getOpenedFile(),
                // fileName);
                // if(locale != null) {
                // language.setLanguage(locale);
                // }
                stillParsing = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (parsingLanguage
                && (line.startsWith("ID_AID_" + language.getId() + "_NAME=") || line
                .startsWith("ID_SID_" + language.getId() + "_NAME="))) {
            String key = "ID_AID_" + language.getId() + "_NAME=";
            String name = line.substring(key.length());
            language.setName(name);
            stillParsing = true;
        } else if (parsingLanguage
                && (line.startsWith("ID_AID_" + language.getId() + "_LANG=") || line
                .startsWith("ID_SID_" + language.getId() + "_LANG="))) {
            String key = "ID_AID_" + language.getId() + "_LANG=";
            String isoCode = line.substring(key.length());
            language.setLanguage(isoCode);
            stillParsing = true;
        } else if (parsingLanguage
                && (line.startsWith("ID_AID_" + language.getId()) || line
                .startsWith("ID_SID_" + language.getId()))) {
            stillParsing = true;
        } else if (line.startsWith(ID_AUDIO_TRACK)) {
            try {
                String audioId = line.substring(ID_AUDIO_TRACK.length());
                reportAudioTrackChanged(audioId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ID_SUBTITLE_TRACK)) {
            try {
                String subtitleId = line.substring(ID_SUBTITLE_TRACK.length());
                reportSubtitleChanged(subtitleId, LanguageSource.STREAM);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line.startsWith(ANS_SUB)) {
            try {
                String subtitleId = line.substring(ANS_SUB.length());
                reportSubtitleChanged(subtitleId, LanguageSource.STREAM);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (line
                .startsWith("<vo_direct3d>Reading display capabilities failed")) {
            reportNewState(MediaPlaybackState.Failed);
        } else if (line.startsWith(ID_EXIT)) {
            reportNewState(MediaPlaybackState.Closed);
        } else if (line.startsWith(ICY_INFO)) {
            String data = line.substring(ICY_INFO.length()).trim();
            reportIcyInfo(data);
        } else if (line.contains("VO: ")) {
            parseVideoSize(line);
        }
        // else System.out.println(line);
        if (parsingLanguage && !stillParsing) {
            Language parsed = language;
            reportParsingDone();
            MPlayerInstance instance = getCurrentInstance();
            if (instance != null) {
                if (instance.activateNextSubtitleLoaded) {
                    instance.activateNextSubtitleLoaded = false;
                    setSubtitles(parsed);
                }
            }
        }
    }

    private double abs(float f) {
        return f > 0 ? f : -f;
    }

    private void parseVideoSize(String line) {
        String[] arr = line.split(" ")[2].split("x");
        int w = Integer.parseInt(arr[0]);
        int h = Integer.parseInt(arr[1]);
        videoSize = new Dimension(w, h);
    }

    public Dimension getVideoSize() {
        return videoSize;
    }

    public void doLoadSubtitlesFile(String file, boolean autoPlay) {
        MPlayerInstance instance = getCurrentInstance();
        if (instance != null) {
            instance.doLoadSubtitlesFile(file, autoPlay);
        }
    }

    private void reportSubtitleChanged(String subtitleId, LanguageSource source) {
        if (metaDataListener != null) {
            metaDataListener.activeSubtitleChanged(subtitleId, source);
        }
    }

    private void reportAudioTrackChanged(String audioId) {
        if (metaDataListener != null) {
            metaDataListener.activeAudioTrackChanged(audioId);
        }
    }

    private void reportParsingDone() {
        if (parsingLanguage) {
            if (isAudioTrack) {
                reportFoundAudioTrack(language);
            } else {
                reportFoundSubtitle(language);
            }
            language = null;
            parsingLanguage = false;
            isAudioTrack = false;
        }
    }

    public void doOpen(String fileOrUrl, int initialVolume) {
        MPlayerInstance instance;
        synchronized (this) {
            doStop(false);
            instance = current_instance = new MPlayerInstance();
        }
        reportNewState(MediaPlaybackState.Opening);
        firstLengthReceived = false;
        firstVolumeReceived = false;
        if (fileOrUrl.startsWith("http://")) {
            // perform a 302 check, mplayer having issues with redirects.
            final HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
            Map<String, List<String>> responseHeaders = new HashMap<>();
            try {
                int responseCode = httpClient.head(fileOrUrl, 5000, responseHeaders);
                if (responseCode == 302 &&
                        responseHeaders.containsKey("Location") &&
                        responseHeaders.get("Location").get(0) != null) {
                    fileOrUrl = responseHeaders.get("Location").get(0);
                }
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
            }
        }
        instance.doOpen(fileOrUrl, initialVolume, line -> {
            synchronized (output) {
                output.add(line);
                output.notifyAll();
            }
        });
    }

    private MPlayerInstance getCurrentInstance() {
        synchronized (this) {
            return (current_instance);
        }
    }

    public void doPause() {
        MPlayerInstance instance = getCurrentInstance();
        if (instance != null) {
            instance.doPause();
        }
        reportNewState(MediaPlaybackState.Paused);
    }

    public void doResume() {
        MPlayerInstance instance = getCurrentInstance();
        if (instance != null) {
            instance.doResume();
        }
        reportNewState(MediaPlaybackState.Playing);
    }

    public void doSeek(float timeInSecs) {
        MPlayerInstance instance = getCurrentInstance();
        if (instance != null) {
            instance.doSeek(timeInSecs);
        }
    }

    public void doSetVolume(int volume) {
        MPlayerInstance instance = getCurrentInstance();
        if (instance != null) {
            instance.doSetVolume(volume);
        }
        reportVolume(volume);
    }

    public void mute(boolean on) {
        MPlayerInstance instance = getCurrentInstance();
        if (instance != null) {
            instance.doMute(on);
        }
    }

    private void setSubtitles(Language language) {
        MPlayerInstance instance = getCurrentInstance();
        if (instance != null) {
            reportSubtitleChanged(instance.setSubtitles(language),
                    language != null ? language.getSource() : null);
        }
    }

    public void doStop() {
        doStop(true);
    }

    private void doStop(boolean report_state) {
        synchronized (this) {
            if (current_instance != null) {
                if (preferences != null) {
                    preferences.setPositionForFile(getOpenedFile(),
                            getPositionInSecs());
                }
                current_instance.doStop();
                current_instance = null;
            }
            synchronized (output) {
                output.clear();
                output.notifyAll();
            }
        }
        if (report_state) {
            reportNewState(MediaPlaybackState.Stopped);
        }
    }

    public void setMetaDataListener(MetaDataListener listener) {
        this.metaDataListener = listener;
    }

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    public void setVolumeListener(VolumeListener listener) {
        this.volumeListener = listener;
    }

    public void setPositionListener(PositionListener listener) {
        this.positionListener = listener;
    }

    public void setIcyInfoListener(IcyInfoListener listener) {
        this.icyInfoListener = listener;
    }

    private void reportPosition(float position) {
        if (positionListener != null) {
            positionListener.positionChanged(position);
        }
    }

    private void reportVolume(int volume) {
        if (volumeListener != null) {
            volumeListener.volumeChanged(volume);
        }
    }

    private void reportDuration(float duration) {
        if (metaDataListener != null) {
            metaDataListener.receivedDuration(duration);
        }
    }

    private void reportFoundAudioTrack(Language audioTrack) {
        if (metaDataListener != null) {
            metaDataListener.foundAudioTrack(audioTrack);
        }
    }

    private void reportFoundSubtitle(Language subtitle) {
        if (metaDataListener != null) {
            metaDataListener.foundSubtitle(subtitle);
        }
    }

    private void reportNewState(MediaPlaybackState state) {
        if (stateListener != null) {
            stateListener.stateChanged(state);
        }
    }

    private void reportIcyInfo(String data) {
        if (icyInfoListener != null) {
            icyInfoListener.newIcyInfoData(data);
        }
    }

    public void dispose() {
        disposed = true;
        doStop();
    }

    @Override
    public Map<String, String> getProperties(String fileOrUrl) {
        MPlayerInstance instance = new MPlayerInstance();
        final Map<String, String> properties = new HashMap<>();
        instance.doGetProperties(fileOrUrl,
                new MPlayerInstance.OutputConsumer() {
                    private String lastKey = null;

                    public void consume(String line) {
                        if (line.split("=").length < 2) {
                            return;
                        }
                        if (line.startsWith("ID_CLIP_INFO_NAME")) {
                            lastKey = line.split("=")[1];
                        } else if (line.startsWith("ID_CLIP_INFO_VALUE")) {
                            if (lastKey != null) {
                                properties.put(lastKey, line.split("=")[1]);
                                lastKey = null;
                            }
                        } else if (line.startsWith("ID_")) {
                            String[] kv = line.split("=");
                            properties.put(kv[0], kv[1]);
                        }
                    }
                });
        return properties;
    }
}
