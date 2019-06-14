/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Erich Pleny (erichpleny)
 * Copyright (c) 2012, FrostWire(R). All rights reserved.
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

class AlphaAnimationThread extends Thread {
    private static final int TARGET_ALPHA = 90 * 255 / 100;
    private static final int ALPHA_STEP = 20;
    private final Object animationStart = new Object();
    private boolean disposed = false;
    private boolean isHiding;
    private boolean isShowing;
    private int currentAlpha = TARGET_ALPHA;
    private final AlphaTarget target;

    AlphaAnimationThread(AlphaTarget target) {
        this.target = target;
    }

    void setDisposed() {
        disposed = true;
    }

    void animateToTransparent() {
        if (isHiding) {
            return;
        }
        if (isShowing) {
            isShowing = false;
        }
        isHiding = true;
        synchronized (animationStart) {
            animationStart.notify();
        }
    }

    void animateToOpaque() {
        if (isShowing) {
            return;
        }
        if (isHiding) {
            isHiding = false;
        }
        isShowing = true;
        synchronized (animationStart) {
            animationStart.notify();
        }
    }

    private float currentAlphaValue() {
        return (currentAlpha * 1f) / TARGET_ALPHA;
    }

    public void run() {
        boolean stopAlphaThread = false;
        while (!stopAlphaThread && !disposed) {
            if (isHiding) {
                if (currentAlpha > 0) {
                    if (currentAlpha >= ALPHA_STEP) {
                        currentAlpha -= ALPHA_STEP;
                    } else {
                        currentAlpha = 0;
                    }
                    target.setAlpha(currentAlphaValue());
                } else {
                    isHiding = false;
                }
            }
            if (isShowing) {
                if (currentAlpha < TARGET_ALPHA) {
                    if (currentAlpha <= TARGET_ALPHA - ALPHA_STEP) {
                        currentAlpha += ALPHA_STEP;
                    } else {
                        currentAlpha = TARGET_ALPHA;
                    }
                    target.setAlpha(currentAlphaValue());
                } else {
                    isShowing = false;
                }
            }
            try {
                if (isShowing || isHiding) {
                    Thread.sleep(50);
                } else {
                    synchronized (animationStart) {
                        if (stopAlphaThread) {
                            return;
                        }
                        animationStart.wait();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
