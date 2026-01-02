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

package com.frostwire.android.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import com.frostwire.util.Logger;

/**
 * A Context wrapper that prevents registration of CONNECTIVITY_CHANGE broadcast receivers.
 *
 * DEPRECATED: This class was created as a workaround for Picasso 3.0.0-alpha06's 
 * HandlerDispatcher race condition. Since the app has migrated to Coil for image loading,
 * this workaround is no longer needed and this class can be safely removed in future cleanup.
 *
 * Original purpose: Picasso's NetworkBroadcastReceiver could receive broadcasts after the 
 * Handler had been nulled out, causing NullPointerException in HandlerDispatcher.dispatchNetworkStateChange().
 * By preventing the registration of network connectivity receivers, we avoided the crash
 * while still allowing Picasso to function normally for image loading.
 *
 * NOTE: CONNECTIVITY_CHANGE broadcast was deprecated in API 24 and restricted in API 26+.
 * Modern apps should use ConnectivityManager.NetworkCallback instead (see NetworkManager).
 * Coil doesn't have these issues as it uses modern Android networking APIs.
 *
 * @author gubatron
 * @author aldenml
 * @deprecated No longer needed after migration from Picasso to Coil
 */
@Deprecated
public class SafeContextWrapper extends ContextWrapper {

    private static final Logger LOG = Logger.getLogger(SafeContextWrapper.class);
    private static final boolean DISABLE_CONNECTIVITY_RECEIVER = true;

    public SafeContextWrapper(Context base) {
        super(base);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (shouldBlockReceiver(receiver, filter)) {
            LOG.info("Blocked CONNECTIVITY_CHANGE receiver registration to prevent Picasso HandlerDispatcher NPE");
            return null;
        }
        return super.registerReceiver(receiver, filter);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        if (shouldBlockReceiver(receiver, filter)) {
            LOG.info("Blocked CONNECTIVITY_CHANGE receiver registration to prevent Picasso HandlerDispatcher NPE");
            return null;
        }
        return super.registerReceiver(receiver, filter, flags);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler) {
        if (shouldBlockReceiver(receiver, filter)) {
            LOG.info("Blocked CONNECTIVITY_CHANGE receiver registration to prevent Picasso HandlerDispatcher NPE");
            return null;
        }
        return super.registerReceiver(receiver, filter, broadcastPermission, scheduler);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler, int flags) {
        if (shouldBlockReceiver(receiver, filter)) {
            LOG.info("Blocked CONNECTIVITY_CHANGE receiver registration to prevent Picasso HandlerDispatcher NPE");
            return null;
        }
        return super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags);
    }

    /**
     * Determines if a receiver registration should be blocked.
     *
     * We block receivers that:
     * 1. Are from Picasso (NetworkBroadcastReceiver)
     * 2. Listen for CONNECTIVITY_CHANGE action
     *
     * This prevents Picasso's buggy network monitoring from causing crashes.
     */
    private boolean shouldBlockReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (!DISABLE_CONNECTIVITY_RECEIVER || receiver == null || filter == null) {
            return false;
        }

        // Check if this is Picasso's NetworkBroadcastReceiver trying to register for connectivity changes
        String receiverClassName = receiver.getClass().getName();
        boolean isPicassoReceiver = receiverClassName.contains("picasso") &&
                                   receiverClassName.contains("NetworkBroadcastReceiver");

        if (isPicassoReceiver) {
            // Check if it's registering for CONNECTIVITY_CHANGE
            for (int i = 0; i < filter.countActions(); i++) {
                String action = filter.getAction(i);
                if ("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
                    LOG.warn("Blocking Picasso NetworkBroadcastReceiver for CONNECTIVITY_CHANGE to prevent HandlerDispatcher NPE crash");
                    return true;
                }
            }
        }

        return false;
    }
}
