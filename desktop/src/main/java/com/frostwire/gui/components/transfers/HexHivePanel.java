/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;

public class HexHivePanel extends JPanel {
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
    private final Area renderedArea = new Area();
    private int lastWidth;
    private int lastHeight;
    private final ExecutorService threadPool = com.frostwire.util.ThreadPool.newThreadPool("HexHivePool", 1);
    private RenderRequest pendingRenderRequest;
    private boolean renderRunning;
    private Rectangle lastVisibleRect = new Rectangle();
    private Color backgroundColor;
    private GraphicsConfiguration graphicsConfig;

    /**
     * @param hexSideLength - if -1 hexLength is calculated out of the available container area.
     */
    HexHivePanel(int hexSideLength,
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
            throw new IllegalArgumentException("Invalid hexSideLength (" + hexSideLength + "). Valid hexSideLength are: -1 (dynamic) and >= 5");
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
    }

    @Override
    public void addNotify() {
        super.addNotify();
        graphicsConfig = getGraphicsConfiguration();
    }

    void clearRenderCache() {
        synchronized (bitmapLock) {
            bitmap = null;
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

    private static float getHexagonSideLength(final int drawingAreaWidth, final int drawingAreaHeight, final int numHexagons) {
        final float THREE_HALVES_SQRT_OF_THREE = 2.59807621135f;
        final int fullArea = drawingAreaWidth * drawingAreaHeight;
        // fullArea             numHexagons                     fullArea
        // --------         =                => s = sqrt(-----------------------)
        // 3/2*sqrt(3)*s^2                               3/2*sqrt(3)*numHexagons
        final float preliminarySideLength = (float) Math.sqrt(fullArea / (THREE_HALVES_SQRT_OF_THREE * numHexagons));
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
     * @param outCorner    - a re-usable Point buffer to output the
     * @param inCenter     - a reusable Point buffer representing the center coordinates of a hexagon
     * @param sideLength   - length of hexagon side
     * @param cornerNumber - from 0 to 6 (we count 7 because we have to get back to the origin)
     */
    private static void getHexCorner(final Point outCorner, final Point inCenter, int cornerNumber, float sideLength) {
        double angle_rad = Math.toRadians((60 * cornerNumber) + 30);
        outCorner.setLocation((int) (inCenter.x + sideLength * Math.cos(angle_rad)), (int) (inCenter.y + sideLength * Math.sin(angle_rad)));
    }

    private static void drawHexagon(final DrawingProperties drawingProperties, final Graphics2D graphics,
                                    final ColoredStroke borderStroke,
                                    final CubePaint fillPaint, final boolean drawCube) {
        // Create outer shape for Hexagon
        drawingProperties.hexagonBorderPath.reset();
        for (int i = 0; i < 6; i++) {
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, i, drawingProperties.hexSideLength);
            if (i == 0) {
                drawingProperties.hexagonBorderPath.moveTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            } else {
                drawingProperties.hexagonBorderPath.lineTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
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
            drawingProperties.fillPathBuffer.moveTo(drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 1, drawingProperties.hexSideLength);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 2, drawingProperties.hexSideLength);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 3, drawingProperties.hexSideLength);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
            // Fill left face
            graphics.setPaint(fillPaint.getDarkColor()); // might be setColor in case this doesn't work
            graphics.fill(drawingProperties.fillPathBuffer);
            // TOP FACE (LIGHT)
            // Create shape for top face
            drawingProperties.fillPathBuffer.reset();
            drawingProperties.fillPathBuffer.moveTo(drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 3, drawingProperties.hexSideLength);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 4, drawingProperties.hexSideLength);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 5, drawingProperties.hexSideLength);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            drawingProperties.fillPathBuffer.lineTo(drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y);
            // Fill top face
            graphics.setPaint(fillPaint.getLightColor());
            graphics.fill(drawingProperties.fillPathBuffer);
            // Now paint inner faces border, 3 line.
            // From center to 1, center to 3 and center to 5
            graphics.setPaint(borderStroke.getColor());
            graphics.setStroke(borderStroke);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 1, drawingProperties.hexSideLength);
            graphics.drawLine(drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y, drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 3, drawingProperties.hexSideLength);
            graphics.drawLine(drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y, drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
            getHexCorner(drawingProperties.cornerBuffer, drawingProperties.hexCenterBuffer, 5, drawingProperties.hexSideLength);
            graphics.drawLine(drawingProperties.hexCenterBuffer.x, drawingProperties.hexCenterBuffer.y, drawingProperties.cornerBuffer.x, drawingProperties.cornerBuffer.y);
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
        final HexHivePanel hexPanel = new HexHivePanel(50, 0x264053, 0xf2f2f2, 0x33b5e5, 0xffffff, 0, 0, 0, 0, true);
        final HexDataAdapter mockAdapter = new HexDataAdapter() {
            @Override
            public void updateData(Object data) {
            }

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
        frame.addComponentListener(new ComponentAdapter() {
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
            snapshot = new DrawingProperties(
                    hexDataAdapter,
                    hexSideLength,
                    hexagonBorderPaint.getLineWidth(),
                    leftPadding,
                    topPadding,
                    canvasWidth - rightPadding,
                    canvasHeight - bottomPadding);
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
        ensureBackingBitmap(snapshot);
        if (hexDataAdapter != null && hexDataAdapter.getFullHexagonsCount() >= 0 && canvasWidth > 0 && canvasHeight > 0) {
            requestFocusedRender(snapshot, hexDataAdapter, true);
        }
    }

    private void ensureBackingBitmap(DrawingProperties snapshot) {
        boolean changed = false;
        synchronized (bitmapLock) {
            if (bitmap == null || bitmap.getWidth() != snapshot.width || bitmap.getHeight() != snapshot.height) {
                GraphicsConfiguration gc = graphicsConfig;
                bitmap = (gc != null)
                        ? gc.createCompatibleImage(snapshot.width, snapshot.height, Transparency.OPAQUE)
                        : new BufferedImage(snapshot.width, snapshot.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = bitmap.createGraphics();
                graphics.setPaint(backgroundColor);
                graphics.fillRect(0, 0, snapshot.width, snapshot.height);
                graphics.dispose();
                renderedArea.reset();
                changed = true;
            }
        }
        if (changed) {
            SwingUtilities.invokeLater(() -> {
                revalidate();
                repaint();
            });
        }
    }

    private void requestFocusedRender(DrawingProperties drawingProperties, HexDataAdapter adapter, boolean force) {
        Rectangle visibleRect = getVisibleRect();
        if (visibleRect.width <= 0 || visibleRect.height <= 0) {
            visibleRect = new Rectangle(0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()));
        }
        Rectangle priorityBounds = computePriorityRenderBounds(drawingProperties, visibleRect, lastVisibleRect);
        Rectangle targetBounds = computeRenderBounds(drawingProperties, visibleRect, lastVisibleRect);
        boolean needsPriorityRender;
        boolean needsTargetRender;
        synchronized (bitmapLock) {
            boolean visibleCovered = renderedArea.contains(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height);
            needsPriorityRender = force || bitmap == null || !visibleCovered ||
                    !renderedArea.contains(priorityBounds.x, priorityBounds.y, priorityBounds.width, priorityBounds.height);
            needsTargetRender = force || bitmap == null ||
                    !renderedArea.contains(targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height);
        }
        lastVisibleRect = new Rectangle(visibleRect);
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
            renderRegionIntoBackingBitmap(request.adapter, request.drawingProperties, request.renderBounds, request.prioritizeLatency);
        }
    }

    private void renderRegionIntoBackingBitmap(HexDataAdapter adapter,
                                               DrawingProperties drawingProperties,
                                               Rectangle renderBounds,
                                               boolean prioritizeLatency) {
        int[] rowRange = getRowRangeForBounds(drawingProperties, renderBounds);
        int firstRow = rowRange[0];
        int lastRow = rowRange[1];
        int maxRow = Math.max(0, drawingProperties.getRowIndexForPiece(Math.max(0, drawingProperties.numHexs - 1)));
        final int rowsPerBatch = prioritizeLatency ? 2 : 4;
        for (int row = firstRow; row <= lastRow; row += rowsPerBatch) {
            int batchLastRow = Math.min(lastRow, row + rowsPerBatch - 1);
            int drawFirstRow = Math.max(0, row - 1);
            int drawLastRow = Math.min(maxRow, batchLastRow + 1);
            Rectangle drawBounds = computeBatchBounds(drawingProperties, renderBounds, drawFirstRow, drawLastRow);
            Rectangle mergeBounds = computeMergeBounds(drawingProperties, renderBounds, row, batchLastRow, maxRow);
            if (drawBounds.width <= 0 || drawBounds.height <= 0 || mergeBounds.width <= 0 || mergeBounds.height <= 0) {
                continue;
            }
            BufferedImage batchBitmap = createBatchBitmap(drawBounds);
            Graphics2D graphics = batchBitmap.createGraphics();
            graphics.setPaint(backgroundColor);
            graphics.fillRect(0, 0, drawBounds.width, drawBounds.height);
            boolean drawCubes = (forceCubes) || drawingProperties.numHexs <= 500;
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    drawCubes ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    drawCubes ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
            graphics.translate(-drawBounds.x, -drawBounds.y);
            drawRowsToGraphics(drawingProperties, adapter, graphics, drawBounds, drawFirstRow, drawLastRow, drawCubes);
            graphics.dispose();
            mergeBatchBitmap(batchBitmap, drawBounds, mergeBounds);
        }
    }

    private BufferedImage createBatchBitmap(Rectangle batchBounds) {
        GraphicsConfiguration gc = graphicsConfig;
        return (gc != null)
                ? gc.createCompatibleImage(batchBounds.width, batchBounds.height, Transparency.OPAQUE)
                : new BufferedImage(batchBounds.width, batchBounds.height, BufferedImage.TYPE_INT_RGB);
    }

    private void drawRowsToGraphics(DrawingProperties drawingProperties,
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
            int rowOriginX = evenRow ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
            int centerY = Math.round(drawingProperties.evenRowOrigin.y + (row * verticalStep));
            int firstCol = Math.max(0, (int) Math.floor((renderBounds.x - (rowOriginX - halfHexWidth)) / drawingProperties.hexWidth) - 1);
            int lastCol = Math.min(rowHexCount - 1,
                    (int) Math.ceil((renderBounds.x + renderBounds.width - (rowOriginX - halfHexWidth)) / drawingProperties.hexWidth));
            for (int col = firstCol; col <= lastCol; col++) {
                int pieceIndex = rowStartIndex + col;
                if (pieceIndex >= drawingProperties.numHexs) {
                    break;
                }
                drawingProperties.hexCenterBuffer.x = Math.round(rowOriginX + (col * drawingProperties.hexWidth));
                drawingProperties.hexCenterBuffer.y = centerY;
                drawHexagon(drawingProperties, graphics, hexagonBorderPaint,
                        adapter.isFull(pieceIndex) ? fullHexPaint : emptyHexPaint,
                        drawCubes);
            }
        }
    }

    private Rectangle computeBatchBounds(DrawingProperties drawingProperties, Rectangle targetBounds, int firstRow, int lastRow) {
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
            int rowOriginX = evenRow ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
            int rowMinX = Math.round(rowOriginX - halfHexWidth);
            int rowMaxX = Math.round(rowOriginX + ((rowHexCount - 1) * drawingProperties.hexWidth) + halfHexWidth);
            minX = Math.min(minX, rowMinX);
            maxX = Math.max(maxX, rowMaxX);
        }
        if (minX == Integer.MAX_VALUE) {
            return new Rectangle();
        }
        int minY = Math.round(drawingProperties.evenRowOrigin.y + (firstRow * verticalStep) - halfHexHeight);
        int maxY = Math.round(drawingProperties.evenRowOrigin.y + (lastRow * verticalStep) + halfHexHeight);
        Rectangle batch = new Rectangle(
                Math.max(0, Math.max(targetBounds.x, minX)),
                Math.max(0, Math.max(targetBounds.y, minY)),
                1,
                1
        );
        int batchRight = Math.min(drawingProperties.width, Math.min(targetBounds.x + targetBounds.width, maxX));
        int batchBottom = Math.min(drawingProperties.height, Math.min(targetBounds.y + targetBounds.height, maxY));
        batch.width = Math.max(1, batchRight - batch.x);
        batch.height = Math.max(1, batchBottom - batch.y);
        return batch;
    }

