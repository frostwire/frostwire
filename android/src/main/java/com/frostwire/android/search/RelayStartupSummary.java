/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import android.content.Context;
import android.text.format.DateUtils;
import com.frostwire.android.R;

/** Render actual startup state rather than guessing readiness from the existence of an identity. */
public final class RelayStartupSummary {
  private RelayStartupSummary() {}

  public static String render(Context context, RelayStartupTracker.Snapshot status) {
    if (status == null) return context.getString(R.string.distributed_stack_no_service);
    int phase =
        switch (status.phase()) {
          case QUEUED -> R.string.waiting;
          case IDENTITY -> R.string.distributed_identity_header;
          case INDEX -> R.string.distributed_identity_shared_count;
          case KARMA -> R.string.distributed_identity_karma;
          case SERVER -> R.string.distributed_icebridge_header;
          case TRANSPORT -> R.string.distributed_stack_status_title;
          case DISCOVERY -> R.string.distributed_peers_header;
        };
    String detail = context.getString(phase);
    return switch (status.state()) {
      case STARTING ->
          context.getString(R.string.distributed_stack_starting)
              + "\n"
              + detail
              + " · "
              + DateUtils.formatElapsedTime(status.seconds());
      case DRAINING ->
          context.getString(R.string.error_connection_timed_out)
              + "\n"
              + context.getString(R.string.waiting);
      case RETRY_WAIT ->
          context.getString(R.string.distributed_identity_failed, detail + " · " + status.failure())
              + "\n"
              + context.getString(R.string.distributed_stack_retry_countdown, status.seconds());
      case FAILED ->
          context.getString(R.string.distributed_identity_failed, detail + " · " + status.failure())
              + "\n"
              + context.getString(R.string.distributed_icebridge_start);
      default -> context.getString(R.string.distributed_stack_not_running);
    };
  }
}
