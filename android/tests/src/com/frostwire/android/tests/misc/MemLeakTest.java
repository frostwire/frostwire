/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
 
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

package com.frostwire.android.tests.misc;

import java.util.Formatter;
import java.util.Locale;

import android.content.Intent;
import android.test.ActivityUnitTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.Suppress;
import android.widget.TextView;

import com.frostwire.android.tests.activities.LeakyActivity;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
@Suppress
public class MemLeakTest extends ActivityUnitTestCase<LeakyActivity> {

    public MemLeakTest() {
        super(LeakyActivity.class);
    }

    private StringBuilder formatBuilder;
    private Formatter formatter;
    private Runtime runtime;
    private final int KB = 1024;
    private TextView textView;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        startActivity(new Intent(getInstrumentation().getContext(), LeakyActivity.class), null, null);

        formatBuilder = new StringBuilder();
        formatter = new Formatter(formatBuilder, Locale.getDefault());
        runtime = Runtime.getRuntime();
        textView = getActivity().getTextView();
    }

    @LargeTest
    public void testStringForTimeLeak() {
        assertNotNull(textView);

        int jumps = 10000;

        for (int i = 1000000; i < Integer.MAX_VALUE; i += jumps) {
            String s = stringForTime(i);
            textView.setText(s);
            assertEquals(textView.getText(), s);

            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / KB;

            if (i % 1000000 == 0) {
                System.out.println("================================================================");
                System.out.println("Time: " + i);
                System.out.println("Text View Text: " + textView.getText());
                System.out.println("Used memory: " + usedMemory);
                System.out.println("Free memory: " + runtime.freeMemory() / KB);
                System.out.println("String Builder length: " + formatBuilder.length());
                System.out.println("================================================================\n");
            }
        }
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        formatBuilder.setLength(0);
        //formatBuilder.trimToSize();

        if (hours > 0) {
            return formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return formatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }
}