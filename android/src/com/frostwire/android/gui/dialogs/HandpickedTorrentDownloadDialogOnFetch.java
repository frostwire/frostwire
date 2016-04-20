package com.frostwire.android.gui.dialogs;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import com.frostwire.android.gui.transfers.TorrentFetcherListener;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * Created by gubatron on 4/20/16.
 */
public class HandpickedTorrentDownloadDialogOnFetch implements TorrentFetcherListener {
    private WeakReference<Context> contextRef;
    private WeakReference<FragmentManager> fragmentManagerRef;

    public HandpickedTorrentDownloadDialogOnFetch(Activity activity, FragmentManager fragmentManager) {
        contextRef = Ref.weak((Context) activity);
        fragmentManagerRef = Ref.weak(fragmentManager);
    }

    @Override
    public void onTorrentInfoFetched(byte[] torrentInfoData) {
        createHandpickedTorrentDownloadDialog(torrentInfoData);
    }

    private void createHandpickedTorrentDownloadDialog(byte[] torrentInfoData) {
        if (!Ref.alive(contextRef) ||
                !Ref.alive(fragmentManagerRef)) {
            return;
        }

        HandpickedTorrentDownloadDialog dlg =
                HandpickedTorrentDownloadDialog.newInstance(
                        contextRef.get(),
                        "some title",
                        "some explanation",
                        TorrentInfo.bdecode(torrentInfoData));

        dlg.show(fragmentManagerRef.get());
    }
}
