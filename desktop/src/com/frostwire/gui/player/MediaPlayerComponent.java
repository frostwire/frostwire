/*
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
import com.frostwire.gui.bittorrent.SendFileProgressDialog;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.LibraryUtils;
import com.frostwire.gui.library.tags.TagsData;
import com.frostwire.gui.library.tags.TagsReader;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.mplayer.MediaPlaybackState;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.limegroup.gnutella.gui.*;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class sets up JPanel with MediaPlayer on it, and takes care of GUI
 * MediaPlayer events.
 */
public final class MediaPlayerComponent implements MediaPlayerListener, RefreshListener {
    private static final Logger LOG = Logger.getLogger(MediaPlayerComponent.class);
    private static final int MAX_TITLE_CHARS = 36;
    private static final int BOUND_TITLE_CHARS = 18;
    /**
     * Constant for the play button.
     */
    private final MediaButton PLAY_BUTTON = new MediaButton(I18n.tr("Play") + " (" + I18n.tr("Long Press to Stop Playback") + ")", "play_up", "play_dn");
    /**
     * Constant for the pause button.
     */
    private final MediaButton PAUSE_BUTTON = new MediaButton(I18n.tr("Pause") + " (" + I18n.tr("Long Press to Stop Playback") + ")", "pause_up", "pause_dn");
    /**
     * Constant for the forward button.
     */
    private final MediaButton NEXT_BUTTON = new MediaButton(I18n.tr("Next"), "forward_up", "forward_dn");
    /**
     * Constant for the rewind button.
     */
    private final MediaButton PREV_BUTTON = new MediaButton(I18n.tr("Previous"), "rewind_up", "rewind_dn");
    /**
     * Constant for the volume control
     */
    private final JSlider VOLUME = new JSlider();
    /**
     * Constant for the progress bar
     */
    private final JProgressBar PROGRESS = new JProgressBar();
    private final JLabel progressCurrentTime = new JLabel("--:--:--");
    private final JLabel progressSongLength = new JLabel("--:--:--");
    /**
     * The media player.
     */
    private final MediaPlayer mediaPlayer;
    /**
     * The ProgressBar dimensions for showing the name & play progress.
     */
    private final Dimension progressBarDimension = new Dimension(245, 10);
    private JPanel PLAY_PAUSE_BUTTON_CONTAINER;
    /**
     * The current song that is playing
     */
    private MediaSource currentPlayListItem;
    /**
     * The lazily constructed media panel.
     */
    private JPanel myMediaPanel = null;
    private JToggleButton SHUFFLE_BUTTON;
    private JButton LOOP_BUTTON;
    private CardLayout PLAY_PAUSE_CARD_LAYOUT;
    private JLabel trackTitle;
    private MediaButton shareButton;
    private MediaButton socialButton;
    private MediaButton mediaSourceButton;
    private final Pattern facebookURLPattern = Pattern.compile("http(s)?\\:\\/\\/(www\\.)?facebook\\.com\\/([\\w-]+)");
    private final Pattern twitterURLPattern = Pattern.compile("http(s)?\\:\\/\\/(www\\.)?twitter\\.com\\/([\\w\\p{L}_]*[\\:|\\.]?\\s?)+");
    private final Pattern twitterUsernamePattern = Pattern.compile("(@[\\w\\p{L}_]*[\\:|\\.]?\\s?)+");
    private Timer longPressTimer;

    /**
     * Constructs a new <tt>MediaPlayerComponent</tt>.
     */
    public MediaPlayerComponent() {
        mediaPlayer = MediaPlayer.instance();
        mediaPlayer.addMediaPlayerListener(this);
        GUIMediator.addRefreshListener(this);
    }

    /**
     * Gets the media panel, constructing it if necessary.
     */
    public JPanel getMediaPanel() {
        if (myMediaPanel == null)
            myMediaPanel = constructMediaPanel();
        return myMediaPanel;
    }

