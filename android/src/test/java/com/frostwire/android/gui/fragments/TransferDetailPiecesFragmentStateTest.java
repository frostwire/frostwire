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

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentFactory;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.views.HexHiveView;
import com.frostwire.jlibtorrent.PieceIndexBitfield;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The Pieces tab keeps its selected piece and the piece's card when its screen is recreated: a
 * theme or font change, a multi-window resize, or the process coming back
 * (frostwire/frostwire#1566).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailPiecesFragmentStateTest {
  private static final String INFO_HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final String OTHER_INFO_HASH = "89abcdef0123456789abcdef0123456789abcdef";

  /** The pieces the fake libtorrent says the transfer has. */
  private static volatile int totalPieces;

  @Before
  public void setUp() {
    totalPieces = 10;
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
  }

  @Test
  public void theSelectedPieceAndItsCardComeBackWhenTheScreenIsRecreated() throws Exception {
    ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup();
    tapPiece(controller.get(), INFO_HASH, 7);

    controller.recreate();
    TransferDetailPiecesFragment fragment = refresh(controller.get(), INFO_HASH);
    View card = card(fragment);
    settleUntil(() -> card.getVisibility() == View.VISIBLE);

    assertEquals(7, hive(fragment).getSelectedPiece());
    assertEquals(View.VISIBLE, card.getVisibility());
    assertEquals(
        fragment.getString(R.string.transfer_detail_piece_title, "7", "10"),
        ((TextView) card.findViewById(R.id.view_transfer_detail_piece_panel_title))
            .getText()
            .toString());
  }

  @Test
  public void aRestoredPiecePastTheTorrentsLastOneIsCleared() throws Exception {
    ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup();
    tapPiece(controller.get(), INFO_HASH, 7);

    totalPieces = 5;
    controller.recreate();
    TransferDetailPiecesFragment fragment = refresh(controller.get(), INFO_HASH);
    TextView piecesNumber =
        fragment.requireView().findViewById(R.id.fragment_transfer_detail_pieces_pieces_number);
    settleUntil(() -> "0/5".contentEquals(piecesNumber.getText()));

    assertEquals(-1, hive(fragment).getSelectedPiece());
    assertEquals(View.GONE, card(fragment).getVisibility());

    // and it isn't saved again
    controller.recreate();
    fragment = refresh(controller.get(), INFO_HASH);
    TextView recreatedPiecesNumber =
        fragment.requireView().findViewById(R.id.fragment_transfer_detail_pieces_pieces_number);
    settleUntil(() -> "0/5".contentEquals(recreatedPiecesNumber.getText()));
    assertEquals(-1, hive(fragment).getSelectedPiece());
    assertEquals(View.GONE, card(fragment).getVisibility());
  }

  @Test
  public void anotherTransferDoesNotGetTheRestoredPiece() throws Exception {
    ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup();
    tapPiece(controller.get(), INFO_HASH, 7);

    controller.recreate();
    TransferDetailPiecesFragment fragment = refresh(controller.get(), OTHER_INFO_HASH);
    TextView piecesNumber =
        fragment.requireView().findViewById(R.id.fragment_transfer_detail_pieces_pieces_number);
    settleUntil(() -> "0/10".contentEquals(piecesNumber.getText()));

    assertEquals(-1, hive(fragment).getSelectedPiece());
    assertEquals(View.GONE, card(fragment).getVisibility());
  }

  /** Binds the transfer, shows its pieces and taps {@code piece}, its card opens. */
  private static void tapPiece(Host host, String infoHash, int piece) throws Exception {
    TransferDetailPiecesFragment fragment = refresh(host, infoHash);
    HexHiveView<?> hive = hive(fragment);
    Point center = new Point();
    settleUntil(() -> hive.pieceCenter(piece, center));
    assertTrue("the hive isn't laid out", hive.pieceCenter(piece, center));
    touch(hive, MotionEvent.ACTION_DOWN, center.x, center.y);
    touch(hive, MotionEvent.ACTION_UP, center.x, center.y);
    View card = card(fragment);
    settleUntil(() -> card.getVisibility() == View.VISIBLE);
    assertEquals(piece, hive.getSelectedPiece());
    assertEquals(View.VISIBLE, card.getVisibility());
  }

  /** What TransferDetailActivity's timer does: binds the transfer and refreshes the tab. */
  private static TransferDetailPiecesFragment refresh(Host host, String infoHash) {
    TransferDetailPiecesFragment fragment =
        (TransferDetailPiecesFragment)
            host.getSupportFragmentManager().findFragmentById(Host.CONTAINER_ID);
    UIBittorrentDownload download = mock(UIBittorrentDownload.class);
    when(download.getInfoHash()).thenReturn(infoHash);
    fragment.bindToTransfer(download);
    fragment.onTime();
    return fragment;
  }

  private static HexHiveView<?> hive(TransferDetailPiecesFragment fragment) {
    return fragment.requireView().findViewById(R.id.fragment_transfer_detail_pieces_hexhive_view);
  }

  private static View card(TransferDetailPiecesFragment fragment) {
    return fragment.requireView().findViewById(R.id.view_transfer_detail_piece_panel);
  }

  private static void touch(View view, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
    view.dispatchTouchEvent(event);
    event.recycle();
  }

  /** Runs the main thread's tasks until {@code done}: the pieces are read on other threads. */
  private static void settleUntil(BooleanSupplier done) throws InterruptedException {
    for (int i = 0; i < 250 && !done.getAsBoolean(); i++) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(20);
    }
    shadowOf(Looper.getMainLooper()).idle();
  }

  /** Reads pieces like libtorrent would: none downloaded yet, every one at default priority. */
  static final class FakePiecesReader implements TransferDetailPiecesFragment.PiecesReader {
    @Override
    public TransferDetailPiecesFragment.PiecesData readPieces(String infoHash, int selectedPiece) {
      int total = totalPieces;
      PieceIndexBitfield pieces = mock(PieceIndexBitfield.class);
      when(pieces.size()).thenReturn(total);
      return new TransferDetailPiecesFragment.PiecesData(
          pieces,
          new byte[total],
          0,
          total,
          "16 KB",
          selectedPiece,
          readPieceDetails(infoHash, selectedPiece));
    }

    @Override
    public PieceDetails readPieceDetails(String infoHash, int piece) {
      int total = totalPieces;
      if (piece < 0 || piece >= total) {
        return null;
      }
      return new PieceDetails(
          infoHash,
          piece,
          total,
          PieceDetails.State.MISSING,
          0,
          0,
          0,
          16384,
          piece * 16384L,
          3,
          Priority.FOUR,
          null,
          Collections.emptyList(),
          0);
    }
  }

  /** Hosts the Pieces tab like TransferDetailActivity's pager, which restores it when recreated. */
  public static final class Host extends FragmentActivity {
    static final int CONTAINER_ID = 0x7f0f1566;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      // the recreated tab is made by the fragment manager, with the fake libtorrent
      getSupportFragmentManager()
          .setFragmentFactory(
              new FragmentFactory() {
                @NonNull
                @Override
                public Fragment instantiate(@NonNull ClassLoader loader, @NonNull String name) {
                  return TransferDetailPiecesFragment.class.getName().equals(name)
                      ? new TransferDetailPiecesFragment(new FakePiecesReader())
                      : super.instantiate(loader, name);
                }
              });
      setTheme(R.style.Theme_FrostWire);
      super.onCreate(savedInstanceState);
      FrameLayout container = new FrameLayout(this);
      container.setId(CONTAINER_ID);
      setContentView(
          container,
          new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      if (savedInstanceState == null) {
        getSupportFragmentManager()
            .beginTransaction()
            .add(CONTAINER_ID, new TransferDetailPiecesFragment(new FakePiecesReader()))
            .commitNow();
      }
    }
  }
}
