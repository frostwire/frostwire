/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.adapters.menu;

import static com.frostwire.android.util.SystemUtils.HandlerThreadName.MISC;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Ref;
import java.lang.ref.WeakReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by gubatron on 12/18/14. Adapter in control of the List View shown when we're browsing
 * the files of one peer.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class CreateNewPlaylistMenuAction extends MenuAction {

  private final long[] fileDescriptors;

  public CreateNewPlaylistMenuAction(Context context, long[] fileDescriptors) {
    super(
        context,
        R.drawable.contextmenu_icon_playlist_add_dark,
        R.string.new_empty_playlist,
        UIUtils.getAppIconPrimaryColor(context));
    this.fileDescriptors = fileDescriptors;
  }

  @Override
  public void onClick(Context context) {
    CreateNewPlaylistDialog.newInstance(fileDescriptors).show(getFragmentManager());
  }

  private static final Pattern NUMBERED = Pattern.compile("^(.*\\S) (\\d+)$");

  /**
   * {@code name} if no playlist has it, else the first free "name 2", "name 3"... A name that
   * already ends in a number counts on from it ("Rock 2" to "Rock 3"). New playlists never replace
   * one with the same name (frostwire/frostwire#1449); the user confirms the suggestion.
   */
  static String uniquePlaylistName(String name, Predicate<String> taken) {
    if (!taken.test(name)) {
      return name;
    }
    String base = name;
    int number = 2;
    Matcher numbered = NUMBERED.matcher(name);
    if (numbered.matches()) {
      base = numbered.group(1);
      number = Integer.parseInt(numbered.group(2)) + 1;
    }
    while (taken.test(base + " " + number)) {
      number++;
    }
    return base + " " + number;
  }

  /**
   * Creates the playlist {@code name} and adds {@code songs} to it, off the main thread.
   *
   * @return the new playlist's id, or -1 when it could not be created (then nothing is added)
   */
  static long createAndFill(Context context, String name, long[] songs) {
    long playlistId = MusicUtils.createPlaylist(context, name);
    if (playlistId < 0) {
      return -1;
    }
    if (songs != null && songs.length > 0) {
      MusicUtils.addToPlaylist(context, songs, playlistId);
    }
    return playlistId;
  }

  public static class CreateNewPlaylistDialog extends AbstractDialog {

    private long[] fileDescriptors;

    private EditText playlistInput;

    public static CreateNewPlaylistDialog newInstance(long[] fileDescriptors) {
      CreateNewPlaylistDialog dlg = new CreateNewPlaylistDialog();

      Bundle args = new Bundle();
      args.putLongArray("file_descriptors", fileDescriptors);
      dlg.setArguments(args);

      return dlg;
    }

    public CreateNewPlaylistDialog() {
      super(R.layout.dialog_default_input);
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
      Bundle args = getArguments();
      this.fileDescriptors = args.getLongArray("file_descriptors");

      TextView title = findView(dlg, R.id.dialog_default_input_title);
      title.setText(R.string.new_empty_playlist);

      playlistInput = findView(dlg, R.id.dialog_default_input_text);

      if (savedInstanceState != null && savedInstanceState.getString("playlistName") != null) {
        playlistInput.setText(savedInstanceState.getString("playlistName"));
      } else {
        playlistInput.setText("");
      }
      playlistInput.setHint(R.string.create_playlist_prompt);
      playlistInput.selectAll();

      Button yesButton = findView(dlg, R.id.dialog_default_input_button_yes);
      yesButton.setText(android.R.string.ok);
      Button noButton = findView(dlg, R.id.dialog_default_input_button_no);
      noButton.setText(R.string.cancel);

      yesButton.setOnClickListener(new DialogButtonClickListener(true));
      noButton.setOnClickListener(new DialogButtonClickListener(false));
    }

    String getPlaylistName() {
      return playlistInput.getText().toString();
    }

    void updatePlaylistName(String playlistName) {
      playlistInput.setText(playlistName);
      playlistInput.setSelection(playlistName.length());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
      outState.putString("playlistName", getPlaylistName());
      super.onSaveInstanceState(outState);
    }

    private void onClickCreatePlaylistButton(CharSequence text) {
      final Context ctx = getActivity();

      SystemUtils.postToHandler(
          MISC,
          () -> {
            createAndFill(ctx, text.toString(), fileDescriptors);
            MusicUtils.refresh();

            SystemUtils.postToUIThread(
                () -> {
                  if (ctx instanceof AbstractActivity) {
                    PlaylistFragment f =
                        ((AbstractActivity) ctx).findFragment(PlaylistFragment.class);
                    if (f != null) {
                      f.restartLoader(true);
                      f.refresh();
                    }
                  }
                });
          });
    }

    private final class DialogButtonClickListener implements View.OnClickListener {

      private final boolean positive;

      DialogButtonClickListener(boolean positive) {
        this.positive = positive;
      }

      @Override
      public void onClick(View view) {
        if (!positive) {
          dismiss();
        } else {
          final String playlistName = getPlaylistName().trim();
          if (playlistName.isEmpty()) {
            return;
          }
          WeakReference<FragmentActivity> fragmentActivityWeakRef = Ref.weak(requireActivity());
          SystemUtils.postToHandler(
              MISC,
              () -> {
                if (!Ref.alive(fragmentActivityWeakRef)) {
                  return;
                }
                FragmentActivity fragmentActivity = fragmentActivityWeakRef.get();
                // A taken name is never overwritten: suggest a free one for the user to confirm.
                final String freeName =
                    uniquePlaylistName(
                        playlistName,
                        name -> MusicUtils.getIdForPlaylist(fragmentActivity, name) >= 0);

                SystemUtils.postToUIThread(
                    () -> {
                      if (!freeName.equals(playlistName)) {
                        updatePlaylistName(freeName);
                      } else {
                        onClickCreatePlaylistButton(playlistName);
                        dismiss();
                      }
                    });
              });
        }
      }
    }
  }
}
