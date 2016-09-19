package com.frostwire.bittorrent.jlibtorrent;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.*;
import com.frostwire.jlibtorrent.swig.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.net.*;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author gubatron
 * @author aldenml
 */
public class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class);

    private static final long REQUEST_STATS_RESOLUTION_MILLIS = 1000;
    private static final long ALERTS_LOOP_WAIT_MILLIS = 500;

    private static final int[] METADATA_ALERT_TYPES = new int[]
            {AlertType.METADATA_RECEIVED.swig(), AlertType.METADATA_FAILED.swig()};

    private final boolean logging;

    private final AlertListener[] listeners;

    private final ReentrantLock sync;
    private final ReentrantLock syncMagnet;

    private session session;

    private final SessionStats stats;
    private long lastStatsRequestTime;
    private boolean firewalled;
    private final List<TcpEndpoint> listenEndpoints;
    private Address externalAddress;

    public SessionManager(boolean logging) {
        this.logging = logging;

        this.listeners = new AlertListener[libtorrent.num_alert_types + 1];

        this.sync = new ReentrantLock();
        this.syncMagnet = new ReentrantLock();

        this.stats = new SessionStats();
        this.listenEndpoints = new LinkedList<>();

        resetState();
    }

    public SessionManager() {
        this(false);
    }

    public com.frostwire.jlibtorrent.swig.session swig() {
        return session;
    }

    public void addListener(AlertListener listener) {
        modifyListeners(true, listener);
    }

    public void removeListener(AlertListener listener) {
        modifyListeners(false, listener);
    }

    public void start(SettingsPack sp) {
        if (session != null) {
            return;
        }

        sync.lock();

        try {
            if (session != null) {
                return;
            }

            onBeforeStart();

            resetState();

            sp.setInteger(settings_pack.int_types.alert_mask.swigValue(), alertMask(logging));
            session = new session(sp.swig());
            alertsLoop();

            onAfterStart();

        } finally {
            sync.unlock();
        }
    }

    public void start() {
        settings_pack sp = new settings_pack();

        // REVIEW JLIBTORRENT
        //sp.set_str(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), dhtBootstrapNodes());

        start(new SettingsPack(sp));
    }

    public void stop() {
        if (session == null) {
            return;
        }

        sync.lock();

        try {
            if (session == null) {
                return;
            }

            onBeforeStop();

            com.frostwire.jlibtorrent.swig.session s = session;
            session = null; // stop alerts loop and session methods

            resetState();

            s.abort().delete();

            onAfterStop();

        } finally {
            sync.unlock();
        }
    }

    /**
     * This method blocks for at least a second, don't call it from the UI thread.
     */
    public void restart() {
        sync.lock();

        try {

            stop();
            Thread.sleep(1000); // allow some time to release native resources
            start();

        } catch (InterruptedException e) {
            // ignore
        } finally {
            sync.unlock();
        }
    }

    public boolean isRunning() {
        return session != null;
    }

    public void pause() {
        if (session != null && !session.is_paused()) {
            session.pause();
        }
    }

    public void resume() {
        if (session != null) {
            session.resume();
        }
    }

    public boolean isPaused() {
        return session != null ? session.is_paused() : false;
    }

    public SessionStats stats() {
        return stats;
    }

    public long downloadRate() {
        return stats.downloadRate();
    }

    public long uploadRate() {
        return stats.uploadRate();
    }

    public long totalDownload() {
        return stats.totalDownload();
    }

    public long totalUpload() {
        return stats.totalUpload();
    }

    public long dhtNodes() {
        return stats.dhtNodes();
    }

    public boolean isFirewalled() {
        return firewalled;
    }

    public Address externalAddress() {
        return externalAddress;
    }

    public List<TcpEndpoint> listenEndpoints() {
        return new LinkedList<>(listenEndpoints);
    }

    //--------------------------------------------------
    // Settings methods
    //--------------------------------------------------

    /**
     * Returns a setting pack with all the settings
     * the current session is working with.
     * <p>
     * If the current internal session is null, returns
     * null.
     *
     * @return
     */
    public SettingsPack settings() {
        return session != null ? new SettingsPack(session.get_settings()) : null;
    }

    public void applySettings(SettingsPack sp) {
        if (session != null) {

            if (sp == null) {
                throw new IllegalArgumentException("settings pack can't be null");
            }

            session.apply_settings(sp.swig());
            onApplySettings(sp);
        }
    }

    public int downloadRateLimit() {
        if (session == null) {
            return 0;
        }
        return settings().downloadRateLimit();
    }

    public void downloadRateLimit(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.downloadRateLimit(limit);
        applySettings(sp);
    }

    public int uploadRateLimit() {
        if (session == null) {
            return 0;
        }
        return settings().uploadRateLimit();
    }

    public void uploadRateLimit(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.uploadRateLimit(limit);
        applySettings(sp);
    }

    public int maxActiveDownloads() {
        if (session == null) {
            return 0;
        }
        return settings().activeDownloads();
    }

    public void maxActiveDownloads(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.activeDownloads(limit);
        applySettings(sp);
    }

    public int maxActiveSeeds() {
        if (session == null) {
            return 0;
        }
        return settings().activeSeeds();
    }

    public void maxActiveSeeds(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.activeSeeds(limit);
        applySettings(sp);
    }

    public int maxConnections() {
        if (session == null) {
            return 0;
        }
        return settings().connectionsLimit();
    }

    public void maxConnections(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.connectionsLimit(limit);
        applySettings(sp);
    }

    public int maxPeers() {
        if (session == null) {
            return 0;
        }
        return settings().maxPeerlistSize();
    }

    public void maxPeers(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.maxPeerlistSize(limit);
        applySettings(sp);
    }

    public String listenInterfaces() {
        if (session == null) {
            return null;
        }
        return settings().getString(settings_pack.string_types.listen_interfaces.swigValue());
    }

    public void listenInterfaces(String value) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), value);
        applySettings(sp);
    }

    //--------------------------------------------------
    // more methods
    //--------------------------------------------------

    /**
     * This function will post a {@link SessionStatsAlert} object, containing a
     * snapshot of the performance counters from the internals of libtorrent.
     */
    public void postSessionStats() {
        if (session != null) {
            session.post_session_stats();
        }
    }

    /**
     * This will cause a {@link DhtStatsAlert} to be posted.
     */
    public void postDHTStats() {
        if (session != null) {
            session.post_dht_stats();
        }
    }

    public boolean isDhtRunning() {
        return session != null ? session.is_dht_running() : false;
    }

    public void startDht() {
        toggleDht(true);
    }

    public void stopDht() {
        toggleDht(false);
    }

    private void toggleDht(boolean on) {
        if (session == null || isDhtRunning() == on) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.enableDht(on);
        applySettings(sp);
    }

    public TorrentHandle find(Sha1Hash sha1) {
        if (session == null) {
            return null;
        }

        torrent_handle th = session.find_torrent(sha1.swig());
        return th != null && th.is_valid() ? new TorrentHandle(th) : null;
    }

    /**
     * @param ti
     * @param saveDir
     */
    public void download(TorrentInfo ti, File saveDir) {
        download(ti, saveDir, null, null, null);
    }

    public void download(TorrentInfo ti, File saveDir, File resumeFile, Priority[] priorities, String magnetUrlParams) {

        String savePath = null;
        if (saveDir != null) {
            savePath = saveDir.getAbsolutePath();
        } else if (resumeFile == null) {
            throw new IllegalArgumentException("Both saveDir and resumeFile can't be null at the same time");
        }

        add_torrent_params p = add_torrent_params.create_instance();

        if (magnetUrlParams != null && !magnetUrlParams.isEmpty()) {
            p.setUrl(magnetUrlParams);
        }

        p.set_ti(ti.swig());
        if (savePath != null) {
            p.setSave_path(savePath);
        }

        if (priorities != null) {
            byte_vector v = new byte_vector();
            for (int i = 0; i < priorities.length; i++) {
                v.push_back((byte) priorities[i].swig());
            }
            p.set_file_priorities(v);
        }
        p.setStorage_mode(storage_mode_t.storage_mode_sparse);

        long flags = p.get_flags();

        flags &= ~add_torrent_params.flags_t.flag_auto_managed.swigValue();

        if (resumeFile != null) {
            try {
                byte[] data = FileUtils.readFileToByteArray(resumeFile);
                p.set_resume_data(Vectors.bytes2byte_vector(data));

                flags |= add_torrent_params.flags_t.flag_use_resume_save_path.swigValue();
            } catch (Throwable e) {
                LOG.warn("Unable to set resume data", e);
            }
        }

        p.set_flags(flags);

        session.async_add_torrent(p);
    }

    public void remove(TorrentHandle th, Session.Options options) {
        if (session != null && th.isValid()) {
            session.remove_torrent(th.swig(), options.swig());
        }
    }

    public void remove(TorrentHandle th) {
        if (session != null && th.isValid()) {
            session.remove_torrent(th.swig());
        }
    }

    /**
     * @param uri
     * @param timeout in seconds
     * @param maxSize in bytes
     * @return
     */
    public byte[] fetchMagnet(String uri, int timeout, final int maxSize) {
        if (session == null) {
            return null;
        }

        add_torrent_params p = add_torrent_params.create_instance_disabled_storage();
        error_code ec = new error_code();
        libtorrent.parse_magnet_uri(uri, p, ec);

        if (ec.value() != 0) {
            throw new IllegalArgumentException(ec.message());
        }

        final sha1_hash info_hash = p.getInfo_hash();
        final byte[][] data = new byte[1][];
        final CountDownLatch signal = new CountDownLatch(1);

        AlertListener listener = new AlertListener() {
            @Override
            public int[] types() {
                return METADATA_ALERT_TYPES;
            }

            @Override
            public void alert(Alert<?> alert) {
                torrent_handle th = ((TorrentAlert<?>) alert).swig().getHandle();
                if (th == null || !th.is_valid() || !th.info_hash().op_eq(info_hash)) {
                    return;
                }

                AlertType type = alert.type();

                if (type.equals(AlertType.METADATA_RECEIVED)) {
                    MetadataReceivedAlert a = ((MetadataReceivedAlert) alert);
                    byte[] arr = torrentData(a);
                    int size = arr != null ? arr.length : -1;
                    if (0 < size && size <= maxSize) {
                        data[0] = arr;
                    }
                }

                signal.countDown();
            }
        };

        addListener(listener);

        boolean add = false;
        torrent_handle th = null;

        try {

            syncMagnet.lock();

            try {
                th = session.find_torrent(info_hash);
                if (th != null && th.is_valid()) {
                    // we have a download with the same info-hash, let's wait
                    add = false;
                } else {
                    add = true;
                }

                if (add) {
                    p.setName("fetch_magnet" + uri);
                    p.setSave_path("fetch_magnet" + uri);

                    long flags = p.get_flags();
                    flags &= ~add_torrent_params.flags_t.flag_auto_managed.swigValue();
                    p.set_flags(flags);

                    ec.clear();
                    th = session.add_torrent(p, ec);
                    th.resume();
                }
            } finally {
                syncMagnet.unlock();
            }

            signal.await(timeout, TimeUnit.SECONDS);

        } catch (Throwable e) {
            LOG.error("Error fetching magnet", e);
        } finally {
            removeListener(listener);
            if (session != null && add && th != null && th.is_valid()) {
                session.remove_torrent(th);
            }
        }

        return data[0];
    }

    /**
     * Similar to call {@link #fetchMagnet(String, int, int)} with
     * a maximum size of 2MB.
     *
     * @param uri
     * @param timeout in seconds
     * @return
     */
    public byte[] fetchMagnet(String uri, int timeout) {
        return fetchMagnet(uri, timeout, 2 * 1024 * 1024);
    }

    public void moveStorage(File dir) {
        if (session == null) {
            return;
        }

        try {
            torrent_handle_vector v = session.get_torrents();
            int size = (int) v.size();

            String path = dir.getAbsolutePath();
            for (int i = 0; i < size; i++) {
                torrent_handle th = v.get(i);
                torrent_status ts = th.status();
                boolean incomplete = !ts.getIs_seeding() && !ts.getIs_finished();
                if (th.is_valid() && incomplete) {
                    th.move_storage(path);
                }
            }
        } catch (Throwable e) {
            LOG.error("Error changing save path for session", e);
        }
    }

    public byte[] saveState() {
        return new SessionHandle(session).saveState();
    }

    public void loadState(byte[] data) {
        new SessionHandle(session).loadState(data);
    }

    public String magnetPeers() {
        if (session == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (TcpEndpoint endp : listenEndpoints) {
            try {
                Address address = endp.address();
                if (address.isLoopback() || address.isMulticast()) {
                    continue;
                }

                if (address.isUnspecified()) {
                    try {
                        addAllInterfaces(address.isV6(), endp.port(), sb);
                    } catch (Throwable e) {
                        LOG.error("Error adding all listen interfaces", e);
                    }
                } else {
                    addMagnetPeer(endp.address(), endp.port(), sb);
                }

                if (externalAddress != null) {
                    addMagnetPeer(externalAddress, endp.port(), sb);
                }
            } catch (Throwable e) {
                LOG.error("Error processing listen endpoint", e);
            }
        }

        return sb.toString();
    }

    protected void onBeforeStart() {
    }

    protected void onAfterStart() {
    }

    protected void onBeforeStop() {
    }

    protected void onAfterStop() {
    }

    protected void onApplySettings(SettingsPack sp) {
    }

    @Override
    protected void finalize() throws Throwable {
        stop();
        super.finalize();
    }

    private void resetState() {
        stats.clear();
        firewalled = true;
        listenEndpoints.clear();
        externalAddress = null;
    }

    private void modifyListeners(boolean add, AlertListener listener) {
        if (listener == null) {
            return;
        }

        int[] types = listener.types();

        // all alert-type including listener
        if (types == null) {
            modifyListeners(add, libtorrent.num_alert_types, listener);
        } else {
            for (int i = 0; i < types.length; i++) {
                modifyListeners(add, types[i], listener);
            }
        }
    }

    private synchronized void modifyListeners(boolean add, int type, AlertListener listener) {
        if (add) {
            listeners[type] = AlertMulticaster.add(listeners[type], listener);
        } else {
            listeners[type] = AlertMulticaster.remove(listeners[type], listener);
        }
    }

    private void fireAlert(Alert<?> a, int type) {
        AlertListener listener = listeners[type];
        if (listener != null) {
            try {
                listener.alert(a);
            } catch (Throwable e) {
                LOG.warn("Error calling alert listener", e);
            }
        }
    }

    private void onListenSucceeded(ListenSucceededAlert alert) {
        try {
            if (alert.socketType() == ListenSucceededAlert.SocketType.TCP) {
                String address = alert.address().toString(); // clone
                int port = alert.port();
                listenEndpoints.add(new TcpEndpoint(address, port));
            }
        } catch (Throwable e) {
            LOG.error("Error adding listen endpoint to internal list", e);
        }
    }

    private void onExternalIpAlert(ExternalIpAlert alert) {
        try {
            // libtorrent perform all kind of tests
            // to avoid non usable addresses
            String address = alert.externalAddress().toString(); // clone
            externalAddress = new Address(address);
        } catch (Throwable e) {
            LOG.error("Error saving reported external ip", e);
        }
    }

    private static int alertMask(boolean logging) {
        int mask = alert.category_t.all_categories.swigValue();
        if (!logging) {
            int log_mask = alert.category_t.session_log_notification.swigValue() |
                    alert.category_t.torrent_log_notification.swigValue() |
                    alert.category_t.peer_log_notification.swigValue() |
                    alert.category_t.dht_log_notification.swigValue() |
                    alert.category_t.port_mapping_log_notification.swigValue() |
                    alert.category_t.picker_log_notification.swigValue();
            mask = mask & ~log_mask;
        }
        return mask;
    }

    private static String dhtBootstrapNodes() {
        StringBuilder sb = new StringBuilder();

        sb.append("dht.libtorrent.org:25401").append(",");
        sb.append("router.bittorrent.com:6881").append(",");
        sb.append("dht.transmissionbt.com:6881").append(",");
        // for DHT IPv6
        sb.append("outer.silotis.us:6881");

        return sb.toString();
    }

    private static void addAllInterfaces(boolean v6, int port, StringBuilder sb) throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface iface = networkInterfaces.nextElement();
            if (iface.isLoopback()) {
                continue;
            }
            List<InterfaceAddress> interfaceAddresses = iface.getInterfaceAddresses();
            for (InterfaceAddress ifaceAddress : interfaceAddresses) {
                InetAddress inetAddress = ifaceAddress.getAddress();

                if (inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                    continue;
                }

                // same family?
                if (inetAddress instanceof Inet6Address != v6) {
                    continue;
                }

                String hostAddress = ifaceAddress.getAddress().getHostAddress();

                // IPv6 address should be expressed as [address]:port
                if (v6) {
                    // remove the %22 or whatever mask at the end.
                    if (hostAddress.contains("%")) {
                        hostAddress = hostAddress.substring(0, hostAddress.indexOf("%"));
                    }
                    hostAddress = "[" + hostAddress + "]";
                }
                sb.append("&x.pe=" + hostAddress + ":" + port);
            }
        }
    }

    private static void addMagnetPeer(Address address, int port, StringBuilder sb) {
        String hostAddress = address.toString();
        if (hostAddress.contains("invalid")) {
            return;
        }

        if (address.isV6()) {
            hostAddress = "[" + hostAddress + "]";
        }
        sb.append("&x.pe=" + hostAddress + ":" + port);
    }

    private void alertsLoop() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                alert_ptr_vector v = new alert_ptr_vector();

                while (session != null) {
                    alert ptr = session.wait_for_alert_ms(ALERTS_LOOP_WAIT_MILLIS);

                    if (session == null) {
                        return;
                    }

                    if (ptr != null) {
                        session.pop_alerts(v);
                        long size = v.size();
                        for (int i = 0; i < size; i++) {
                            alert a = v.get(i);
                            int type = a.type();

                            Alert<?> alert = null;

                            switch (AlertType.fromSwig(type)) {
                                case SESSION_STATS:
                                    alert = Alerts.cast(a);
                                    stats.update((SessionStatsAlert) alert);
                                    break;
                                case PORTMAP:
                                    firewalled = false;
                                    break;
                                case PORTMAP_ERROR:
                                    firewalled = true;
                                    break;
                                case LISTEN_SUCCEEDED:
                                    alert = Alerts.cast(a);
                                    onListenSucceeded((ListenSucceededAlert) alert);
                                    break;
                                case EXTERNAL_IP:
                                    alert = Alerts.cast(a);
                                    onExternalIpAlert((ExternalIpAlert) alert);
                                    break;
                            }

                            if (listeners[type] != null) {
                                if (alert == null) {
                                    alert = Alerts.cast(a);
                                }
                                fireAlert(alert, type);
                            }

                            if (type != AlertType.SESSION_STATS.swig() &&
                                    listeners[libtorrent.num_alert_types] != null) {
                                if (alert == null) {
                                    alert = Alerts.cast(a);
                                }
                                fireAlert(alert, libtorrent.num_alert_types);
                            }
                        }
                        v.clear();
                    }

                    long now = System.currentTimeMillis();
                    if ((now - lastStatsRequestTime) >= REQUEST_STATS_RESOLUTION_MILLIS) {
                        lastStatsRequestTime = now;
                        postSessionStats();
                    }
                }
            }
        };

        Thread t = new Thread(r, "SessionManager-alertsLoop");
        t.setDaemon(true);
        t.start();
    }

    static int_vector array2int_vector(Priority[] arr) {
        int_vector v = new int_vector();

        for (int i = 0; i < arr.length; i++) {
            Priority p = arr[i];
            v.push_back(p != Priority.UNKNOWN ? p.swig() : Priority.IGNORE.swig());
        }

        return v;
    }

    public byte[] torrentData(MetadataReceivedAlert alert) {
        try {
            torrent_handle th = alert.swig().getHandle();
            if (th == null || !th.is_valid()) {
                return null;
            }

            torrent_info ti = th.get_torrent_copy();
            if (ti == null || !ti.is_valid()) {
                return null;
            }

            create_torrent ct = new create_torrent(ti);
            entry e = ct.generate();

            return Vectors.byte_vector2bytes(e.bencode());

        } catch (Throwable e) {
            LOG.error("Error building torrent data from metadata", e);
            return null;
        }
    }
}
