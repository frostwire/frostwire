package com.frostwire.bittorrent.jlibtorrent;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.jlibtorrent.swig.torrent_handle.status_flags_t;

import java.util.ArrayList;
import java.util.List;

/**
 * You will usually have to store your torrent handles somewhere, since it's
 * the object through which you retrieve information about the torrent and
 * aborts the torrent.
 * <p>
 * .. warning::
 * Any member function that returns a value or fills in a value has to be
 * made synchronously. This means it has to wait for the main thread to
 * complete the query before it can return. This might potentially be
 * expensive if done from within a GUI thread that needs to stay
 * responsive. Try to avoid querying for information you don't need, and
 * try to do it in as few calls as possible. You can get most of the
 * interesting information about a torrent from the
 * torrent_handle::status() call.
 * <p>
 * The default constructor will initialize the handle to an invalid state.
 * Which means you cannot perform any operation on it, unless you first
 * assign it a valid handle. If you try to perform any operation on an
 * uninitialized handle, it will throw ``invalid_handle``.
 * <p>
 * .. warning::
 * All operations on a torrent_handle may throw libtorrent_exception
 * exception, in case the handle is no longer referring to a torrent.
 * There is one exception is_valid() will never throw. Since the torrents
 * are processed by a background thread, there is no guarantee that a
 * handle will remain valid between two calls.
 *
 * @author gubatron
 * @author aldenml
 */
public final class TorrentHandle {

    private static final long REQUEST_STATUS_RESOLUTION_MILLIS = 500;

    private final torrent_handle th;

    private long lastStatusRequestTime;
    private TorrentStatus lastStatus;

    public TorrentHandle(torrent_handle th) {
        this.th = th;
    }

    public torrent_handle swig() {
        return th;
    }

    /**
     * This function will write {@code data} to the storage as piece {@code piece},
     * as if it had been downloaded from a peer. {@code data} is expected to
     * point to a buffer of as many bytes as the size of the specified piece.
     * The data in the buffer is copied and passed on to the disk IO thread
     * to be written at a later point.
     * <p>
     * By default, data that's already been downloaded is not overwritten by
     * this buffer. If you trust this data to be correct (and pass the piece
     * hash check) you may pass the overwrite_existing flag. This will
     * instruct libtorrent to overwrite any data that may already have been
     * downloaded with this data.
     * <p>
     * Since the data is written asynchronously, you may know that is passed
     * or failed the hash check by waiting for
     * {@link com.frostwire.jlibtorrent.alerts.PieceFinishedAlert} or
     * {@link com.frostwire.jlibtorrent.alerts.HashFailedAlert}.
     *
     * @param piece
     * @param data
     * @param flags
     */
    public void addPiece(int piece, byte[] data, int flags) {
        th.add_piece_bytes(piece, Vectors.bytes2byte_vector(data), flags);
    }

    /**
     * Same as calling {@link #addPiece(int, byte[], int)} with
     * {@code flags} with value 0.
     *
     * @param piece
     * @param data
     */
    public void addPiece(int piece, byte[] data) {
        th.add_piece_bytes(piece, Vectors.bytes2byte_vector(data));
    }

    /**
     * This function starts an asynchronous read operation of the specified
     * piece from this torrent. You must have completed the download of the
     * specified piece before calling this function.
     * <p>
     * When the read operation is completed, it is passed back through an
     * alert, {@link com.frostwire.jlibtorrent.alerts.ReadPieceAlert}.
     * Since this alert is a response to an explicit
     * call, it will always be posted, regardless of the alert mask.
     * <p>
     * Note that if you read multiple pieces, the read operations are not
     * guaranteed to finish in the same order as you initiated them.
     *
     * @param piece
     */
    public void readPiece(int piece) {
        th.read_piece(piece);
    }

    /**
     * Returns true if this piece has been completely downloaded, and false
     * otherwise.
     *
     * @param piece
     * @return
     */
    public boolean havePiece(int piece) {
        return th.have_piece(piece);
    }

    /**
     * Returns a list filled with one entry for each peer connected to this
     * torrent, given the handle is valid. If the handle is invalid, it will
     * return an empty list.
     * <p>
     * Each entry in the vector contains information about that particular peer.
     *
     * @return
     * @see PeerInfo
     */
    public ArrayList<PeerInfo> peerInfo() {
        // JLIBTORRENT 1.1
        /*if (!th.is_valid()) {
            return new ArrayList<>();
        }

        peer_info_vector v = new peer_info_vector();
        th.get_peer_info(v);

        int size = (int) v.size();
        ArrayList<PeerInfo> l = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            l.add(new PeerInfo(v.get(i)));
        }

        return l;*/return null;
    }

    /**
     * Returns a pointer to the torrent_info object associated with this
     * torrent. The {@link com.frostwire.jlibtorrent.TorrentInfo} object
     * may be a copy of the internal object.
     * <p>
     * If the torrent doesn't have metadata, the pointer will not be
     * initialized (i.e. a NULL pointer). The torrent may be in a state
     * without metadata only if it was started without a .torrent file, e.g.
     * by using the libtorrent extension of just supplying a tracker and
     * info-hash.
     *
     * @return
     */
    // JLIBTORRENT 1.1
    /*public TorrentInfo torrentFile() {
        torrent_info ti = th.get_torrent_copy();
        return ti != null ? new TorrentInfo(ti) : null;
    }*/

