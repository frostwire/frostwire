package com.frostwire.android.gui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import com.frostwire.android.R;
import com.frostwire.android.gui.tasks.StartDownloadTask;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ConfirmSoundcloudDownloadDialog extends AbstractConfirmListDialog<SoundcloudSearchResult> {

    public static ConfirmSoundcloudDownloadDialog newInstance(
            Context ctx,
            String dialogTitle,
            String dialogText,
            List<SoundcloudSearchResult> listData) {
        ConfirmSoundcloudDownloadDialog dlg = new ConfirmSoundcloudDownloadDialog();
        SoundcloudSearchResultList srList = new SoundcloudSearchResultList();
        srList.listData = listData;

        // this creates a bundle that gets passed to setArguments(). It's supposed to be ready
        // before the dialog is attached to the underlying activity, after we attach to it, then
        // we are able to use such Bundle to create our adapter.
        dlg.prepareArguments(R.drawable.download_icon, dialogTitle, dialogText, JsonUtils.toJson(srList), SelectionMode.MULTIPLE_SELECTION);

        dlg.setOnYesListener(new OnStartDownloadsClickListener(ctx, dlg));
        return dlg;
    }

    private static void startDownloads(Context ctx, List<? extends SearchResult> srs) {
        if (srs != null && !srs.isEmpty()) {
            for (SearchResult sr : srs) {
                StartDownloadTask task = new StartDownloadTask(ctx, sr);
                task.execute();
            }
            UIUtils.showTransfersOnDownloadStart(ctx);
        }
    }

    @Override
    public List<SoundcloudSearchResult> deserializeData(String listDataInJSON) {
        SoundcloudSearchResultList srList = JsonUtils.toObject(listDataInJSON, SoundcloudSearchResultList.class);
        return srList.listData;
    }

    @Override
    public ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> createAdapter(Context context,
                                                                                 List<SoundcloudSearchResult> listData,
                                                                                 SelectionMode selectionMode,
                                                                                 Bundle bundle) {
        return new SoundcloudPlaylistConfirmListDialogAdapter(context, listData, selectionMode);
    }


    @Override
    protected OnStartDownloadsClickListener createOnYesListener(final AbstractConfirmListDialog dlg) {
        OnStartDownloadsClickListener listener = new OnStartDownloadsClickListener(getActivity(), dlg);
        listener.setDialog(this);
        return listener;
    }

    private static class SoundcloudSearchResultList {
        List<SoundcloudSearchResult> listData;
    }

    private static class OnStartDownloadsClickListener implements View.OnClickListener {
        private final WeakReference<Context> ctxRef;
        private WeakReference<AbstractConfirmListDialog> dlgRef;

        OnStartDownloadsClickListener(Context ctx, AbstractConfirmListDialog dlg) {
            ctxRef = new WeakReference<>(ctx);
            dlgRef = new WeakReference<>(dlg);
        }

        public void setDialog(AbstractConfirmListDialog dlg) {
            dlgRef = new WeakReference<>(dlg);
        }

        @Override
        public void onClick(View v) {
            if (Ref.alive(ctxRef) && Ref.alive(dlgRef)) {
                final AbstractConfirmListDialog dlg = dlgRef.get();

                final AbstractConfirmListDialog.SelectionMode selectionMode = dlg.getSelectionMode();
                List<SoundcloudSearchResult> results = (selectionMode == AbstractConfirmListDialog.SelectionMode.NO_SELECTION) ?
                        (List<SoundcloudSearchResult>) dlg.getList() :
                        new ArrayList<SoundcloudSearchResult>();

                if (selectionMode == AbstractConfirmListDialog.SelectionMode.MULTIPLE_SELECTION) {
                    results.addAll(dlg.getChecked());
                } else if (selectionMode == AbstractConfirmListDialog.SelectionMode.SINGLE_SELECTION) {
                    if (results.isEmpty()) {
                        return;
                    }
                    SoundcloudSearchResult selected = results.get(dlg.getLastSelected());
                    if (selected == null) {
                        return;
                    }
                    results.add(selected);
                }

                if (!results.isEmpty()) {
                    startDownloads(ctxRef.get(), results);
                    dlg.dismiss();
                }
            }
        }
    }

    private static class SoundcloudPlaylistConfirmListDialogAdapter extends ConfirmListDialogDefaultAdapter<SoundcloudSearchResult> {
        public SoundcloudPlaylistConfirmListDialogAdapter(Context context, List list, AbstractConfirmListDialog.SelectionMode selectionMode) {
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
    }
}
