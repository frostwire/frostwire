/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import com.frostwire.android.R;
import com.frostwire.android.gui.tasks.AsyncStartDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.Ref;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConfirmSoundcloudDownloadDialog
    extends AbstractConfirmListDialog<SoundcloudSearchResult> {

  public static ConfirmSoundcloudDownloadDialog newInstance(
      Context ctx, String dialogTitle, String dialogText, List<SoundcloudSearchResult> listData) {
    ConfirmSoundcloudDownloadDialog dlg = new ConfirmSoundcloudDownloadDialog();

    // The results (a live HttpClient, a BooleanSupplier) can't go through Gson: the dialog's
    // arguments carry a key to the list kept here, which survives a configuration change.
    dlg.prepareArguments(
        R.drawable.download_icon,
        dialogTitle,
        dialogText,
        rememberResults(listData),
        SelectionMode.MULTIPLE_SELECTION);

    dlg.setOnYesListener(new OnStartDownloadsClickListener(ctx, dlg));
    return dlg;
  }

  private static void startDownloads(Context ctx, List<? extends SearchResult> srs) {
    if (srs != null && !srs.isEmpty()) {
      for (SearchResult sr : srs) {
        new AsyncStartDownload(ctx, sr);
      }
      UIUtils.showTransfersOnDownloadStart(ctx);
    }
  }

  /** Keeps the lists of the last few dialogs; a dialog the user never answered can't pile up. */
  private static final Map<String, List<SoundcloudSearchResult>> RESULTS =
      new LinkedHashMap<String, List<SoundcloudSearchResult>>() {
        @Override
        protected boolean removeEldestEntry(
            Map.Entry<String, List<SoundcloudSearchResult>> eldest) {
          return size() > 8;
        }
      };

  private static String rememberResults(List<SoundcloudSearchResult> listData) {
    final String key = UUID.randomUUID().toString();
    synchronized (RESULTS) {
      RESULTS.put(key, listData);
    }
    return key;
  }

  /**
   * @param listDataKey the key {@link #newInstance} stored the results under. A dialog the system
   *     restored after the process died finds nothing, and shows an empty list.
   */
  @Override
  public List<SoundcloudSearchResult> deserializeData(String listDataKey) {
    synchronized (RESULTS) {
      final List<SoundcloudSearchResult> results = RESULTS.get(listDataKey);
      return results != null ? results : new ArrayList<>();
    }
  }

  @Override
  public ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> createAdapter(
      Context context,
      List<SoundcloudSearchResult> listData,
      SelectionMode selectionMode,
      Bundle bundle) {
    return new SoundcloudPlaylistConfirmListDialogAdapter(context, listData, selectionMode);
  }

  @Override
  protected OnStartDownloadsClickListener createOnYesListener() {
    return new OnStartDownloadsClickListener(getActivity(), this);
  }

  // TODO: this class needs heavy refactor/cleanup
  private static class OnStartDownloadsClickListener implements View.OnClickListener {
    private final WeakReference<Context> ctxRef;
    private final WeakReference<AbstractConfirmListDialog<SoundcloudSearchResult>> dlgRef;

    OnStartDownloadsClickListener(
        Context ctx, AbstractConfirmListDialog<SoundcloudSearchResult> dlg) {
      ctxRef = new WeakReference<>(ctx);
      dlgRef = new WeakReference<>(dlg);
    }

    @Override
    public void onClick(View v) {
      if (Ref.alive(ctxRef) && Ref.alive(dlgRef)) {
        final AbstractConfirmListDialog<SoundcloudSearchResult> dlg = dlgRef.get();

        final AbstractConfirmListDialog.SelectionMode selectionMode = dlg.getSelectionMode();
        List<SoundcloudSearchResult> results =
            (selectionMode == AbstractConfirmListDialog.SelectionMode.NO_SELECTION)
                ? dlg.getList()
                : new ArrayList<>();

        if (results.isEmpty()) {
          results.addAll(dlg.getChecked());
        }

        if (!results.isEmpty()) {
          startDownloads(ctxRef.get(), results);
          dlg.dismiss();
        }
      }
    }
  }

  private static class SoundcloudPlaylistConfirmListDialogAdapter
      extends ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> {
    SoundcloudPlaylistConfirmListDialogAdapter(
        Context context,
        List<SoundcloudSearchResult> list,
        AbstractConfirmListDialog.SelectionMode selectionMode) {
      super(context, list, selectionMode);
    }

    @Override
    public CharSequence getItemTitle(SoundcloudSearchResult data) {
      return data.getDisplayName();
    }

    @Override
    public long getItemSize(SoundcloudSearchResult data) {
      return data.getSize();
    }

    @Override
    public CharSequence getItemThumbnailUrl(SoundcloudSearchResult data) {
      return data.getThumbnailUrl();
    }

    @Override
    public int getItemThumbnailResourceId(SoundcloudSearchResult data) {
      return -1;
    }

    @Override
    public String getCheckedSum() {
      if (checked == null || checked.isEmpty()) {
        return null;
      }

      long totalBytes = 0;
      for (SoundcloudSearchResult sr : checked) {
        totalBytes += sr.getSize();
      }

      return UIUtils.getBytesInHuman(totalBytes);
    }
  }
}