    /**
     * `status()`` will return a structure with information about the status
     * of this torrent. If the torrent_handle is invalid, it will throw
     * libtorrent_exception exception. See torrent_status. The ``flags``
     * argument filters what information is returned in the torrent_status.
     * Some information in there is relatively expensive to calculate, and if
     * you're not interested in it (and see performance issues), you can
     * filter them out.
     * <p>
     * By default everything is included. The flags you can use to decide
     * what to *include* are defined in the status_flags_t enum.
     * <p>
     * It is important not to call this method for each field in the status
     * for performance reasons.
     *
     * @return
     */
    public TorrentStatus status(boolean force) {
        long now = System.currentTimeMillis();
        if (force || (now - lastStatusRequestTime) >= REQUEST_STATUS_RESOLUTION_MILLIS) {
            lastStatusRequestTime = now;
            lastStatus = new TorrentStatus(th.status(0));
        }

        return lastStatus;
    }

    /**
     * `status()`` will return a structure with information about the status
     * of this torrent. If the torrent_handle is invalid, it will throw
     * libtorrent_exception exception. See torrent_status. The ``flags``
     * argument filters what information is returned in the torrent_status.
     * Some information in there is relatively expensive to calculate, and if
     * you're not interested in it (and see performance issues), you can
     * filter them out.
     *
     * @return
     */
    public TorrentStatus status() {
        return status(false);
    }

    /**
     * Returns an array (list) with information about pieces that are partially
     * downloaded or not downloaded at all but partially requested. See
     * {@link PartialPieceInfo} for the fields in the returned vector.
     *
     * @return a list with partial piece info
     */
    public ArrayList<PartialPieceInfo> getDownloadQueue() {
        partial_piece_info_vector v = new partial_piece_info_vector();
        th.get_download_queue(v);
        int size = (int) v.size();
        ArrayList<PartialPieceInfo> l = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            l.add(new PartialPieceInfo(v.get(i)));
        }