    private Rectangle computeMergeBounds(DrawingProperties drawingProperties,
                                         Rectangle targetBounds,
                                         int firstRow,
                                         int lastRow,
                                         int maxRow) {
        Rectangle batch = computeBatchBounds(drawingProperties, targetBounds, firstRow, lastRow);
        if (batch.width <= 0 || batch.height <= 0) {
            return batch;
        }
        float halfHexHeight = drawingProperties.hexHeight / 2f;
        int topEdge = firstRow <= 0
                ? Math.round(getRowCenterY(drawingProperties, firstRow) - halfHexHeight)
                : Math.round((getRowCenterY(drawingProperties, firstRow - 1) + getRowCenterY(drawingProperties, firstRow)) / 2f);
        int bottomEdge = lastRow >= maxRow
                ? Math.round(getRowCenterY(drawingProperties, lastRow) + halfHexHeight)
                : Math.round((getRowCenterY(drawingProperties, lastRow) + getRowCenterY(drawingProperties, lastRow + 1)) / 2f);
        int mergeTop = Math.max(batch.y, Math.max(targetBounds.y, topEdge));
        int mergeBottom = Math.min(batch.y + batch.height, Math.min(targetBounds.y + targetBounds.height, bottomEdge));
        if (mergeBottom <= mergeTop) {
            return new Rectangle(batch.x, batch.y, batch.width, batch.height);
        }
        return new Rectangle(batch.x, mergeTop, batch.width, mergeBottom - mergeTop);
    }

