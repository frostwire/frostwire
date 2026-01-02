/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.devspark.appmsg;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.frostwire.android.util.SystemUtils;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Evgeny Shishkin
 * @author Angel Leon
 */
class MsgManager extends Handler {

    private static final int MESSAGE_DISPLAY = 0xc2007;
    private static final int MESSAGE_ADD_VIEW = 0xc20074dd;
    private static final int MESSAGE_REMOVE = 0xc2007de1;

    private static MsgManager mInstance;

    private final Queue<AppMsg> msgQueue;
    private Animation inAnimation, outAnimation;

    private MsgManager() {
        msgQueue = new LinkedList<>();
    }

    /**
     * @return The currently used instance of the {@link MsgManager}.
     */
    static synchronized MsgManager getInstance() {
        if (mInstance == null) {
            mInstance = new MsgManager();
        }
        return mInstance;
    }

    /**
     * Inserts a {@link AppMsg} to be displayed.
     */
    void add(AppMsg appMsg) {
        msgQueue.add(appMsg);

        if (appMsg.getActivity() != null) {
            if (inAnimation == null) {
                inAnimation = AnimationUtils.loadAnimation(appMsg.getActivity(),
                        android.R.anim.fade_in);
            }
            if (outAnimation == null) {
                outAnimation = AnimationUtils.loadAnimation(appMsg.getActivity(),
                        android.R.anim.fade_out);
            }
        }
        displayMsg();
    }

    /**
     * Removes all {@link AppMsg} from the queue.
     */
    void clearMsg(AppMsg appMsg) {
        msgQueue.remove(appMsg);
    }

    /**
     * Displays the next {@link AppMsg} within the queue.
     */
    private void displayMsg() {
        if (msgQueue.isEmpty()) {
            return;
        }
        if (!SystemUtils.isUIThread()) {
            SystemUtils.postToUIThread(this::displayMsg);
            return;
        }

        // First peek whether the AppMsg is being displayed.
        final AppMsg appMsg = msgQueue.peek();
        // If the activity is null we throw away the AppMsg.
        if (appMsg.getActivity() == null) {
            try {
                msgQueue.poll();
            } catch (Throwable t) {
                // Android's LinkedList implementation can throw NoSuchElementException
            }
        }
        final Message msg;
        if (!appMsg.isShowing()) {
            // Display the AppMsg
            msg = obtainMessage(MESSAGE_ADD_VIEW);
            msg.obj = appMsg;
            sendMessage(msg);
        } else {
            msg = obtainMessage(MESSAGE_DISPLAY);
            sendMessageDelayed(msg, appMsg.getDuration()
                    + inAnimation.getDuration() + outAnimation.getDuration());
        }
    }

    /**
     * Removes the {@link AppMsg}'s view after it's display duration.
     *
     * @param appMsg The {@link AppMsg} added to a {@link ViewGroup} and should be removed.s
     */
    private void removeMsg(final AppMsg appMsg) {
        if (!SystemUtils.isUIThread()) {
            SystemUtils.postToUIThread(() -> removeMsg(appMsg));
            return;
        }

        ViewGroup parent = ((ViewGroup) appMsg.getView().getParent());
        if (parent != null) {
            appMsg.getView().startAnimation(outAnimation);
            // Remove the AppMsg from the queue.
            try {
                msgQueue.poll();
            } catch (Throwable t) {
                // Android's LinkedList implementation can throw NoSuchElementException
            }
            // Remove the AppMsg from the view's parent.
            parent.removeView(appMsg.getView());

            Message msg = obtainMessage(MESSAGE_DISPLAY);
            sendMessage(msg);
        }
    }

    private void addMsgToView(AppMsg appMsg) {
        boolean onMainThread = Looper.myLooper() == Looper.getMainLooper();
        if (!onMainThread) {
            new Handler(Looper.getMainLooper()).post(() -> addMsgToView(appMsg));
            return;
        }
        View view = appMsg.getView();
        if (view.getParent() == null && appMsg.getActivity() != null) {
            appMsg.getActivity().addContentView(
                    view,
                    appMsg.getLayoutParams());
        }
        view.clearAnimation();
        view.startAnimation(inAnimation);
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
        final Message msg = obtainMessage(MESSAGE_REMOVE);
        msg.obj = appMsg;
        sendMessageDelayed(msg, appMsg.getDuration());
    }

    @Override
    public void handleMessage(Message msg) {
        final AppMsg appMsg;
        switch (msg.what) {
            case MESSAGE_DISPLAY:
                displayMsg();
                break;
            case MESSAGE_ADD_VIEW:
                appMsg = (AppMsg) msg.obj;
                addMsgToView(appMsg);
                break;
            case MESSAGE_REMOVE:
                appMsg = (AppMsg) msg.obj;
                removeMsg(appMsg);
                break;
            default:
                super.handleMessage(msg);
                break;
        }
    }
}