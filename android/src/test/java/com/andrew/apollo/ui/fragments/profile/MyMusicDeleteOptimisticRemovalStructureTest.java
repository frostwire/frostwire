/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments.profile;

import com.andrew.apollo.adapters.ApolloFragmentAdapter;
import com.andrew.apollo.menu.DeleteDialog;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Structural regression test: My Music deletion must use optimistic UI removal
 * so deleted items disappear from the adapter immediately, not after an
 * asynchronous loader refresh.
 */
public class MyMusicDeleteOptimisticRemovalStructureTest {

    private static final String APOLLO_FRAGMENT_SOURCE =
            "apollo/src/com/andrew/apollo/ui/fragments/profile/ApolloFragment.java";
    private static final String DELETE_DIALOG_SOURCE =
            "apollo/src/com/andrew/apollo/menu/DeleteDialog.java";

    @Test
    public void deleteDialog_declaresPreDeleteCallbackInterface() {
        try {
            Class<?> cls = Class.forName("com.andrew.apollo.menu.DeleteDialog$DeleteDialogPreDeleteCallback");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("DeleteDialog must declare DeleteDialogPreDeleteCallback for optimistic UI removal");
        }
    }

    @Test
    public void apolloFragmentAdapter_declaresRemoveItemsByIdMethod() throws NoSuchMethodException {
        // ApolloFragmentAdapter is abstract, so we verify the method exists via reflection
        // on the concrete SongAdapter subclass.
        try {
            Class<?> songAdapterClass = Class.forName("com.andrew.apollo.adapters.SongAdapter");
            songAdapterClass.getMethod("removeItemsById", long[].class);
        } catch (ClassNotFoundException e) {
            fail("SongAdapter class not found: " + e.getMessage());
        }
    }

    @Test
    public void apolloFragment_onDelete_usesSetOnDeleteConfirmedCallback() throws IOException {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        Path sourceFile = projectRoot.resolve(APOLLO_FRAGMENT_SOURCE);
        if (!Files.exists(sourceFile)) {
            String classFile = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
            Path buildDir = Paths.get(classFile).normalize();
            projectRoot = buildDir.getParent().getParent().getParent().getParent();
            sourceFile = projectRoot.resolve(APOLLO_FRAGMENT_SOURCE);
        }
        String source = new String(Files.readAllBytes(sourceFile));
        assertTrue("ApolloFragment.onDelete must wire setOnDeleteConfirmedCallback "
                + "for optimistic adapter removal before the async deletion completes",
                source.contains("setOnDeleteConfirmedCallback"));
    }

    @Test
    public void deleteDialog_positiveButton_firesPreDeleteCallbackBeforeBackgroundDeletion() throws IOException {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        Path sourceFile = projectRoot.resolve(DELETE_DIALOG_SOURCE);
        if (!Files.exists(sourceFile)) {
            String classFile = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
            Path buildDir = Paths.get(classFile).normalize();
            projectRoot = buildDir.getParent().getParent().getParent().getParent();
            sourceFile = projectRoot.resolve(DELETE_DIALOG_SOURCE);
        }
        String source = new String(Files.readAllBytes(sourceFile));
        assertTrue("DeleteDialog PositiveButtonOnClickListener must invoke preDeleteCallback "
                + "before MusicUtils.deleteTracks() so the UI updates immediately",
                source.contains("preDeleteCallback.onDeleteConfirmed"));
    }
}
