/*
 * Created on Mar 9, 2010
 * Created by Paul Gardner
 *
 * Copyright 2010 Vuze, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */

package com.frostwire.gui.mplayer;

import com.frostwire.mplayer.Language;
import com.frostwire.mplayer.LanguageSource;
import com.frostwire.util.OSUtils;
import org.limewire.util.FileUtils;
import org.limewire.util.SystemUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MPlayerInstance {
    private static final boolean LOG = true;
    private static final com.frostwire.util.Logger LOGGER = com.frostwire.util.Logger.getLogger(MPlayerInstance.class);
    private static File BINARY_PATH;
    volatile boolean activateNextSubtitleLoaded = false;
    private volatile Process mPlayerProcess;
    private boolean starting;
    private boolean started;
    private boolean stop_pending;
    private boolean stopped;
    private final Semaphore stop_sem = new Semaphore(0);
    private boolean paused;
    private final List<String> commands = new LinkedList<>();
    private final Semaphore command_sem = new Semaphore(0);
    private boolean isSeeking;
    private int seekingTo;
    private volatile long seekingSendTime;
    private float nextSeek = -1;
    private int pause_change_id_next;
    private boolean pause_reported;
    private long pause_reported_time = -1;
    private int pending_sleeps;
    private int mute_count;
    private boolean redrawing;
    private long redraw_completion;
    //private String fileOpened;
    private long redraw_last_frame;

    MPlayerInstance() {
    }

    static void initialize(File binary_path) {
        BINARY_PATH = binary_path;
        killProcesses(false);
    }

    private static void killProcesses(boolean delay) {
        if (OSUtils.isAnyMac()) {
            String process_name = BINARY_PATH.getName();
            if (delay) {
                try {
                    Thread.sleep(250);
                } catch (Throwable ignored) {
                }
            }
            runCommand(new String[]{"killall", "-9", process_name});
        } else if (OSUtils.isWindows()) {
            String process_name = BINARY_PATH.getName();
            int pos = process_name.lastIndexOf(".");
            if (pos != -1) {
                process_name = process_name.substring(0, pos);
            }
            if (LOG) {
                System.out.println("running tskill " + process_name);
            }
            if (delay) {
                try {
                    Thread.sleep(250);
                } catch (Throwable ignored) {
                }
            }
            runCommand(new String[]{"cmd", "/C", "tskill", process_name});
        }
    }

    private static void runCommand(String[] command) {
        try {
            if (!OSUtils.isWindows()) {
                command[0] = findCommand(command[0]);
            }
            Runtime.getRuntime().exec(command).waitFor();
        } catch (Throwable e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private static String findCommand(String name) {
        final String[] locations = {"/bin", "/usr/bin"};
        for (String s : locations) {
            File f = new File(s, name);
            if (f.exists() && f.canRead()) {
                return f.getAbsolutePath();
            }
        }
        return name;
    }

    void doOpen(String fileOrUrl, int initialVolume, final OutputConsumer _outputConsumer) {
        synchronized (this) {
            if (starting || started) {
                throw new RuntimeException("no can do");
            }
            starting = true;
        }
        final OutputConsumer output_consumer = new OutputConsumer() {
            boolean latest = false;

            public void consume(String output) {
                boolean is_paused = output.startsWith("ID_PAUSED");
                if (is_paused != latest) {
                    updateObservedPaused(is_paused);
                    latest = is_paused;
                }
                _outputConsumer.consume(output);
            }
        };
        try {
            List<String> cmdList = new ArrayList<>();
            cmdList.add(BINARY_PATH.getAbsolutePath());
            cmdList.add("-slave");
            if (fileOrUrl.toLowerCase().startsWith("http")) {
                cmdList.add("-cache");
                cmdList.add("64");
                cmdList.add("-cache-min");
                cmdList.add("50");
            }
            if (fileOrUrl.startsWith("https://")) {
                fileOrUrl = fileOrUrl.replace("https://", "ffmpeg://https://");
            }
            cmdList.add("-identify");
            cmdList.add("-prefer-ipv4");
            cmdList.add("-osdlevel");
            cmdList.add("0");
            cmdList.add("-noautosub");
            cmdList.add("-vo");
            if (OSUtils.isMacOSX()) {
                cmdList.add("corevideo:buffer_name=fwmplayer");
            } else if (OSUtils.isWindows()) {
                cmdList.add("direct3d,gl,directx,sdl");
            } else if (OSUtils.isLinux()) {
                cmdList.add("x11,gl,sdl");
            }
            if (OSUtils.isWindows()) {
                cmdList.add("-double");
                cmdList.add("-priority");
                cmdList.add("high");
                cmdList.add("-framedrop");
                if (FileUtils.hasExtension(fileOrUrl, "wma", "wmv", "asf")) {
                    cmdList.add("-demuxer");
                    cmdList.add("lavf");
                }
            }
            if (OSUtils.isLinux()) {
                cmdList.add("-double");
                cmdList.add("-framedrop");
            }
            cmdList.add("-volume");
            cmdList.add(String.valueOf(initialVolume));
            if (OSUtils.isLinux()) {
                cmdList.add("-zoom");
            }
            if (OSUtils.isMacOSX()) {
                cmdList.add(fileOrUrl);
            } else if (OSUtils.isWindows()) {
                if (fileOrUrl.length() > 250 && !fileOrUrl.toLowerCase().startsWith("http://")
                        && !fileOrUrl.toLowerCase().startsWith("ffmpeg://")) {
                    String shortFileName = SystemUtils.getShortFileName(fileOrUrl);
                    if (shortFileName == null) {
                        shortFileName = fileOrUrl;
                    }
                    cmdList.add(String.format("\"%s\"", shortFileName));
                } else {
                    cmdList.add(String.format("\"%s\"", fileOrUrl));
                }
            } else if (OSUtils.isLinux()) {
                cmdList.add(fileOrUrl);
            }
            String[] cmd = cmdList.toArray(new String[0]);
            String cmdString = Arrays.toString(cmd).replace(", ", " ");
            System.out.printf("starting mplayer: %s%n", cmdString);
            try {
                System.out.println("File Path: [" + cmdList.get(cmdList.size() - 1) + "]");
            } catch (Exception ignored) {
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                mPlayerProcess = pb.start();
                InputStream stdOut = mPlayerProcess.getInputStream();
                InputStream stdErr = mPlayerProcess.getErrorStream();
                OutputStream stdIn = mPlayerProcess.getOutputStream();
                final BufferedReader brStdOut = new BufferedReader(new InputStreamReader(stdOut));
                final BufferedReader brStdErr = new BufferedReader(new InputStreamReader(stdErr));
                final PrintWriter pwStdIn = new PrintWriter(new OutputStreamWriter(stdIn));
                Thread stdOutReader = new Thread("Player Console Out Reader") {
                    public void run() {
                        try {
                            String line;
                            while ((line = brStdOut.readLine()) != null) {
                                output_consumer.consume(line);
                            }
                        } catch (Exception e) {
                            //e.printStackTrace();
                        }
                    }
                };
                stdOutReader.setDaemon(true);
                stdOutReader.start();
                Thread stdErrReader = new Thread("Player Console Err Reader") {
                    public void run() {
                        try {
                            String line;
                            while ((line = brStdErr.readLine()) != null) {
                                output_consumer.consume(line);
                            }
                        } catch (Exception e) {
                            //e.printStackTrace();
                        }
                    }
                };
                stdErrReader.setDaemon(true);
                stdErrReader.start();
                Thread stdInWriter = new Thread("Player Console In Writer") {
                    public void run() {
                        try {
                            while (true) {
                                try {
                                    command_sem.acquire();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                                String toBeSent;
                                synchronized (MPlayerInstance.this) {
                                    if (commands.isEmpty()) {
                                        break;
                                    }
                                    toBeSent = commands.remove(0);
                                }
                                if (LOG) {
                                    System.out.println("-> " + toBeSent);
                                }
                                if (toBeSent.startsWith("sleep ") || toBeSent.startsWith("pausing_keep_force sleep ")) {
                                    int millis = Integer.parseInt(toBeSent.substring(toBeSent.startsWith("p") ? 25 : 6));
                                    try {
                                        Thread.sleep(millis);
                                    } catch (Throwable ignored) {
                                    }
                                    synchronized (MPlayerInstance.this) {
                                        pending_sleeps -= millis;
                                    }
                                } else if (toBeSent.startsWith("seek") || toBeSent.startsWith("pausing_keep_force seek")) {
                                    seekingSendTime = System.nanoTime() / 1_000_000;
                                }
                                toBeSent = toBeSent.replaceAll("\\\\", "\\\\\\\\");
                                pwStdIn.println(toBeSent + "\n");
                                pwStdIn.flush();
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        } finally {
                            stop_sem.release();
                        }
                    }
                };
                stdInWriter.setDaemon(true);
                stdInWriter.start();
            } catch (Throwable e) {
                e.printStackTrace();
                stop_sem.release();
            }
        } finally {
            synchronized (this) {
                starting = false;
                started = true;
                if (stop_pending) {
                    doStop();
                }
            }
        }
    }

    private void sendCommand(String cmd, CommandPauseMode pauseMode) {
        synchronized (this) {
            if (stopped) {
                return;
            }
            String prefix = "";
            if (CommandPauseMode.KEEP_FORCE == pauseMode) {
                prefix = "pausing_keep_force ";
            } else if (CommandPauseMode.KEEP == pauseMode) {
                prefix = "pausing_keep ";
            }
            commands.add(prefix + cmd);
            command_sem.release();
        }
    }

    private void sendCommand(String cmd) {
        sendCommand(cmd, CommandPauseMode.NONE);
    }

    void initialised() {
        synchronized (this) {
            sendCommand("get_property LENGTH");
            sendCommand("get_property SUB");
            sendCommand("get_property ASPECT");
            sendCommand("get_property WIDTH");
            sendCommand("get_property HEIGHT");
            sendCommand("get_property VOLUME");
        }
    }

    private void updateObservedPaused(boolean r_paused) {
        synchronized (this) {
            pause_reported = r_paused;
            pause_reported_time = System.nanoTime() / 1_000_000;
        }
    }

    private void pausedStateChanging() {
        final int delay = 333;
        pause_reported_time = -1;
        final int pause_change_id = ++pause_change_id_next;
        Timer timer = new Timer(true); // Daemon thread
        TimerTask task = new TimerTask() {
            int level = 0;

            @Override
            public void run() {
                synchronized (MPlayerInstance.this) {
                    if (!stopped && pause_change_id == pause_change_id_next && level < 20) {
                        level++;
                        if (pause_reported_time >= 0 && pause_reported == paused) {
                            return;
                        }
                        sendCommand("pause", CommandPauseMode.NONE);
                        new Timer(true).schedule(new TimerTask() {
                            @Override
                            public void run() {
                                synchronized (MPlayerInstance.this) {
                                    if (!stopped && pause_change_id == pause_change_id_next && level < 20) {
                                        if (pause_reported_time < 0 || pause_reported != paused) {
                                            sendCommand("pause", CommandPauseMode.NONE);
                                        }
                                    }
                                }
                            }
                        }, delay + pending_sleeps);
                    }
                }
            }
        };
        timer.schedule(task, delay + pending_sleeps);
    }

    void doPause() {
        synchronized (this) {
            if (paused) {
                return;
            }
            paused = true;
            pausedStateChanging();
            sendCommand("pause", CommandPauseMode.NONE);
        }
    }

    void doResume() {
        synchronized (this) {
            if (!paused) {
                return;
            }
            paused = false;
            pausedStateChanging();
            sendCommand("pause", CommandPauseMode.NONE);
        }
    }

    void doSeek(float timeInSecs) {
        synchronized (this) {
            if (isSeeking) {
                nextSeek = timeInSecs;
            } else {
                isSeeking = true;
                nextSeek = -1;
                int value = (int) timeInSecs;
                seekingTo = value;
                seekingSendTime = -1;
                sendCommand("seek " + value + " 2", CommandPauseMode.KEEP);
                sendCommand("get_time_pos", CommandPauseMode.KEEP);
            }
        }
    }

    void positioned(float time) {
        long now = System.nanoTime() / 1_000_000;
        synchronized (this) {
            if (seekingSendTime == -1) {
                return;
            }
            if (isSeeking) {
                if (time >= seekingTo) {
                    if (now - seekingSendTime > 1000 || time - seekingTo <= 2) {
                        positioned();
                    }
                }
            }
        }
    }

    void positioned() {
        synchronized (this) {
            if (isSeeking) {
                isSeeking = false;
                seekingSendTime = -1;
                if (nextSeek != -1) {
                    doSeek(nextSeek);
                }
            }
        }
    }

    void doSetVolume(int volume) {
        synchronized (this) {
            CommandPauseMode pauseMode = paused ? CommandPauseMode.KEEP_FORCE : CommandPauseMode.NONE;
            sendCommand("volume " + volume + " 1", pauseMode);
        }
    }

    void doMute(boolean on) {
        synchronized (this) {
            if (on) {
                mute_count++;
                if (mute_count == 1) {
                    sendCommand("mute 1");
                }
            } else {
                mute_count--;
                if (mute_count == 0) {
                    if (paused) {
                        nextSeek = -1;
                        pending_sleeps += 100;
                        sendCommand("sleep 100");
                    }
                    sendCommand("mute 0");
                }
            }
        }
    }

    @SuppressWarnings("unused")
    protected void setAudioTrack(Language language) {
        synchronized (this) {
            if (language != null) {
                sendCommand("switch_audio " + language.getId());
            }
        }
    }

    @SuppressWarnings("unused")
    protected void doRedraw() {
        synchronized (this) {
            final int delay = 250;
            long now = System.nanoTime() / 1_000_000;
            redraw_completion = now + delay;
            if (redrawing) {
                if (now - redraw_last_frame > delay) {
                    redraw_last_frame = now;
                    sendCommand("frame_step", CommandPauseMode.NONE);
                }
            } else {
                doMute(true);
                redraw_last_frame = now;
                sendCommand("frame_step", CommandPauseMode.NONE);
                redrawing = true;
                Timer timer = new Timer(true); // Daemon thread
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        synchronized (MPlayerInstance.this) {
                            long now = System.nanoTime() / 1_000_000;
                            long diff = redraw_completion - now;
                            if (diff < 0 || Math.abs(diff) <= 25) {
                                redrawing = false;
                                doMute(false);
                            } else {
                                new Timer(true).schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        synchronized (MPlayerInstance.this) {
                                            long now = System.nanoTime() / 1_000_000;
                                            long diff = redraw_completion - now;
                                            if (diff < 0 || Math.abs(diff) <= 25) {
                                                redrawing = false;
                                                doMute(false);
                                            }
                                        }
                                    }
                                }, diff);
                            }
                        }
                    }
                };
                timer.schedule(task, delay);
            }
        }
    }

    String setSubtitles(Language language) {
        synchronized (this) {
            String langId;
            String commandName = "sub_demux ";
            if (language != null) {
                langId = language.getId();
                if (language.getSource() == LanguageSource.FILE) {
                    commandName = "sub_file ";
                }
                sendCommand("set_property sub_visibility 1");
            } else {
                sendCommand("set_property sub_visibility 0");
                return null;
            }
            sendCommand(commandName + langId);
            return langId;
        }
    }

    void doLoadSubtitlesFile(String file, boolean autoPlay) {
        synchronized (this) {
            activateNextSubtitleLoaded = autoPlay;
            sendCommand("sub_load \"" + file + "\"");
        }
    }

    void doStop() {
        synchronized (this) {
            if (starting) {
                stop_pending = true;
                return;
            }
            if (stopped) {
                return;
            }
            sendCommand("stop");
            sendCommand("quit 0");
            stopped = true;
        }
        command_sem.release();
        if (mPlayerProcess != null) {
            mPlayerProcess.destroy();
        }
        killProcesses(true);
        try {
            stop_sem.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
    }

    void doGetProperties(String fileOrUrl, final OutputConsumer _outputConsumer) {
        final OutputConsumer output_consumer = _outputConsumer::consume;
        final CountDownLatch signal = new CountDownLatch(1);
        List<String> cmdList = new ArrayList<>();
        cmdList.add(BINARY_PATH.getAbsolutePath());
        cmdList.add("-slave");
        cmdList.add("-identify");
        cmdList.add("-prefer-ipv4");
        cmdList.add("-osdlevel");
        cmdList.add("0");
        cmdList.add("-noautosub");
        cmdList.add("-vo");
        cmdList.add("null");
        cmdList.add("-ao");
        cmdList.add("null");
        cmdList.add("-frames");
        cmdList.add("0");
        cmdList.add(fileOrUrl);
        String[] cmd = cmdList.toArray(new String[0]);
        try {
            InputStream stdOut = Runtime.getRuntime().exec(cmd).getInputStream();
            final BufferedReader brStdOut = new BufferedReader(new InputStreamReader(stdOut));
            Thread stdOutReader = new Thread("Player Console Out Reader") {
                public void run() {
                    try {
                        String line;
                        while ((line = brStdOut.readLine()) != null) {
                            if (line.startsWith("ID_EXIT")) {
                                signal.countDown();
                                break;
                            }
                            output_consumer.consume(line);
                        }
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                }
            };
            stdOutReader.setDaemon(true);
            stdOutReader.start();
            signal.await(5, TimeUnit.SECONDS);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    private void printCommand(String[] cmd) {
        for (String s : cmd) {
            if (s.contains(" ")) {
                System.out.print("\"");
            }
            System.out.print(s);
            if (s.contains(" ")) {
                System.out.print("\"");
            }
            System.out.print(" ");
        }
        System.out.println("\n");
    }

    public enum CommandPauseMode {
        NONE, KEEP, KEEP_FORCE
    }

    interface OutputConsumer {
        void consume(String output);
    }
}