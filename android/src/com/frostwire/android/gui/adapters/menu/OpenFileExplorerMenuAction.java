package com.frostwire.android.gui.adapters.menu;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.frostwire.android.AndroidPaths;
import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.views.MenuAction;

import java.io.File;

public class OpenFileExplorerMenuAction extends MenuAction {

    private final FWFileDescriptor fd;

    public OpenFileExplorerMenuAction(Context context, FWFileDescriptor fd) {
        super(context, R.drawable.menu_icon_library, R.string.open_file_explorer);
        this.fd = fd;
    }
    @Override
    public void onClick(Context context) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        byte fileType = AndroidPaths.getFileType(fd.filePath, true);

        Uri uri = Uri.parse(new File(fd.filePath).getParentFile().getAbsolutePath()); // a directory
        intent.setDataAndType(uri, "*/*");
        context.startActivity(Intent.createChooser(intent, context.getResources().getString(R.string.open_file_explorer)));
    }
}
