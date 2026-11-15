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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.frostwire.android.R;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.PiecePriorities;
import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.util.Logger;
import com.frostwire.util.TaskThrottle;
import java.util.concurrent.ExecutorService;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa Created on 11/23/17.
 */
public class HexHiveView<T> extends View {
  private static final Logger LOG = Logger.getLogger(HexHiveView.class);
  // Dedicated 2-threaded executor for HexHiveView rendering
  // Allows parallel rendering without blocking sequential handler queues
  private static ExecutorService hexHiveRenderExecutor;

  private static ExecutorService getHexHiveRenderExecutor() {
    if (hexHiveRenderExecutor == null) {
      hexHiveRenderExecutor = ExecutorsHelper.newFixedSizeThreadPool(2, "HexHiveView-Renderer");
    }
    return hexHiveRenderExecutor;
  }

  static final int SKIPPED_COLOR = 0xffe53935;
  static final int HIGH_PRIORITY_COLOR = 0xffffb74d;

  /** The tapped piece is drawn like desktop's hovered one: an orange cube, a darker outline. */
  static final int SELECTED_COLOR = 0xffff9800;

  private static final int SELECTED_BORDER_COLOR = 0xffb35c00;

  /** Told which piece was tapped, on the UI thread. */
  public interface OnPieceTapListener {
    /**
     * @param piece the tapped piece, now the selected one, or -1 for a tap off the pieces, which
     *     clears the selection
     */
    void onPieceTapped(int piece);
  }

  private OnPieceTapListener pieceTapListener;
  private int selectedPiece = -1;

  /** The selected piece is drawn on the UI thread, these are its own shapes and paints. */
  private final Shapes selectionShapes = new Shapes();

  private final Paint selectedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint selectedBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  /**
   * The selected cube is drawn at least this big, in pixels: a big torrent's cubes are a few pixels
   * wide, too small to see which one a finger picked.
   */
  private final float minSelectedSideLength;

  private final int touchSlop;
  private float downX;
  private float downY;

  /** Whether the touch going on can still be a tap, no drag or scroll moved it too far. */
  private boolean tapPending;

  /** Where the last tap ended, NaN once it was handled. */
  private float tapX = Float.NaN;

  private float tapY = Float.NaN;

  private Paint bitmapPaint;
  private Paint hexagonBorderPaint;
  private CubePaint emptyHexPaint;
  private CubePaint fullHexPaint;

  /** Pieces not downloaded yet set to Don't Download (red) or High (orange), like on desktop. */
  private CubePaint skippedHexPaint;

  private CubePaint highPriorityHexPaint;
  private DrawingProperties DP;
  private volatile Bitmap compressedBitmap;

  private static float getHexWidth(float sideLength) {
    return (float) (Math.sqrt(3) * sideLength);
  }

  private static float getHexHeight(float sideLength) {
    return 2 * sideLength;
  }

  public static float getHexagonSideLength(
      final int width, final int height, final int numHexagons) {
    final float THREE_HALVES_SQRT_OF_THREE = 2.59807621135f;
    final int fullArea = width * height;
    // fullArea             numHexagons                     fullArea
    // --------         =                => s = sqrt(-----------------------)
    // 3/2*sqrt(3)*s^2                               3/2*sqrt(3)*numHexagons
    final float preliminarySideLength =
        (float) Math.sqrt(fullArea / (THREE_HALVES_SQRT_OF_THREE * numHexagons));

    float spaceToUse = 0.9f;

    if (numHexagons < 50) {
      spaceToUse = 0.85f;
    }

    if (numHexagons < 15) {
      spaceToUse = 0.8f;
    }

    return preliminarySideLength * spaceToUse;
  }

