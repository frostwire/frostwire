/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 
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

package com.limegroup.gnutella.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LongPressMouseAdapter extends MouseAdapter {

    private final long MILLISECONDS_FOR_LONGPRESS = 1500;
    private final LongPressable component;
    private boolean pressed;
    
    public LongPressMouseAdapter(final LongPressable component) {
        this.component = component;
    }
    
    @Override
    public void mousePressed(final MouseEvent e) {
        new Thread(new Runnable() {
            
            @Override
            public void run() {
                pressed = true;
                while (pressed) {
                    try {
                        Thread.sleep(MILLISECONDS_FOR_LONGPRESS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (pressed) {
                        component.onLongPress(e);
                    }
                }
            }
        }).start();
        
        super.mousePressed(e);
    }
    
    @Override
    public void mouseReleased(final MouseEvent e) {
        pressed = false;
    }
}