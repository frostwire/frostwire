/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchInputViewTest {

  private Context context;
  private SearchInputView view;
  private final List<String> events = new ArrayList<>();

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    ViewTestSupport.configuration().setLastMediaTypeFilter(Constants.FILE_TYPE_VIDEOS);
    context = ViewTestSupport.themedContext();
    view = new SearchInputView(context, null);
    view.onFinishInflate();
    view.setOnSearchListener(
        new SearchInputView.OnSearchListener() {
          @Override
          public void onSearch(View v, String query, int mediaTypeId) {
            events.add("search:" + query + ":" + mediaTypeId);
          }

          @Override
          public void onMediaTypeSelected(View v, int mediaTypeId) {
            events.add("type:" + mediaTypeId);
          }

          @Override
          public void onClear(View v) {
            events.add("clear");
          }
        });
  }

  private TabLayout tabs() {
    return view.findViewById(R.id.view_search_input_tab_layout_file_type);
  }

  private ClearableEditTextView input() {
    return view.findViewById(R.id.view_search_input_text_input);
  }

  @Test
  public void textHintAndEmptinessAreForwardedToTheInput() {
    assertTrue(view.isEmpty());
    view.setText("frostwire");
    assertFalse(view.isEmpty());
    assertEquals("frostwire", view.getText());
    view.setHint("custom hint");
    assertEquals("custom hint", input().getHint());
    view.setShowKeyboardOnPaste(true);
    assertTrue(input().isShowKeyboardOnPaste());
  }

  @Test
  public void hintOffersSearchOrUrl() {
    String expected =
        context.getString(R.string.search_label) + "  " + context.getString(R.string.or_enter_url);
    assertEquals(expected, input().getHint());
  }

  @Test
  public void textInputCanBeHiddenAndShown() {
    view.hideTextInput();
    assertEquals(View.GONE, input().getVisibility());
    view.showTextInput();
    assertEquals(View.VISIBLE, input().getVisibility());
  }

  @Test
  public void enterKeyStartsASearchWithTheLastMediaTypeAndSelectsItsTab() {
    view.setText("  ubuntu  ");
    boolean handled =
        input()
            .getAutoCompleteTextView()
            .dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    ShadowLooper.idleMainLooper();
    assertTrue(handled);
    assertTrue(events.contains("search:ubuntu:" + Constants.FILE_TYPE_VIDEOS));
    assertEquals(Constants.FILE_TYPE_VIDEOS, view.getSelectedFileType());
    // the videos tab is the second one
    assertEquals(1, tabs().getSelectedTabPosition());
  }

  @Test
  public void enterWithoutTextDoesNotSearch() {
    input()
        .getAutoCompleteTextView()
        .dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    ShadowLooper.idleMainLooper();
    for (String e : events) {
      assertFalse(e, e.startsWith("search:"));
    }
  }

  @Test
  public void otherKeysAreLeftAlone() {
    boolean handled =
        input()
            .getAutoCompleteTextView()
            .dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A));
    assertFalse(handled);
    assertTrue(events.isEmpty());
  }

  @Test
  public void selectingATabReportsItsFileType() {
    tabs().getTabAt(3).select();
    assertTrue(events.contains("type:" + Constants.FILE_TYPE_APPLICATIONS));
    events.clear();
    tabs().getTabAt(3).select();
    // selecting the already selected tab is a reselect, which is reported too
    assertTrue(events.contains("type:" + Constants.FILE_TYPE_APPLICATIONS));
  }

  @Test
  public void selectTabByMediaTypeMovesTheTabAndRemembersTheType() {
    view.selectTabByMediaType(Constants.FILE_TYPE_DOCUMENTS);
    assertEquals(4, tabs().getSelectedTabPosition());
    assertEquals(Constants.FILE_TYPE_DOCUMENTS, view.getSelectedFileType());
    // a type without a tab only records the selection
    view.selectTabByMediaType(Constants.FILE_TYPE_RINGTONES);
    assertEquals(4, tabs().getSelectedTabPosition());
    assertEquals(Constants.FILE_TYPE_RINGTONES, view.getSelectedFileType());
  }

  @Test
  public void switchingTabsWrapsAroundBothEnds() {
    view.selectTabByMediaType(Constants.FILE_TYPE_AUDIO);
    view.switchToThe(false);
    assertEquals(5, tabs().getSelectedTabPosition());
    assertEquals(Constants.FILE_TYPE_TORRENTS, view.getSelectedFileType());
    view.switchToThe(true);
    assertEquals(0, tabs().getSelectedTabPosition());
    assertEquals(Constants.FILE_TYPE_AUDIO, view.getSelectedFileType());
    view.switchToThe(true);
    assertEquals(1, tabs().getSelectedTabPosition());
  }

  @Test
  public void clearingTheInputNotifiesTheListenerAndShowsTheInput() {
    view.hideTextInput();
    view.setText("x");
    input().findViewById(R.id.view_clearable_edit_text_button_clear).performClick();
    assertTrue(events.contains("clear"));
    assertEquals(View.VISIBLE, input().getVisibility());
  }

  @Test
  public void fileTypeCountersShowTheCountAndSaturateAtAThousand() {
    view.setFileTypeCountersVisible(true);
    assertEquals(View.VISIBLE, tabs().getVisibility());
    view.updateFileTypeCounter(Constants.FILE_TYPE_AUDIO, 42);
    view.updateFileTypeCounter(Constants.FILE_TYPE_VIDEOS, 1500);
    ShadowLooper.idleMainLooper();
    assertEquals("42", tabs().getTabAt(0).getText().toString());
    assertEquals("+1k", tabs().getTabAt(1).getText().toString());
    view.setFileTypeCountersVisible(false);
    assertEquals(View.GONE, tabs().getVisibility());
  }

  @Test
  public void focusAutoPastesALinkFromTheClipboardAndLosingFocusHidesTheKeyboard() {
    ClipboardManager clipboard =
        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("l", "https://example.com/a.torrent"));
    View.OnFocusChangeListener listener = input().getOnFocusChangeListener();
    listener.onFocusChange(input(), true);
    assertEquals("https://example.com/a.torrent", view.getText());
    listener.onFocusChange(input(), false);
    assertEquals("https://example.com/a.torrent", view.getText());
  }

  @Test
  public void pickingASuggestionStartsTheSearch() {
    view.setText("matrix");
    input().getOnItemClickListener().onItemClick(null, input(), 0, 0);
    ShadowLooper.idleMainLooper();
    assertTrue(events.contains("search:matrix:" + Constants.FILE_TYPE_VIDEOS));
  }
}
