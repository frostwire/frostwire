/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.frostclick.Slide;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The TransferManager singleton with every collaborator mocked. The engine itself needs
 * libtorrent's native library, so only the paths that stay on the Java side run here. A distinct
 * SDK level keeps the singleton in a Robolectric sandbox of its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
public class TransferManagerTest {
  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final String MAGNET = "magnet:?xt=urn:btih:" + HASH + "&dn=test";

  private final List<Runnable> downloader = new ArrayList<>();
  private final List<Runnable> configThread = new ArrayList<>();
  private final List<Runnable> misc = new ArrayList<>();
  private ConfigurationManager config;
  private NetworkManager network;
  private Engine engine;
  private File dir;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<NetworkManager> networkStatic;
  private MockedStatic<MainApplication> main;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<Platforms> platforms;
  private TransferManager manager;

  @Before
  public void setUp() throws Exception {
    config = mock(ConfigurationManager.class);
    network = mock(NetworkManager.class);
    engine = mock(Engine.class);
    dir = Files.createTempDirectory("transfers").toFile();
    when(config.getStoragePath()).thenReturn(dir.getAbsolutePath());
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              Runnable r = call.getArgument(1);
              switch ((SystemUtils.HandlerThreadName) call.getArgument(0)) {
                case DOWNLOADER:
                  downloader.add(r);
                  break;
                case CONFIG_MANAGER:
                  configThread.add(r);
                  break;
                default:
                  misc.add(r);
              }
              return null;
            });
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    networkStatic = mockStatic(NetworkManager.class);
    networkStatic.when(NetworkManager::instance).thenReturn(network);
    main = mockStatic(MainApplication.class);
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    FileSystem fs = mock(FileSystem.class);
    when(fs.isDirectory(any())).thenReturn(true);
    platforms = mockStatic(Platforms.class);
    platforms.when(Platforms::data).thenReturn(new File(dir, "data"));
    platforms.when(Platforms::temp).thenReturn(new File(dir, "temp"));
    platforms.when(Platforms::fileSystem).thenReturn(fs);
    manager = TransferManager.instance();
    manager.onShutdown(false); // forget whatever an earlier test left behind
    manager.resetStartedTransfers();
    manager.clearDownloadsToReview();
  }

  @After
  public void tearDown() {
    manager.onShutdown(false);
    platforms.close();
    engineStatic.close();
    main.close();
    networkStatic.close();
    configStatic.close();
    system.close();
    deleteRecursively(dir);
  }

  @Test
  public void theManagerIsASingleton() {
    assertSame(manager, TransferManager.instance());
    assertTrue(manager.getTransfers().isEmpty());
  }

  @Test
  public void preferenceChangesAreAppliedOnTheConfigThreadOnlyWhileTheEngineIsUsable() {
    // the singleton registered its listener when it was first created; ask it to register again
    manager.reset();
    ArgumentCaptor<ConfigurationRepository.OnPreferenceChangeListener> captor =
        ArgumentCaptor.forClass(ConfigurationRepository.OnPreferenceChangeListener.class);
    verify(config, org.mockito.Mockito.atLeastOnce()).registerOnPreferenceChange(captor.capture());
    ConfigurationRepository.OnPreferenceChangeListener listener = captor.getValue();
    configThread.clear();
    listener.onPreferenceChanged(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED);
    assertEquals(1, configThread.size());
    // the engine already failed to initialize: nothing is applied
    main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(true);
    configThread.get(0).run();
    main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()), never());
    // the engine cannot be created here (no native library): the failure is recorded once
    main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
    configThread.get(0).run();
    main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()));
  }

  @Test
  public void restoringTorrentsIsSkippedWhenTheEngineIsBrokenAndRecordedWhenItCannotBeCreated() {
    manager.ensureTorrentsRestored();
    assertFalse(downloader.isEmpty());
    main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(true);
    downloader.get(downloader.size() - 1).run();
    main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()), never());
    main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
    downloader.get(downloader.size() - 1).run();
    main.verify(() -> MainApplication.recordBTEngineInitializationFailure(any()));
  }

  @Test
  public void shuttingDownWhileConnectedKeepsTheTransfersButADisconnectClearsThem() {
    manager.downloadTorrent(MAGNET, mock(TorrentFetcherListener.class));
    assertEquals(1, manager.getTransfers().size());
    manager.onShutdown(true);
    assertEquals(1, manager.getTransfers().size());
    manager.onShutdown(false);
    assertTrue(manager.getTransfers().isEmpty());
    assertNull(manager.getBittorrentDownload(HASH));
  }

  @Test
  public void mobileDataSavingsRefuseTorrentsAndCloudDownloadsWithAPlaceholder() {
    when(network.isDataMobileUp()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    assertTrue(manager.isMobileAndDataSavingsOn());
    TorrentSearchResult torrent = torrent("test:a", HASH);
    Transfer refusedTorrent = manager.download(torrent);
    assertTrue(refusedTorrent instanceof InvalidBittorrentDownload);
    assertEquals(
        R.string.torrent_transfer_aborted_on_mobile_data,
        ((InvalidBittorrentDownload) refusedTorrent).getReasonResId());
    Transfer refusedCloud = manager.download(mock(HttpSearchResult.class));
    assertTrue(refusedCloud instanceof InvalidDownload);
    assertEquals(
        R.string.cloud_download_aborted_on_mobile_data,
        ((InvalidDownload) refusedCloud).getReasonResId());
    assertTrue(
        manager.isBittorrentDownloadAndMobileDataSavingsOn(
            new TorrentFetcherDownload(manager, new TorrentUrlInfo(MAGNET), null, Runnable::run)));
    assertTrue(manager.getTransfers().isEmpty());
  }

  @Test
  public void theVpnGuardRefusesTorrentsWithoutAVpn() {
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY)).thenReturn(true);
    assertTrue(manager.isBittorrentOnVpnOnlyAndNoVpn());
    assertTrue(manager.isBittorrentSearchResultAndVpnRequired(torrent("test:v", HASH)));
    assertFalse(manager.isBittorrentSearchResultAndVpnRequired(mock(HttpSearchResult.class)));
    Transfer refused = manager.download(torrent("test:v", HASH));
    assertEquals(
        R.string.cannot_start_engine_without_vpn,
        ((InvalidBittorrentDownload) refused).getReasonResId());
    BittorrentDownload viaUri = manager.downloadTorrent(MAGNET, null);
    assertTrue(viaUri instanceof InvalidBittorrentDownload);
    // a VPN, or a tunnel, lifts the guard
    when(network.isVpnConnected()).thenReturn(true);
    assertFalse(manager.isBittorrentOnVpnOnlyAndNoVpn());
    when(network.isVpnConnected()).thenReturn(false);
    when(network.isTunnelUp()).thenReturn(true);
    assertFalse(manager.isBittorrentOnVpnOnlyAndNoVpn());
  }

  @Test
  public void aTorrentSearchResultBecomesAMetadataFetchTransferOnlyOnce() {
    TorrentSearchResult result = torrent("test:fetch", HASH.toUpperCase());
    Transfer first = manager.download(result);
    assertTrue(first instanceof TorrentFetcherDownload);
    assertEquals(1, manager.startedTransfers());
    assertEquals(1, manager.getTransfers().size());
    assertTrue(manager.isBittorrentDownload(first));
    // asking again returns the transfer that is already there
    assertSame(first, manager.download(result));
    assertEquals(1, manager.getTransfers().size());
    assertSame(first, manager.getBittorrentDownload(HASH.toUpperCase()));
  }

  @Test
  public void aTorrentWithoutAHashIsRecognizedByItsUri() {
    TorrentSearchResult result = torrent("test:no-hash", null);
    Transfer first = manager.download(result);
    assertTrue(first instanceof TorrentFetcherDownload);
    assertSame(first, manager.download(torrent("test:no-hash", "")));
    assertEquals(1, manager.getTransfers().size());
  }

  @Test
  public void aTorrentResultWithoutAnyUrlStartsNothing() {
    assertNull(manager.download(torrent(null, HASH.replace('0', '1'))));
    assertTrue(manager.getTransfers().isEmpty());
  }

  @Test
  public void aCrawledTorrentNeedsTheEngineAndFailsAsAPlaceholderWithoutIt() {
    TorrentCrawledSearchResult crawled = mock(TorrentCrawledSearchResult.class);
    when(crawled.getHash()).thenReturn(null);
    Transfer transfer = manager.download(crawled);
    assertTrue(transfer instanceof InvalidBittorrentDownload);
    assertEquals(R.string.empty_string, ((InvalidBittorrentDownload) transfer).getReasonResId());
  }

  @Test
  public void httpSearchResultsStartHttpDownloads() {
    HttpSearchResult sr = mock(HttpSearchResult.class);
    when(sr.getDownloadUrl()).thenReturn("http://127.0.0.1:1/a.mp3");
    when(sr.getFilename()).thenReturn("a.mp3");
    when(sr.getDisplayName()).thenReturn("a");
    when(sr.getDetailsUrl()).thenReturn("details-a");
    Transfer transfer = manager.download(sr);
    assertTrue(transfer instanceof UIHttpDownload);
    assertTrue(manager.getTransfers().contains(transfer));
    assertFalse(manager.isBittorrentDownload(transfer));
    assertEquals(1, manager.startedTransfers());
    assertTrue(manager.remove(transfer));
    assertFalse(manager.remove(transfer));
    assertTrue(manager.getTransfers().isEmpty());
  }

  @Test
  public void slidesAndCompositeResultsStartHttpDownloads() {
    Slide slide = new Slide();
    slide.title = "Slide";
    slide.httpDownloadURL = "http://127.0.0.1:1/slide.zip";
    Transfer fromSlide = manager.download(new HttpSlideSearchResult(slide));
    assertTrue(fromSlide instanceof UIHttpDownload);

    CompositeFileSearchResult downloadable = mock(CompositeFileSearchResult.class);
    when(downloadable.isHttpDownloadable()).thenReturn(true);
    when(downloadable.getHttpDownloadUrl()).thenReturn(Optional.of("http://127.0.0.1:1/c.mp4"));
    when(downloadable.getFilename()).thenReturn("c.mp4");
    when(downloadable.getDisplayName()).thenReturn("c");
    Transfer fromComposite = manager.download(downloadable);
    assertTrue(fromComposite instanceof UIHttpDownload);

    CompositeFileSearchResult streamOnly = mock(CompositeFileSearchResult.class);
    when(streamOnly.isHttpDownloadable()).thenReturn(false);
    assertNull(manager.download(streamOnly));

    assertEquals(2, manager.getTransfers().size());
    assertNull(
        "an unknown kind of result starts nothing", manager.download(mock(SearchResult.class)));
    manager.remove(fromSlide);
    manager.remove(fromComposite);
    assertFalse(manager.remove(null));
  }

  @Test
  public void torrentLinksAreCheckedBeforeTheyAreFetched() {
    // unsupported scheme
    BittorrentDownload ftp = manager.downloadTorrent("ftp://example.invalid/a.torrent", null);
    assertTrue(ftp instanceof InvalidBittorrentDownload);
    assertEquals(
        R.string.torrent_scheme_download_not_supported,
        ((InvalidBittorrentDownload) ftp).getReasonResId());
    // a link without a scheme is refused too
    assertTrue(manager.downloadTorrent("not a link", null) instanceof InvalidBittorrentDownload);
  }

  @Test
  public void magnetLinksStartAMetadataFetchEvenWhenOverEncoded() {
    String encoded = "magnet:?xt=urn%3Abtih%3A" + HASH + "&dn=x";
    BittorrentDownload download = manager.downloadTorrent("  " + encoded + " ", null, "My title");
    assertTrue(download instanceof TorrentFetcherDownload);
    assertEquals("My title", download.getDisplayName());
    assertEquals(1, manager.startedTransfers());
    assertSame(download, manager.getBittorrentDownload(HASH));
    // the same link again is already being fetched
    assertNull(manager.downloadTorrent(MAGNET, null));
    assertTrue(manager.downloadTorrent("", null) instanceof InvalidBittorrentDownload);
  }

  @Test
  public void aFetcherListenerIsKeptOnTheTransferAndTheStartIsCounted() {
    TorrentFetcherListener listener = mock(TorrentFetcherListener.class);
    BittorrentDownload download =
        manager.downloadTorrent(MAGNET, listener, "Title", new byte[] {1, 2, 3});
    assertTrue(download instanceof TorrentFetcherDownload);
    assertSame(listener, ((TorrentFetcherDownload) download).getTorrentFetcherListener());
    assertEquals(1, manager.startedTransfers());
    manager.resetStartedTransfers();
    assertEquals(0, manager.startedTransfers());
  }

  @Test
  public void aLocalTorrentFileIsReadInTheBackgroundAndHandedToTheListener() throws Exception {
    File torrent = new File(dir, "local.torrent");
    Files.write(torrent.toPath(), new byte[] {9, 8, 7});
    TorrentFetcherListener listener = mock(TorrentFetcherListener.class);
    BittorrentDownload download = manager.downloadTorrent(torrent.toURI().toString(), listener);
    assertNull(download);
    assertEquals(1, misc.size());
    misc.get(0).run();
    verify(listener).onTorrentInfoFetched(eq(new byte[] {9, 8, 7}), eq(null), anyLong());
    // an unreadable file is swallowed
    assertTrue(torrent.delete());
    misc.get(0).run();
  }

  @Test
  public void aLocalTorrentFileWithoutAListenerNeedsTheEngine() {
    BittorrentDownload download = manager.downloadTorrent("file:///tmp/none.torrent", null);
    assertTrue(download instanceof InvalidBittorrentDownload);
  }

  @Test
  public void activeCountsAndBandwidthStayOnTheJavaSideWhenTheEngineFailed() {
    assertEquals(0, manager.getActiveDownloads());
    assertEquals(0, manager.getActiveUploads());
    main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(true);
    assertEquals(0, manager.getDownloadsBandwidth());
    assertEquals(0.0, manager.getUploadsBandwidth(), 0.0);
    // an engine that cannot be created is recorded and counts as no traffic
    main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
    assertEquals(0, manager.getDownloadsBandwidth());
    assertEquals(0.0, manager.getUploadsBandwidth(), 0.0);
  }

  @Test
  public void reviewCountersAreIncrementedAndCleared() {
    assertEquals(0, manager.getDownloadsToReview());
    manager.incrementDownloadsToReview();
    manager.incrementDownloadsToReview();
    assertEquals(2, manager.getDownloadsToReview());
    manager.clearDownloadsToReview();
    assertEquals(0, manager.getDownloadsToReview());
    manager.incrementStartedTransfers();
    assertEquals(1, manager.startedTransfers());
  }

  @Test
  public void bulkOperationsOnMetadataFetchesAreHarmless() {
    BittorrentDownload download =
        manager.downloadTorrent(MAGNET, mock(TorrentFetcherListener.class));
    manager.pauseTorrents();
    manager.stopSeedingTorrents();
    manager.suspendSeedingTorrents();
    manager.suspendTorrentsForPolicy();
    manager.resumePolicySuspendedDownloads();
    manager.resumePolicySuspendedSeeding();
    manager.resumeResumableTransfers();
    manager.seedFinishedTransfers();
    manager.forceReannounceTorrents();
    assertFalse(download.isComplete());
    assertSame(download, manager.getBittorrentDownload(HASH));
  }

  @Test
  public void policiesThatBlockTorrentsStopTheResumeAndSeedPasses() {
    when(network.isDataMobileUp()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    manager.resumePolicySuspendedSeeding();
    manager.resumeResumableTransfers();
    manager.seedFinishedTransfers();
    assertFalse(manager.isBittorrentDownloadAndMobileDataSavingsOff(null));
  }

  @Test
  public void httpTransfersCanBeStoppedAndTheirProgressIsReported() {
    HttpSearchResult sr = mock(HttpSearchResult.class);
    when(sr.getDownloadUrl()).thenReturn("http://127.0.0.1:1/stop.mp3");
    when(sr.getFilename()).thenReturn("stop.mp3");
    when(sr.getDisplayName()).thenReturn("stop");
    Transfer transfer = manager.download(sr);
    manager.stopHttpTransfers();
    manager.clearComplete();
    assertNotNull(transfer);
    manager.remove(transfer);
    assertFalse(manager.isHttpDownloadInProgress());
  }

  @Test
  public void clearingCompleteTransfersSkipsNullsAndUnfinishedOnes() {
    BittorrentDownload download =
        manager.downloadTorrent(MAGNET, mock(TorrentFetcherListener.class));
    manager.clearComplete();
    assertNotNull(download);
  }

  @Test
  public void resumabilityOfFinishedTorrentsFollowsTheSeedingSettings() {
    BittorrentDownload finished = mock(BittorrentDownload.class);
    when(finished.isFinished()).thenReturn(true);
    when(finished.isPaused()).thenReturn(true);
    // seeding disabled: a finished torrent is paused only because it is done
    when(config.isSeedFinishedTorrents()).thenReturn(false);
    assertFalse(TransferManager.isResumable(finished));
    // seeding only on wifi, and no wifi
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(config.isSeedingEnabledOnlyForWifi()).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(false);
    assertFalse(TransferManager.isResumable(finished));
    // wifi up
    when(network.isDataWIFIUp()).thenReturn(true);
    assertTrue(TransferManager.isResumable(finished));
    // seeding on any network
    when(config.isSeedingEnabledOnlyForWifi()).thenReturn(false);
    when(network.isDataWIFIUp()).thenReturn(false);
    assertTrue(TransferManager.isResumable(finished));
    // an unfinished torrent is resumable exactly when paused
    BittorrentDownload paused = mock(BittorrentDownload.class);
    when(paused.isPaused()).thenReturn(true);
    assertTrue(TransferManager.isResumable(paused));
    assertFalse(TransferManager.isResumable(mock(BittorrentDownload.class)));
  }

  @Test
  public void theEngineConnectionStateComesFromTheEngine() {
    assertFalse(manager.isBittorrentDisconnected());
    when(engine.isStopping()).thenReturn(true);
    assertTrue(manager.isBittorrentDisconnected());
    when(engine.isStopping()).thenReturn(false);
    when(engine.isDisconnected()).thenReturn(true);
    assertTrue(manager.isBittorrentDisconnected());
    when(engine.isDisconnected()).thenReturn(false);
    when(engine.isStopped()).thenReturn(true);
    assertTrue(manager.isBittorrentDisconnected());
  }

  @Test
  public void deletingStartedTorrentFilesIsASetting() {
    assertFalse(manager.isDeleteStartedTorrentEnabled());
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_DELETE_STARTED_TORRENT_FILES))
        .thenReturn(true);
    assertTrue(manager.isDeleteStartedTorrentEnabled());
  }

  @Test
  public void theAvailableBytesOfTheStorageMountAreReadFromTheConfiguredPath() {
    assertTrue(TransferManager.getCurrentMountAvailableBytes() >= 0);
  }

  @Test
  public void aMissingTorrentHandleHasNoUiDownload() {
    assertNull(manager.ensureUiDownload(null));
  }

  private BittorrentDownload registered(String hash, TransferState state) {
    BittorrentDownload bt = mock(BittorrentDownload.class);
    when(bt.getInfoHash()).thenReturn(hash);
    when(bt.getState()).thenReturn(state);
    manager.registerBittorrentDownload(bt);
    return bt;
  }

  private UIBittorrentDownload registeredUi(String hash) {
    UIBittorrentDownload ui = mock(UIBittorrentDownload.class);
    when(ui.getInfoHash()).thenReturn(hash);
    when(ui.getState()).thenReturn(TransferState.DOWNLOADING);
    manager.registerBittorrentDownload(ui);
    return ui;
  }

  @Test
  public void registeredTransfersAreListedIndexedByHashAndRemovable() {
    BittorrentDownload bt = registered("h1", TransferState.DOWNLOADING);
    assertTrue(manager.getTransfers().contains(bt));
    assertSame(bt, manager.getBittorrentDownload("h1"));
    assertTrue(manager.remove(bt));
    assertNull(manager.getBittorrentDownload("h1"));
    assertFalse(manager.remove(bt));
    assertTrue(manager.getTransfers().isEmpty());
  }

  @Test
  public void removingATransferThatIsNotTheIndexedOneKeepsTheIndexEntry() {
    BittorrentDownload first = registered("same", TransferState.DOWNLOADING);
    BittorrentDownload second = registered("same", TransferState.DOWNLOADING);
    assertSame(
        "the later registration owns the hash", second, manager.getBittorrentDownload("same"));
    assertTrue(manager.remove(first));
    assertSame(second, manager.getBittorrentDownload("same"));
  }

  @Test
  public void onlyHealthyUnfinishedTransfersThatMoveBytesAreActiveDownloads() {
    BittorrentDownload moving = registered("moving", TransferState.DOWNLOADING);
    when(moving.isDownloading()).thenReturn(true);
    BittorrentDownload errored = registered("errored", TransferState.ERROR);
    when(errored.isDownloading()).thenReturn(true);
    BittorrentDownload done = registered("done", TransferState.SEEDING);
    when(done.isDownloading()).thenReturn(true);
    when(done.isComplete()).thenReturn(true);
    BittorrentDownload idle = registered("idle", TransferState.DOWNLOADING);
    assertEquals(1, manager.getActiveDownloads());
    assertNotNull(idle);
  }

  @Test
  public void finishedUnpausedTransfersAreActiveUploads() {
    BittorrentDownload seeding = registered("seeding", TransferState.SEEDING);
    when(seeding.isFinished()).thenReturn(true);
    BittorrentDownload pausedSeed = registered("pausedSeed", TransferState.SEEDING);
    when(pausedSeed.isFinished()).thenReturn(true);
    when(pausedSeed.isPaused()).thenReturn(true);
    BittorrentDownload erroredSeed = registered("erroredSeed", TransferState.ERROR);
    when(erroredSeed.isFinished()).thenReturn(true);
    registered("downloading", TransferState.DOWNLOADING);
    assertEquals(1, manager.getActiveUploads());
  }

  @Test
  public void stoppingSeedingPausesSeedingAndCompleteTransfersOnly() {
    BittorrentDownload seeding = registered("seeding", TransferState.SEEDING);
    when(seeding.isSeeding()).thenReturn(true);
    BittorrentDownload complete = registered("complete", TransferState.SEEDING);
    when(complete.isComplete()).thenReturn(true);
    BittorrentDownload downloading = registered("downloading", TransferState.DOWNLOADING);
    manager.stopSeedingTorrents();
    verify(seeding).pause();
    verify(complete).pause();
    verify(downloading, never()).pause();
    manager.pauseTorrents();
    verify(downloading).pause();
  }

  @Test
  public void policyPausesApplyToUiTransfersOnly() {
    UIBittorrentDownload seeding = registeredUi("ui-seeding");
    when(seeding.isSeeding()).thenReturn(true);
    UIBittorrentDownload downloading = registeredUi("ui-downloading");
    BittorrentDownload plain = registered("plain", TransferState.SEEDING);
    when(plain.isSeeding()).thenReturn(true);
    manager.suspendSeedingTorrents();
    verify(seeding).pauseForPolicy();
    verify(downloading, never()).pauseForPolicy();
    manager.suspendTorrentsForPolicy();
    verify(downloading).pauseForPolicy();
    verify(plain, never()).pause();
  }

  @Test
  public void policySuspendedTransfersResumeWhenThePolicyAllowsIt() {
    UIBittorrentDownload downloading = registeredUi("ui-downloading");
    UIBittorrentDownload complete = registeredUi("ui-complete");
    when(complete.isComplete()).thenReturn(true);
    manager.resumePolicySuspendedDownloads();
    verify(downloading).resumeFromPolicy();
    verify(complete, never()).resumeFromPolicy();
    manager.resumePolicySuspendedSeeding();
    verify(complete).resumeFromPolicy();
    verify(downloading, org.mockito.Mockito.times(1)).resumeFromPolicy();
  }

  @Test
  public void seedingStaysSuspendedWhileMobileDataSavingsOrTheVpnGuardApply() {
    UIBittorrentDownload complete = registeredUi("ui-complete");
    when(complete.isComplete()).thenReturn(true);
    when(network.isDataMobileUp()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    manager.resumePolicySuspendedSeeding();
    verify(complete, never()).resumeFromPolicy();
  }

  @Test
  public void finishedAndErroredTransfersAreClearedAndOthersStay() {
    BittorrentDownload finished = registered("finished", TransferState.SEEDING);
    when(finished.isComplete()).thenReturn(true);
    when(finished.isPaused()).thenReturn(true);
    BittorrentDownload seeding = registered("seeding", TransferState.SEEDING);
    when(seeding.isComplete()).thenReturn(true);
    BittorrentDownload errored = registered("errored", TransferState.ERROR);
    BittorrentDownload downloading = registered("downloading", TransferState.DOWNLOADING);
    manager.clearComplete();
    verify(finished).remove(false);
    verify(errored).remove(false);
    verify(seeding, never()).remove(false);
    verify(downloading, never()).remove(false);
  }

  @Test
  public void pausedUnfinishedTransfersResumeAndFinishedOnesSeedWhenAllowed() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    BittorrentDownload paused = registered("paused", TransferState.PAUSED);
    when(paused.isPaused()).thenReturn(true);
    BittorrentDownload finished = registered("finished", TransferState.SEEDING);
    when(finished.isFinished()).thenReturn(true);
    when(finished.isPaused()).thenReturn(true);
    BittorrentDownload running = registered("running", TransferState.DOWNLOADING);
    manager.resumeResumableTransfers();
    verify(paused).resume();
    verify(finished, never()).resume();
    verify(running, never()).resume();
    manager.seedFinishedTransfers();
    verify(finished).resume();
    verify(paused, org.mockito.Mockito.times(1)).resume();
  }

  @Test
  public void nothingResumesWhileAPolicyBlocksTorrents() {
    BittorrentDownload paused = registered("paused", TransferState.PAUSED);
    when(paused.isPaused()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY)).thenReturn(true);
    manager.resumeResumableTransfers();
    manager.seedFinishedTransfers();
    verify(paused, never()).resume();
  }

  @Test
  public void bothKindsOfUiTransferCountAsBittorrentDownloads() {
    UIBittorrentDownload ui = registeredUi("ui");
    assertTrue(manager.isBittorrentDownload(ui));
    assertFalse(manager.isBittorrentDownload(mock(Transfer.class)));
    assertFalse(manager.isBittorrentDownloadAndMobileDataSavingsOn(ui));
  }

  private static TorrentSearchResult torrent(String url, String hash) {
    TorrentSearchResult sr = mock(TorrentSearchResult.class);
    when(sr.getTorrentUrl()).thenReturn(url);
    when(sr.getHash()).thenReturn(hash);
    when(sr.getDetailsUrl()).thenReturn("details-" + url);
    when(sr.getDisplayName()).thenReturn("torrent " + url);
    return sr;
  }

  private static void deleteRecursively(File f) {
    File[] children = f.listFiles();
    if (children != null) {
      for (File c : children) {
        deleteRecursively(c);
      }
    }
    f.delete();
  }

  /** downloadTorrent(null, ...) threw a NullPointerException outside its try block. */
  @Test
  public void aMissingOrBlankUriIsAnInvalidDownloadNotAnException() {
    assertTrue(manager.downloadTorrent(null, null) instanceof InvalidBittorrentDownload);
    assertTrue(manager.downloadTorrent("   ", null) instanceof InvalidBittorrentDownload);
    assertTrue(manager.downloadTorrent("", null) instanceof InvalidBittorrentDownload);
  }
}
