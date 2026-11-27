/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util.picasso;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Stand-in whose class name matches Picasso's receiver, which SafeContextWrapper blocks. */
public class NetworkBroadcastReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {}
}
