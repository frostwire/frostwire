/*
 * Copyright (C) 2007 The Android Open Source Project Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.KeyEvent;

import com.andrew.apollo.ui.activities.HomeActivity;
import com.frostwire.android.gui.services.Engine;

import androidx.legacy.content.WakefulBroadcastReceiver;

/**
 * Used to control headset playback.
 *   Single press: pause/resume
 *   Double press: next track
 *   Triple press: previous track
 *   Long press: voice search
 */
public class MediaButtonIntentReceiver extends WakefulBroadcastReceiver {
    private static final boolean DEBUG = false;
    private static final String TAG = "MBIntentReceiver";

    private static final int MSG_LONGPRESS_TIMEOUT = 1;
    private static final int MSG_HEADSET_DOUBLE_CLICK_TIMEOUT = 2;

    private static final int LONG_PRESS_DELAY = 1000;
    private static final int DOUBLE_CLICK = 800;

    private static WakeLock mWakeLock = null;
    private static int mClickCounter = 0;
    private static long mLastClickTime = 0;
    private static boolean mDown = false;
    private static boolean mLaunched = false;
    private static MBIReceiverHandler mbiReceiverHandler = new MBIReceiverHandler();

    private static class MBIReceiverHandler extends Handler {
        private static final String TAG = "MBIReceiverHandler";

        /**
         * {@inheritDoc}
         */
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_LONGPRESS_TIMEOUT:
                    if (DEBUG) Log.v(TAG, "Handling long press timeout, launched " + mLaunched);
                    if (!mLaunched) {
                        final Context context = (Context)msg.obj;
                        final Intent i = new Intent();
                        i.setClass(context, HomeActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(i);
                        mLaunched = true;
                    }
                    break;

                case MSG_HEADSET_DOUBLE_CLICK_TIMEOUT:
                    final int clickCount = msg.arg1;
                    final String command;

                    if (DEBUG) Log.v(TAG, "Handling headset click, count = " + clickCount);
                    switch (clickCount) {
                        case 1: command = MusicPlaybackService.CMDTOGGLEPAUSE; break;
                        case 2: command = MusicPlaybackService.CMDNEXT; break;
                        case 3: command = MusicPlaybackService.CMDPREVIOUS; break;
                        default: command = null; break;
                    }

                    if (command != null) {
                        final Context context = (Context) msg.obj;
                        sendCommandToMusicPlaybackService(context, command);
                    }
                    break;
            }
            releaseWakeLockIfHandlerIdle();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (DEBUG) Log.v(TAG, "Received intent: " + intent);
        final String intentAction = intent.getAction();
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intentAction)) {
            sendCommandToMusicPlaybackService(context, MusicPlaybackService.CMDPAUSE);
        } else if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null) {
                return;
            }

            final int keycode = event.getKeyCode();
            final int action = event.getAction();
            final long eventtime = event.getEventTime();

            String command = null;
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    command = MusicPlaybackService.CMDSTOP;
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = MusicPlaybackService.CMDTOGGLEPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = MusicPlaybackService.CMDNEXT;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = MusicPlaybackService.CMDPREVIOUS;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    command = MusicPlaybackService.CMDPAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    command = MusicPlaybackService.CMDPLAY;
                    break;
            }
            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (mDown) {
                        if (MusicPlaybackService.CMDTOGGLEPAUSE.equals(command)
                                || MusicPlaybackService.CMDPLAY.equals(command)) {
                            if (mLastClickTime != 0
                                    && eventtime - mLastClickTime > LONG_PRESS_DELAY) {
                                acquireWakeLockAndSendMessage(context,
                                        mbiReceiverHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context), 0);
                            }
                        }
                    } else if (event.getRepeatCount() == 0) {
                        // Only consider the first event in a sequence, not the repeat events,
                        // so that we don't trigger in cases where the first event went to
                        // a different app (e.g. when the user ends a phone call by
                        // long pressing the headset button)

                        // The service may or may not be running, but we need to send it
                        // a command.
                        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK) {
                            if (eventtime - mLastClickTime >= DOUBLE_CLICK) {
                                mClickCounter = 0;
                            }

                            mClickCounter++;
                            if (DEBUG) Log.v(TAG, "Got headset click, count = " + mClickCounter);
                            mbiReceiverHandler.removeMessages(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT);

                            Message msg = mbiReceiverHandler.obtainMessage(
                                    MSG_HEADSET_DOUBLE_CLICK_TIMEOUT, mClickCounter, 0, context);

                            long delay = mClickCounter < 3 ? DOUBLE_CLICK : 0;
                            if (mClickCounter >= 3) {
                                mClickCounter = 0;
                            }
                            mLastClickTime = eventtime;
                            acquireWakeLockAndSendMessage(context, msg, delay);
                        } else {
                            sendCommandToMusicPlaybackService(context, command);
                        }
                        mLaunched = false;
                        mDown = true;
                    }
                } else {
                    mbiReceiverHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                    mDown = false;
                }
                if (isOrderedBroadcast()) {
                    abortBroadcast();
                }
                releaseWakeLockIfHandlerIdle();
            }
        }
    }

    private static void sendCommandToMusicPlaybackService(Context context, String command) {
        final Intent i = new Intent(context, MusicPlaybackService.class);
        i.setAction(MusicPlaybackService.SERVICECMD);
        i.putExtra(MusicPlaybackService.CMDNAME, command);
        i.putExtra(MusicPlaybackService.FROM_MEDIA_BUTTON, true);
        Engine.startForegroundService(context, i);
    }

    private static void acquireWakeLockAndSendMessage(Context context, Message msg, long delay) {
        if (mWakeLock == null) {
            Context appContext = context.getApplicationContext();
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FW:headset button");
                if (mWakeLock != null) {
                    mWakeLock.setReferenceCounted(false);
                }
            }
        }

        if (mWakeLock != null) {
            if (DEBUG) Log.v(TAG, "Acquiring wake lock and sending " + msg.what);
            // Make sure we don't indefinitely hold the wake lock under any circumstances
            mWakeLock.acquire(10000);
            mbiReceiverHandler.sendMessageDelayed(msg, delay);
        }
    }

    private static void releaseWakeLockIfHandlerIdle() {
        if (mbiReceiverHandler.hasMessages(MSG_LONGPRESS_TIMEOUT)
                || mbiReceiverHandler.hasMessages(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT)) {
            if (DEBUG) Log.v(TAG, "Handler still has messages pending, not releasing wake lock");
            return;
        }

        if (mWakeLock != null) {
            if (DEBUG) Log.v(TAG, "Releasing wake lock");
            mWakeLock.release();
            mWakeLock = null;
        }
    }
}