    /**
     * Constructs the media panel.
     */
    private JPanel constructMediaPanel() {
        // create sliders
        PROGRESS.setMinimumSize(progressBarDimension);
        PROGRESS.setPreferredSize(progressBarDimension);
        PROGRESS.setMaximum(3600);
        PROGRESS.setEnabled(false);
        VOLUME.setMinimum(0);
        VOLUME.setValue(50);
        VOLUME.setMaximum(100);
        VOLUME.setEnabled(true);
        VOLUME.setOpaque(false);
        VOLUME.setToolTipText(I18n.tr("Volume"));
        // setup buttons
        registerListeners();
        JPanel panel = new JPanel();
        panel.setLayout(new MigLayout("insets 0, gap 0, fillx", //component constraints
                "[][]"));
        panel.setPreferredSize(new Dimension(480, 55));
        panel.setMinimumSize(new Dimension(480, 55));
        panel.setMaximumSize(new Dimension(480, 55));
        panel.add(createPlaybackButtonsPanel(), "span 1 2,growy, gapright 0");
        panel.add(createTrackDetailPanel(), "wrap, w 345px");
        panel.add(createProgressPanel(), "w 345px");
        return panel;
    }

    private JPanel createPlaybackButtonsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new MigLayout("insets 0, filly"));
        panel.add(ThemeMediator.createAppHeaderSeparator(), "growy");
        panel.add(PREV_BUTTON, "w 30px!");
        PLAY_PAUSE_CARD_LAYOUT = new CardLayout();
        PLAY_PAUSE_BUTTON_CONTAINER = new JPanel(PLAY_PAUSE_CARD_LAYOUT);
        PLAY_PAUSE_BUTTON_CONTAINER.setOpaque(false);
        PLAY_PAUSE_BUTTON_CONTAINER.add(PLAY_BUTTON, "PLAY");
        PLAY_PAUSE_BUTTON_CONTAINER.add(PAUSE_BUTTON, "PAUSE");
        panel.add(PLAY_PAUSE_BUTTON_CONTAINER, "w 36px!");
        panel.add(NEXT_BUTTON, "w 30px!");
        panel.add(ThemeMediator.createAppHeaderSeparator(), "growy");
        return panel;
    }

    private JPanel createTrackDetailPanel() {
        JPanel panel = new JPanel();
        Cursor theHand = new Cursor(Cursor.HAND_CURSOR);
        panel.setLayout(new MigLayout("insets 0, gap 4px, fillx, w 340px!", //layout
                "[][][grow][][][]", //columns
                "")); //row
        socialButton = new MediaButton("", null, null);
        socialButton.setCursor(theHand);
        socialButton.setVisible(false);
        panel.add(socialButton, "w 18px!");
        //only one of these 2 buttons is shown at the time, that's why it's on the same container.
        JPanel shareAndSourceButtonPanel = new JPanel();
        shareButton = new MediaButton(I18n.tr("Send this file to a friend"), "player_share_on", "player_share_off");
        shareButton.setCursor(theHand);
        shareButton.addActionListener(new SendToFriendActionListener());
        shareButton.setVisible(false);
        mediaSourceButton = new MediaButton(I18n.tr("Show the source of this media"), null, null);
        mediaSourceButton.setCursor(theHand);
        mediaSourceButton.addActionListener(new ShowSourceActionListener());
        mediaSourceButton.setVisible(false);
        shareAndSourceButtonPanel.add(shareButton);
        shareAndSourceButtonPanel.add(mediaSourceButton);
        panel.add(shareAndSourceButtonPanel, "w 18px!");
        Font buttonFont = new Font("Helvetica", Font.BOLD, 10);
        trackTitle = new JLabel("");
        trackTitle.setBorder(null);
        trackTitle.setFont(buttonFont);
        trackTitle.setCursor(theHand);
        trackTitle.setForeground(Color.WHITE);
        trackTitle.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (MediaPlayer.instance().getCurrentMedia().getFile() != null || MediaPlayer.instance().getCurrentMedia().getPlaylistItem() != null) {
                    showCurrentMedia();
                } else if (MediaPlayer.instance().getCurrentMedia() instanceof StreamMediaSource) {
                    StreamMediaSource mediaSource = (StreamMediaSource) MediaPlayer.instance().getCurrentMedia();
                    if (mediaSource.getDetailsUrl() != null) {
                        GUIMediator.openURL(mediaSource.getDetailsUrl());
                    }
                } else if (MediaPlayer.instance().getCurrentMedia().getURL() != null) {
                    GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
                }
            }
        });
        panel.add(trackTitle, "w 186px, wmax 186px");
        initPlaylistPlaybackModeControls();
        panel.add(LOOP_BUTTON, "w 20px!");
        panel.add(SHUFFLE_BUTTON, "w 20px!");
        panel.add(VOLUME, "w 58px!");
        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new MigLayout("insets 0 0 5px 0 5px, fillx", "[][grow][]"));
        Font f = panel.getFont();
        f = f.deriveFont(10f);
        progressCurrentTime.setForeground(Color.WHITE);
        progressCurrentTime.setFont(f);
        progressSongLength.setForeground(Color.WHITE);
        progressSongLength.setFont(f);
        panel.add(progressCurrentTime, "gap 2px!");
        panel.add(PROGRESS, "growx");
        panel.add(progressSongLength, "align center");
        return panel;
    }

    private void initPlaylistPlaybackModeControls() {
        SHUFFLE_BUTTON = new JToggleButton();
        SHUFFLE_BUTTON.setContentAreaFilled(false);
        SHUFFLE_BUTTON.setBackground(null);
        SHUFFLE_BUTTON.setIcon(GUIMediator.getThemeImage("shuffle_off"));
        SHUFFLE_BUTTON.setSelectedIcon(GUIMediator.getThemeImage("shuffle_on"));
        SHUFFLE_BUTTON.setToolTipText(I18n.tr("Shuffle songs"));
        SHUFFLE_BUTTON.setSelected(mediaPlayer.isShuffle());
        LOOP_BUTTON = new JButton();
        LOOP_BUTTON.setContentAreaFilled(false);
        LOOP_BUTTON.setBackground(null);
        LOOP_BUTTON.setIcon(getCurrentLoopButtonImage());
        LOOP_BUTTON.setToolTipText(I18n.tr("Repeat songs"));
        SHUFFLE_BUTTON.addActionListener(e -> mediaPlayer.setShuffle(SHUFFLE_BUTTON.isSelected()));
        LOOP_BUTTON.addActionListener(e -> {
            mediaPlayer.setRepeatMode(mediaPlayer.getRepeatMode().getNextState());
            LOOP_BUTTON.setIcon(getCurrentLoopButtonImage());
        });
    }

    private ImageIcon getCurrentLoopButtonImage() {
        if (mediaPlayer.getRepeatMode() == RepeatMode.ALL) {
            return GUIMediator.getThemeImage("loop_all");
        } else if (mediaPlayer.getRepeatMode() == RepeatMode.SONG) {
            return GUIMediator.getThemeImage("loop_one");
        } else { // RepeatMode.None
            return GUIMediator.getThemeImage("loop_off");
        }
    }

    private void showPauseButton() {
        PLAY_PAUSE_CARD_LAYOUT.show(PLAY_PAUSE_BUTTON_CONTAINER, "PAUSE");
    }

    private void showPlayButton() {
        PLAY_PAUSE_CARD_LAYOUT.show(PLAY_PAUSE_BUTTON_CONTAINER, "PLAY");
    }

    private void registerListeners() {
        PLAY_BUTTON.addActionListener(new PlayListener());
        PAUSE_BUTTON.addActionListener(new PauseListener());
        NEXT_BUTTON.addActionListener(new NextListener());
        PREV_BUTTON.addActionListener(new BackListener());
        VOLUME.addChangeListener(new VolumeSliderListener());
        PROGRESS.addMouseListener(new ProgressBarMouseAdapter());
        longPressTimer = new Timer(1500, e -> stopSong());
        longPressTimer.setRepeats(false);
        MouseListener longPressListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                longPressTimer.start();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                longPressTimer.stop();
            }
        };
        PLAY_BUTTON.addMouseListener(longPressListener);
        PAUSE_BUTTON.addMouseListener(longPressListener);
    }

    /**
     * Updates the audio player.
     */
    public void refresh() {
        mediaPlayer.refresh();
    }

    /**
     * Updates the current progress of the progress bar, on the Swing thread.
     */
    private void setProgressValue(final int update) {
        GUIMediator.safeInvokeLater(() -> PROGRESS.setValue(update));
    }

    /**
     * Enables or disables the skipping action on the progress bar safely from
     * the swing event queue
     *
     * @param enabled - true to allow skipping, false otherwise
     */
    private void setProgressEnabled(final boolean enabled) {
        GUIMediator.safeInvokeLater(() -> PROGRESS.setEnabled(enabled));
        setProgressValue(0);
    }

    /**
     * Updates the volume based on the position of the volume slider
     */
    private void setVolumeValue() {
        VOLUME.repaint();
        mediaPlayer.setVolume(((float) VOLUME.getValue()) / VOLUME.getMaximum());
    }

    /**
     * Begins playing the loaded song
     */
    private void play() {
        if (mediaPlayer.getCurrentMedia() != null) {
            if (mediaPlayer.getState() == MediaPlaybackState.Paused || mediaPlayer.getState() == MediaPlaybackState.Playing) {
                mediaPlayer.togglePause();
            } else if (mediaPlayer.getState() == MediaPlaybackState.Closed) {
                LibraryMediator.instance().playCurrentSelection();
            }
        } else {
            if (GUIMediator.instance().getSelectedTab() != null && GUIMediator.instance().getSelectedTab().equals(GUIMediator.Tabs.LIBRARY)) {
                LibraryMediator.instance().playCurrentSelection();
            }
        }
    }

    /**
     * Pauses the currently playing audio file.
     */
    private void pauseSong() {
        mediaPlayer.togglePause();
    }

    /**
     * Stops the currently playing audio file.
     */
    private void stopSong() {
        mediaPlayer.stop();
    }

    private void seek(float percent) {
        if (currentPlayListItem != null && currentPlayListItem.getURL() == null && mediaPlayer.canSeek()) {
            float timeInSecs = mediaPlayer.getDurationInSecs() * percent;
            mediaPlayer.seek(timeInSecs);
        }
    }

    /**
     * This event is thrown everytime a new media is opened and is ready to be
     * played.
     */
    @Override
    public void mediaOpened(MediaPlayer mediaPlayer, MediaSource mediaSource) {
        currentPlayListItem = mediaSource;
        setVolumeValue();
        if (mediaSource.getURL() == null && mediaPlayer.canSeek()) {
            setProgressEnabled(true);
            progressSongLength.setText(LibraryUtils.getSecondsInDDHHMMSS((int) mediaPlayer.getDurationInSecs()));
        } else {
            setProgressEnabled(false);
            progressSongLength.setText("--:--:--");
        }
        updateTitle(mediaSource);
        updateSocialButton(mediaSource);
        updateMediaSourceButton(mediaSource);
    }

    private void updateTitle(MediaSource mediaSource) {
        try {
            if (mediaSource == null) {
                return;
            }

            /* update controls */
            updateShareButtonVisibility(mediaSource);
            PlaylistItem playlistItem = mediaSource.getPlaylistItem();
            String currentText = null;
            if (mediaSource instanceof StreamMediaSource) {
                currentText = ((StreamMediaSource) mediaSource).getTitle();
            } else if (playlistItem != null) {
                //Playing from Playlist.
                String artistName = playlistItem.getTrackArtist();
                String songTitle = playlistItem.getTrackTitle();
                String albumToolTip = (playlistItem.getTrackAlbum() != null && playlistItem.getTrackAlbum().length() > 0) ? " - " + playlistItem.getTrackAlbum() : "";
                String yearToolTip = (playlistItem.getTrackYear() != null && playlistItem.getTrackYear().length() > 0) ? " (" + playlistItem.getTrackYear() + ")" : "";
                currentText = artistName + " - " + songTitle;
                trackTitle.setToolTipText(artistName + " - " + songTitle + albumToolTip + yearToolTip);
            } else if (mediaSource != null && mediaSource.getFile() != null) {
                //playing from Audio.
                currentText = mediaSource.getFile().getName();
                trackTitle.setToolTipText(mediaSource.getFile().getAbsolutePath());
            } else if (mediaSource != null && mediaSource.getFile() == null && mediaSource.getURL() != null) {
                //
                //System.out.println("StreamURL: " + currentMedia.getURL().toString());
                //sString streamURL = currentMedia.getURL().toString();
                //Pattern urlStart = Pattern.compile("(http://[\\d\\.]+:\\d+).*");
                //Matcher matcher = urlStart.matcher(streamURL);
                currentText = "internet "; // generic internet stream
            }
            setTitleHelper(currentText);
        } catch (Throwable e) {
            LOG.error("Error doing UI updates", e);
        }
    }

    private void updateShareButtonVisibility(MediaSource currentMedia) {
        boolean isLocalOrPlaylistFiles = (currentMedia != null && (currentMedia.getFile() != null || (currentMedia.getPlaylistItem() != null && currentMedia.getPlaylistItem().getFilePath() != null && new File(currentMedia.getPlaylistItem().getFilePath()).exists())));
        boolean showShareButton = currentMedia != null && (isLocalOrPlaylistFiles);
        shareButton.setVisible(showShareButton);
    }

    private void setTitleHelper(String currentText) {
        if (currentText.length() > MAX_TITLE_CHARS) {
            currentText = currentText.substring(0, BOUND_TITLE_CHARS) + " ... " + currentText.substring(currentText.length() - BOUND_TITLE_CHARS);
        }
        final String finalCurrentText = currentText;
        GUIMediator.safeInvokeLater(() -> {
            trackTitle.setText("<html><u>" + finalCurrentText + "</u></html>");
            trackTitle.setText(finalCurrentText);
        });
    }

    /**
     * This event is thrown a number of times a second. It updates the current
     * frames that have been read, along with position and bytes read
     */
    public void progressChange(MediaPlayer mediaPlayer, float currentTimeInSecs) {
        progressCurrentTime.setText(LibraryUtils.getSecondsInDDHHMMSS((int) currentTimeInSecs));
        if (currentPlayListItem != null && currentPlayListItem.getURL() == null) {
            progressSongLength.setText(LibraryUtils.getSecondsInDDHHMMSS((int) mediaPlayer.getDurationInSecs()));
        }
        if (currentPlayListItem != null && currentPlayListItem.getURL() == null && mediaPlayer.canSeek()) {
            setProgressEnabled(true);
            float progressUpdate = ((PROGRESS.getMaximum() * currentTimeInSecs) / mediaPlayer.getDurationInSecs());
            setProgressValue((int) progressUpdate);
        }
    }

    public void stateChange(MediaPlayer mediaPlayer, MediaPlaybackState state) {
        if (state == MediaPlaybackState.Opening) {
            setVolumeValue();
        } else if (state == MediaPlaybackState.Stopped || state == MediaPlaybackState.Closed) {
            setProgressValue(PROGRESS.getMinimum());
            progressCurrentTime.setText("--:--:--");
            progressSongLength.setText("--:--:--");
            mediaSourceButton.setVisible(false);
            showPlayButton();
        } else if (state == MediaPlaybackState.Playing) {
            showPauseButton();
        } else if (state == MediaPlaybackState.Paused) {
            showPlayButton();
        }
        if (state == MediaPlaybackState.Stopped || state == MediaPlaybackState.Closed) {
            trackTitle.setText("");
            updateMediaSourceButton(null);
            updateShareButtonVisibility(null);
        } else {
            updateTitle(mediaPlayer.getCurrentMedia());
        }
    }

    private void next() {
        mediaPlayer.playNextMedia();
    }

    private void back() {
        MediaSource currentMedia = mediaPlayer.getCurrentMedia();
        if (currentMedia != null) {
            MediaSource previousSong = mediaPlayer.getPreviousMedia(currentMedia);
            if (previousSong != null) {
                mediaPlayer.asyncLoadMedia(previousSong, false, true);
            }
        }
    }

    @Override
    public void volumeChange(MediaPlayer mediaPlayer, double currentVolume) {
        VolumeSliderListener oldListener = (VolumeSliderListener) VOLUME.getChangeListeners()[0];
        VOLUME.removeChangeListener(oldListener);
        VOLUME.setValue((int) (VOLUME.getMaximum() * currentVolume));
        VOLUME.addChangeListener(oldListener);
    }

    @Override
    public void icyInfo(String data) {
        if (data != null) {
            for (String s : data.split(";")) {
                if (s.startsWith("StreamTitle=")) {
                    try {
                        String streamTitle = s.substring(13, s.length() - 1);
                        setTitleHelper("radio " + streamTitle);
                    } catch (Throwable e) {
                        LOG.warn("Error updating UI", e);
                    }
                    break;
                }
            }
        }
    }

    private void showCurrentMedia() {
        GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
        LibraryMediator.instance().selectCurrentMedia();
    }

    private void updateSocialButton(final MediaSource currentMedia) {
        SwingWorker<Void, Void> swingWorker = new SwingWorker<>() {
            private boolean foundSocialLink;
            private String socialLink;

            @Override
            protected Void doInBackground() {
                if (currentMedia != null && (currentMedia.getFile() != null || currentMedia.getPlaylistItem() != null)) {
                    String commentToParse = "";
                    if (currentMedia.getFile() != null) {
                        commentToParse = getCommentFromMP3(currentMedia);
                    } else if (currentMedia.getPlaylistItem() != null) {
                        PlaylistItem playlistItem = currentMedia.getPlaylistItem();
                        commentToParse = playlistItem.getTrackComment();
                    }
                    parseSocialLink(commentToParse);
                }
                return null;
            }

            @Override
            protected void done() {
                if (foundSocialLink) {
                    setupSocialButtonAction();
                }
                socialButton.setVisible(foundSocialLink);
            }

            private void setupSocialButtonAction() {
                String artist = getArtistFromMP3(currentMedia);
                if (artist.equals("")) {
                    artist = I18n.tr("this artist(s)");
                }
                if (socialLink.contains("facebook")) {
                    socialButton.init(I18n.tr("Open Facebook page of") + " " + artist, "FACEBOOK", "FACEBOOK");
                } else if (socialLink.contains("twitter")) {
                    socialButton.init(I18n.tr("Open Twitter page of") + " " + artist, "TWITTER", "TWITTER");
                }
                removeSocialButtonActionListeners();
                socialButton.addActionListener(arg0 -> GUIMediator.openURL(socialLink));
            }

            private void removeSocialButtonActionListeners() {
                ActionListener[] actionListeners = socialButton.getActionListeners();
                if (actionListeners != null && actionListeners.length > 0) {
                    for (ActionListener al : actionListeners) {
                        socialButton.removeActionListener(al);
                    }
                }
            }

            private void parseSocialLink(String commentToParse) {
                if (!StringUtils.isNullOrEmpty(commentToParse)) {
                    String trimmedComment = commentToParse.toLowerCase().trim();
                    Matcher facebookMatcher = facebookURLPattern.matcher(trimmedComment);
                    if (facebookMatcher.find()) {
                        socialLink = facebookMatcher.group(0);
                    } else {
                        Matcher twitterURLMatcher = twitterURLPattern.matcher(trimmedComment);
                        if (twitterURLMatcher.find()) {
                            socialLink = twitterURLMatcher.group(0);
                        } else {
                            Matcher twitterUsernameMatcher = twitterUsernamePattern.matcher(trimmedComment);
                            if (twitterUsernameMatcher.find()) {
                                String tweep = twitterUsernameMatcher.group(0).trim();
                                socialLink = "https://twitter.com/" + tweep.substring(1);
                            }
                        }
                    }
                    foundSocialLink = !StringUtils.isNullOrEmpty(socialLink);
                }
            }
        };
        swingWorker.execute();
    }

    private void updateMediaSourceButton(final MediaSource currentMedia) {
        SwingWorker<Void, Void> swingWorker = new SwingWorker<>() {
            private boolean isLocalFile;
            private boolean isPlaylistItem;
            private boolean isSC;
            private boolean isAR;
            private String playlistName;

            @Override
            protected Void doInBackground() {
                if (currentMedia == null) {
                    return null;
                }
                if (currentMedia.getFile() != null) {
                    //won't be shown in 5.6.x, code here for 6.x
                    isLocalFile = true;
                } else if (currentMedia.getPlaylistItem() != null) {
                    //won't be shown in 5.6.x, code here for 6.x
                    isPlaylistItem = true;
                    setupPlaylistName(currentMedia);
                }
                if (currentMedia instanceof StreamMediaSource) {
                    StreamMediaSource streamMedia = (StreamMediaSource) currentMedia;
                    if (streamMedia.getDetailsUrl() != null) {
                        isSC = streamMedia.getDetailsUrl().contains("soundcloud");
                        isAR = streamMedia.getDetailsUrl().contains("archive");
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                String tooltipText = "";
                String iconUpName = "";
                String iconDownName = "";
                if (isLocalFile) { //won't be shown in 5.6.x, code here for 6.x
                    tooltipText = I18n.tr("Playing local file");
                    iconUpName = iconDownName = "speaker_light";
                } else if (isPlaylistItem) { //won't be shown in 5.6.x, code here for 6.x
                    tooltipText = I18n.tr("Playing track from") + " " + playlistName;
                    iconUpName = iconDownName = "playlist";
                } else if (isSC) {
                    tooltipText = I18n.tr("Open SoundCloud source page");
                    iconUpName = "soundcloud_off";
                    iconDownName = "soundcloud_on";
                } else if (isAR) {
                    tooltipText = I18n.tr("Open Archive.org source page");
                    iconUpName = "archive_off";
                    iconDownName = "archive_on";
                }
                //TODO: Add "isLocalFile || isPlaylistItem ||" on FrostWire 6.x when we have room for 3 buttons.
                boolean mediaSourceButtonVisible = (currentMedia != null) && (isSC || isAR);
                //System.out.println("mediaSourceButton should be visible? " + mediaSourceButtonVisible);
                if (mediaSourceButtonVisible) {
                    mediaSourceButton.init(tooltipText, iconUpName, iconDownName);
                }
                mediaSourceButton.setVisible(mediaSourceButtonVisible);
            }

            private void setupPlaylistName(final MediaSource currentMedia) {
                Playlist playlist = currentMedia.getPlaylistItem().getPlaylist();
                if (playlist != null && playlist.getName() != null) {
                    playlistName = playlist.getName();
                } else {
                    playlistName = I18n.tr("playlist");
                }
            }
        };
        swingWorker.execute();
    }

    private String getCommentFromMP3(MediaSource currentMedia) {
        String comment = "";
        File fileToParse = currentMedia.getFile();
        if (fileToParse != null && fileToParse.isFile() && fileToParse.exists() && fileToParse.getAbsolutePath().toLowerCase().endsWith(".mp3")) {
            TagsReader reader = new TagsReader(fileToParse);
            TagsData tagData = reader.parse();
            if (tagData != null && !StringUtils.isNullOrEmpty(tagData.getComment())) {
                comment = tagData.getComment();
            }
        }
        return comment;
    }

    private String getArtistFromMP3(MediaSource currentMedia) {
        String artist = "";
        if (currentMedia.getFile() != null) {
            File fileToParse = currentMedia.getFile();
            if (fileToParse.isFile() && fileToParse.exists() && fileToParse.getAbsolutePath().toLowerCase().endsWith(".mp3")) {
                TagsReader reader = new TagsReader(fileToParse);
                TagsData tagData = reader.parse();
                if (tagData != null && !StringUtils.isNullOrEmpty(tagData.getComment())) {
                    artist = tagData.getArtist();
                }
            }
        } else if (currentMedia.getPlaylistItem() != null && !StringUtils.isNullOrEmpty(currentMedia.getPlaylistItem().getTrackArtist())) {
            artist = currentMedia.getPlaylistItem().getTrackArtist();
        }
        return artist;
    }

    /**
     * Listens for the play button being pressed.
     */
    private class PlayListener implements ActionListener {
        public void actionPerformed(ActionEvent ae) {
            play();
        }
    }

    /**
     * Listens for the next button being pressed.
     */
    private class NextListener implements ActionListener {
        public void actionPerformed(ActionEvent ae) {
            next();
        }
    }

    /**
     * Listens for the back button being pressed.
     */
    private class BackListener implements ActionListener {
        public void actionPerformed(ActionEvent ae) {
            back();
        }
    }

    /**
     * Listens for the pause button being pressed.
     */
    private class PauseListener implements ActionListener {
        public void actionPerformed(ActionEvent ae) {
            pauseSong();
        }
    }

    /**
     * This listener is added to the progressbar to process when the user has
     * skipped to a new part of the song with a mouse
     */
    private class ProgressBarMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            seek(e.getX() * 1.0f / ((Component) e.getSource()).getWidth());
        }
    }

    /**
     * This listener is added to the volume slider to process whene the user has
     * adjusted the volume of the audio player
     */
    private class VolumeSliderListener implements ChangeListener {
        /**
         * If the user moved the thumb, adjust the volume of the player
         */
        public void stateChanged(ChangeEvent e) {
            setVolumeValue();
        }
    }

    private final class SendToFriendActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            MediaSource currentMedia = MediaPlayer.instance().getCurrentMedia();
            if (currentMedia == null) {
                return;
            }
            File file = null;
            if (currentMedia.getFile() != null) {
                file = currentMedia.getFile();
            } else if (currentMedia.getPlaylistItem() != null && currentMedia.getPlaylistItem().getFilePath() != null) {
                file = new File(currentMedia.getPlaylistItem().getFilePath());
            }
            if (file == null) {
                return;
            }
            String fileFolder = file.isFile() ? I18n.tr("file") : I18n.tr("folder");
            DialogOption result = GUIMediator.showYesNoMessage(I18n.tr("Do you want to send this {0} to a friend?", fileFolder) + "\n\n\"" + file.getName() + "\"", I18n.tr("Send files with FrostWire"), JOptionPane.QUESTION_MESSAGE);
            if (result == DialogOption.YES) {
                new SendFileProgressDialog(GUIMediator.getAppFrame(), file).setVisible(true);
                GUIMediator.instance().setWindow(GUIMediator.Tabs.TRANSFERS);
            }
        }
    }

    private final class ShowSourceActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            MediaSource currentMedia = MediaPlayer.instance().getCurrentMedia();
            if (currentMedia == null) {
                return;
            }
            if (currentMedia instanceof StreamMediaSource) {
                StreamMediaSource streamMedia = (StreamMediaSource) currentMedia;
                if (!StringUtils.isNullOrEmpty(streamMedia.getDetailsUrl())) {
                    GUIMediator.openURL(streamMedia.getDetailsUrl());
                }
            }
        }
    }
}
