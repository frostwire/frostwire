/*
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogs;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class SoftwareUpdaterDialogUpdateMessagesTest {

    @Test
    public void newInstanceStoresUpdateMessagesAsBundle() {
        Map<String, String> updateMessages = new HashMap<>();
        updateMessages.put("en", "Update available");
        updateMessages.put("es", "Actualizacion disponible");

        SoftwareUpdaterDialog dialog = SoftwareUpdaterDialog.newInstance(
                "https://example.com/frostwire.apk",
                updateMessages,
                Arrays.asList("Fixed update dialog", "Improved downloads"));

        Bundle args = dialog.getArguments();
        Bundle storedMessages = args.getBundle("updateMessages");

        assertNotNull(storedMessages);
        assertEquals(updateMessages, SoftwareUpdaterDialog.fromBundle(storedMessages));
        assertEquals(
                Arrays.asList("Fixed update dialog", "Improved downloads"),
                args.getStringArrayList("changelog"));
    }

    @Test
    public void updateMessagesBundleRoundTripsNullValues() {
        Map<String, String> updateMessages = new HashMap<>();
        updateMessages.put("en", "Update available");
        updateMessages.put("pt", null);

        Bundle bundle = SoftwareUpdaterDialog.toBundle(updateMessages);

        assertEquals(updateMessages, SoftwareUpdaterDialog.fromBundle(bundle));
    }
}
