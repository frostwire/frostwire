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

package com.frostwire.gui.searchfield;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuddySupport {
    private static final String OUTER_MARGIN = "outerMargin";

    static void addLeft(Component c, JTextField textField) {
        add(c, Position.LEFT, textField);
    }

    static void addRight(Component c, JTextField textField) {
        add(c, Position.RIGHT, textField);
    }

    private static void add(Component c, Position pos, JTextField textField) {
        TextUIWrapper.getDefaultWrapper().install(textField, true);
        List<Component> leftBuddies = buddies(Position.LEFT, textField);
        List<Component> rightBuddies = buddies(Position.RIGHT, textField);
        // ensure buddies are added
        setLeft(textField, leftBuddies);
        setRight(textField, rightBuddies);
        // check if component is already here
        if (leftBuddies.contains(c) || rightBuddies.contains(c)) {
            throw new IllegalStateException("Component already added.");
        }
        if (Position.LEFT == pos) {
            leftBuddies.add(c);
        } else {
            rightBuddies.add(0, c);
        }
        addToComponentHierarchy(c, pos, textField);
    }

    private static void setRight(JTextField textField, List<Component> rightBuddies) {
        set(rightBuddies, Position.RIGHT, textField);
    }

    private static void setLeft(JTextField textField, List<Component> leftBuddies) {
        set(leftBuddies, Position.LEFT, textField);
    }

    private static void set(List<Component> buddies, Position pos, JTextField textField) {
        textField.putClientProperty(pos, buddies);
    }

    private static void addToComponentHierarchy(Component c, Position pos, JTextField textField) {
        try {
            textField.add(c, pos.toString());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static List<Component> getLeft(JTextField textField) {
        return getBuddies(Position.LEFT, textField);
    }

    static List<Component> getRight(JTextField textField) {
        return getBuddies(Position.RIGHT, textField);
    }

    private static List<Component> getBuddies(Position pos, JTextField textField) {
        return Collections.unmodifiableList(buddies(pos, textField));
    }

    @SuppressWarnings("unchecked")
    private static List<Component> buddies(Position pos, JTextField textField) {
        List<Component> buddies = (List<Component>) textField.getClientProperty(pos);
        if (buddies != null) {
            return buddies;
        }
        return new ArrayList<>();
    }

    public static void removeAll(JTextField textField) {
        List<Component> left = buddies(Position.LEFT, textField);
        for (Component c : left) {
            textField.remove(c);
        }
        left.clear();
        List<Component> right = buddies(Position.RIGHT, textField);
        for (Component c : right) {
            textField.remove(c);
        }
        right.clear();
    }

    static void setOuterMargin(JTextField buddyField, Insets margin) {
        buddyField.putClientProperty(OUTER_MARGIN, margin);
    }

    static Insets getOuterMargin(JTextField buddyField) {
        return (Insets) buddyField.getClientProperty(OUTER_MARGIN);
    }

    static void ensureBuddiesAreInComponentHierarchy(JTextField textField) {
        for (Component c : BuddySupport.getLeft(textField)) {
            addToComponentHierarchy(c, Position.LEFT, textField);
        }
        for (Component c : BuddySupport.getRight(textField)) {
            addToComponentHierarchy(c, Position.RIGHT, textField);
        }
    }

    /**
     * Create a gap to insert between to buddies.
     */
    static Component createGap(int width) {
        return Box.createHorizontalStrut(width);
    }

    public enum Position {
        LEFT, RIGHT
    }
}
