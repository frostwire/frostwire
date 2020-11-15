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

package com.frostwire.gui.theme;

import com.limegroup.gnutella.gui.search.RangeSlider;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.synth.SynthConstants;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthSliderUI;
import java.awt.*;
import java.awt.event.MouseEvent;
// adapted from http://www.java2s.com/Code/Java/Swing-Components/ThumbSliderExample2.htm

/**
 * @author gubatron
 * @author aldenml
 */
public class SkinRangeSliderUI extends SynthSliderUI {
    private RangeSliderAdditionalUI additonalUi;
    private MouseInputAdapter thumbTrackListener;
    private final Rectangle zeroRect = new Rectangle();
    private transient boolean mousePressed;
    private transient boolean mouseOver;

    public SkinRangeSliderUI(JSlider c) {
        super(c);
    }

    public static ComponentUI createUI(JComponent c) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinRangeSliderUI((JSlider) c);
    }

    @Override
    public void installUI(JComponent c) {
        additonalUi = new RangeSliderAdditionalUI(this);
        additonalUi.installUI(c);
        thumbTrackListener = createThumbTrackListener((JSlider) c);
        super.installUI(c);
    }

    @Override
    public void uninstallUI(JComponent c) {
        super.uninstallUI(c);
        additonalUi.uninstallUI(c);
        additonalUi = null;
        thumbTrackListener = null;
    }

    public void scrollByBlock(int direction) {
    }

    public void scrollByUnit(int direction) {
    }

    public Rectangle getTrackRect() {
        return trackRect;
    }

    @Override
    protected TrackListener createTrackListener(JSlider slider) {
        return null;
    }

    protected ChangeListener createChangeListener(JSlider slider) {
        return additonalUi.changeHandler;
    }

    @Override
    protected void installListeners(JSlider slider) {
        slider.addMouseListener(thumbTrackListener);
        slider.addMouseMotionListener(thumbTrackListener);
        slider.addFocusListener(focusListener);
        slider.addComponentListener(componentListener);
        slider.addPropertyChangeListener(propertyChangeListener);
        slider.getModel().addChangeListener(changeListener);
        if (slider instanceof RangeSlider) {
            RangeSlider rangeSlider = (RangeSlider) slider;
            for (int i = 0; i < rangeSlider.getThumbNum(); i++) {
                rangeSlider.getModelAt(i).addChangeListener(changeListener);
            }
        }
    }

    @Override
    protected void uninstallListeners(JSlider slider) {
        slider.removeMouseListener(thumbTrackListener);
        slider.removeMouseMotionListener(thumbTrackListener);
        slider.removeFocusListener(focusListener);
        slider.removeComponentListener(componentListener);
        slider.removePropertyChangeListener(propertyChangeListener);
        slider.getModel().removeChangeListener(changeListener);
        if (slider instanceof RangeSlider) {
            RangeSlider rangeSlider = (RangeSlider) slider;
            for (int i = 0; i < rangeSlider.getThumbNum(); i++) {
                rangeSlider.getModelAt(i).removeChangeListener(changeListener);
            }
        }
    }

    @Override
    protected void calculateGeometry() {
        super.calculateGeometry();
        additonalUi.calculateThumbsSize();
        additonalUi.calculateThumbsLocation();
    }

    @Override
    protected void calculateThumbLocation() {
    }

    @Override
    protected void paint(SynthContext context, Graphics g) {
        calculateThumbRect();
        super.paint(context, g);
    }

    @Override
    protected void paintThumb(SynthContext context, Graphics g, Rectangle thumbBounds) {
        paintThumbs(context, g);
    }

    private void paintThumbs(SynthContext context, Graphics g) {
        Rectangle clip = g.getClipBounds();
        int thumbNum = additonalUi.getThumbNum();
        Rectangle[] thumbRects = additonalUi.getThumbRects();
        for (int i = thumbNum - 1; 0 <= i; i--) {
            if (clip.intersects(thumbRects[i])) {
                thumbRect = thumbRects[i];
                super.paintThumb(getThumbContext(context), g, thumbRect);
            }
        }
    }

    private void calculateThumbRect() {
        int thumbNum = additonalUi.getThumbNum();
        Rectangle[] thumbRects = additonalUi.getThumbRects();
        thumbRect = zeroRect;
        for (int i = thumbNum - 1; 0 <= i; i--) {
            Rectangle rect = thumbRects[i];
            SwingUtilities.computeUnion(rect.x, rect.y, rect.width, rect.height, thumbRect);
        }
    }

    private MouseInputAdapter createThumbTrackListener(JSlider slider) {
        return additonalUi.trackListener;
    }

    private SynthContext getThumbContext(SynthContext ctx) {
        SynthContext context = ctx;
        int state = ctx.getComponentState();
        if (mousePressed) {
            state |= SynthConstants.PRESSED;
        }
        // not working logic per thumb
        //        Point p = slider.getMousePosition();
        //        if (thumbRect != null && p != null && thumbRect.contains(p)) {
        //            state |= SynthConstants.MOUSE_OVER;
        //        }
        if (mouseOver) {
            state |= SynthConstants.MOUSE_OVER;
        }
        if (state != ctx.getComponentState()) {
            context = new SynthContext(ctx.getComponent(), ctx.getRegion(), ctx.getStyle(), state);
        }
        return context;
    }

    private void setMousePressed(boolean pressed) {
        if (mousePressed != pressed) {
            mousePressed = pressed;
            slider.repaint();
        }
    }

    private void setMouseOver(boolean pressed) {
        if (mouseOver != pressed) {
            mouseOver = pressed;
            slider.repaint();
        }
    }

    private static class RangeSliderAdditionalUI {
        private static final Rectangle unionRect = new Rectangle();
        private RangeSlider mSlider;
        private final SkinRangeSliderUI ui;
        private Rectangle[] thumbRects;
        private int thumbNum;
        private transient boolean isDragging;
        private ChangeHandler changeHandler;
        private TrackListener trackListener;

        public RangeSliderAdditionalUI(SkinRangeSliderUI ui) {
            this.ui = ui;
        }

        public void installUI(JComponent c) {
            mSlider = (RangeSlider) c;
            thumbNum = mSlider.getThumbNum();
            thumbRects = new Rectangle[thumbNum];
            for (int i = 0; i < thumbNum; i++) {
                thumbRects[i] = new Rectangle();
            }
            isDragging = false;
            trackListener = new RangeSliderAdditionalUI.TrackListener(mSlider);
            changeHandler = new ChangeHandler();
        }

        public void uninstallUI(JComponent c) {
            thumbRects = null;
            trackListener = null;
            changeHandler = null;
        }

        public int getThumbNum() {
            return thumbNum;
        }

        public Rectangle[] getThumbRects() {
            return thumbRects;
        }

        public void setThumbLocationAt(int x, int y, int index) {
            Rectangle rect = thumbRects[index];
            unionRect.setBounds(rect);
            rect.setLocation(x, y);
            SwingUtilities.computeUnion(rect.x, rect.y, rect.width, rect.height, unionRect);
            mSlider.repaint(unionRect.x, unionRect.y, unionRect.width, unionRect.height);
        }

        public Rectangle getTrackRect() {
            return ui.getTrackRect();
        }

        protected void calculateThumbsSize() {
            Dimension size = ui.getThumbSize();
            for (int i = 0; i < thumbNum; i++) {
                thumbRects[i].setSize(size.width, size.height);
            }
        }

        protected void calculateThumbsLocation() {
            for (int i = 0; i < thumbNum; i++) {
                if (mSlider.getSnapToTicks()) {
                    int tickSpacing = mSlider.getMinorTickSpacing();
                    if (tickSpacing == 0) {
                        tickSpacing = mSlider.getMajorTickSpacing();
                    }
                    if (tickSpacing != 0) {
                        int sliderValue = mSlider.getValueAt(i);
                        int snappedValue = sliderValue;
                        //int min = mSlider.getMinimumAt(i);
                        int min = mSlider.getMinimum();
                        if ((sliderValue - min) % tickSpacing != 0) {
                            float temp = (float) (sliderValue - min) / (float) tickSpacing;
                            int whichTick = Math.round(temp);
                            snappedValue = min + (whichTick * tickSpacing);
                            mSlider.setValueAt(snappedValue, i);
                        }
                    }
                }
                Rectangle trackRect = getTrackRect();
                if (mSlider.getOrientation() == JSlider.HORIZONTAL) {
                    int value = mSlider.getValueAt(i);
                    int valuePosition = ui.xPositionForValue(value);
                    thumbRects[i].x = valuePosition - (thumbRects[i].width / 2);
                    thumbRects[i].y = trackRect.y;
                } else {
                    int valuePosition = ui.yPositionForValue(mSlider.getValueAt(i)); // need
                    thumbRects[i].x = trackRect.x;
                    thumbRects[i].y = valuePosition - (thumbRects[i].height / 2);
                }
            }
        }

        public class ChangeHandler implements ChangeListener {
            public void stateChanged(ChangeEvent e) {
                if (!isDragging) {
                    calculateThumbsLocation();
                    mSlider.repaint();
                }
            }
        }

        public class TrackListener extends MouseInputAdapter {
            protected transient int offset;
            protected transient int currentMouseX, currentMouseY;
            protected Rectangle adjustingThumbRect = null;
            protected int adjustingThumbIndex;
            protected final RangeSlider slider;
            protected Rectangle trackRect;

            TrackListener(RangeSlider slider) {
                this.slider = slider;
            }

            public void mousePressed(MouseEvent e) {
                if (!slider.isEnabled()) {
                    return;
                }
                ui.setMousePressed(true);
                currentMouseX = e.getX();
                currentMouseY = e.getY();
                slider.requestFocus();
                for (int i = 0; i < thumbNum; i++) {
                    Rectangle rect = thumbRects[i];
                    if (rect.contains(currentMouseX, currentMouseY)) {
                        switch (slider.getOrientation()) {
                            case JSlider.VERTICAL:
                                offset = currentMouseY - rect.y;
                                break;
                            case JSlider.HORIZONTAL:
                                offset = currentMouseX - rect.x;
                                break;
                        }
                        isDragging = true;
                        slider.setValueIsAdjusting(true);
                        adjustingThumbRect = rect;
                        adjustingThumbIndex = i;
                        // since the slider have a bias towards the first thumb
                        // it is necessary to correct the actual selection once
                        // the second is behind the first one and both to the
                        // left
                        if (slider.getOrientation() == JSlider.HORIZONTAL &&
                                adjustingThumbIndex == 0 &&
                                thumbRects[1].equals(rect)) {
                            int halfThumbWidth = rect.width / 2;
                            int thumbLeft = ui.xPositionForValue(mSlider.getValueAt(1) - 1) - halfThumbWidth;
                            if (thumbLeft <= 1) {
                                adjustingThumbIndex = 1;
                            }
                        }
                        return;
                    }
                }
            }

            public void mouseDragged(MouseEvent e) {
                if (!slider.isEnabled() || !isDragging || !slider.getValueIsAdjusting() || adjustingThumbRect == null) {
                    return;
                }
                int thumbMiddle = 0;
                currentMouseX = e.getX();
                currentMouseY = e.getY();
                Rectangle rect = thumbRects[adjustingThumbIndex];
                trackRect = getTrackRect();
                switch (slider.getOrientation()) {
                    case JSlider.VERTICAL:
                        int halfThumbHeight = rect.height / 2;
                        int thumbTop = e.getY() - offset;
                        int trackTop = trackRect.y;
                        int trackBottom = trackRect.y + (trackRect.height - 1);
                        thumbTop = Math.max(thumbTop, trackTop - halfThumbHeight);
                        thumbTop = Math.min(thumbTop, trackBottom - halfThumbHeight);
                        // pending over-range control
                        setThumbLocationAt(rect.x, thumbTop, adjustingThumbIndex);
                        thumbMiddle = thumbTop + halfThumbHeight;
                        mSlider.setValueAt(ui.valueForYPosition(thumbMiddle), adjustingThumbIndex);
                        break;
                    case JSlider.HORIZONTAL:
                        int halfThumbWidth = rect.width / 2;
                        int thumbLeft = e.getX() - offset;
                        int trackLeft = trackRect.x;
                        int trackRight = trackRect.x + (trackRect.width - 1);
                        thumbLeft = Math.max(thumbLeft, trackLeft - halfThumbWidth);
                        thumbLeft = Math.min(thumbLeft, trackRight - halfThumbWidth);
                        if (adjustingThumbIndex == 0) {
                            thumbLeft = Math.min(thumbLeft, ui.xPositionForValue(mSlider.getValueAt(1) - 1) - halfThumbWidth);
                        }
                        if (adjustingThumbIndex == 1) {
                            thumbLeft = Math.max(ui.xPositionForValue(mSlider.getValueAt(0) + 1) - halfThumbWidth, thumbLeft);
                        }
                        setThumbLocationAt(thumbLeft, rect.y, adjustingThumbIndex);
                        thumbMiddle = thumbLeft + halfThumbWidth;
                        mSlider.setValueAt(ui.valueForXPosition(thumbMiddle), adjustingThumbIndex);
                        break;
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (!slider.isEnabled()) {
                    return;
                }
                offset = 0;
                isDragging = false;
                mSlider.setValueIsAdjusting(false);
                ui.setMousePressed(false);
                mSlider.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ui.setMouseOver(false);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                ui.setMouseOver(true);
            }
        }
    }
}