  public HexHiveView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    setLayerType(LAYER_TYPE_HARDWARE, null);
    Resources r = getResources();
    TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.HexHiveView);
    Resources.Theme theme = getContext().getTheme();
    int borderColor =
        typedArray.getColor(
            R.styleable.HexHiveView_hexhive_hexBorderColor, r.getColor(R.color.white, theme));
    int emptyColor =
        typedArray.getColor(
            R.styleable.HexHiveView_hexhive_emptyColor, r.getColor(R.color.basic_gray_dark, theme));
    int fullColor =
        typedArray.getColor(
            R.styleable.HexHiveView_hexhive_fullColor,
            r.getColor(R.color.basic_blue_highlight, theme));
    typedArray.recycle();
    initPaints(borderColor, emptyColor, fullColor);
    selectedFillPaint.setStyle(Paint.Style.FILL);
    selectedFillPaint.setColor(SELECTED_COLOR);
    selectedBorderPaint.setStyle(Paint.Style.STROKE);
    selectedBorderPaint.setColor(SELECTED_BORDER_COLOR);
    selectedBorderPaint.setStrokeWidth(
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, r.getDisplayMetrics()));
    minSelectedSideLength =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, r.getDisplayMetrics());
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
  }

  /** Who to tell when a piece is tapped, null for nobody (then the hive takes no touches). */
  public void setOnPieceTapListener(OnPieceTapListener listener) {
    pieceTapListener = listener;
  }

  /** The piece drawn as selected, -1 for none. */
  public int getSelectedPiece() {
    return selectedPiece;
  }

  /** Draws {@code piece} as selected, -1 for none. */
  public void setSelectedPiece(int piece) {
    if (piece != selectedPiece) {
      selectedPiece = piece;
      invalidate();
    }
  }

  /**
   * @return the piece whose cube is at ({@code x}, {@code y}) in view coordinates, or -1 for none
   *     (padding, past a row's last cube, below the last row, or no layout yet)
   */
  public int pieceAt(float x, float y) {
    DrawingProperties dp = DP;
    return dp != null ? dp.pieceAt(x, y) : -1;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (pieceTapListener == null || !isEnabled()) {
      return super.onTouchEvent(event);
    }
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = event.getX();
        downY = event.getY();
        tapPending = true;
        return true;
      case MotionEvent.ACTION_MOVE:
        if (Math.abs(event.getX() - downX) > touchSlop
            || Math.abs(event.getY() - downY) > touchSlop) {
          tapPending = false;
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (tapPending) {
          tapPending = false;
          tapX = event.getX();
          tapY = event.getY();
          performClick();
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        tapPending = false;
        return true;
      default:
        return true;
    }
  }

  /** A tap selects the piece under it and tells the listener. */
  @Override
  public boolean performClick() {
    boolean handled = super.performClick();
    // an accessibility click has no spot on the hive, it picks no piece
    if (pieceTapListener == null || Float.isNaN(tapX)) {
      return handled;
    }
    int piece = pieceAt(tapX, tapY);
    tapX = Float.NaN;
    tapY = Float.NaN;
    setSelectedPiece(piece);
    pieceTapListener.onPieceTapped(piece);
    return true;
  }

  public boolean ready() {
    return DP != null && compressedBitmap != null;
  }

  /**
   * Sets {@code out} to the center of {@code piece}'s cube in view coordinates.
   *
   * @return false, leaving {@code out} alone, when there's no layout yet or no such piece
   */
  boolean pieceCenter(int piece, Point out) {
    DrawingProperties dp = DP;
    if (dp == null || piece < 0 || piece >= dp.numHexs) {
      return false;
    }
    dp.centerOf(piece, out);
    return true;
  }

  private void initPaints(int borderColor, int emptyColor, int fullColor) {
    bitmapPaint = new Paint();
    hexagonBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    hexagonBorderPaint.setStyle(Paint.Style.STROKE);
    hexagonBorderPaint.setColor(borderColor);
    hexagonBorderPaint.setStrokeWidth(0);
    emptyHexPaint = new CubePaint(emptyColor, 10);
    fullHexPaint = new CubePaint(fullColor, 20);
    // Normal and Low keep the empty cube's color
    skippedHexPaint = new CubePaint(SKIPPED_COLOR, 20);
    highPriorityHexPaint = new CubePaint(HIGH_PRIORITY_COLOR, 20);
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    // see asyncDraw to see how compressedBitmap is created (in a background thread)
    // once that thread is done, it posts an invalidate call on the UI's handler loop.
    if (compressedBitmap != null) {
      canvas.drawBitmap(compressedBitmap, 0, 0, bitmapPaint);
    }
    drawSelectedPiece(canvas);
  }

  /** Drawn over the rendered hive, so selecting a piece needs no new render. */
  private void drawSelectedPiece(Canvas canvas) {
    DrawingProperties dp = DP;
    if (dp == null
        || compressedBitmap == null
        || selectedPiece < 0
        || selectedPiece >= dp.numHexs) {
      return;
    }
    dp.centerOf(selectedPiece, selectionShapes.center);
    float sideLength = Math.max(dp.hexSideLength, minSelectedSideLength);
    Path path = selectionShapes.path;
    path.reset();
    for (int i = 0; i < 7; i++) {
      getHexCorner(selectionShapes.corner, selectionShapes.center, i, sideLength);
      if (i == 0) {
        path.moveTo(selectionShapes.corner.x, selectionShapes.corner.y);
      } else {
        path.lineTo(selectionShapes.corner.x, selectionShapes.corner.y);
      }
    }
    canvas.drawPath(path, selectedFillPaint);
    canvas.drawPath(path, selectedBorderPaint);
  }

  public void updateData(HexDataAdapter<T> hexDataAdapter) {
    int canvasWidth = getWidth();
    int canvasHeight = getHeight();

    if (DP == null && canvasHeight > 0 && canvasWidth > 0 && hexDataAdapter != null) {
      DP =
          new DrawingProperties(
              hexDataAdapter,
              hexagonBorderPaint.getStrokeWidth(),
              getPaddingLeft(),
              getPaddingTop(),
              canvasWidth - getPaddingRight(),
              canvasHeight - getPaddingBottom());
    }
    if (DP == null) {
      // not ready yet (perhaps during animation or rotation)
      return;
    }
    if (hexDataAdapter != null
        && hexDataAdapter.getFullHexagonsCount() >= 0
        && canvasWidth > 0
        && canvasHeight > 0) {
      // Use dedicated 2-threaded HexHiveView executor for background drawing.
      // This prevents blocking the single-threaded MISC handler when rendering large hexagons.
      // Throttle to 100ms minimum to prevent excessive redraws during piece downloading.
      if (TaskThrottle.isReadyToSubmitTask("HexHiveView::updateData", 100)) {
        getHexHiveRenderExecutor()
            .execute(
                () -> {
                  asyncDraw(canvasWidth, canvasHeight, hexDataAdapter);
                  SystemUtils.postToUIThread(this::invalidate);
                });
      }
    }
  }

  public interface HexDataAdapter<T> {
    void updateData(T data);

    int getTotalHexagonsCount();

    int getFullHexagonsCount();

    boolean isFull(int hexOffset);

    /**
     * How a piece that isn't full is marked, one of the {@code PiecePriorities.MARK_*} values; read
     * on the render thread.
     */
    default int priorityMark(int hexOffset) {
      return PiecePriorities.MARK_NONE;
    }
  }

  /**
   * The base color of a cube and its darker left and lighter top faces. Never changed once made: a
   * render can run while another one, or the UI thread, draws with the same paints.
   */
  private static final class CubePaint {
    final Paint base;
    final Paint dark;
    final Paint light;

    CubePaint(int color, int shades) {
      base = fill(color);
      dark = fill(shade(color, -shades));
      light = fill(shade(color, shades));
    }

    private static Paint fill(int color) {
      Paint paint = new Paint();
      paint.setColor(color);
      paint.setStyle(Paint.Style.FILL);
      return paint;
    }

    /** {@code color} with {@code delta} added to each of its red, green and blue components. */
    private static int shade(int color, int delta) {
      int a = (color >>> 24) & 0xff;
      int r = Math.max(0, Math.min(0xff, ((color >> 16) & 0xff) + delta));
      int g = Math.max(0, Math.min(0xff, ((color >> 8) & 0xff) + delta));
      int b = Math.max(0, Math.min(0xff, (color & 0xff) + delta));
      return a << 24 | r << 16 | g << 8 | b;
    }
  }

  /**
   * The point and path objects one drawing reuses for every cube, so drawing allocates nothing per
   * cube. Each drawing has its own: two renders may overlap on the render threads.
   */
  private static final class Shapes {
    final Point center = new Point(-1, -1);
    final Point corner = new Point(-1, -1);
    final Path path = new Path();
  }

  private static final class DrawingProperties {
    // Painting Area Configuration
    /** Drawing area top-left */
    Point origin;

    /** Drawing area center */
    Point center;

    /** Drawing are bottom-right cornerBuffer */
    Point end;

    /** Drawing area dimensions */
    Rect dimensions;

    // Hexagon Geometry Helpers
    /** Number of hexagons to draw */
    private final int numHexs;

    /** Side length of each hexagon */
    private final float hexSideLength;

    /** Height of each hexagon */
    private final float hexHeight;

    /** Width of each hexagon */
    private final float hexWidth;

    private final Point evenRowOrigin;

    private final Point oddRowOrigin;

    /** Distance between the centers of two cubes next to each other in a row. */
    private final int columnStep;

    /** Distance between the centers of two rows. */
    private final int rowStep;

    /** Cubes in an even row (the first is row 0) and in an odd one, which starts further right. */
    private final int evenRowCount;

    private final int oddRowCount;

    @SuppressWarnings("rawtypes")
    DrawingProperties(
        HexDataAdapter adapter, float hexBorderWidth, int left, int top, int right, int bottom) {
      if (adapter == null) {
        throw new RuntimeException(
            "check your logic, you need a data adapter before calling initDrawingProperties");
      }
      // The canvas can paint the entire view, if padding has been defined,
      // we won't draw outside the padded area.
      /*
       Hexagon border stroke width, has to be converted to pixels depending on screen density
      */
      dimensions = new Rect(left, top, right, bottom);
      origin = new Point(dimensions.left, dimensions.top);
      center = new Point(dimensions.centerX(), dimensions.centerY());
      end = new Point(dimensions.right, dimensions.bottom);
      /*
       Drawing area width
      */
      int width = dimensions.width();
      /*
       Drawing area height
      */
      int height = dimensions.height();
      numHexs = adapter.getTotalHexagonsCount();
      hexSideLength = getHexagonSideLength(width, height, numHexs);
      hexHeight = getHexHeight(hexSideLength) + 2 * hexBorderWidth;
      hexWidth = getHexWidth(hexSideLength) + (2 * hexBorderWidth);
      evenRowOrigin =
          new Point((int) (origin.x + (hexWidth / 2)), (int) (origin.y + (hexHeight / 2)));
      // calculate number of hexagons in an even row
      oddRowOrigin =
          new Point((int) (evenRowOrigin.x + (hexWidth / 2)), (int) (evenRowOrigin.y + hexHeight));
      columnStep = Math.max(1, (int) (hexWidth + (hexBorderWidth * 4)));
      rowStep = Math.max(1, (int) ((hexHeight / 4) * 3));
      evenRowCount = rowCount(evenRowOrigin.x, hexBorderWidth);
      oddRowCount = rowCount(oddRowOrigin.x, hexBorderWidth);
    }

    /** How many cubes fit in a row starting at {@code firstX}, at least one. */
    private int rowCount(int firstX, float hexBorderWidth) {
      int count = 1;
      // a cube goes on the row while the right side of the next one stays inside the drawing area
      while (firstX + (count * columnStep) + (hexWidth / 2) + (hexBorderWidth * 3) < end.x) {
        count++;
      }
      return count;
    }

    /**
     * The piece whose center is nearest to ({@code x}, {@code y}) and no farther than a cube's
     * corners, -1 for none. The nearest center is the cube the point is in: cubes tile the hive.
     */
    int pieceAt(float x, float y) {
      if (numHexs <= 0) {
        return -1;
      }
      Point candidate = new Point();
      int nearest = -1;
      float nearestDistance = Float.MAX_VALUE;
      int firstRow = numHexs == 1 ? 0 : Math.round((y - evenRowOrigin.y) / rowStep) - 1;
      for (int row = Math.max(0, firstRow); row <= firstRow + 2; row++) {
        boolean evenRow = row % 2 == 0;
        int rowLength = evenRow ? evenRowCount : oddRowCount;
        int firstX = evenRow ? evenRowOrigin.x : oddRowOrigin.x;
        int firstColumn = numHexs == 1 ? 0 : Math.round((x - firstX) / columnStep) - 1;
        for (int column = Math.max(0, firstColumn);
            column <= firstColumn + 2 && column < rowLength;
            column++) {
          int piece =
              (row / 2) * (evenRowCount + oddRowCount) + (evenRow ? 0 : evenRowCount) + column;
          if (piece >= numHexs) {
            break;
          }
          centerOf(piece, candidate);
          float dx = x - candidate.x;
          float dy = y - candidate.y;
          float distance = dx * dx + dy * dy;
          if (distance < nearestDistance) {
            nearestDistance = distance;
            nearest = piece;
          }
        }
      }
      return nearestDistance <= hexSideLength * hexSideLength ? nearest : -1;
    }

    /** Sets {@code out} to the center of {@code piece}'s cube. */
    void centerOf(int piece, Point out) {
      if (numHexs == 1) {
        out.set(center.x, center.y);
        return;
      }
      int rowPair = piece / (evenRowCount + oddRowCount);
      int inPair = piece % (evenRowCount + oddRowCount);
      boolean evenRow = inPair < evenRowCount;
      int row = rowPair * 2 + (evenRow ? 0 : 1);
      int column = evenRow ? inPair : inPair - evenRowCount;
      out.set(
          (evenRow ? evenRowOrigin.x : oddRowOrigin.x) + column * columnStep,
          evenRowOrigin.y + row * rowStep);
    }
  }

  @SuppressWarnings("rawtypes")
  private void asyncDraw(int canvasWidth, int canvasHeight, HexDataAdapter adapter) {
    try {
      // with DP we don't need to think about padding offsets. We just use DP numbers for our
      // calculations
      boolean drawCubes = DP.numHexs <= 600;
      Bitmap bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      Shapes shapes = new Shapes();
      // every piece: the hive is sized for all of them, a piece left out is a hole in the hive
      for (int pieceIndex = 0; pieceIndex < DP.numHexs; pieceIndex++) {
        DP.centerOf(pieceIndex, shapes.center);
        drawHexagon(
            DP, shapes, canvas, hexagonBorderPaint, paintFor(adapter, pieceIndex), drawCubes);
      }
      compressedBitmap = bitmap;
    } catch (Exception e) {
      LOG.error("asyncDraw failed with exception: " + e.getMessage(), e);
    }
  }

  /** A downloaded piece is full whatever its priority, the others are tinted by it. */
  @SuppressWarnings("rawtypes")
  private CubePaint paintFor(HexDataAdapter adapter, int piece) {
    if (adapter.isFull(piece)) {
      return fullHexPaint;
    }
    switch (adapter.priorityMark(piece)) {
      case PiecePriorities.MARK_SKIP:
        return skippedHexPaint;
      case PiecePriorities.MARK_HIGH:
        return highPriorityHexPaint;
      default:
        return emptyHexPaint;
    }
  }

  // Drawing/Geometry functions

  /**
   * @param outCorner - a re-usable Point buffer to output the
   * @param inCenter - a reusable Point buffer representing the center coordinates of a hexagon
   * @param sideLength - length of hexagon side
   * @param cornerNumber - from 0 to 6 (we count 7 because we have to get back to the origin)
   */
  private static void getHexCorner(
      final Point outCorner, final Point inCenter, int cornerNumber, float sideLength) {
    double angle_rad = Math.toRadians((60 * cornerNumber) + 30);
    outCorner.set(
        (int) (inCenter.x + sideLength * Math.cos(angle_rad)),
        (int) (inCenter.y + sideLength * Math.sin(angle_rad)));
  }

  private static void drawHexagon(
      final DrawingProperties DP,
      final Shapes shapes,
      final Canvas canvas,
      final Paint borderPaint,
      final CubePaint fillPaint,
      final boolean drawCube) {
    shapes.path.reset();
    for (int i = 0; i < 7; i++) {
      getHexCorner(shapes.corner, shapes.center, i, DP.hexSideLength);
      if (i == 0) {
        shapes.path.moveTo(shapes.corner.x, shapes.corner.y);
      } else {
        shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      }
    }
    canvas.drawPath(shapes.path, fillPaint.base);
    canvas.drawPath(shapes.path, borderPaint);
    shapes.path.reset();
    if (drawCube) {
      // LEFT FACE
      // bottom corner - 90 degrees points straight below (zero at horizon on the right side)
      // angles move clockwise. 0 is below right horizon, 1 right below the center...

      // make left face path
      shapes.path.moveTo(shapes.center.x, shapes.center.y);
      getHexCorner(shapes.corner, shapes.center, 1, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      getHexCorner(shapes.corner, shapes.center, 2, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      getHexCorner(shapes.corner, shapes.center, 3, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      shapes.path.lineTo(shapes.center.x, shapes.center.y);
      // fill left face path
      canvas.drawPath(shapes.path, fillPaint.dark);

      // TOP FACE
      // make top face path
      shapes.path.reset();
      shapes.path.moveTo(shapes.center.x, shapes.center.y);
      getHexCorner(shapes.corner, shapes.center, 3, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      getHexCorner(shapes.corner, shapes.center, 4, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      getHexCorner(shapes.corner, shapes.center, 5, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      shapes.path.lineTo(shapes.center.x, shapes.center.y);
      canvas.drawPath(shapes.path, fillPaint.light);

      // Now draw 3 lines from the center to corners 1,3 and 5
      shapes.path.reset();
      shapes.path.moveTo(shapes.center.x, shapes.center.y);
      getHexCorner(shapes.corner, shapes.center, 1, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      shapes.path.moveTo(shapes.center.x, shapes.center.y);
      getHexCorner(shapes.corner, shapes.center, 3, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      shapes.path.moveTo(shapes.center.x, shapes.center.y);
      getHexCorner(shapes.corner, shapes.center, 5, DP.hexSideLength);
      shapes.path.lineTo(shapes.corner.x, shapes.corner.y);
      canvas.drawPath(shapes.path, borderPaint);

      shapes.path.reset();
    }
  }
}