        return l;
    }

    /**
     * Returns the info-hash for the torrent.
     * <p>
     * If this handle is to a torrent that hasn't loaded yet (for instance by being added)
     * by a URL, the returned value is undefined.
     *
     * @return
     */
    public Sha1Hash getInfoHash() {
        return new Sha1Hash(th.info_hash());
    }

    /**
     * This method will disconnect all peers.
     * <p>
     * When a torrent is paused, it will however
     * remember all share ratios to all peers and remember all potential (not
     * connected) peers. Torrents may be paused automatically if there is a
     * file error (e.g. disk full) or something similar. See
     * {@link com.frostwire.jlibtorrent.alerts.FileErrorAlert}.
     * <p>
     * To know if a torrent is paused or not, call
     * {@link #status()} and inspect {@link TorrentStatus#isPaused()} .
     * <p>
     * The ``flags`` argument to pause can be set to
     * ``torrent_handle::graceful_pause`` which will delay the disconnect of
     * peers that we're still downloading outstanding requests from. The
     * torrent will not accept any more requests and will disconnect all idle
     * peers. As soon as a peer is done transferring the blocks that were
     * requested from it, it is disconnected. This is a graceful shut down of
     * the torrent in the sense that no downloaded bytes are wasted.
     * <p>
     * torrents that are auto-managed may be automatically resumed again. It
     * does not make sense to pause an auto-managed torrent without making it
     * not automanaged first.
     * <p>
     * The current {@link Session} add torrent implementations add the torrent
     * in no-auto-managed mode.
     */
    public void pause() {
        th.pause();
    }

    /**
     * Will reconnect all peers.
     * <p>
     * Torrents that are auto-managed may be automatically resumed again.
     */
    public void resume() {
        th.resume();
    }

    /**
     * Set or clear the stop-when-ready flag. When this flag is set, the
     * torrent will *force stop* whenever it transitions from a
     * non-data-transferring state into a data-transferring state (referred to
     * as being ready to download or seed). This is useful for torrents that
     * should not start downloading or seeding yet, but what to be made ready
     * to do so. A torrent may need to have its files checked for instance, so
     * it needs to be started and possibly queued for checking (auto-managed
     * and started) but as soon as it's done, it should be stopped.
     * <p>
     * *Force stopped* means auto-managed is set to false and it's paused. As
     * if auto_manage(false) and pause() were called on the torrent.
     *
     * @param value
     */
    public void stopWhenReady(boolean value) {
        th.stop_when_ready(value);
    }

    /**
     * Explicitly sets the upload mode of the torrent. In upload mode, the
     * torrent will not request any pieces. If the torrent is auto managed,
     * it will automatically be taken out of upload mode periodically (see
     * ``session_settings::optimistic_disk_retry``). Torrents are
     * automatically put in upload mode whenever they encounter a disk write
     * error.
     * <p>
     * {@code value} should be true to enter upload mode, and false to leave it.
     * <p>
     * To test if a torrent is in upload mode, call
     * ``torrent_handle::status()`` and inspect
     * ``torrent_status::upload_mode``.
     *
     * @param value
     */
    public void setUploadMode(boolean value) {
        th.set_upload_mode(value);
    }

    /**
     * Enable or disable share mode for this torrent. When in share mode, the
     * torrent will not necessarily be downloaded, especially not the whole
     * of it. Only parts that are likely to be distributed to more than 2
     * other peers are downloaded, and only if the previous prediction was
     * correct.
     *
     * @param value
     */
    public void setShareMode(boolean value) {
        th.set_share_mode(value);
    }

    /**
     * Instructs libtorrent to flush all the disk caches for this torrent and
     * close all file handles. This is done asynchronously and you will be
     * notified that it's complete through {@link com.frostwire.jlibtorrent.alerts.CacheFlushedAlert}.
     * <p>
     * Note that by the time you get the alert, libtorrent may have cached
     * more data for the torrent, but you are guaranteed that whatever cached
     * data libtorrent had by the time you called
     * {@link #flushCache()} has been written to disk.
     */
    public void flushCache() {
        th.flush_cache();
    }

    /**
     * This function returns true if any whole chunk has been downloaded
     * since the torrent was first loaded or since the last time the resume
     * data was saved. When saving resume data periodically, it makes sense
     * to skip any torrent which hasn't downloaded anything since the last
     * time.
     * <p>
     * .. note::
     * A torrent's resume data is considered saved as soon as the alert is
     * posted. It is important to make sure this alert is received and
     * handled in order for this function to be meaningful.
     *
     * @return
     */
    public boolean needSaveResumeData() {
        return th.need_save_resume_data();
    }

    /**
     * changes whether the torrent is auto managed or not. For more info,
     * see queuing_.
     *
     * @param value
     */
    public void setAutoManaged(boolean value) {
        th.auto_managed(value);
    }

    /**
     * Every torrent that is added is assigned a queue position exactly one
     * greater than the greatest queue position of all existing torrents.
     * Torrents that are being seeded have -1 as their queue position, since
     * they're no longer in line to be downloaded.
     * <p>
     * When a torrent is removed or turns into a seed, all torrents with
     * greater queue positions have their positions decreased to fill in the
     * space in the sequence.
     * <p>
     * This function returns the torrent's position in the download
     * queue. The torrents with the smallest numbers are the ones that are
     * being downloaded. The smaller number, the closer the torrent is to the
     * front of the line to be started.
     * <p>
     * The queue position is also available in the torrent_status.
     *
     * @return
     */
    public int getQueuePosition() {
        return th.queue_position();
    }

    /**
     * The ``queue_position_*()`` functions adjust the torrents position in
     * the queue. Up means closer to the front and down means closer to the
     * back of the queue. Top and bottom refers to the front and the back of
     * the queue respectively.
     */
    public void queuePositionUp() {
        th.queue_position_up();
    }

    /**
     * The ``queue_position_*()`` functions adjust the torrents position in
     * the queue. Up means closer to the front and down means closer to the
     * back of the queue. Top and bottom refers to the front and the back of
     * the queue respectively.
     */
    public void queuePositionDown() {
        th.queue_position_down();
    }

    /**
     * The ``queue_position_*()`` functions adjust the torrents position in
     * the queue. Up means closer to the front and down means closer to the
     * back of the queue. Top and bottom refers to the front and the back of
     * the queue respectively.
     */
    public void queuePositionTop() {
        th.queue_position_top();
    }

    /**
     * The ``queue_position_*()`` functions adjust the torrents position in
     * the queue. Up means closer to the front and down means closer to the
     * back of the queue. Top and bottom refers to the front and the back of
     * the queue respectively.
     */
    public void queuePositionBottom() {
        th.queue_position_bottom();
    }

    /**
     * ``save_resume_data()`` generates fast-resume data and returns it as an
     * entry. This entry is suitable for being bencoded. For more information
     * about how fast-resume works, see fast-resume_.
     * <p>
     * The ``flags`` argument is a bitmask of flags ORed together. see
     * save_resume_flags_t
     * <p>
     * This operation is asynchronous, ``save_resume_data`` will return
     * immediately. The resume data is delivered when it's done through an
     * save_resume_data_alert.
     * <p>
     * The fast resume data will be empty in the following cases:
     * <p>
     * 1. The torrent handle is invalid.
     * 2. The torrent is checking (or is queued for checking) its storage, it
     * will obviously not be ready to write resume data.
     * 3. The torrent hasn't received valid metadata and was started without
     * metadata (see libtorrent's metadata-from-peers_ extension)
     * <p>
     * Note that by the time you receive the fast resume data, it may already
     * be invalid if the torrent is still downloading! The recommended
     * practice is to first pause the session, then generate the fast resume
     * data, and then close it down. Make sure to not remove_torrent() before
     * you receive the save_resume_data_alert though. There's no need to
     * pause when saving intermittent resume data.
     * <p>
     * .. warning::
     * If you pause every torrent individually instead of pausing the
     * session, every torrent will have its paused state saved in the
     * resume data!
     * <p>
     * .. warning::
     * The resume data contains the modification timestamps for all files.
     * If one file has been modified when the torrent is added again, the
     * will be rechecked. When shutting down, make sure to flush the disk
     * cache before saving the resume data. This will make sure that the
     * file timestamps are up to date and won't be modified after saving
     * the resume data. The recommended way to do this is to pause the
     * torrent, which will flush the cache and disconnect all peers.
     * <p>
     * .. note::
     * It is typically a good idea to save resume data whenever a torrent
     * is completed or paused. In those cases you don't need to pause the
     * torrent or the session, since the torrent will do no more writing to
     * its files. If you save resume data for torrents when they are
     * paused, you can accelerate the shutdown process by not saving resume
     * data again for paused torrents. Completed torrents should have their
     * resume data saved when they complete and on exit, since their
     * statistics might be updated.
     * <p>
     * In full allocation mode the reume data is never invalidated by
     * subsequent writes to the files, since pieces won't move around. This
     * means that you don't need to pause before writing resume data in full
     * or sparse mode. If you don't, however, any data written to disk after
     * you saved resume data and before the session closed is lost.
     * <p>
     * It also means that if the resume data is out dated, libtorrent will
     * not re-check the files, but assume that it is fairly recent. The
     * assumption is that it's better to loose a little bit than to re-check
     * the entire file.
     * <p>
     * It is still a good idea to save resume data periodically during
     * download as well as when closing down.
     * <p>
     * Example code to pause and save resume data for all torrents and wait
     * for the alerts:
     * <p>
     * .. code:: c++
     * <p>
     * extern int outstanding_resume_data; // global counter of outstanding resume data
     * std::vector<torrent_handle> handles = ses.get_torrents();
     * ses.pause();
     * for (std::vector<torrent_handle>::iterator i = handles.begin();
     * i != handles.end(); ++i)
     * {
     * torrent_handle& h = *i;
     * if (!h.is_valid()) continue;
     * torrent_status s = h.status();
     * if (!s.has_metadata) continue;
     * if (!s.need_save_resume_data()) continue;
     * <p>
     * h.save_resume_data();
     * ++outstanding_resume_data;
     * }
     * <p>
     * while (outstanding_resume_data > 0)
     * {
     * alert const* a = ses.wait_for_alert(seconds(10));
     * <p>
     * // if we don't get an alert within 10 seconds, abort
     * if (a == 0) break;
     * <p>
     * std::auto_ptr<alert> holder = ses.pop_alert();
     * <p>
     * if (alert_cast<save_resume_data_failed_alert>(a))
     * {
     * process_alert(a);
     * --outstanding_resume_data;
     * continue;
     * }
     * <p>
     * save_resume_data_alert const* rd = alert_cast<save_resume_data_alert>(a);
     * if (rd == 0)
     * {
     * process_alert(a);
     * continue;
     * }
     * <p>
     * torrent_handle h = rd->handle;
     * torrent_status st = h.status(torrent_handle::query_save_path | torrent_handle::query_name);
     * std::ofstream out((st.save_path
     * + "/" + st.name + ".fastresume").c_str()
     * , std::ios_base::binary);
     * out.unsetf(std::ios_base::skipws);
     * bencode(std::ostream_iterator<char>(out), *rd->resume_data);
     * --outstanding_resume_data;
     * }
     * <p>
     * .. note::
     * Note how ``outstanding_resume_data`` is a global counter in this
     * example. This is deliberate, otherwise there is a race condition for
     * torrents that was just asked to save their resume data, they posted
     * the alert, but it has not been received yet. Those torrents would
     * report that they don't need to save resume data again, and skipped by
     * the initial loop, and thwart the counter otherwise.
     */
    public void saveResumeData() {
        th.save_resume_data(torrent_handle.save_resume_flags_t.save_info_dict.swigValue());
    }

    /**
     * Returns true if this handle refers to a valid torrent and false if it
     * hasn't been initialized or if the torrent it refers to has been
     * aborted. Note that a handle may become invalid after it has been added
     * to the session. Usually this is because the storage for the torrent is
     * somehow invalid or if the filenames are not allowed (and hence cannot
     * be opened/created) on your filesystem. If such an error occurs, a
     * file_error_alert is generated and all handles that refers to that
     * torrent will become invalid.
     *
     * @return
     */
    public boolean isValid() {
        return th.is_valid();
    }

    /**
     * Generates a magnet URI from the specified torrent. If the torrent
     * handle is invalid, null is returned.
     *
     * @return
     */
    public String makeMagnetUri() {
        return th.is_valid() ? libtorrent.make_magnet_uri(th) : null;
    }

    // ``set_upload_limit`` will limit the upload bandwidth used by this
    // particular torrent to the limit you set. It is given as the number of
    // bytes per second the torrent is allowed to upload.
    // ``set_download_limit`` works the same way but for download bandwidth
    // instead of upload bandwidth. Note that setting a higher limit on a
    // torrent then the global limit
    // (``session_settings::upload_rate_limit``) will not override the global
    // rate limit. The torrent can never upload more than the global rate
    // limit.
    //
    // ``upload_limit`` and ``download_limit`` will return the current limit
    // setting, for upload and download, respectively.
    public int getUploadLimit() {
        return th.upload_limit();
    }

    // ``set_upload_limit`` will limit the upload bandwidth used by this
    // particular torrent to the limit you set. It is given as the number of
    // bytes per second the torrent is allowed to upload.
    // ``set_download_limit`` works the same way but for download bandwidth
    // instead of upload bandwidth. Note that setting a higher limit on a
    // torrent then the global limit
    // (``session_settings::upload_rate_limit``) will not override the global
    // rate limit. The torrent can never upload more than the global rate
    // limit.
    //
    // ``upload_limit`` and ``download_limit`` will return the current limit
    // setting, for upload and download, respectively.
    public void setUploadLimit(int limit) {
        th.set_upload_limit(limit);
    }

    // ``set_upload_limit`` will limit the upload bandwidth used by this
    // particular torrent to the limit you set. It is given as the number of
    // bytes per second the torrent is allowed to upload.
    // ``set_download_limit`` works the same way but for download bandwidth
    // instead of upload bandwidth. Note that setting a higher limit on a
    // torrent then the global limit
    // (``session_settings::upload_rate_limit``) will not override the global
    // rate limit. The torrent can never upload more than the global rate
    // limit.
    //
    // ``upload_limit`` and ``download_limit`` will return the current limit
    // setting, for upload and download, respectively.
    public int getDownloadLimit() {
        return th.download_limit();
    }

    // ``set_upload_limit`` will limit the upload bandwidth used by this
    // particular torrent to the limit you set. It is given as the number of
    // bytes per second the torrent is allowed to upload.
    // ``set_download_limit`` works the same way but for download bandwidth
    // instead of upload bandwidth. Note that setting a higher limit on a
    // torrent then the global limit
    // (``session_settings::upload_rate_limit``) will not override the global
    // rate limit. The torrent can never upload more than the global rate
    // limit.
    //
    // ``upload_limit`` and ``download_limit`` will return the current limit
    // setting, for upload and download, respectively.
    public void setDownloadLimit(int limit) {
        th.set_download_limit(limit);
    }

    /**
     * Enables or disables *sequential download*.
     * <p>
     * When enabled, the piece picker will pick pieces in sequence
     * instead of rarest first. In this mode, piece priorities are ignored,
     * with the exception of priority 7, which are still preferred over the
     * sequential piece order.
     * <p>
     * Enabling sequential download will affect the piece distribution
     * negatively in the swarm. It should be used sparingly.
     *
     * @param sequential
     */
    public void setSequentialDownload(boolean sequential) {
        th.set_sequential_download(sequential);
    }

    // ``force_recheck`` puts the torrent back in a state where it assumes to
    // have no resume data. All peers will be disconnected and the torrent
    // will stop announcing to the tracker. The torrent will be added to the
    // checking queue, and will be checked (all the files will be read and
    // compared to the piece hashes). Once the check is complete, the torrent
    // will start connecting to peers again, as normal.
    public void forceRecheck() {
        th.force_recheck();
    }

    // ``force_reannounce()`` will force this torrent to do another tracker
    // request, to receive new peers. The ``seconds`` argument specifies how
    // many seconds from now to issue the tracker announces.
    //
    // If the tracker's ``min_interval`` has not passed since the last
    // announce, the forced announce will be scheduled to happen immediately
    // as the ``min_interval`` expires. This is to honor trackers minimum
    // re-announce interval settings.
    //
    // The ``tracker_index`` argument specifies which tracker to re-announce.
    // If set to -1 (which is the default), all trackers are re-announce.
    //
    public void forceReannounce(int seconds, int tracker_index) {
        th.force_reannounce(seconds, tracker_index);
    }

    // ``force_reannounce()`` will force this torrent to do another tracker
    // request, to receive new peers. The ``seconds`` argument specifies how
    // many seconds from now to issue the tracker announces.
    //
    // If the tracker's ``min_interval`` has not passed since the last
    // announce, the forced announce will be scheduled to happen immediately
    // as the ``min_interval`` expires. This is to honor trackers minimum
    // re-announce interval settings.
    //
    // The ``tracker_index`` argument specifies which tracker to re-announce.
    // If set to -1 (which is the default), all trackers are re-announce.
    //
    public void forceReannounce(int seconds) {
        th.force_reannounce(seconds);
    }

    /**
     * Force this torrent to do another tracker
     * request, to receive new peers. The ``seconds`` argument specifies how
     * many seconds from now to issue the tracker announces.
     * <p>
     * If the tracker's ``min_interval`` has not passed since the last
     * announce, the forced announce will be scheduled to happen immediately
     * as the ``min_interval`` expires. This is to honor trackers minimum
     * re-announce interval settings.
     * <p>
     * The ``tracker_index`` argument specifies which tracker to re-announce.
     * If set to -1 (which is the default), all trackers are re-announce.
     */
    public void forceReannounce() {
        th.force_reannounce();
    }

    /**
     * Announce the torrent to the DHT immediately.
     */
    public void forceDHTAnnounce() {
        th.force_dht_announce();
    }

    /**
     * Will return a sorted list with the trackers of this torrent.
     * <p>
     * The announce entry contains both a string {@code url} which specify the
     * announce url for the tracker as well as an int {@code tier}, which
     * specifies the order in which this tracker is tried.
     *
     * @return
     */
    public List<AnnounceEntry> trackers() {
        return trackers(th.trackers());
    }

    /**
     * Will send a scrape request to the tracker. A
     * scrape request queries the tracker for statistics such as total number
     * of incomplete peers, complete peers, number of downloads etc.
     * <p>
     * This request will specifically update the ``num_complete`` and
     * ``num_incomplete`` fields in the torrent_status struct once it
     * completes. When it completes, it will generate a scrape_reply_alert.
     * If it fails, it will generate a scrape_failed_alert.
     */
    public void scrapeTracker() {
        th.scrape_tracker();
    }

    /**
     * If you want libtorrent to use another list of trackers for this torrent,
     * you can use {@link #replaceTrackers(List< AnnounceEntry >)} which takes a list of the same
     * form as the one returned from {@link #trackers()} and will replace it.
     * If you want an immediate effect, you have to call {@link #forceReannounce()}.
     * <p>
     * The updated set of trackers will be saved in the resume data, and when
     * a torrent is started with resume data, the trackers from the resume
     * data will replace the original ones.
     *
     * @param trackers
     * @see AnnounceEntry
     */
    public void replaceTrackers(List<AnnounceEntry> trackers) {
        announce_entry_vector v = new announce_entry_vector();

        for (AnnounceEntry t : trackers) {
            v.push_back(t.swig());
        }

        th.replace_trackers(v);
    }

    /**
     * This method will look if the specified tracker is already in the
     * set. If it is, it doesn't do anything. If it's not in the current set
     * of trackers, it will insert it in the tier specified in the
     * {@link AnnounceEntry}.
     * <p>
     * The updated set of trackers will be saved in the resume data, and when
     * a torrent is started with resume data, the trackers from the resume
     * data will replace the original ones.
     */
    public void addTracker(AnnounceEntry tracker) {
        th.add_tracker(tracker.swig());
    }

    // ``add_url_seed()`` adds another url to the torrent's list of url
    // seeds. If the given url already exists in that list, the call has no
    // effect. The torrent will connect to the server and try to download
    // pieces from it, unless it's paused, queued, checking or seeding.
    // ``remove_url_seed()`` removes the given url if it exists already.
    // ``url_seeds()`` return a set of the url seeds currently in this
    // torrent. Note that urls that fails may be removed automatically from
    // the list.
    //
    // See http-seeding_ for more information.
    public void addUrlSeed(String url) {
        th.add_url_seed(url);
    }

    // ``add_url_seed()`` adds another url to the torrent's list of url
    // seeds. If the given url already exists in that list, the call has no
    // effect. The torrent will connect to the server and try to download
    // pieces from it, unless it's paused, queued, checking or seeding.
    // ``remove_url_seed()`` removes the given url if it exists already.
    // ``url_seeds()`` return a set of the url seeds currently in this
    // torrent. Note that urls that fails may be removed automatically from
    // the list.
    //
    // See http-seeding_ for more information.
    public void removeUrlSeed(String url) {
        th.remove_url_seed(url);
    }

    // These functions are identical as the ``*_url_seed()`` variants, but
    // they operate on `BEP 17`_ web seeds instead of `BEP 19`_.
    //
    // See http-seeding_ for more information.
    public void addHttpSeed(String url) {
        th.add_url_seed(url);
    }

    // These functions are identical as the ``*_url_seed()`` variants, but
    // they operate on `BEP 17`_ web seeds instead of `BEP 19`_.
    //
    // See http-seeding_ for more information.
    public void removeHttpSeed(String url) {
        th.remove_http_seed(url);
    }

    // ``use_interface()`` sets the network interface this torrent will use
    // when it opens outgoing connections. By default, it uses the same
    // interface as the session uses to listen on. The parameter must be a
    // string containing one or more, comma separated, ip-address (either an
    // IPv4 or IPv6 address). When specifying multiple interfaces, the
    // torrent will round-robin which interface to use for each outgoing
    // connection. This is useful for clients that are multi-homed.