    private int[] getRowRangeForBounds(DrawingProperties drawingProperties, Rectangle renderBounds) {
        float halfHexHeight = drawingProperties.hexHeight / 2f;
        float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
        int firstRow = Math.max(0, (int) Math.floor((renderBounds.y - drawingProperties.evenRowOrigin.y - halfHexHeight) / verticalStep) - 1);
        int maxRows = Math.max(0, drawingProperties.getRowIndexForPiece(Math.max(0, drawingProperties.numHexs - 1)));
        int lastRow = Math.min(maxRows,
                Math.max(firstRow, (int) Math.ceil((renderBounds.y + renderBounds.height - drawingProperties.evenRowOrigin.y + halfHexHeight) / verticalStep) + 1));
        return new int[]{firstRow, lastRow};
    }

    private int getRowCenterY(DrawingProperties drawingProperties, int row) {
        float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
        return Math.round(drawingProperties.evenRowOrigin.y + (row * verticalStep));
    }

    private void mergeBatchBitmap(BufferedImage renderedBitmap, Rectangle sourceBounds, Rectangle mergeBounds) {
        int sourceX1 = mergeBounds.x - sourceBounds.x;
        int sourceY1 = mergeBounds.y - sourceBounds.y;
        int sourceX2 = sourceX1 + mergeBounds.width;
        int sourceY2 = sourceY1 + mergeBounds.height;
        synchronized (bitmapLock) {
            Graphics2D graphics = bitmap.createGraphics();
            graphics.drawImage(renderedBitmap,
                    mergeBounds.x,
                    mergeBounds.y,
                    mergeBounds.x + mergeBounds.width,
                    mergeBounds.y + mergeBounds.height,
                    sourceX1,
                    sourceY1,
                    sourceX2,
                    sourceY2,
                    null);
            graphics.dispose();
            renderedArea.add(new Area(mergeBounds));
        }
        SwingUtilities.invokeLater(() ->
                repaint(mergeBounds.x, mergeBounds.y, mergeBounds.width, mergeBounds.height));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(lastWidth, lastHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        synchronized (bitmapLock) {
            if (bitmap != null) {
                g2d.drawImage(bitmap, 0, 0, null);
            }
        }
        DrawingProperties snapshotProperties;
        HexDataAdapter adapterSnapshot;
        synchronized (drawingPropertiesLock) {
            snapshotProperties = drawingProperties;
            adapterSnapshot = latestAdapter;
        }
        if (snapshotProperties != null && adapterSnapshot != null) {
            requestFocusedRender(snapshotProperties, adapterSnapshot, false);
        }
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

        private RenderRequest(HexDataAdapter adapter,
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
         * Point object we'll reuse to draw hexagons
         * (Object creation and destruction must be avoided when calling onDraw())
         */
        private final Point hexCenterBuffer = new Point(-1, -1);
        /**
         * Point object we'll reuse to draw hexagon sides
         * (Object creation and destruction must be avoided when calling onDraw())
         */
        private final Point cornerBuffer = new Point(-1, -1);
        /**
         * Path object we'll reuse to draw the filled areas of the hexagons
         */
        private final GeneralPath fillPathBuffer = new GeneralPath();
        /**
         * Path object we'll reuse to draw the hexagons outer shapes
         */
        private final GeneralPath hexagonBorderPath = new GeneralPath();
        /**
         * Drawing area top-left
         */
        private Point origin;
        // Hexagon Geometry Helpers
        /**
         * Drawing area center
         */
        private Point center;
        /**
         * Drawing are bottom-right cornerBuffer
         */
        private Point end;
        /**
         * Right/bottom edge of the available drawing viewport; used for wrapping rows.
         */
        private int wrapRight;
        private int wrapBottom;
        /**
         * Drawing area width
         */
        private int width;
        /**
         * Drawing area height
         */
        private int height;
        /**
         * Number of hexagons to draw
         */
        private int numHexs;
        /**
         * Side length of each hexagon
         */
        private float hexSideLength;
        /**
         * Height of each hexagon
         */
        private float hexHeight;
        /**
         * Width of each hexagon
         */
        private float hexWidth;
        /**
         * Hexagon border stroke width, has to be converted to pixels depending on screen density
         */
        private float hexBorderStrokeWidth;
        private int evenRowHexCount;
        private int oddRowHexCount;

        DrawingProperties(HexDataAdapter adapter, int hexSideLen, float hexBorderWidth, int left, int top, int right, int bottom) {
            if (adapter == null) {
                throw new RuntimeException("check your logic, you need a data adapter before calling initDrawingProperties");
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
                int widestRowCenterX = evenRowHexCount >= oddRowHexCount
                        ? Math.round(evenRowOrigin.x + Math.max(0, evenRowHexCount - 1) * hexWidth)
                        : Math.round(oddRowOrigin.x + Math.max(0, oddRowHexCount - 1) * hexWidth);
                end.x = Math.round(Math.max(widestRowCenterX, lastCenterX) + halfHexWidth);
                end.y = Math.round(lastCenterY + halfHexHeight);
                width = Math.max(1, end.x);
                height = Math.max(1, end.y);
            } else {
                width = Math.max(1, end.x);
                height = Math.max(1, end.y);
            }
        }

        private int computeRowHexCount(int rowOriginX, float halfHexWidth, int right) {
            if (hexWidth <= 0) {
                return 1;
            }
            return Math.max(1, (int) Math.floor((right - halfHexWidth - rowOriginX) / hexWidth) + 1);
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

    private Rectangle computeRenderBounds(DrawingProperties drawingProperties, Rectangle visibleRect, Rectangle previousVisibleRect) {
        int dx = visibleRect.x - previousVisibleRect.x;
        int dy = visibleRect.y - previousVisibleRect.y;
        int horizontalMargin = Math.max((int) (visibleRect.width * 0.35f), (int) drawingProperties.hexWidth * 2);
        int verticalMargin = Math.max((int) (visibleRect.height * 0.35f), (int) drawingProperties.hexHeight * 2);
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

    private Rectangle computePriorityRenderBounds(DrawingProperties drawingProperties, Rectangle visibleRect, Rectangle previousVisibleRect) {
        int dx = visibleRect.x - previousVisibleRect.x;
        int dy = visibleRect.y - previousVisibleRect.y;
        int horizontalMargin = Math.max((int) (visibleRect.width * 0.12f), (int) drawingProperties.hexWidth);
        int verticalMargin = Math.max((int) (visibleRect.height * 0.12f), (int) drawingProperties.hexHeight);
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
