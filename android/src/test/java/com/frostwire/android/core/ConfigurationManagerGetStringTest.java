/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import android.content.SharedPreferences;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ConfigurationManager.getStringFromPrefs() — the migration path for
 * keys stored as integers in pre-3.0.17 installs.
 *
 * When SharedPreferences.getString() throws ClassCastException (because an old
 * install stored the key as an int), the code must:
 *   1. Catch the exception silently
 *   2. Read the value as int
 *   3. Migrate the key to String in-place
 *   4. Return the String representation
 *
 * Pure JVM test — Mockito only, no Android framework, no Robolectric.
 */
public class ConfigurationManagerGetStringTest {

    /** Normal case: key stored as String — return it, no migration. */
    @Test
    public void getStringFromPrefs_normalStringValue_returnsIt() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(prefs.edit()).thenReturn(editor);
        when(prefs.getString(eq("key"), anyString())).thenReturn("stored");

        String result = ConfigurationManager.getStringFromPrefs(prefs, "key", "default");

        assertEquals("stored", result);
        verify(editor, never()).putString(anyString(), anyString());
    }

    /** Migration: ClassCastException from int stored under String key → returns "8080". */
    @Test
    public void getStringFromPrefs_intStoredUnderStringKey_migratesToString() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = chainedEditor();
        when(prefs.edit()).thenReturn(editor);
        when(prefs.getString(anyString(), anyString()))
                .thenThrow(new ClassCastException("type mismatch"));
        when(prefs.getInt(eq("portKey"), anyInt())).thenReturn(8080);

        String result = ConfigurationManager.getStringFromPrefs(prefs, "portKey", "0");

        assertEquals("8080", result);
        verify(editor).remove("portKey");
        verify(editor).putString("portKey", "8080");
        verify(editor).apply();
    }

    /** Migration: integer 0 → "0" (not null, not empty). */
    @Test
    public void getStringFromPrefs_zeroIntValue_returnsZeroString() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = chainedEditor();
        when(prefs.edit()).thenReturn(editor);
        when(prefs.getString(anyString(), anyString()))
                .thenThrow(new ClassCastException("type mismatch"));
        when(prefs.getInt(anyString(), anyInt())).thenReturn(0);

        String result = ConfigurationManager.getStringFromPrefs(prefs, "k", "default");

        assertEquals("0", result);
        verify(editor).putString("k", "0");
    }

    /** Migration: negative int value is stringified correctly. */
    @Test
    public void getStringFromPrefs_negativeIntValue_returnsNegativeString() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = chainedEditor();
        when(prefs.edit()).thenReturn(editor);
        when(prefs.getString(anyString(), anyString()))
                .thenThrow(new ClassCastException("type mismatch"));
        when(prefs.getInt(anyString(), anyInt())).thenReturn(-42);

        String result = ConfigurationManager.getStringFromPrefs(prefs, "k", "0");

        assertEquals("-42", result);
    }

    /** Null default is returned as-is in the normal (non-CCE) case. */
    @Test
    public void getStringFromPrefs_nullDefault_returnedAsIs() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.edit()).thenReturn(mock(SharedPreferences.Editor.class));
        when(prefs.getString(eq("k"), eq((String) null))).thenReturn(null);

        String result = ConfigurationManager.getStringFromPrefs(prefs, "k", null);

        assertNull(result);
    }

    // -----------------------------------------------------------------------

    private static SharedPreferences.Editor chainedEditor() {
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(editor.remove(anyString())).thenReturn(editor);
        when(editor.putString(anyString(), anyString())).thenReturn(editor);
        return editor;
    }
}
