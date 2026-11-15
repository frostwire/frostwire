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

import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.bittorrent.PiecePriorities;
import com.frostwire.jlibtorrent.Priority;
import java.util.Locale;

/**
 * The tapped piece's panel in the Pieces tab: what desktop's piece card shows, and the piece's
 * download priority, which a piece not downloaded yet can change there (frostwire/frostwire#1566).
 * Main thread only.
 */
final class PieceDetailsPanel {
  /** Told when the priority of the shown piece is changed, on the main thread. */
  interface PriorityListener {
    void onPriorityChosen(PieceDetails details, Priority priority);
  }

  private static final Priority[] CHOICES = PiecePriorities.choices();

  private final View root;
  private final TextView title;
  private final TextView details;
  private final TextView priorityValue;
  private final Spinner priorityChoices;
  private PieceDetails shown;

  /** The choice the spinner was last set to or picked, a selection of it again is no change. */
  private int boundChoice = AdapterView.INVALID_POSITION;

  PieceDetailsPanel(View root, PriorityListener priorityListener, Runnable onClose) {
    this.root = root;
    Resources resources = root.getResources();
    title = root.findViewById(R.id.view_transfer_detail_piece_panel_title);
    details = root.findViewById(R.id.view_transfer_detail_piece_panel_details);
    priorityValue = root.findViewById(R.id.view_transfer_detail_piece_panel_priority);
    priorityChoices = root.findViewById(R.id.view_transfer_detail_piece_panel_priority_choices);
    TextView priorityLabel =
        root.findViewById(R.id.view_transfer_detail_piece_panel_priority_label);
    priorityLabel.setText(resources.getString(R.string.transfer_detail_piece_priority) + ":");
    root.findViewById(R.id.view_transfer_detail_piece_panel_close)
        .setOnClickListener(v -> onClose.run());
    String[] labels = new String[CHOICES.length];
    for (int i = 0; i < CHOICES.length; i++) {
      labels[i] = priorityLabel(resources, CHOICES[i]);
    }
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(root.getContext(), android.R.layout.simple_spinner_item, labels);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    priorityChoices.setAdapter(adapter);
    priorityChoices.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            // setSelection when binding a piece calls back too, with the piece's own priority
            if (shown == null || shown.downloaded() || position == boundChoice) {
              return;
            }
            boundChoice = position;
            priorityListener.onPriorityChosen(shown, CHOICES[position]);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
  }

  /** Shows {@code pieceDetails}, or refreshes them while the same piece is shown. */
  void show(PieceDetails pieceDetails) {
    shown = pieceDetails;
    Resources resources = root.getResources();
    title.setText(
        resources.getString(
            R.string.transfer_detail_piece_title,
            String.format(Locale.getDefault(), "%,d", pieceDetails.piece),
            String.format(Locale.getDefault(), "%,d", pieceDetails.numPieces)));
    details.setText(describe(resources, pieceDetails));
    int choice = PiecePriorities.choiceIndex(pieceDetails.priority);
    // a downloaded piece's priority no longer matters: it's shown, not offered
    if (pieceDetails.downloaded() || choice < 0) {
      priorityChoices.setVisibility(View.GONE);
      priorityValue.setVisibility(View.VISIBLE);
      priorityValue.setText(describePriority(resources, pieceDetails.priority));
    } else {
      priorityValue.setVisibility(View.GONE);
      priorityChoices.setVisibility(View.VISIBLE);
      if (choice != boundChoice || priorityChoices.getSelectedItemPosition() != choice) {
        boundChoice = choice;
        priorityChoices.setSelection(choice, false);
      }
    }
    root.setVisibility(View.VISIBLE);
  }

  void hide() {
    shown = null;
    boundChoice = AdapterView.INVALID_POSITION;
    root.setVisibility(View.GONE);
  }

  boolean isShowing() {
    return root.getVisibility() == View.VISIBLE && shown != null;
  }

