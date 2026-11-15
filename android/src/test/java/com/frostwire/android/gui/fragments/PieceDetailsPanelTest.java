/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.jlibtorrent.Priority;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Pieces tab's tapped piece: desktop's piece card, and the priority choices only for a piece
 * that isn't downloaded yet (frostwire/frostwire#1566).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PieceDetailsPanelTest {
  private Context context;
  private Resources resources;
  private View root;
  private PieceDetailsPanel panel;
  private final List<Priority> chosen = new ArrayList<>();
  private final List<Integer> chosenPieces = new ArrayList<>();
  private int closed;

  @Before
  public void setUp() {
    // in a window, like on screen: a spinner reports its selections through its window's queue
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    activity.setTheme(R.style.Theme_FrostWire);
    context = activity;
    resources = context.getResources();
    FrameLayout parent = new FrameLayout(context);
    activity.setContentView(parent);
    LayoutInflater.from(context).inflate(R.layout.view_transfer_detail_piece_panel, parent);
    root = parent.findViewById(R.id.view_transfer_detail_piece_panel);
    panel =
        new PieceDetailsPanel(
            root,
            (details, priority) -> {
              chosenPieces.add(details.piece);
              chosen.add(priority);
            },
            () -> closed++);
  }

  @Test
  public void aDownloadedPieceShowsItsPriorityButOffersNoChoice() {
    panel.show(details(7, PieceDetails.State.DOWNLOADED, Priority.FOUR));

    assertTrue(panel.isShowing());
    assertEquals(View.GONE, choices().getVisibility());
    TextView priority = root.findViewById(R.id.view_transfer_detail_piece_panel_priority);
    assertEquals(View.VISIBLE, priority.getVisibility());
    assertEquals("Normal", priority.getText().toString());
    assertTrue(chosen.isEmpty());
  }

  @Test
  public void aMissingPieceOffersEveryPriorityWithItsOwnSelected() {
    panel.show(details(7, PieceDetails.State.MISSING, Priority.FOUR));
    idle();

    Spinner choices = choices();
    assertEquals(View.VISIBLE, choices.getVisibility());
    assertEquals(
        View.GONE,
        root.findViewById(R.id.view_transfer_detail_piece_panel_priority).getVisibility());
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < choices.getAdapter().getCount(); i++) {
      labels.add(choices.getAdapter().getItem(i).toString());
    }
    assertEquals(List.of("Don't Download", "Low", "Normal", "High"), labels);
    assertEquals(2, choices.getSelectedItemPosition());
    assertTrue("showing a piece is no choice", chosen.isEmpty());

    choices.setSelection(0);
    idle();
    assertEquals(List.of(Priority.IGNORE), chosen);
    assertEquals(List.of(7), chosenPieces);

    // the refresh after the change shows the new priority, that's no new choice either
    panel.show(details(7, PieceDetails.State.MISSING, Priority.IGNORE));
    idle();
    assertEquals(0, choices.getSelectedItemPosition());
    assertEquals(List.of(Priority.IGNORE), chosen);

    choices.setSelection(3);
    idle();
    assertEquals(List.of(Priority.IGNORE, Priority.SEVEN), chosen);
  }

  @Test
  public void aPieceDownloadedWhileShownStopsOfferingChoices() {
    panel.show(details(3, PieceDetails.State.DOWNLOADING, Priority.SEVEN));
    idle();
    assertEquals(View.VISIBLE, choices().getVisibility());
    assertEquals(3, choices().getSelectedItemPosition());

    panel.show(details(3, PieceDetails.State.DOWNLOADED, Priority.SEVEN));
    idle();
    assertEquals(View.GONE, choices().getVisibility());
    assertTrue(chosen.isEmpty());
  }

  @Test
  public void anInBetweenPriorityIsOfferedAsTheNearestChoice() {
    panel.show(details(3, PieceDetails.State.MISSING, Priority.FIVE));
    idle();
    assertEquals(3, choices().getSelectedItemPosition());
    assertTrue(chosen.isEmpty());

    assertEquals("High (5)", PieceDetailsPanel.describePriority(resources, Priority.FIVE));
    assertEquals("Low (2)", PieceDetailsPanel.describePriority(resources, Priority.TWO));
    assertEquals("Low", PieceDetailsPanel.describePriority(resources, Priority.NORMAL));
    assertEquals("Don't Download", PieceDetailsPanel.describePriority(resources, Priority.IGNORE));
    assertEquals("Unknown", PieceDetailsPanel.describePriority(resources, null));
  }

  @Test
  public void showsWhatDesktopsPieceCardShows() {
    List<PieceDetails.FilePart> files = new ArrayList<>();
    files.add(new PieceDetails.FilePart("a.mp4", 1536));
    files.add(new PieceDetails.FilePart("b.srt", 2048));
    PieceDetails details =
        new PieceDetails(
            "hash",
            1234,
            5000,
            PieceDetails.State.DOWNLOADING,
            3,
            16,
            2,
            262_144,
            1234L * 262_144,
            5,
            Priority.FOUR,
            "0123456789abcdef0123456789abcdef01234567",
            files,
            2);
    panel.show(details);

    TextView title = root.findViewById(R.id.view_transfer_detail_piece_panel_title);
    assertEquals("Piece #1,234 of 5,000", title.getText().toString());
    String text = PieceDetailsPanel.describe(resources, details).toString();
    assertEquals(
        String.join(
            "\n",
            "State: Downloading, 3 of 16 blocks (2 requested)",
            "Size: 256.00 KB (262,144 bytes)",
            "Byte range: 323,485,696 - 323,747,839",
            "Peers with it: 5",
            "SHA-1: 0123456789abcdef0123456789abcdef01234567",
            "Files:",
            "a.mp4 (1.50 KB)",
            "b.srt (2.00 KB)",
            "and 2 more"),
        text);
    TextView shown = root.findViewById(R.id.view_transfer_detail_piece_panel_details);
    assertEquals(text, shown.getText().toString());
  }

  @Test
  public void statesAreNamedLikeOnDesktop() {
    assertEquals(
        "Downloaded and verified",
        PieceDetailsPanel.describeState(
            resources, details(1, PieceDetails.State.DOWNLOADED, Priority.FOUR)));
    assertEquals(
        "Not downloading",
        PieceDetailsPanel.describeState(
            resources, details(1, PieceDetails.State.NOT_DOWNLOADING, Priority.IGNORE)));
    assertEquals(
        "Missing",
        PieceDetailsPanel.describeState(
            resources, details(1, PieceDetails.State.MISSING, Priority.FOUR)));
    // one file, no SHA-1 (a v2-only torrent), no availability: those rows are left out
    String text =
        PieceDetailsPanel.describe(resources, details(1, PieceDetails.State.MISSING, Priority.FOUR))
            .toString();
    assertTrue(text, text.contains("File:\nmovie.mkv (16.00 KB)"));
    assertFalse(text, text.contains("SHA-1"));
    assertFalse(text, text.contains("Peers with it"));
  }

  @Test
  public void closingAndHiding() {
    panel.show(details(1, PieceDetails.State.MISSING, Priority.FOUR));
    root.findViewById(R.id.view_transfer_detail_piece_panel_close).performClick();
    assertEquals(1, closed);

    panel.hide();
    assertFalse(panel.isShowing());
    assertEquals(-1, panel.shownPiece());
    assertEquals(View.GONE, root.getVisibility());
  }

  @Test
  public void padFilesAreNoContent() {
    assertTrue(PieceDetails.isPadFile(".pad/16384"));
    assertTrue(PieceDetails.isPadFile("Movie/.pad/16384"));
    assertTrue(PieceDetails.isPadFile("Movie\\.pad\\16384"));
    assertFalse(PieceDetails.isPadFile("Movie/movie.pad.mkv"));
    assertFalse(PieceDetails.isPadFile(null));
  }

  private Spinner choices() {
    return root.findViewById(R.id.view_transfer_detail_piece_panel_priority_choices);
  }

  /**
   * Lays the panel out and runs what that posted, like a frame on screen: a spinner reports a
   * selection when it is laid out, so a selection made while binding a piece reaches the listener
   * here too.
   */
  private void idle() {
    View parent = (View) root.getParent();
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
    parent.layout(0, 0, 480, parent.getMeasuredHeight());
    shadowOf(Looper.getMainLooper()).idle();
  }

  static PieceDetails details(int piece, PieceDetails.State state, Priority priority) {
    List<PieceDetails.FilePart> files = new ArrayList<>();
    files.add(new PieceDetails.FilePart("movie.mkv", 16_384));
    return new PieceDetails(
        "hash", piece, 100, state, 0, 0, 0, 16_384, piece * 16_384L, -1, priority, null, files, 0);
  }
}
