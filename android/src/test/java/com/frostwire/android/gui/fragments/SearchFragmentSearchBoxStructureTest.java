package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guards the recovery path for distraction-free search: hiding the query box on
 * scroll-down must not leave the user unable to clear results or start a new search
 * (seen after short Internet Archive result sets).
 */
public class SearchFragmentSearchBoxStructureTest {

    @Test
    public void scrollDownDoesNotHideSearchBoxWhenListCannotScroll() throws Exception {
        String searchFragment =
                readProjectFile(
                        "src/main/java/com/frostwire/android/gui/fragments/SearchFragment.java");
        String onSearchScrollDown = blockStartingAt(searchFragment, "private void onSearchScrollDown()");
        assertTrue(
                "Must refuse to hide the query box when the list fits on one screen",
                onSearchScrollDown.contains("last >= total - 1"));
        assertTrue(
                "Must keep/show the query box when already at the top of the list",
                onSearchScrollDown.contains("first == 0"));
        assertTrue(
                "cancelSearch must restore the query box so clear is reachable",
                blockStartingAt(searchFragment, "private void cancelSearch()").contains("showSearchBox()"));
        assertTrue(
                "prepareUIForSearch must restore the query box before a new query",
                blockStartingAt(searchFragment, "private void prepareUIForSearch")
                        .contains("showSearchBox()"));
        assertTrue(
                "File-type tab selection must restore the query box (tabs remain after hide)",
                blockStartingAt(searchFragment, "public void onMediaTypeSelected")
                        .contains("showSearchBox()"));
    }

    @Test
    public void directionDetectorRevealsChromeAtListTop() throws Exception {
        String detector =
                readProjectFile(
                        "src/main/java/com/frostwire/android/gui/util/DirectionDetectorScrollListener.java");
        String onScroll = blockStartingAt(detector, "public void onScroll(AbsListView absListView");
        assertTrue(
                "Reaching firstVisibleItem==0 must call onScrollUp without vote threshold",
                onScroll.contains("firstVisibleItem == 0") && onScroll.contains("onScrollUp()"));
        assertTrue(
                "Must not hide chrome when visibleItemCount covers the full list",
                onScroll.contains("visibleItemCount >= totalItemCount"));
    }

    private static String blockStartingAt(String source, String marker) {
        int start = source.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf("\n    }\n", start);
        return end > start ? source.substring(start, end) : source.substring(start);
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));
        Path file = root.resolve(relativePath);
        if (!Files.exists(file)) {
            file = root.resolve("android").resolve(relativePath);
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
