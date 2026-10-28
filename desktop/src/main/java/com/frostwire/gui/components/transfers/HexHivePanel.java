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

package com.frostwire.gui.components.transfers;

import com.frostwire.util.Logger;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;

public class HexHivePanel extends JPanel implements Scrollable {
  private static final Logger LOG = Logger.getLogger(HexHivePanel.class);
  private final boolean forceCubes;
  private final Object drawingPropertiesLock = new Object();
  private final Object bitmapLock = new Object();
  private final Object renderLock = new Object();
  private final int topPadding;
  private final int rightPadding;
  private final int bottomPadding;
  private final int leftPadding;
  private int hexSideLength;
  private ColoredStroke hexagonBorderPaint;
  private CubePaint emptyHexPaint;
  private CubePaint fullHexPaint;
  private DrawingProperties drawingProperties;
  private HexDataAdapter latestAdapter;
  private BufferedImage bitmap;

  /**
   * Device pixels per logical pixel of the backing bitmap (1.0 on regular screens, 1.25/1.5/2.0 on
   * HiDPI ones). Rendering at device resolution keeps the cubes crisp instead of letting Java2D
   * upscale a logical-size bitmap.
   */
  private double bitmapScale = 1.0;

  /** Layout the backing bitmap was drawn for, batches rendered for any other one are dropped. */
  private DrawingProperties bitmapLayout;

  /**
   * The backing bitmap only holds a window of the hive around the viewport, not the whole content:
   * a torrent with tens of thousands of pieces is tens of thousands of pixels tall, hundreds of MB
   * as a single bitmap on a HiDPI screen. The window spans the full content width and {@link
   * #bitmapWindowHeight} logical pixels starting at content row {@link #bitmapOriginY}.
   */
  private int bitmapOriginY;

  private int bitmapWindowHeight;

  /** Bumped whenever the backing window is reallocated or moved, guarded by bitmapLock. */
  private int bitmapWindowGeneration;

  /** Height of the backing window in viewports, leaves room for the scroll look-ahead renders. */
  private static final int BACKING_WINDOW_VIEWPORTS = 2;

  /** Viewport height assumed while the hive is not showing yet. */
  private static final int FALLBACK_VIEWPORT_HEIGHT = 600;

  private volatile double renderScale = 1.0;
  private final Area renderedArea = new Area();
  private int lastWidth;
  private int lastHeight;
  private final ExecutorService threadPool =
      com.frostwire.util.ThreadPool.newThreadPool("HexHivePool", 1);
  private RenderRequest pendingRenderRequest;
  private boolean renderRunning;
  private Rectangle lastVisibleRect = new Rectangle();
  private Color backgroundColor;
  private GraphicsConfiguration graphicsConfig;
  // Hover highlight + floating details card. All hover state is EDT confined.
  private static final CubePaint HOVER_PAINT = new CubePaint(0xff9800, 45);
  private static final ColoredStroke HOVER_STROKE = new ColoredStroke(1f, new Color(0xb35c00));
  private static final int CARD_CURSOR_OFFSET_X = 16;
  private static final int CARD_CURSOR_OFFSET_Y = 18;
  private static final int CARD_FLIPPED_GAP = 8;
  private final ExecutorService hoverPool =
      com.frostwire.util.ThreadPool.newThreadPool("HexHiveHoverPool", 1);
  private int hoveredHex = -1;
  private int hoverGeneration;
  private Point hoverScreenLocation;
  private Popup hoverPopup;
  private JPanel hoverCard;
  private JViewport observedViewport;
  private Point lastViewPosition;
  private final ChangeListener viewportListener = e -> onViewportChanged();

  /**
   * @param hexSideLength - if -1 hexLength is calculated out of the available container area.
   */
  HexHivePanel(
      int hexSideLength,
      int borderColor,
      int emptyColor,
      int fullColor,
      int backgroundColor,
      int topPadding,
      int rightPadding,
      int bottomPadding,
      int leftPadding,
      boolean forceCubes) {
    if (hexSideLength != -1 && hexSideLength < 5) {
      throw new IllegalArgumentException(
          "Invalid hexSideLength ("
              + hexSideLength
              + "). Valid hexSideLength are: -1 (dynamic) and >= 5");
    }
    setDoubleBuffered(true);
    initPaints(borderColor, emptyColor, fullColor, backgroundColor);
    this.hexSideLength = hexSideLength;
    this.topPadding = topPadding;
    this.rightPadding = rightPadding;
    this.bottomPadding = bottomPadding;
    this.leftPadding = leftPadding;
    this.forceCubes = forceCubes;
    lastWidth = getWidth();
    lastHeight = getHeight();
    MouseAdapter hoverListener =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            updateHover(e.getPoint(), e.getLocationOnScreen());
          }

