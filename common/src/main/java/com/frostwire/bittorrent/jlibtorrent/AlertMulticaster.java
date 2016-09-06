package com.frostwire.bittorrent.jlibtorrent;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.alerts.Alert;

/**
 * @author gubatron
 * @author aldenml
 */
final class AlertMulticaster implements AlertListener {

    private final AlertListener a;
    private final AlertListener b;

    public AlertMulticaster(AlertListener a, AlertListener b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public int[] types() {
        return null;
    }

    @Override
    public void alert(Alert<?> alert) {
        a.alert(alert);
        b.alert(alert);
    }

    public static AlertListener add(AlertListener a, AlertListener b) {
        return addInternal(a, b);
    }

    public static AlertListener remove(AlertListener l, AlertListener oldl) {
        return removeInternal(l, oldl);
    }

    private AlertListener remove(AlertListener oldl) {
        if (oldl == a) return b;
        if (oldl == b) return a;
        AlertListener a2 = removeInternal(a, oldl);
        AlertListener b2 = removeInternal(b, oldl);
        if (a2 == a && b2 == b) {
            return this;        // it's not here
        }
        return addInternal(a2, b2);
    }

    private static AlertListener addInternal(AlertListener a, AlertListener b) {
        if (a == null) return b;
        if (b == null) return a;
        return new AlertMulticaster(a, b);
    }

    private static AlertListener removeInternal(AlertListener l, AlertListener oldl) {
        if (l == oldl || l == null) {
            return null;
        } else if (l instanceof AlertMulticaster) {
            return ((AlertMulticaster) l).remove(oldl);
        } else {
            return l; // it's not here
        }
    }
}
