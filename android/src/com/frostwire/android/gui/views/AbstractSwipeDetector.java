/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * 
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public abstract class AbstractSwipeDetector extends SimpleOnGestureListener implements OnTouchListener {

    @SuppressWarnings("unused")
    private static final String TAG = "FW.AbstractSwipeDetector";
    
    private final int MIN_DISTANCE = 100;
    private float downX;
    private float downY;
    private float upX;
    private float upY;
    private int  lastDownPointerId;
    private long lastActionDown;
    
    public void onRightToLeftSwipe(){
    }

    public void onLeftToRightSwipe(){
    }

    public void onTopToBottomSwipe(){
    }

    public void onBottomToTopSwipe(){
    }
    
    public boolean onMultiTouchEvent(View v, MotionEvent event) {
        return true;
    }
    
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        
        if (event.getPointerCount() == 2 &&
            event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            return onMultiTouchEvent(v, event);
        }
        
        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN: {
                downX = event.getX();
                downY = event.getY();
                lastDownPointerId = event.getPointerId(0);
                long now = System.currentTimeMillis();
                if (now - lastActionDown <= 500) {
                    return onDoubleTap(event);
                }
                lastActionDown = System.currentTimeMillis();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                upX = event.getX();
                upY = event.getY();
                
                if (event.getPointerId(0) != lastDownPointerId) {
                    return false;
                }

                float deltaX = downX - upX;
                float deltaY = downY - upY;

                // horizontal
                if(Math.abs(deltaX) > MIN_DISTANCE){
                    if(deltaX < 0) { 
                        onLeftToRightSwipe(); 
                        return true; 
                    }
                    
                    if(deltaX > 0) { 
                        onRightToLeftSwipe(); 
                        return true; 
                    }
                }
                else {
                        return false;
                }

                // vertical
                if(Math.abs(deltaY) > MIN_DISTANCE) {
                    if(deltaY < 0) { 
                        onTopToBottomSwipe(); 
                        return true; 
                    }
                    if(deltaY > 0) { 
                        onBottomToTopSwipe(); 
                        return true; 
                    }
                }
                else {
                        return false;
                }

                return true;
            }
        }
        return false;
    }
}