          @Override
          public void mouseExited(MouseEvent e) {
            clearHover();
          }
        };
    addMouseListener(hoverListener);
    addMouseMotionListener(hoverListener);
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            relayoutIfWidthChanged();
          }
        });
  }

  /** Re-wraps the rows to the new width, e.g. when the window or the viewport is resized. */
  private void relayoutIfWidthChanged() {
    DrawingProperties snapshot;
    HexDataAdapter adapter;
    synchronized (drawingPropertiesLock) {
      snapshot = drawingProperties;
      adapter = latestAdapter;
    }
    // height follows the layout, reacting to it would only re-render the same rows
    if (adapter != null && (snapshot == null || snapshot.canvasWidth != getWidth())) {
      updateData(adapter);
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    graphicsConfig = getGraphicsConfiguration();
    if (graphicsConfig != null) {
      renderScale = sanitizeScale(graphicsConfig.getDefaultTransform().getScaleX());
    }
    Container viewport = SwingUtilities.getAncestorOfClass(JViewport.class, this);
    if (viewport instanceof JViewport) {
      observedViewport = (JViewport) viewport;
      lastViewPosition = observedViewport.getViewPosition();
      observedViewport.addChangeListener(viewportListener);
    }
  }

  @Override
  public void removeNotify() {
    clearHover();
    if (observedViewport != null) {
      observedViewport.removeChangeListener(viewportListener);
      observedViewport = null;
    }
    super.removeNotify();
  }

  private void onViewportChanged() {
    if (observedViewport == null) {
      return;
    }
    Point viewPosition = observedViewport.getViewPosition();
    // Scrolling moves the cubes under a still mouse, the hovered piece is no longer under it
    if (!viewPosition.equals(lastViewPosition)) {
      lastViewPosition = viewPosition;
      clearHover();
    }
  }

  private void updateHover(Point mouse, Point mouseOnScreen) {
    DrawingProperties snapshot;
    HexDataAdapter adapter;
    synchronized (drawingPropertiesLock) {
      snapshot = drawingProperties;
      adapter = latestAdapter;
    }
    int index = snapshot != null ? hexIndexAt(snapshot, mouse.x, mouse.y) : -1;
    if (index == hoveredHex) {
      return;
    }
    repaintHoveredHex(snapshot);
    hoveredHex = index;
    hoverScreenLocation = mouseOnScreen;
    hideHoverPopup();
    repaintHoveredHex(snapshot);
    if (index >= 0 && adapter != null) {
      requestHoverDetails(adapter, index);
    }
  }

  private void clearHover() {
    if (hoveredHex < 0 && hoverPopup == null) {
      return;
    }
    DrawingProperties snapshot;
    synchronized (drawingPropertiesLock) {
      snapshot = drawingProperties;
    }
    repaintHoveredHex(snapshot);
    hoveredHex = -1;
    hoverGeneration++;
    hideHoverPopup();
  }

  private void requestHoverDetails(HexDataAdapter<?> adapter, int index) {
    final int generation = ++hoverGeneration;
    // Adapters may hit JNI (e.g. libtorrent) to describe a piece, keep that off the EDT
    hoverPool.execute(
        () -> {
          Map<String, String> details;
          try {
            details = adapter.describeHex(index);
          } catch (Throwable t) {
            LOG.warn("describeHex(" + index + ") failed", t);
            details = null;
          }
          final Map<String, String> result = details;
          SwingUtilities.invokeLater(
              () -> {
                if (generation == hoverGeneration
                    && hoveredHex == index
                    && result != null
                    && !result.isEmpty()) {
                  showHoverPopup(result);
                }
              });
        });
  }

  private void showHoverPopup(Map<String, String> details) {
    if (!isShowing() || hoverScreenLocation == null) {
      return;
    }
    if (hoverCard == null) {
      hoverCard = createInfoCard();
    }
    Dimension previousSize = hoverPopup != null ? hoverCard.getPreferredSize() : null;
    fillInfoCard(hoverCard, details);
    Dimension size = hoverCard.getPreferredSize();
    if (hoverPopup != null && size.equals(previousSize)) {
      // live refresh of the same piece, update in place to avoid flicker
      hoverCard.revalidate();
      hoverCard.repaint();
      return;
    }
    hideHoverPopup();
    Rectangle screen =
        graphicsConfig != null
            ? graphicsConfig.getBounds()
            : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    // below-right of the cursor, flipped to the other side when it would leave the screen
    int x = hoverScreenLocation.x + CARD_CURSOR_OFFSET_X;
    int y = hoverScreenLocation.y + CARD_CURSOR_OFFSET_Y;
    if (x + size.width > screen.x + screen.width) {
      x = Math.max(screen.x, hoverScreenLocation.x - size.width - CARD_FLIPPED_GAP);
    }
    if (y + size.height > screen.y + screen.height) {
      y = Math.max(screen.y, hoverScreenLocation.y - size.height - CARD_FLIPPED_GAP);
    }
    hoverPopup = PopupFactory.getSharedInstance().getPopup(this, hoverCard, x, y);
    hoverPopup.show();
  }

  private void hideHoverPopup() {
    if (hoverPopup != null) {
      hoverPopup.hide();
      hoverPopup = null;
    }
  }

  private void repaintHoveredHex(DrawingProperties snapshot) {
    if (hoveredHex < 0 || snapshot == null || hoveredHex >= snapshot.numHexs) {
      return;
    }
    Point center = getHexCenter(snapshot, hoveredHex);
    int reach = (int) Math.ceil(snapshot.hexSideLength) + 3;
    repaint(center.x - reach, center.y - reach, reach * 2, reach * 2);
  }

  private static Point getHexCenter(DrawingProperties drawingProperties, int index) {
    int row = drawingProperties.getRowIndexForPiece(index);
    int col = index - drawingProperties.getRowStartIndex(row);
    int rowOriginX =
        (row % 2) == 0 ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
    float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
    return new Point(
        Math.round(rowOriginX + (col * drawingProperties.hexWidth)),
        Math.round(drawingProperties.evenRowOrigin.y + (row * verticalStep)));
  }

  /**
   * @return the index of the hexagon under (x, y), or -1. In a hex grid the closest center is the
   *     hexagon containing the point, so it's enough to check the neighbouring rows and columns.
   */
  private static int hexIndexAt(DrawingProperties drawingProperties, int x, int y) {
    if (drawingProperties.numHexs <= 0 || drawingProperties.hexWidth <= 0) {
      return -1;
    }
    float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
    int approxRow = Math.round((y - drawingProperties.evenRowOrigin.y) / verticalStep);
    int best = -1;
    long bestDistance = Long.MAX_VALUE;
    for (int row = Math.max(0, approxRow - 1); row <= approxRow + 1; row++) {
      int rowHexCount = drawingProperties.getRowHexCount(row);
      if (rowHexCount <= 0) {
        continue;
      }
      int rowStartIndex = drawingProperties.getRowStartIndex(row);
      int rowOriginX =
          (row % 2) == 0 ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
      int centerY = Math.round(drawingProperties.evenRowOrigin.y + (row * verticalStep));
      int approxCol = Math.round((x - rowOriginX) / drawingProperties.hexWidth);
      for (int col = Math.max(0, approxCol - 1);
          col <= Math.min(rowHexCount - 1, approxCol + 1);
          col++) {
        long dx = x - Math.round(rowOriginX + (col * drawingProperties.hexWidth));
        long dy = y - centerY;
        long distance = dx * dx + dy * dy;
        if (distance < bestDistance) {
          bestDistance = distance;
          best = rowStartIndex + col;
        }
      }
    }
    float side = drawingProperties.hexSideLength;
    return (best >= 0 && bestDistance <= side * side) ? best : -1;
  }

  private static double sanitizeScale(double scale) {
    return (Double.isNaN(scale) || scale <= 0) ? 1.0 : scale;
  }

  /**
   * Maps a logical coordinate to the backing bitmap's device pixel grid. Every device-space
   * rectangle is derived from this same mapping so batches merge 1:1 without resampling seams.
   */
  private static int toDevice(int logical, double scale) {
    return (int) Math.round(logical * scale);
  }

  /**
   * @return logical height of the backing window for a viewport this tall, bounded by the viewport
   *     instead of the piece count.
   */
  static int backingWindowHeight(int contentHeight, int viewportHeight) {
    return Math.max(1, Math.min(contentHeight, viewportHeight * BACKING_WINDOW_VIEWPORTS));
  }

  /**
   * @return the content row the backing window starts at, centered on the visible rows.
   */
  static int anchorBackingWindow(int contentHeight, int windowHeight, Rectangle visibleRect) {
    int centered = visibleRect.y + (visibleRect.height - windowHeight) / 2;
    return Math.max(0, Math.min(contentHeight - windowHeight, centered));
  }

  /**
   * @return whether the backing window still holds the visible rows plus a small margin, so it
   *     doesn't have to move on every scroll step.
   */
  static boolean backingWindowCovers(
      int originY, int windowHeight, int contentHeight, Rectangle visibleRect) {
    int margin = visibleRect.height / 8;
    int top = Math.max(0, visibleRect.y - margin);
    int bottom = Math.min(contentHeight, visibleRect.y + visibleRect.height + margin);
    return top >= originY && bottom <= originY + windowHeight;
  }

  /**
   * Device rows of a window this tall. At fractional scales the rounding of the window's top and
   * bottom edges can add a row depending on where the window starts, so keep one spare.
   */
  private static int deviceWindowHeight(int windowHeight, double scale) {
    boolean integralScale = scale == Math.rint(scale);
    return Math.max(1, toDevice(windowHeight, scale) + (integralScale ? 0 : 1));
  }

  void clearRenderCache() {
    synchronized (bitmapLock) {
      bitmap = null;
      bitmapWindowGeneration++;
      renderedArea.reset();
    }
    synchronized (renderLock) {
      pendingRenderRequest = null;
    }
    lastVisibleRect = new Rectangle();
  }

  private static float getHexWidth(float sideLength) {
    return (float) (2 * (Math.cos(Math.toRadians(30)) * sideLength));
  }

  private static float getHexHeight(float sideLength) {
    return 2 * sideLength;
  }

  private static float getHexagonSideLength(
      final int drawingAreaWidth, final int drawingAreaHeight, final int numHexagons) {
    final float THREE_HALVES_SQRT_OF_THREE = 2.59807621135f;
    final int fullArea = drawingAreaWidth * drawingAreaHeight;
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

  /**
   * @param outCorner - a re-usable Point buffer to output the
   * @param inCenter - a reusable Point buffer representing the center coordinates of a hexagon
   * @param sideLength - length of hexagon side
   * @param cornerNumber - from 0 to 6 (we count 7 because we have to get back to the origin)
   */
  private static void getHexCorner(
      final Point2D.Float outCorner, final Point inCenter, int cornerNumber, float sideLength) {
    double angle_rad = Math.toRadians((60 * cornerNumber) + 30);
    // Sub-pixel corners: int truncation makes the cubes jagged once scaled on HiDPI screens
    outCorner.setLocation(
        inCenter.x + sideLength * Math.cos(angle_rad),
        inCenter.y + sideLength * Math.sin(angle_rad));
  }

  private static void drawInnerEdge(
      final DrawingProperties drawingProperties, final Graphics2D graphics, int cornerNumber) {
    getHexCorner(
        drawingProperties.cornerBuffer,
        drawingProperties.hexCenterBuffer,
        cornerNumber,
        drawingProperties.hexSideLength);
    drawingProperties.lineBuffer.setLine(
        drawingProperties.hexCenterBuffer.x,
        drawingProperties.hexCenterBuffer.y,
        drawingProperties.cornerBuffer.x,
        drawingProperties.cornerBuffer.y);
    graphics.draw(drawingProperties.lineBuffer);
  }

  private static void drawHexagon(
      final DrawingProperties drawingProperties,
      final Graphics2D graphics,
      final ColoredStroke borderStroke,
      final CubePaint fillPaint,
      final boolean drawCube) {
    // Create outer shape for Hexagon
    drawingProperties.hexagonBorderPath.reset();
    for (int i = 0; i < 6; i++) {
      getHexCorner(
          drawingProperties.cornerBuffer,
          drawingProperties.hexCenterBuffer,
          i,
          drawingProperties.hexSideLength);
      if (i == 0) {
        drawingProperties.hexagonBorderPath.moveTo(
            drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      } else {
        drawingProperties.hexagonBorderPath.lineTo(
            drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      }
    }
    drawingProperties.hexagonBorderPath.closePath();
    // Fill hexagon with base color
    graphics.setPaint(fillPaint.getBaseColor());
    graphics.fill(drawingProperties.hexagonBorderPath);
    drawingProperties.fillPathBuffer.reset();
    if (drawCube) {
      // LEFT FACE (DARK)
      // bottom corner - 90 degrees (with zero at horizon on the right side)
      // angles move clockwise - corner 1 is at the bottom (90 degrees down from the right)
      // Create shape for left face
      drawingProperties.fillPathBuffer.moveTo(
          drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
      getHexCorner(
          drawingProperties.cornerBuffer,
          drawingProperties.hexCenterBuffer,
          1,
          drawingProperties.hexSideLength);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      getHexCorner(
          drawingProperties.cornerBuffer,
          drawingProperties.hexCenterBuffer,
          2,
          drawingProperties.hexSideLength);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      getHexCorner(
          drawingProperties.cornerBuffer,
          drawingProperties.hexCenterBuffer,
          3,
          drawingProperties.hexSideLength);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
      // Fill left face
      graphics.setPaint(fillPaint.getDarkColor()); // might be setColor in case this doesn't work
      graphics.fill(drawingProperties.fillPathBuffer);
      // TOP FACE (LIGHT)
      // Create shape for top face
      drawingProperties.fillPathBuffer.reset();
      drawingProperties.fillPathBuffer.moveTo(
          drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
      getHexCorner(
          drawingProperties.cornerBuffer,
          drawingProperties.hexCenterBuffer,
          3,
          drawingProperties.hexSideLength);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      getHexCorner(
          drawingProperties.cornerBuffer,
          drawingProperties.hexCenterBuffer,
          4,
          drawingProperties.hexSideLength);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      getHexCorner(
          drawingProperties.cornerBuffer,
          drawingProperties.hexCenterBuffer,
          5,
          drawingProperties.hexSideLength);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
      drawingProperties.fillPathBuffer.lineTo(
          drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
      // Fill top face
      graphics.setPaint(fillPaint.getLightColor());
      graphics.fill(drawingProperties.fillPathBuffer);
      // Now paint inner faces border, 3 line.
      // From center to 1, center to 3 and center to 5
      graphics.setPaint(borderStroke.getColor());
      graphics.setStroke(borderStroke);
      drawInnerEdge(drawingProperties, graphics, 1);
      drawInnerEdge(drawingProperties, graphics, 3);
      drawInnerEdge(drawingProperties, graphics, 5);
      graphics.setPaint(fillPaint.getBaseColor());
    }
    // Paint outer border
    graphics.setPaint(borderStroke.getColor());
    graphics.setStroke(borderStroke);
    graphics.draw(drawingProperties.hexagonBorderPath);
    drawingProperties.hexagonBorderPath.reset();
    drawingProperties.cornerBuffer.setLocation(-1, -1);
  }

  public static void main(String[] args) {
    final HexHivePanel hexPanel =
        new HexHivePanel(50, 0x264053, 0xf2f2f2, 0x33b5e5, 0xffffff, 0, 0, 0, 0, true);
    final HexDataAdapter mockAdapter =
        new HexDataAdapter() {
          @Override
          public void updateData(Object data) {}

          @Override
          public int getTotalHexagonsCount() {
            return 10;
          }

          @Override
          public int getFullHexagonsCount() {
            return 0;
          }

          @Override
          public boolean isFull(int hexOffset) {
            return hexOffset % 2 == 0;
          }
        };
    JFrame frame = new JFrame("HexHive Testing Area");
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setResizable(true);
    frame.setVisible(true);
    frame.add(hexPanel);
    frame.setSize(640, 480);
    frame.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            super.componentResized(e);
            hexPanel.updateData(mockAdapter);
          }

          @Override
          public void componentMoved(ComponentEvent e) {
            super.componentMoved(e);
            hexPanel.updateData(mockAdapter);
          }
        });
    hexPanel.updateData(mockAdapter);
  }

  void updateData(HexDataAdapter hexDataAdapter) {
    final int canvasWidth = getWidth();
    final int canvasHeight = getHeight();
    DrawingProperties snapshot = null;
    if (canvasHeight > 0 && canvasWidth > 0 && hexDataAdapter != null) {
      snapshot =
          new DrawingProperties(
              hexDataAdapter,
              hexSideLength,
              hexagonBorderPaint.getLineWidth(),
              leftPadding,
              topPadding,
              canvasWidth - rightPadding,
              canvasHeight - bottomPadding);
      snapshot.scale = renderScale;
      snapshot.canvasWidth = canvasWidth;
      synchronized (drawingPropertiesLock) {
        drawingProperties = snapshot;
        latestAdapter = hexDataAdapter;
        lastHeight = snapshot.height;
        lastWidth = snapshot.width;
      }
    }
    if (snapshot == null) {
      // not ready yet (perhaps during animation or rotation)
      return;
    }
    ensureBackingBitmap(snapshot, currentVisibleRect());
    if (hexDataAdapter != null
        && hexDataAdapter.getFullHexagonsCount() >= 0
        && canvasWidth > 0
        && canvasHeight > 0) {
      requestFocusedRender(snapshot, hexDataAdapter, true);
    }
    if (hoveredHex >= snapshot.numHexs) {
      clearHover();
    } else if (hoveredHex >= 0 && hexDataAdapter != null) {
      // keep the details card live while the piece downloads
      requestHoverDetails(hexDataAdapter, hoveredHex);
    }
  }

  private Rectangle currentVisibleRect() {
    Rectangle visibleRect = getVisibleRect();
    if (visibleRect.width <= 0 || visibleRect.height <= 0) {
      // not showing yet, don't size the backing window after the whole content
      visibleRect =
          new Rectangle(
              0,
              0,
              Math.max(1, getWidth()),
              Math.max(1, Math.min(getHeight(), FALLBACK_VIEWPORT_HEIGHT)));
    }
    return visibleRect;
  }

  /**
   * Makes the backing bitmap hold the rows around {@code visibleRect} for this layout, moving the
   * window along when the viewport scrolls away from it.
   *
   * @return the content-space rectangle the backing bitmap covers
   */
  private Rectangle ensureBackingBitmap(DrawingProperties snapshot, Rectangle visibleRect) {
    boolean changed = false;
    Rectangle window;
    synchronized (bitmapLock) {
      int windowHeight = backingWindowHeight(snapshot.height, visibleRect.height);
      if (bitmap == null || bitmapLayout == null || !bitmapLayout.hasSameLayout(snapshot)) {
        // a fresh bitmap: pixels of the previous layout outside the new rows would linger
        int deviceWidth = Math.max(1, toDevice(snapshot.width, snapshot.scale));
        int deviceHeight = deviceWindowHeight(windowHeight, snapshot.scale);
        bitmap = createOpaqueImage(deviceWidth, deviceHeight);
        bitmapScale = snapshot.scale;
        bitmapLayout = snapshot;
        bitmapOriginY = anchorBackingWindow(snapshot.height, windowHeight, visibleRect);
        bitmapWindowHeight = windowHeight;
        bitmapWindowGeneration++;
        Graphics2D graphics = bitmap.createGraphics();
        graphics.setPaint(backgroundColor);
        graphics.fillRect(0, 0, deviceWidth, deviceHeight);
        graphics.dispose();
        renderedArea.reset();
        changed = true;
      } else if (windowHeight != bitmapWindowHeight
          || !backingWindowCovers(
              bitmapOriginY, bitmapWindowHeight, snapshot.height, visibleRect)) {
        int originY = anchorBackingWindow(snapshot.height, windowHeight, visibleRect);
        moveBackingWindow(snapshot, originY, windowHeight);
      }
      window = new Rectangle(0, bitmapOriginY, snapshot.width, bitmapWindowHeight);
    }
    if (changed) {
      SwingUtilities.invokeLater(
          () -> {
            revalidate();
            repaint();
          });
    }
    return window;
  }

  /**
   * Re-anchors the backing window at {@code originY}, keeping the pixels of the rows the old and
   * new windows share. Must hold {@link #bitmapLock}.
   */
  private void moveBackingWindow(DrawingProperties snapshot, int originY, int windowHeight) {
    double scale = bitmapScale;
    int deviceWidth = bitmap.getWidth();
    int deviceHeight = deviceWindowHeight(windowHeight, scale);
    // device row the old window's first row lands on in the new one
    int shift = toDevice(bitmapOriginY, scale) - toDevice(originY, scale);
    BufferedImage target =
        bitmap.getHeight() == deviceHeight ? bitmap : createOpaqueImage(deviceWidth, deviceHeight);
    Graphics2D graphics = target.createGraphics();
    if (target == bitmap) {
      graphics.copyArea(0, 0, deviceWidth, deviceHeight, 0, shift);
    } else {
      graphics.drawImage(bitmap, 0, shift, null);
    }
    int keptTop = Math.max(0, shift);
    int keptBottom = Math.min(deviceHeight, shift + bitmap.getHeight());
    graphics.setPaint(backgroundColor);
    if (keptBottom <= keptTop) {
      graphics.fillRect(0, 0, deviceWidth, deviceHeight);
    } else {
      graphics.fillRect(0, 0, deviceWidth, keptTop);
      graphics.fillRect(0, keptBottom, deviceWidth, deviceHeight - keptBottom);
    }
    graphics.dispose();
    bitmap = target;
    bitmapOriginY = originY;
    bitmapWindowHeight = windowHeight;
    bitmapWindowGeneration++;
    renderedArea.intersect(new Area(new Rectangle(0, originY, snapshot.width, windowHeight)));
  }

  private void requestFocusedRender(
      DrawingProperties drawingProperties, HexDataAdapter adapter, boolean force) {
    Rectangle visibleRect = currentVisibleRect();
    Rectangle window = ensureBackingBitmap(drawingProperties, visibleRect);
    // nothing outside the backing window can be kept, nor needs rendering
    Rectangle priorityBounds =
        computePriorityRenderBounds(drawingProperties, visibleRect, lastVisibleRect)
            .intersection(window);
    Rectangle targetBounds =
        computeRenderBounds(drawingProperties, visibleRect, lastVisibleRect).intersection(window);
    Rectangle visibleBounds = visibleRect.intersection(window);
    lastVisibleRect = new Rectangle(visibleRect);
    if (priorityBounds.isEmpty() || targetBounds.isEmpty()) {
      return;
    }
    boolean needsPriorityRender;
    boolean needsTargetRender;
    synchronized (bitmapLock) {
      boolean visibleCovered =
          visibleBounds.isEmpty()
              || renderedArea.contains(
                  visibleBounds.x, visibleBounds.y, visibleBounds.width, visibleBounds.height);
      needsPriorityRender =
          force
              || bitmap == null
              || !visibleCovered
              || !renderedArea.contains(
                  priorityBounds.x, priorityBounds.y, priorityBounds.width, priorityBounds.height);
      needsTargetRender =
          force
              || bitmap == null
              || !renderedArea.contains(
                  targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height);
    }
    if (needsPriorityRender) {
      enqueueRender(new RenderRequest(adapter, drawingProperties, priorityBounds, true));
    } else if (needsTargetRender) {
      enqueueRender(new RenderRequest(adapter, drawingProperties, targetBounds, false));
    }
  }

  private void enqueueRender(RenderRequest request) {
    boolean shouldStartWorker = false;
    synchronized (renderLock) {
      pendingRenderRequest = request;
      if (!renderRunning) {
        renderRunning = true;
        shouldStartWorker = true;
      }
    }
    if (shouldStartWorker) {
      threadPool.execute(this::processPendingRenders);
    }
  }

  private void processPendingRenders() {
    while (true) {
      final RenderRequest request;
      synchronized (renderLock) {
        request = pendingRenderRequest;
        pendingRenderRequest = null;
        if (request == null) {
          renderRunning = false;
          return;
        }
      }
      renderRegionIntoBackingBitmap(
          request.adapter,
          request.drawingProperties,
          request.renderBounds,
          request.prioritizeLatency);
    }
  }

  private void renderRegionIntoBackingBitmap(
      HexDataAdapter adapter,
      DrawingProperties drawingProperties,
      Rectangle renderBounds,
      boolean prioritizeLatency) {
    final int windowGeneration;
    synchronized (bitmapLock) {
      windowGeneration = bitmapWindowGeneration;
    }
    int[] rowRange = getRowRangeForBounds(drawingProperties, renderBounds);
    int firstRow = rowRange[0];
    int lastRow = rowRange[1];
    int maxRow =
        Math.max(
            0, drawingProperties.getRowIndexForPiece(Math.max(0, drawingProperties.numHexs - 1)));
    final int rowsPerBatch = prioritizeLatency ? 2 : 4;
    for (int row = firstRow; row <= lastRow; row += rowsPerBatch) {
      int batchLastRow = Math.min(lastRow, row + rowsPerBatch - 1);
      int drawFirstRow = Math.max(0, row - 1);
      int drawLastRow = Math.min(maxRow, batchLastRow + 1);
      Rectangle drawBounds =
          computeBatchBounds(drawingProperties, renderBounds, drawFirstRow, drawLastRow);
      Rectangle mergeBounds =
          computeMergeBounds(drawingProperties, renderBounds, row, batchLastRow, maxRow);
      if (drawBounds.width <= 0
          || drawBounds.height <= 0
          || mergeBounds.width <= 0
          || mergeBounds.height <= 0) {
        continue;
      }
      double scale = drawingProperties.scale;
      int deviceX = toDevice(drawBounds.x, scale);
      int deviceY = toDevice(drawBounds.y, scale);
      int deviceWidth = Math.max(1, toDevice(drawBounds.x + drawBounds.width, scale) - deviceX);
      int deviceHeight = Math.max(1, toDevice(drawBounds.y + drawBounds.height, scale) - deviceY);
      BufferedImage batchBitmap = createOpaqueImage(deviceWidth, deviceHeight);
      Graphics2D graphics = batchBitmap.createGraphics();
      graphics.setPaint(backgroundColor);
      graphics.fillRect(0, 0, deviceWidth, deviceHeight);
      boolean drawCubes = (forceCubes) || drawingProperties.numHexs <= 500;
      graphics.setRenderingHint(
          RenderingHints.KEY_ANTIALIASING,
          drawCubes ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
      graphics.setRenderingHint(
          RenderingHints.KEY_STROKE_CONTROL,
          drawCubes ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      graphics.translate(-deviceX, -deviceY);
      graphics.scale(scale, scale);
      drawRowsToGraphics(
          drawingProperties, adapter, graphics, drawBounds, drawFirstRow, drawLastRow, drawCubes);
      graphics.dispose();
      if (!mergeBatchBitmap(batchBitmap, drawingProperties, deviceX, deviceY, mergeBounds)) {
        // the layout changed meanwhile, its own render is queued
        return;
      }
    }
    synchronized (bitmapLock) {
      // The batches only cover the rows' cubes. The padding around them is already background,
      // count it as rendered too, or the visible area never looks covered and every paint queues
      // yet another render. Only valid if the window didn't move while the batches were merged.
      if (bitmapWindowGeneration == windowGeneration
          && bitmapLayout != null
          && bitmapLayout.hasSameLayout(drawingProperties)) {
        renderedArea.add(
            new Area(
                renderBounds.intersection(
                    new Rectangle(0, bitmapOriginY, drawingProperties.width, bitmapWindowHeight))));
      }
    }
  }

  private BufferedImage createOpaqueImage(int width, int height) {
    GraphicsConfiguration gc = graphicsConfig;
    return (gc != null)
        ? gc.createCompatibleImage(width, height, Transparency.OPAQUE)
        : new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
  }

  private void drawRowsToGraphics(
      DrawingProperties drawingProperties,
      HexDataAdapter adapter,
      Graphics2D graphics,
      Rectangle renderBounds,
      int firstRow,
      int lastRow,
      boolean drawCubes) {
    float halfHexWidth = drawingProperties.hexWidth / 2f;
    float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
    for (int row = firstRow; row <= lastRow; row++) {
      int rowStartIndex = drawingProperties.getRowStartIndex(row);
      int rowHexCount = drawingProperties.getRowHexCount(row);
      if (rowHexCount <= 0 || rowStartIndex >= drawingProperties.numHexs) {
        continue;
      }
      boolean evenRow = (row % 2) == 0;
      int rowOriginX =
          evenRow ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
      int centerY = Math.round(drawingProperties.evenRowOrigin.y + (row * verticalStep));
      int firstCol =
          Math.max(
              0,
              (int)
                      Math.floor(
                          (renderBounds.x - (rowOriginX - halfHexWidth))
                              / drawingProperties.hexWidth)
                  - 1);
      int lastCol =
          Math.min(
              rowHexCount - 1,
              (int)
                  Math.ceil(
                      (renderBounds.x + renderBounds.width - (rowOriginX - halfHexWidth))
                          / drawingProperties.hexWidth));
      for (int col = firstCol; col <= lastCol; col++) {
        int pieceIndex = rowStartIndex + col;
        if (pieceIndex >= drawingProperties.numHexs) {
          break;
        }
        drawingProperties.hexCenterBuffer.x =
            Math.round(rowOriginX + (col * drawingProperties.hexWidth));
        drawingProperties.hexCenterBuffer.y = centerY;
        drawHexagon(
            drawingProperties,
            graphics,
            hexagonBorderPaint,
            adapter.isFull(pieceIndex) ? fullHexPaint : emptyHexPaint,
            drawCubes);
      }
    }
  }

  private Rectangle computeBatchBounds(
      DrawingProperties drawingProperties, Rectangle targetBounds, int firstRow, int lastRow) {
    float halfHexWidth = drawingProperties.hexWidth / 2f;
    float halfHexHeight = drawingProperties.hexHeight / 2f;
    float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    for (int row = firstRow; row <= lastRow; row++) {
      int rowHexCount = drawingProperties.getRowHexCount(row);
      if (rowHexCount <= 0) {
        continue;
      }
      boolean evenRow = (row % 2) == 0;
      int rowOriginX =
          evenRow ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
      // the cubes are drawn at rounded centers, and their border reaches past the hexagon
      float overhang = drawingProperties.borderOverhang();
      int rowMinX = (int) Math.floor(rowOriginX - halfHexWidth - overhang);
      int rowMaxX =
          (int)
              Math.ceil(
                  Math.round(rowOriginX + ((rowHexCount - 1) * drawingProperties.hexWidth))
                      + halfHexWidth
                      + overhang);
      minX = Math.min(minX, rowMinX);
      maxX = Math.max(maxX, rowMaxX);
    }
    if (minX == Integer.MAX_VALUE) {
      return new Rectangle();
    }
    int minY =
        Math.round(drawingProperties.evenRowOrigin.y + (firstRow * verticalStep) - halfHexHeight);
    int maxY =
        Math.round(drawingProperties.evenRowOrigin.y + (lastRow * verticalStep) + halfHexHeight);
    Rectangle batch =
        new Rectangle(
            Math.max(0, Math.max(targetBounds.x, minX)),
            Math.max(0, Math.max(targetBounds.y, minY)),
            1,
            1);
    int batchRight =
        Math.min(drawingProperties.width, Math.min(targetBounds.x + targetBounds.width, maxX));
    int batchBottom =
        Math.min(drawingProperties.height, Math.min(targetBounds.y + targetBounds.height, maxY));
    batch.width = Math.max(1, batchRight - batch.x);
    batch.height = Math.max(1, batchBottom - batch.y);
    return batch;
  }

  private Rectangle computeMergeBounds(
      DrawingProperties drawingProperties,
      Rectangle targetBounds,
      int firstRow,
      int lastRow,
      int maxRow) {
    // The band between neighbouring row midpoints also holds the pointy tips of the rows above and
    // below, so it must span those rows too, or a short last row leaves the second to last row's
    // bottom tips unmerged.
    Rectangle batch =
        computeBatchBounds(
            drawingProperties,
            targetBounds,
            Math.max(0, firstRow - 1),
            Math.min(maxRow, lastRow + 1));
    if (batch.width <= 0 || batch.height <= 0) {
      return batch;
    }
    float halfHexHeight = drawingProperties.hexHeight / 2f;
    int topEdge =
        firstRow <= 0
            ? Math.round(getRowCenterY(drawingProperties, firstRow) - halfHexHeight)
            : Math.round(
                (getRowCenterY(drawingProperties, firstRow - 1)
                        + getRowCenterY(drawingProperties, firstRow))
                    / 2f);
    int bottomEdge =
        lastRow >= maxRow
            ? Math.round(getRowCenterY(drawingProperties, lastRow) + halfHexHeight)
            : Math.round(
                (getRowCenterY(drawingProperties, lastRow)
                        + getRowCenterY(drawingProperties, lastRow + 1))
                    / 2f);
    int mergeTop = Math.max(batch.y, Math.max(targetBounds.y, topEdge));
    int mergeBottom =
        Math.min(
            batch.y + batch.height, Math.min(targetBounds.y + targetBounds.height, bottomEdge));
    if (mergeBottom <= mergeTop) {
      return new Rectangle(batch.x, batch.y, batch.width, batch.height);
    }
    return new Rectangle(batch.x, mergeTop, batch.width, mergeBottom - mergeTop);
  }

  private int[] getRowRangeForBounds(DrawingProperties drawingProperties, Rectangle renderBounds) {
    float halfHexHeight = drawingProperties.hexHeight / 2f;
    float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
    int firstRow =
        Math.max(
            0,
            (int)
                    Math.floor(
                        (renderBounds.y - drawingProperties.evenRowOrigin.y - halfHexHeight)
                            / verticalStep)
                - 1);
    int maxRows =
        Math.max(
            0, drawingProperties.getRowIndexForPiece(Math.max(0, drawingProperties.numHexs - 1)));
    int lastRow =
        Math.min(
            maxRows,
            Math.max(
                firstRow,
                (int)
                        Math.ceil(
                            (renderBounds.y
                                    + renderBounds.height
                                    - drawingProperties.evenRowOrigin.y
                                    + halfHexHeight)
                                / verticalStep)
                    + 1));
    return new int[] {firstRow, lastRow};
  }

  private int getRowCenterY(DrawingProperties drawingProperties, int row) {
    float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
    return Math.round(drawingProperties.evenRowOrigin.y + (row * verticalStep));
  }

  private boolean mergeBatchBitmap(
      BufferedImage renderedBitmap,
      DrawingProperties drawingProperties,
      int sourceDeviceX,
      int sourceDeviceY,
      Rectangle mergeBounds) {
    double scale = drawingProperties.scale;
    final Rectangle merged;
    synchronized (bitmapLock) {
      if (bitmap == null
          || bitmapLayout == null
          || !bitmapLayout.hasSameLayout(drawingProperties)) {
        return false;
      }
      // the window may have moved since this batch was requested, keep only what still fits
      merged =
          mergeBounds.intersection(
              new Rectangle(0, bitmapOriginY, drawingProperties.width, bitmapWindowHeight));
      if (merged.isEmpty()) {
        return true;
      }
      int deviceOriginY = toDevice(bitmapOriginY, scale);
      int destX1 = toDevice(merged.x, scale);
      int destY1 = toDevice(merged.y, scale);
      int destX2 = toDevice(merged.x + merged.width, scale);
      int destY2 = toDevice(merged.y + merged.height, scale);
      Graphics2D graphics = bitmap.createGraphics();
      graphics.drawImage(
          renderedBitmap,
          destX1,
          destY1 - deviceOriginY,
          destX2,
          destY2 - deviceOriginY,
          destX1 - sourceDeviceX,
          destY1 - sourceDeviceY,
          destX2 - sourceDeviceX,
          destY2 - sourceDeviceY,
          null);
      graphics.dispose();
      renderedArea.add(new Area(merged));
    }
    SwingUtilities.invokeLater(() -> repaint(merged.x, merged.y, merged.width, merged.height));
    return true;
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    DrawingProperties snapshot;
    synchronized (drawingPropertiesLock) {
      snapshot = drawingProperties;
    }
    // one row of cubes
    return snapshot != null ? Math.max(1, Math.round(snapshot.hexHeight * 3f / 4f)) : 16;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
  }

  /** Rows wrap to the viewport's width, so only vertical scrolling is ever needed. */
  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  /** Fills the viewport when the rows are shorter than it, so no stale area is left below. */
  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(lastWidth, lastHeight);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;
    AffineTransform transform = g2d.getTransform();
    double deviceScale = sanitizeScale(transform.getScaleX());
    DrawingProperties snapshotProperties;
    HexDataAdapter adapterSnapshot;
    synchronized (drawingPropertiesLock) {
      snapshotProperties = drawingProperties;
      adapterSnapshot = latestAdapter;
    }
    boolean scaleChanged;
    synchronized (bitmapLock) {
      scaleChanged = Math.abs(deviceScale - bitmapScale) > 0.001;
    }
    boolean rescale = scaleChanged && renderScale != deviceScale;
    if (!rescale && snapshotProperties != null && adapterSnapshot != null) {
      // moves the backing window to the visible rows before it's blitted
      requestFocusedRender(snapshotProperties, adapterSnapshot, false);
    }
    synchronized (bitmapLock) {
      scaleChanged = Math.abs(deviceScale - bitmapScale) > 0.001;
      if (bitmap != null) {
        if (!scaleChanged && transform.getShearX() == 0 && transform.getShearY() == 0) {
          // Blit the device-resolution bitmap 1:1 onto the device pixels, no resampling
          Graphics2D deviceGraphics = (Graphics2D) g2d.create();
          deviceGraphics.setTransform(
              AffineTransform.getTranslateInstance(
                  Math.round(transform.getTranslateX()),
                  Math.round(transform.getTranslateY()) + toDevice(bitmapOriginY, bitmapScale)));
          deviceGraphics.drawImage(bitmap, 0, 0, null);
          deviceGraphics.dispose();
        } else {
          // Screen scale changed (window moved to another monitor), stretch until it re-renders
          g2d.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          g2d.drawImage(
              bitmap,
              0,
              bitmapOriginY,
              (int) Math.round(bitmap.getWidth() / bitmapScale),
              (int) Math.round(bitmap.getHeight() / bitmapScale),
              null);
        }
      }
    }
    paintHoveredHex(g2d, snapshotProperties);
    if (rescale) {
      renderScale = deviceScale;
      if (adapterSnapshot != null) {
        final HexDataAdapter adapter = adapterSnapshot;
        SwingUtilities.invokeLater(() -> updateData(adapter));
      }
    }
  }

  private void paintHoveredHex(Graphics2D g2d, DrawingProperties snapshot) {
    if (hoveredHex < 0 || snapshot == null || hoveredHex >= snapshot.numHexs) {
      return;
    }
    // Own drawing buffers: the snapshot's are in use by the render thread
    DrawingProperties painter = new DrawingProperties(snapshot.hexSideLength);
    Point center = getHexCenter(snapshot, hoveredHex);
    painter.hexCenterBuffer.setLocation(center);
    Graphics2D graphics = (Graphics2D) g2d.create();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    drawHexagon(painter, graphics, HOVER_STROKE, HOVER_PAINT, true);
    graphics.dispose();
  }

  private void initPaints(int numBorderColor, int numEmptyColor, int numFullColor, int bgColor) {
    hexagonBorderPaint = new ColoredStroke(0.5f, new Color(numBorderColor, false));
    emptyHexPaint = new CubePaint(numEmptyColor, 10);
    fullHexPaint = new CubePaint(numFullColor, 30);
    backgroundColor = new Color(bgColor);
  }

  // Drawing/Geometry functions
  public interface HexDataAdapter<T> {
    void updateData(T data);

    int getTotalHexagonsCount();

    int getFullHexagonsCount();

    boolean isFull(int hexOffset);

    /**
     * Label/value rows shown in the floating card when the mouse hovers a hexagon. Called off the
     * EDT. Values may contain '\n' to span several lines.
     *
     * @return null or empty for no card
     */
    default Map<String, String> describeHex(int hexOffset) {
      return null;
    }
  }

  /** Floating card listing a hovered hexagon's details, styled like the theme's tooltips. */
  private static JPanel createInfoCard() {
    JPanel card = new JPanel(new GridBagLayout());
    Color background = UIManager.getColor("ToolTip.background");
    card.setBackground(background != null ? background : new Color(0xffffe1));
    Border border = UIManager.getBorder("ToolTip.border");
    if (border == null) {
      border = BorderFactory.createLineBorder(Color.GRAY);
    }
    card.setBorder(
        BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    return card;
  }

  private static void fillInfoCard(JPanel card, Map<String, String> details) {
    card.removeAll();
    Font font = UIManager.getFont("ToolTip.font");
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.FIRST_LINE_START;
    c.gridy = 0;
    for (Map.Entry<String, String> row : details.entrySet()) {
      c.gridx = 0;
      c.insets = new Insets(1, 0, 1, 10);
      card.add(createCardLabel(row.getKey(), font != null ? font.deriveFont(Font.BOLD) : null), c);
      c.gridx = 1;
      c.insets = new Insets(1, 0, 1, 0);
      for (String line : String.valueOf(row.getValue()).split("\\n")) {
        card.add(createCardLabel(line, font), c);
        c.gridy++;
      }
    }
  }

  private static JComponent createCardLabel(String text, Font font) {
    JLabel label = new JLabel(text);
    if (font != null) {
      label.setFont(font);
    }
    label.setForeground(UIManager.getColor("ToolTip.foreground"));
    return label;
  }

  private static final class ColoredStroke extends BasicStroke {
    private final Color color;

    ColoredStroke(float width, Color color) {
      super(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
      this.color = color;
    }

    Color getColor() {
      return color;
    }
  }

  private static final class CubePaint {
    private final Color baseColor;
    private final Color darkColor;
    private final Color lightColor;

    CubePaint(int numBaseColor, int shades) {
      int numDarkColor;
      int numLightColor;
      int R = (numBaseColor >> 16) & 0xff;
      int G = (numBaseColor >> 8) & 0xff;
      int B = (numBaseColor) & 0xff;
      int darkR = Math.max(R - shades, 0);
      int darkG = Math.max(G - shades, 0);
      int darkB = Math.max(B - shades, 0);
      int lightR = Math.min(R + shades, 0xff);
      int lightG = Math.min(G + shades, 0xff);
      int lightB = Math.min(B + shades, 0xff);
      numDarkColor = (darkR & 0xff) << 16 | (darkG & 0xff) << 8 | (darkB & 0xff);
      numLightColor = (lightR & 0xff) << 16 | (lightG & 0xff) << 8 | (lightB & 0xff);
      baseColor = new Color(numBaseColor, false);
      darkColor = new Color(numDarkColor, false);
      lightColor = new Color(numLightColor, false);
    }

    Color getBaseColor() {
      return baseColor;
    }

    Color getDarkColor() {
      return darkColor;
    }

    Color getLightColor() {
      return lightColor;
    }
  }

  private static final class RenderRequest {
    private final HexDataAdapter adapter;
    private final DrawingProperties drawingProperties;
    private final Rectangle renderBounds;
    private final boolean prioritizeLatency;

    private RenderRequest(
        HexDataAdapter adapter,
        DrawingProperties drawingProperties,
        Rectangle renderBounds,
        boolean prioritizeLatency) {
      this.adapter = adapter;
      this.drawingProperties = drawingProperties;
      this.renderBounds = renderBounds;
      this.prioritizeLatency = prioritizeLatency;
    }
  }

  private static final class DrawingProperties {
    // Painting Area Configuration
    private final Point evenRowOrigin;
    private final Point oddRowOrigin;

    /**
     * Point object we'll reuse to draw hexagons (Object creation and destruction must be avoided
     * when calling onDraw())
     */
    private final Point hexCenterBuffer = new Point(-1, -1);

    /**
     * Point object we'll reuse to draw hexagon sides (Object creation and destruction must be
     * avoided when calling onDraw())
     */
    private final Point2D.Float cornerBuffer = new Point2D.Float(-1, -1);

    /** Line object we'll reuse to draw the inner cube edges */
    private final Line2D.Float lineBuffer = new Line2D.Float();

    /** Path object we'll reuse to draw the filled areas of the hexagons */
    private final GeneralPath fillPathBuffer = new GeneralPath();

    /** Path object we'll reuse to draw the hexagons outer shapes */
    private final GeneralPath hexagonBorderPath = new GeneralPath();

    /** Drawing area top-left */
    private Point origin;

    // Hexagon Geometry Helpers
    /** Drawing area center */
    private Point center;

    /** Drawing are bottom-right cornerBuffer */
    private Point end;

    /** Right/bottom edge of the available drawing viewport; used for wrapping rows. */
    private int wrapRight;

    private int wrapBottom;

    /** Drawing area width */
    private int width;

    /** Drawing area height */
    private int height;

    /** Number of hexagons to draw */
    private int numHexs;

    /** Side length of each hexagon */
    private float hexSideLength;

    /** Height of each hexagon */
    private float hexHeight;

    /** Width of each hexagon */
    private float hexWidth;

    /** Hexagon border stroke width, has to be converted to pixels depending on screen density */
    private float hexBorderStrokeWidth;

    private int evenRowHexCount;
    private int oddRowHexCount;

    /** Device pixels per logical pixel this snapshot renders at */
    private double scale = 1.0;

    /** Panel width this layout wrapped its rows to */
    private int canvasWidth;

    /** Whether a bitmap drawn for this layout puts every hexagon where {@code other} does. */
    private boolean hasSameLayout(DrawingProperties other) {
      return width == other.width
          && height == other.height
          && scale == other.scale
          && numHexs == other.numHexs
          && hexSideLength == other.hexSideLength
          && evenRowHexCount == other.evenRowHexCount
          && oddRowHexCount == other.oddRowHexCount
          && evenRowOrigin.equals(other.evenRowOrigin)
          && oddRowOrigin.equals(other.oddRowOrigin);
    }

    /** Geometry-less instance used only for its drawing buffers when painting a single hexagon. */
    DrawingProperties(float hexSideLength) {
      evenRowOrigin = new Point(0, 0);
      oddRowOrigin = new Point(0, 0);
      this.hexSideLength = hexSideLength;
    }

    DrawingProperties(
        HexDataAdapter adapter,
        int hexSideLen,
        float hexBorderWidth,
        int left,
        int top,
        int right,
        int bottom) {
      if (adapter == null) {
        throw new RuntimeException(
            "check your logic, you need a data adapter before calling initDrawingProperties");
      }
      // The canvas can paint the entire view, if padding has been defined,
      // we won't draw outside the padded area.
      hexBorderStrokeWidth = hexBorderWidth;
      origin = new Point(0, 0);
      center = new Point(0, 0);
      end = new Point(0, 0);
      evenRowOrigin = new Point(0, 0);
      oddRowOrigin = new Point(0, 0);
      numHexs = adapter.getTotalHexagonsCount();
      hexSideLength = hexSideLen;
      update(left, top, right, bottom);
    }

    void update(int left, int top, int right, int bottom) {
      origin.x = left;
      origin.y = top;
      center.x = (left + right) >> 1;
      center.y = (top + bottom) >> 1;
      end.x = right;
      end.y = bottom;
      wrapRight = right;
      wrapBottom = bottom;
      int availableWidth = right - left;
      int availableHeight = bottom - top;
      if (hexSideLength == -1) {
        hexSideLength = getHexagonSideLength(availableWidth, availableHeight, numHexs);
      }
      hexHeight = getHexHeight(hexSideLength);
      hexWidth = getHexWidth(hexSideLength);
      float halfHexWidth = hexWidth / 2f;
      float halfHexHeight = hexHeight / 2f;
      float verticalStep = (hexHeight * 3f) / 4f;
      evenRowOrigin.x = Math.round(origin.x + halfHexWidth);
      evenRowOrigin.y = Math.round(origin.y + halfHexHeight);
      oddRowOrigin.x = Math.round(evenRowOrigin.x + halfHexWidth);
      oddRowOrigin.y = Math.round(evenRowOrigin.y + verticalStep);
      evenRowHexCount = computeRowHexCount(evenRowOrigin.x, halfHexWidth, right);
      oddRowHexCount = computeRowHexCount(oddRowOrigin.x, halfHexWidth, right);
      if (hexSideLength != -1) {
        // we need to calculate the end.y and the new drawing total height depending
        // on how many rows we'll have.
        int lastRow = getRowIndexForPiece(numHexs - 1);
        int lastRowHexCount = getRowHexCount(lastRow);
        int lastRowStartIndex = getRowStartIndex(lastRow);
        int lastCol = Math.max(0, Math.min(lastRowHexCount - 1, (numHexs - 1) - lastRowStartIndex));
        boolean lastRowEven = (lastRow % 2) == 0;
        int lastRowOriginX = lastRowEven ? evenRowOrigin.x : oddRowOrigin.x;
        int lastCenterX = Math.round(lastRowOriginX + (lastCol * hexWidth));
        int lastCenterY = Math.round(evenRowOrigin.y + (lastRow * verticalStep));
        // Odd rows start half a cube further right: holding as many cubes as the even rows,
        // they also end further right, so the widest row is whichever ends further right.
        int widestRowCenterX =
            Math.round(evenRowOrigin.x + Math.max(0, evenRowHexCount - 1) * hexWidth);
        if (numHexs > evenRowHexCount) {
          widestRowCenterX =
              Math.max(
                  widestRowCenterX,
                  Math.round(oddRowOrigin.x + Math.max(0, oddRowHexCount - 1) * hexWidth));
        }
        end.x =
            (int)
                Math.ceil(
                    Math.max(widestRowCenterX, lastCenterX) + halfHexWidth + borderOverhang());
        end.y = Math.round(lastCenterY + halfHexHeight);
        width = Math.max(1, end.x + 1);
        height = Math.max(1, end.y + 1);
      } else {
        width = Math.max(1, end.x + 1);
        height = Math.max(1, end.y + 1);
      }
    }

    private int computeRowHexCount(int rowOriginX, float halfHexWidth, int right) {
      if (hexWidth <= 0) {
        return 1;
      }
      return Math.max(
          1,
          (int) Math.floor((right - halfHexWidth - borderOverhang() - rowOriginX) / hexWidth) + 1);
    }

    /**
     * How far a cube's anti-aliased border reaches past its hexagon's edge. The hive's bounds and
     * the render batches must include it, or the outermost cubes lose their outline.
     */
    float borderOverhang() {
      return hexBorderStrokeWidth / 2f + 1f;
    }

    int getRowHexCount(int rowIndex) {
      int rowStartIndex = getRowStartIndex(rowIndex);
      if (rowStartIndex >= numHexs) {
        return 0;
      }
      int maxRowCount = (rowIndex % 2) == 0 ? evenRowHexCount : oddRowHexCount;
      return Math.min(maxRowCount, numHexs - rowStartIndex);
    }

    int getRowStartIndex(int rowIndex) {
      if (rowIndex <= 0) {
        return 0;
      }
      int fullPairs = rowIndex / 2;
      int startIndex = fullPairs * (evenRowHexCount + oddRowHexCount);
      if ((rowIndex % 2) != 0) {
        startIndex += evenRowHexCount;
      }
      return startIndex;
    }

    int getRowIndexForPiece(int pieceIndex) {
      if (pieceIndex <= 0) {
        return 0;
      }
      int pairSize = evenRowHexCount + oddRowHexCount;
      int fullPairs = pieceIndex / pairSize;
      int remainder = pieceIndex % pairSize;
      if (remainder < evenRowHexCount) {
        return fullPairs * 2;
      }
      return fullPairs * 2 + 1;
    }
  }

  private Rectangle computeRenderBounds(
      DrawingProperties drawingProperties, Rectangle visibleRect, Rectangle previousVisibleRect) {
    int dx = visibleRect.x - previousVisibleRect.x;
    int dy = visibleRect.y - previousVisibleRect.y;
    int horizontalMargin =
        Math.max((int) (visibleRect.width * 0.35f), (int) drawingProperties.hexWidth * 2);
    int verticalMargin =
        Math.max((int) (visibleRect.height * 0.35f), (int) drawingProperties.hexHeight * 2);
    int horizontalLookAhead = Math.max((int) (visibleRect.width * 0.75f), horizontalMargin);
    int verticalLookAhead = Math.max((int) (visibleRect.height * 0.75f), verticalMargin);

    int leftExtra = dx < 0 ? horizontalLookAhead : horizontalMargin;
    int rightExtra = dx > 0 ? horizontalLookAhead : horizontalMargin;
    int topExtra = dy < 0 ? verticalLookAhead : verticalMargin;
    int bottomExtra = dy > 0 ? verticalLookAhead : verticalMargin;

    int x = Math.max(0, visibleRect.x - leftExtra);
    int y = Math.max(0, visibleRect.y - topExtra);
    int maxWidth = Math.max(1, drawingProperties.width - x);
    int maxHeight = Math.max(1, drawingProperties.height - y);
    int width = Math.min(maxWidth, visibleRect.width + leftExtra + rightExtra);
    int height = Math.min(maxHeight, visibleRect.height + topExtra + bottomExtra);
    return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
  }

  private Rectangle computePriorityRenderBounds(
      DrawingProperties drawingProperties, Rectangle visibleRect, Rectangle previousVisibleRect) {
    int dx = visibleRect.x - previousVisibleRect.x;
    int dy = visibleRect.y - previousVisibleRect.y;
    int horizontalMargin =
        Math.max((int) (visibleRect.width * 0.12f), (int) drawingProperties.hexWidth);
    int verticalMargin =
        Math.max((int) (visibleRect.height * 0.12f), (int) drawingProperties.hexHeight);
    int horizontalLookAhead = Math.max((int) (visibleRect.width * 0.25f), horizontalMargin);
    int verticalLookAhead = Math.max((int) (visibleRect.height * 0.25f), verticalMargin);

    int leftExtra = dx < 0 ? horizontalLookAhead : horizontalMargin;
    int rightExtra = dx > 0 ? horizontalLookAhead : horizontalMargin;
    int topExtra = dy < 0 ? verticalLookAhead : verticalMargin;
    int bottomExtra = dy > 0 ? verticalLookAhead : verticalMargin;

    int x = Math.max(0, visibleRect.x - leftExtra);
    int y = Math.max(0, visibleRect.y - topExtra);
    int maxWidth = Math.max(1, drawingProperties.width - x);
    int maxHeight = Math.max(1, drawingProperties.height - y);
    int width = Math.min(maxWidth, visibleRect.width + leftExtra + rightExtra);
    int height = Math.min(maxHeight, visibleRect.height + topExtra + bottomExtra);
    return new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
  }
}
