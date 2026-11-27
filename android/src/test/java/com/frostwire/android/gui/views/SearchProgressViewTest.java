/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchProgressViewTest {

  private Context context;
  private SearchProgressView view;

  @Before
  public void setUp() {
    context = ViewTestSupport.themedContext();
    view = new SearchProgressView(context, null);
    view.onFinishInflate();
  }

  private <T extends View> T find(int id) {
    return view.findViewById(id);
  }

  @Test
  public void startsIdleWithEverythingHidden() {
    assertEquals(View.GONE, find(R.id.view_search_progress_progressbar).getVisibility());
    assertEquals(View.GONE, find(R.id.view_search_progress_text_searching).getVisibility());
    assertEquals(View.GONE, find(R.id.view_search_progress_text_icebridge).getVisibility());
    assertTrue(
        (((TextView) find(R.id.view_search_progress_try_frostwire_plus)).getPaintFlags()
                & Paint.UNDERLINE_TEXT_FLAG)
            != 0);
  }

  @Test
  public void enablingProgressShowsTheSpinnerAndALabelledCancelButton() {
    view.setProgressEnabled(true);
    assertEquals(View.VISIBLE, find(R.id.view_search_progress_progressbar).getVisibility());
    assertEquals(View.VISIBLE, find(R.id.view_search_progress_text_searching).getVisibility());
    assertEquals(
        View.GONE, find(R.id.view_search_progress_text_no_results_feedback).getVisibility());
    assertEquals(
        context.getString(android.R.string.cancel),
        ((Button) find(R.id.view_search_progress_button_cancel)).getText().toString());
    // enabling again, as a retry does, reapplies the same state
    view.setProgressEnabled(true);
    assertEquals(View.VISIBLE, find(R.id.view_search_progress_progressbar).getVisibility());
  }

  @Test
  public void stoppingProgressOffersARetryAndTheNoResultsLabel() {
    view.setProgressEnabled(true);
    view.setIceBridgeHint("peers");
    view.setProgressEnabled(false);
    assertEquals(View.GONE, find(R.id.view_search_progress_progressbar).getVisibility());
    assertEquals(View.GONE, find(R.id.view_search_progress_text_searching).getVisibility());
    assertEquals(
        View.VISIBLE, find(R.id.view_search_progress_text_no_results_feedback).getVisibility());
    assertEquals(View.GONE, find(R.id.view_search_progress_text_icebridge).getVisibility());
    assertEquals(
        context.getString(R.string.retry),
        ((Button) find(R.id.view_search_progress_button_cancel)).getText().toString());
    // stopping twice just refreshes the labels
    view.setProgressEnabled(false);
    assertEquals(
        View.VISIBLE, find(R.id.view_search_progress_text_no_results_feedback).getVisibility());
  }

  @Test
  public void retrySuggestionsAppearOnlyAfterARealQueryWithDataUp() {
    view.setProgressEnabled(true);
    view.setProgressEnabled(false);
    assertEquals(
        View.GONE, find(R.id.view_search_progress_try_other_keywords_or_filters).getVisibility());

    view.setCurrentQueryReporter(() -> "ubuntu");
    view.setProgressEnabled(true);
    view.setProgressEnabled(false);
    assertEquals(
        View.VISIBLE,
        find(R.id.view_search_progress_try_other_keywords_or_filters).getVisibility());
    assertEquals(
        Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? View.VISIBLE : View.GONE,
        find(R.id.view_search_progress_try_frostwire_plus).getVisibility());
    assertEquals(View.GONE, find(R.id.view_search_progress_no_data_connection).getVisibility());

    view.setProgressEnabled(true);
    assertEquals(
        View.GONE, find(R.id.view_search_progress_try_other_keywords_or_filters).getVisibility());
  }

  @Test
  public void withoutADataConnectionTheViewSaysSo() {
    view.setCurrentQueryReporter(() -> "ubuntu");
    view.setDataUp(false);
    view.setProgressEnabled(true);
    view.setProgressEnabled(false);
    assertEquals(View.VISIBLE, find(R.id.view_search_progress_no_data_connection).getVisibility());
    assertEquals(
        View.GONE, find(R.id.view_search_progress_try_other_keywords_or_filters).getVisibility());
    view.setDataUp(true);
    view.setProgressEnabled(true);
    assertEquals(View.GONE, find(R.id.view_search_progress_no_data_connection).getVisibility());
  }

  @Test
  public void queryReporterWithoutAQueryHidesTheSuggestions() {
    view.setCurrentQueryReporter(() -> null);
    view.setProgressEnabled(true);
    view.setProgressEnabled(false);
    assertEquals(
        View.GONE, find(R.id.view_search_progress_try_other_keywords_or_filters).getVisibility());
  }

  @Test
  public void icebridgeHintShowsTextAndHidesWhenEmpty() {
    TextView hint = find(R.id.view_search_progress_text_icebridge);
    view.setIceBridgeHint("3 peers");
    assertEquals(View.VISIBLE, hint.getVisibility());
    assertEquals("3 peers", hint.getText().toString());
    view.setIceBridgeHint("");
    assertEquals(View.GONE, hint.getVisibility());
    view.setIceBridgeHint("again");
    view.setIceBridgeHint(null);
    assertEquals(View.GONE, hint.getVisibility());
  }

  @Test
  public void cancelAndTouchListenersAreForwarded() {
    List<String> events = new ArrayList<>();
    view.setCancelOnClickListener(v -> events.add("cancel"));
    find(R.id.view_search_progress_button_cancel).performClick();
    assertEquals(List.of("cancel"), events);

    view.setOnTouchListener(
        (v, event) -> {
          events.add("touch");
          return true;
        });
    MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1, 1, 0);
    try {
      view.dispatchTouchEvent(down);
      find(R.id.view_search_progress_button_cancel).dispatchTouchEvent(down);
    } finally {
      down.recycle();
    }
    assertTrue(events.contains("touch"));
  }
}