//    public void useInterface(String netInterface) {
//        th.use_interface(netInterface);
//    }

    // Fills the specified ``std::vector<int>`` with the availability for
    // each piece in this torrent. libtorrent does not keep track of
    // availability for seeds, so if the torrent is seeding the availability
    // for all pieces is reported as 0.
    //
    // The piece availability is the number of peers that we are connected
    // that has advertised having a particular piece. This is the information
    // that libtorrent uses in order to prefer picking rare pieces.
    public int[] getPieceAvailability() {
        int_vector v = new int_vector();
        th.piece_availability(v);
        return Vectors.int_vector2ints(v);
    }

    // These functions are used to set and get the prioritiy of individual
    // pieces. By default all pieces have priority 1. That means that the
    // random rarest first algorithm is effectively active for all pieces.
    // You may however change the priority of individual pieces. There are 8
    // different priority levels:
    //
    //  0. piece is not downloaded at all
    //  1. normal priority. Download order is dependent on availability
    //  2. higher than normal priority. Pieces are preferred over pieces with
    //     the same availability, but not over pieces with lower availability
    //  3. pieces are as likely to be picked as partial pieces.
    //  4. pieces are preferred over partial pieces, but not over pieces with
    //     lower availability
    //  5. *currently the same as 4*
    //  6. piece is as likely to be picked as any piece with availability 1
    //  7. maximum priority, availability is disregarded, the piece is
    //     preferred over any other piece with lower priority
    //
    // The exact definitions of these priorities are implementation details,
    // and subject to change. The interface guarantees that higher number
    // means higher priority, and that 0 means do not download.
    //
    // ``piece_priority`` sets or gets the priority for an individual piece,
    // specified by ``index``.
    //
    // ``prioritize_pieces`` takes a vector of integers, one integer per
    // piece in the torrent. All the piece priorities will be updated with
    // the priorities in the vector.
    //
    // ``piece_priorities`` returns a vector with one element for each piece
    // in the torrent. Each element is the current priority of that piece.
    public void piecePriority(int index, Priority priority) {
        th.piece_priority(index, priority.swig());
    }

    public Priority piecePriority(int index) {
        return Priority.fromSwig(th.piece_priority(index));
    }

    public void prioritizePieces(Priority[] priorities) {
        th.prioritize_pieces(SessionManager.array2int_vector(priorities));
    }

    public Priority[] getPiecePriorities() {
        int_vector v = th.piece_priorities();
        int size = (int) v.size();
        Priority[] arr = new Priority[size];
        for (int i = 0; i < size; i++) {
            arr[i] = Priority.fromSwig(v.get(i));
        }
        return arr;
    }

    /**
     * index must be in the range [0, number_of_files).
     * <p>
     * The priority values are the same as for piece_priority().
     * <p>
     * Whenever a file priority is changed, all other piece priorities are
     * reset to match the file priorities. In order to maintain sepcial
     * priorities for particular pieces, piece_priority() has to be called
     * again for those pieces.
     * <p>
     * You cannot set the file priorities on a torrent that does not yet have
     * metadata or a torrent that is a seed. ``file_priority(int, int)`` and
     * prioritize_files() are both no-ops for such torrents.
     *
     * @param index
     * @param priority
     */
    public void setFilePriority(int index, Priority priority) {
        th.file_priority(index, priority.swig());
    }

    /**
     * index must be in the range [0, number_of_files).
     * <p>
     * queries or sets the priority of file index.
     *
     * @param index
     * @return
     */
    public Priority getFilePriority(int index) {
        return Priority.fromSwig(th.file_priority(index));
    }

    /**
     * Takes a vector that has at as many elements as
     * there are files in the torrent. Each entry is the priority of that
     * file. The function sets the priorities of all the pieces in the
     * torrent based on the vector.
     *
     * @param priorities
     */
    public void prioritizeFiles(Priority[] priorities) {
        th.prioritize_files(SessionManager.array2int_vector(priorities));
    }

    /**
     * Returns a vector with the priorities of all files.
     *
     * @return
     */
    public Priority[] filePriorities() {
        int_vector v = th.file_priorities();
        int size = (int) v.size();
        Priority[] arr = new Priority[size];
        for (int i = 0; i < size; i++) {
            arr[i] = Priority.fromSwig(v.get(i));
        }
        return arr;
    }

    /**
     * This function sets or resets the deadline associated with a specific
     * piece index (``index``). libtorrent will attempt to download this
     * entire piece before the deadline expires. This is not necessarily
     * possible, but pieces with a more recent deadline will always be
     * prioritized over pieces with a deadline further ahead in time. The
     * deadline (and flags) of a piece can be changed by calling this
     * function again.
     * <p>
     * If the piece is already downloaded when this call is made, nothing
     * happens, unless the alert_when_available flag is set, in which case it
     * will do the same thing as calling read_piece() for ``index``.
     *
     * @param index
     * @param deadline
     */
    public void setPieceDeadline(int index, int deadline) {
        th.set_piece_deadline(index, deadline);
    }

    /**
     * This function sets or resets the deadline associated with a specific
     * piece index (``index``). libtorrent will attempt to download this
     * entire piece before the deadline expires. This is not necessarily
     * possible, but pieces with a more recent deadline will always be
     * prioritized over pieces with a deadline further ahead in time. The
     * deadline (and flags) of a piece can be changed by calling this
     * function again.
     * <p>
     * The ``flags`` parameter can be used to ask libtorrent to send an alert
     * once the piece has been downloaded, by passing alert_when_available.
     * When set, the read_piece_alert alert will be delivered, with the piece
     * data, when it's downloaded.
     * <p>
     * If the piece is already downloaded when this call is made, nothing
     * happens, unless the alert_when_available flag is set, in which case it
     * will do the same thing as calling read_piece() for ``index``.
     *
     * @param index
     * @param deadline
     * @param flags
     */
    public void setPieceDeadline(int index, int deadline, DeadlineFlags flags) {
        th.set_piece_deadline(index, deadline, flags.getSwig());
    }

    /**
     * Removes the deadline from the piece. If it
     * hasn't already been downloaded, it will no longer be considered a
     * priority.
     *
     * @param index
     */
    public void resetPieceDeadline(int index) {
        th.reset_piece_deadline(index);
    }

    /**
     * Removes deadlines on all pieces in the torrent.
     * As if {@link #resetPieceDeadline(int)} was called on all pieces.
     */
    public void clearPieceDeadlines() {
        th.clear_piece_deadlines();
    }

    /**
     * This function fills in the supplied vector with the number of
     * bytes downloaded of each file in this torrent. The progress values are
     * ordered the same as the files in the torrent_info. This operation is
     * not very cheap. Its complexity is *O(n + mj)*. Where *n* is the number
     * of files, *m* is the number of downloading pieces and *j* is the
     * number of blocks in a piece.
     * <p>
     * The ``flags`` parameter can be used to specify the granularity of the
     * file progress. If left at the default value of 0, the progress will be
     * as accurate as possible, but also more expensive to calculate. If
     * ``torrent_handle::piece_granularity`` is specified, the progress will
     * be specified in piece granularity. i.e. only pieces that have been
     * fully downloaded and passed the hash check count. When specifying
     * piece granularity, the operation is a lot cheaper, since libtorrent
     * already keeps track of this internally and no calculation is required.
     *
     * @param flags
     * @return
     */
    public long[] getFileProgress(FileProgressFlags flags) {
        int64_vector v = new int64_vector();
        th.file_progress(v, flags.getSwig());
        return Vectors.int64_vector2longs(v);
    }

    /**
     * This function fills in the supplied vector with the number of
     * bytes downloaded of each file in this torrent. The progress values are
     * ordered the same as the files in the torrent_info. This operation is
     * not very cheap. Its complexity is *O(n + mj)*. Where *n* is the number
     * of files, *m* is the number of downloading pieces and *j* is the
     * number of blocks in a piece.
     *
     * @return
     */
    public long[] getFileProgress() {
        int64_vector v = new int64_vector();
        th.file_progress(v);
        return Vectors.int64_vector2longs(v);
    }

    /**
     * The path to the directory where this torrent's files are stored.
     * It's typically the path as was given to async_add_torrent() or
     * add_torrent() when this torrent was started.
     *
     * @return
     */
    public String getSavePath() {
        torrent_status ts = th.status(status_flags_t.query_save_path.swigValue());
        return ts.getSave_path();
    }

    /**
     * The name of the torrent. Typically this is derived from the
     * .torrent file. In case the torrent was started without metadata,
     * and hasn't completely received it yet, it returns the name given
     * to it when added to the session.
     *
     * @return
     */
    public String getName() {
        torrent_status ts = th.status(status_flags_t.query_name.swigValue());
        return ts.getName();
    }

    /**
     * Moves the file(s) that this torrent are currently seeding from or
     * downloading to. If the given {@code savePath} is not located on the same
     * drive as the original save path, the files will be copied to the new
     * drive and removed from their original location. This will block all
     * other disk IO, and other torrents download and upload rates may drop
     * while copying the file.
     * <p>
     * Since disk IO is performed in a separate thread, this operation is
     * also asynchronous. Once the operation completes, the
     * {@link com.frostwire.jlibtorrent.alerts.StorageMovedAlert} is generated,
     * with the new path as the message. If the move fails for some reason,
     * {@link com.frostwire.jlibtorrent.alerts.StorageMovedFailedAlert}
     * generated instead, containing the error message.
     * <p>
     * The {@code flags} argument determines the behavior of the copying/moving
     * of the files in the torrent. see move_flags_t.
     * <p>
     * * always_replace_files = 0
     * * fail_if_exist = 1
     * * dont_replace = 2
     * <p>
     * ``always_replace_files`` is the default and replaces any file that
     * exist in both the source directory and the target directory.
     * <p>
     * ``fail_if_exist`` first check to see that none of the copy operations
     * would cause an overwrite. If it would, it will fail. Otherwise it will
     * proceed as if it was in ``always_replace_files`` mode. Note that there
     * is an inherent race condition here. If the files in the target
     * directory appear after the check but before the copy or move
     * completes, they will be overwritten. When failing because of files
     * already existing in the target path, the ``error`` of
     * ``move_storage_failed_alert`` is set to
     * ``boost::system::errc::file_exists``.
     * <p>
     * The intention is that a client may use this as a probe, and if it
     * fails, ask the user which mode to use. The client may then re-issue
     * the ``move_storage`` call with one of the other modes.
     * <p>
     * ``dont_replace`` always takes the existing file in the target
     * directory, if there is one. The source files will still be removed in
     * that case.
     * <p>
     * Files that have been renamed to have absolute pahts are not moved by
     * this function. Keep in mind that files that don't belong to the
     * torrent but are stored in the torrent's directory may be moved as
     * well. This goes for files that have been renamed to absolute paths
     * that still end up inside the save path.
     *
     * @param savePath
     * @param flags
     */
    public void moveStorage(String savePath, int flags) {
        th.move_storage(savePath, flags);
    }

    /**
     * Sames as calling {@link #moveStorage(String, int)} with flags 0.
     *
     * @param savePath
     * @see #moveStorage(String, int)
     */
    public void moveStorage(String savePath) {
        th.move_storage(savePath);
    }

    /**
     * Renames the file with the given index asynchronously. The rename
     * operation is complete when either a {@link com.frostwire.jlibtorrent.alerts.FileRenamedAlert}  or
     * {@link com.frostwire.jlibtorrent.alerts.FileRenameFailedAlert} is posted.
     *
     * @param index
     * @param newName
     */
    public void renameFile(int index, String newName) {
        th.rename_file(index, newName);
    }

    /**
     * Flags for {@link #addPiece(int, byte[], int)}.
     */
    public enum Flags {

        /**
         *
         */
        OVERWRITE_EXISTING(torrent_handle.flags_t.overwrite_existing.swigValue()),

        /**
         *
         */
        UNKNOWN(-1);

        Flags(int swigValue) {
            this.swigValue = swigValue;
        }

        private final int swigValue;

        /**
         * @return
         */
        public int swig() {
            return swigValue;
        }

        /**
         * @param swigValue
         * @return
         */
        public static Flags fromSwig(int swigValue) {
            Flags[] enumValues = Flags.class.getEnumConstants();
            for (Flags ev : enumValues) {
                if (ev.swig() == swigValue) {
                    return ev;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Flags to pass in to status() to specify which properties of the
     * torrent to query for. By default all flags are set.
     */
    public enum StatusFlags {

        /**
         * calculates ``distributed_copies``, ``distributed_full_copies`` and
         * ``distributed_fraction``.
         */
        QUERY_DISTRIBUTED_COPIES(status_flags_t.query_distributed_copies.swigValue()),

        /**
         * includes partial downloaded blocks in ``total_done`` and
         * ``total_wanted_done``.
         */
        QUERY_ACCURATE_DOWNLOAD_COUNTERS(status_flags_t.query_accurate_download_counters.swigValue()),

        /**
         * includes ``last_seen_complete``.
         */
        QUERY_LAST_SEEN_COMPLETE(status_flags_t.query_last_seen_complete.swigValue()),

        /**
         * includes ``pieces``.
         */
        QUERY_PIECES(status_flags_t.query_pieces.swigValue()),

        /**
         * includes ``verified_pieces`` (only applies to torrents in *seed mode*).
         */
        QUERY_VERIFIED_PIECES(status_flags_t.query_verified_pieces.swigValue()),

        /**
         * includes ``torrent_file``, which is all the static information from the .torrent file.
         */
        QUERY_TORRENT_FILE(status_flags_t.query_torrent_file.swigValue()),

        /**
         * includes ``name``, the name of the torrent. This is either derived
         * from the .torrent file, or from the ``&dn=`` magnet link argument
         * or possibly some other source. If the name of the torrent is not
         * known, this is an empty string.
         */
        QUERY_NAME(status_flags_t.query_name.swigValue()),

        /**
         * includes ``save_path``, the path to the directory the files of the
         * torrent are saved to.
         */
        QUERY_SAVE_PATH(status_flags_t.query_save_path.swigValue());

        private StatusFlags(int swigValue) {
            this.swigValue = swigValue;
        }

        private final int swigValue;

        public int getSwig() {
            return swigValue;
        }
    }

    /**
     * Flags for {@link #setPieceDeadline(int, int, com.frostwire.jlibtorrent.TorrentHandle.DeadlineFlags)}.
     */
    public enum DeadlineFlags {

        ALERT_WHEN_AVAILABLE(torrent_handle.deadline_flags.alert_when_available.swigValue());

        private DeadlineFlags(int swigValue) {
            this.swigValue = swigValue;
        }

        private final int swigValue;

        public int getSwig() {
            return swigValue;
        }
    }

    /**
     * Flags to be passed in {@link #getFileProgress(com.frostwire.jlibtorrent.TorrentHandle.FileProgressFlags)}.
     */
    public enum FileProgressFlags {

        DEFAULT(0),

        /**
         * only calculate file progress at piece granularity. This makes
         * the file_progress() call cheaper and also only takes bytes that
         * have passed the hash check into account, so progress cannot
         * regress in this mode.
         */
        PIECE_GRANULARITY(torrent_handle.file_progress_flags_t.piece_granularity.swigValue());

        private FileProgressFlags(int swigValue) {
            this.swigValue = swigValue;
        }

        private final int swigValue;

        public int getSwig() {
            return swigValue;
        }
    }

    static ArrayList<AnnounceEntry> trackers(announce_entry_vector v) {
        int size = (int) v.size();
        ArrayList<AnnounceEntry> l = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            l.add(new AnnounceEntry(v.get(i)));
        }

        return l;
    }
}