  /** The piece shown, -1 for none. */
  int shownPiece() {
    return shown != null ? shown.piece : -1;
  }

  View getView() {
    return root;
  }

  /** The rows of desktop's piece card after the piece number, one "Label: value" per line. */
  static CharSequence describe(Resources resources, PieceDetails d) {
    SpannableStringBuilder text = new SpannableStringBuilder();
    row(
        text,
        resources.getString(R.string.transfer_detail_piece_state),
        describeState(resources, d));
    row(
        text,
        resources.getString(R.string.size),
        resources.getString(
            R.string.transfer_detail_piece_size_bytes,
            UIUtils.getBytesInHuman(d.size),
            String.format(Locale.getDefault(), "%,d", d.size)));
    row(
        text,
        resources.getString(R.string.transfer_detail_piece_byte_range),
        String.format(Locale.getDefault(), "%,d - %,d", d.firstByte, d.firstByte + d.size - 1));
    if (d.peers >= 0) {
      row(
          text,
          resources.getString(R.string.transfer_detail_piece_peers),
          String.format(Locale.getDefault(), "%,d", d.peers));
    }
    if (d.sha1 != null) {
      row(text, "SHA-1", d.sha1);
    }
    if (!d.files.isEmpty()) {
      StringBuilder files = new StringBuilder();
      for (PieceDetails.FilePart file : d.files) {
        files.append('\n').append(file.name).append(" (");
        files.append(UIUtils.getBytesInHuman(file.size)).append(')');
      }
      if (d.moreFiles > 0) {
        files
            .append('\n')
            .append(resources.getString(R.string.transfer_detail_piece_more_files, d.moreFiles));
      }
      boolean several = d.files.size() + d.moreFiles > 1;
      row(
          text,
          resources.getString(
              several ? R.string.transfer_detail_piece_files : R.string.transfer_detail_piece_file),
          files);
    }
    return text;
  }

  private static void row(SpannableStringBuilder text, String label, CharSequence value) {
    if (text.length() > 0) {
      text.append('\n');
    }
    int start = text.length();
    text.append(label).append(':');
    text.setSpan(
        new StyleSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    // a value of several lines (the files) starts on the line below its label
    if (value.length() == 0 || value.charAt(0) != '\n') {
      text.append(' ');
    }
    text.append(value);
  }

  static String describeState(Resources resources, PieceDetails d) {
    switch (d.state) {
      case DOWNLOADED:
        return resources.getString(R.string.transfer_detail_piece_state_downloaded);
      case DOWNLOADING:
        return resources.getString(
            R.string.transfer_detail_piece_state_downloading,
            d.finishedBlocks,
            d.blocks,
            d.requestedBlocks);
      case NOT_DOWNLOADING:
        return resources.getString(R.string.transfer_detail_piece_state_not_downloading);
      default:
        return resources.getString(R.string.transfer_detail_piece_state_missing);
    }
  }

  /** What a priority is called, priorities between the offered ones go to the nearest level. */
  static String priorityLabel(Resources resources, Priority priority) {
    switch (PiecePriorities.label(priority)) {
      case DONT_DOWNLOAD:
        return resources.getString(R.string.transfer_detail_piece_priority_dont_download);
      case LOW:
        return resources.getString(R.string.transfer_detail_piece_priority_low);
      case NORMAL:
        return resources.getString(R.string.transfer_detail_piece_priority_normal);
      case HIGH:
        return resources.getString(R.string.transfer_detail_piece_priority_high);
      default:
        return resources.getString(R.string.unknown);
    }
  }

  /** Like {@link #priorityLabel} with the libtorrent level for the in-between ones: "High (5)". */
  static String describePriority(Resources resources, Priority priority) {
    if (priority == null || PiecePriorities.isChoice(priority)) {
      return priorityLabel(resources, priority);
    }
    return priorityLabel(resources, priority) + " (" + priority.swig() + ")";
  }
}
