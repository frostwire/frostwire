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
    private BufferedImage bitmap;
    private int lastWidth;
    private int lastHeight;
    private final ExecutorService threadPool = com.frostwire.util.ThreadPool.newThreadPool("HexHivePool", 1);
    private RenderRequest pendingRenderRequest;
    private boolean renderRunning;
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
                lastHeight = snapshot.height;
                lastWidth = snapshot.width;
            }
        }
        if (snapshot == null) {
            // not ready yet (perhaps during animation or rotation)
            return;
        }
        if (hexDataAdapter != null && hexDataAdapter.getFullHexagonsCount() >= 0 && canvasWidth > 0 && canvasHeight > 0) {
            Rectangle visibleRect = getVisibleRect();
            int previewCanvasWidth = visibleRect.width > 0 ? Math.min(visibleRect.width, canvasWidth) : canvasWidth;
            int previewCanvasHeight = visibleRect.height > 0 ? Math.min(visibleRect.height, canvasHeight) : canvasHeight;
            int previewHexCount = countVisibleHexagons(snapshot, previewCanvasWidth, previewCanvasHeight);
            DrawingProperties previewProperties = null;
            if (previewHexCount > 0 && previewHexCount < snapshot.numHexs) {
                previewProperties = new DrawingProperties(
                        previewHexCount,
                        hexSideLength,
                        hexagonBorderPaint.getLineWidth(),
                        leftPadding,
                        topPadding,
                        previewCanvasWidth - rightPadding,
                        previewCanvasHeight - bottomPadding);
            }
            enqueueRender(new RenderRequest(hexDataAdapter, snapshot, previewProperties));
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
            if (request.previewDrawingProperties != null) {
                BufferedImage previewBitmap = asyncDraw(request.adapter, request.previewDrawingProperties);
                publishBitmap(previewBitmap);
            }
            BufferedImage backgroundBitmap = asyncDraw(request.adapter, request.drawingProperties);
            publishBitmap(backgroundBitmap);
        }
    }

    private void publishBitmap(BufferedImage renderedBitmap) {
        synchronized (bitmapLock) {
            bitmap = renderedBitmap;
        }
        SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(lastWidth, lastHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        BufferedImage snapshot;
        synchronized (bitmapLock) {
            snapshot = bitmap;
        }
        if (snapshot != null) {
            g2d.drawImage(snapshot, 0, 0, snapshot.getWidth(), snapshot.getHeight(), null);
        }
    }

    private void initPaints(int numBorderColor, int numEmptyColor, int numFullColor, int bgColor) {
        hexagonBorderPaint = new ColoredStroke(0.5f, new Color(numBorderColor, false));
        emptyHexPaint = new CubePaint(numEmptyColor, 10);
        fullHexPaint = new CubePaint(numFullColor, 30);
        backgroundColor = new Color(bgColor);
    }

    private BufferedImage asyncDraw(HexDataAdapter adapter, DrawingProperties drawingProperties) {
        // with drawingProperties we don't need to think about padding offsets. We just use drawingProperties numbers for our calculations
        drawingProperties.hexCenterBuffer.setLocation(drawingProperties.evenRowOrigin.x, drawingProperties.evenRowOrigin.y);
        boolean evenRow = true;
        int pieceIndex = 0;
        float halfHexWidth = drawingProperties.hexWidth / 2f;
        float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
        // if we have just one piece to draw, we'll draw it in the center
        if (drawingProperties.numHexs == 1) {
            drawingProperties.hexCenterBuffer.x = drawingProperties.center.x;
            drawingProperties.hexCenterBuffer.y = drawingProperties.center.y;
        }
        boolean drawCubes = (forceCubes) || drawingProperties.numHexs <= 500;
        GraphicsConfiguration gc = graphicsConfig;
        BufferedImage bitmap = (gc != null)
                ? gc.createCompatibleImage(drawingProperties.width, drawingProperties.height, Transparency.OPAQUE)
                : new BufferedImage(drawingProperties.width, drawingProperties.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bitmap.createGraphics();
        graphics.setPaint(backgroundColor);
        graphics.fillRect(0, 0, drawingProperties.width, drawingProperties.height);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                drawCubes ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                drawCubes ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
        while (pieceIndex < drawingProperties.numHexs) {
            drawHexagon(drawingProperties, graphics, hexagonBorderPaint, (adapter.isFull(pieceIndex) ? fullHexPaint : emptyHexPaint), drawCubes);
            pieceIndex++;
            if (pieceIndex >= drawingProperties.numHexs) {
                break;
            }
            int nextCenterX = Math.round(drawingProperties.hexCenterBuffer.x + drawingProperties.hexWidth);
            if (nextCenterX + halfHexWidth > drawingProperties.wrapRight) {
                evenRow = !evenRow;
                drawingProperties.hexCenterBuffer.x = evenRow ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
                drawingProperties.hexCenterBuffer.y = Math.round(drawingProperties.hexCenterBuffer.y + verticalStep);
            } else {
                drawingProperties.hexCenterBuffer.x = nextCenterX;
            }
        }
        graphics.dispose();
        return bitmap;
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
        private final DrawingProperties previewDrawingProperties;

        private RenderRequest(HexDataAdapter adapter, DrawingProperties drawingProperties, DrawingProperties previewDrawingProperties) {
            this.adapter = adapter;
            this.drawingProperties = drawingProperties;
            this.previewDrawingProperties = previewDrawingProperties;
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

        DrawingProperties(int numHexs, int hexSideLen, float hexBorderWidth, int left, int top, int right, int bottom) {
            hexBorderStrokeWidth = hexBorderWidth;
            origin = new Point(0, 0);
            center = new Point(0, 0);
            end = new Point(0, 0);
            evenRowOrigin = new Point(0, 0);
            oddRowOrigin = new Point(0, 0);
            this.numHexs = numHexs;
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
            width = right - left;
            height = bottom - top;
            if (hexSideLength == -1) {
                hexSideLength = getHexagonSideLength(width, height, numHexs);
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
            if (hexSideLength != -1) {
                // we need to calculate the end.y and the new drawing total height depending
                // on how many rows we'll have.
                Point bufferCenter = new Point(evenRowOrigin.x, evenRowOrigin.y);
                int consideredHexagons = 0;
                int maxCenterX = evenRowOrigin.x;
                int maxCenterY = evenRowOrigin.y;
                boolean evenRow = true;
                while (consideredHexagons < numHexs) {
                    maxCenterX = Math.max(maxCenterX, bufferCenter.x);
                    maxCenterY = Math.max(maxCenterY, bufferCenter.y);
                    consideredHexagons++;
                    if (consideredHexagons >= numHexs) {
                        break;
                    }
                    int nextCenterX = Math.round(bufferCenter.x + hexWidth);
                    if (nextCenterX + halfHexWidth > right) {
                        evenRow = !evenRow;
                        bufferCenter.x = evenRow ? evenRowOrigin.x : oddRowOrigin.x;
                        bufferCenter.y = Math.round(bufferCenter.y + verticalStep);
                    } else {
                        bufferCenter.x = nextCenterX;
                    }
                }
                end.x = Math.round(maxCenterX + halfHexWidth);
                end.y = Math.round(maxCenterY + halfHexHeight);
                width = Math.max(1, end.x - left);
                height = Math.max(1, end.y - top);
            }
        }
    }

    private int countVisibleHexagons(DrawingProperties drawingProperties, int visibleWidth, int visibleHeight) {
        if (visibleWidth <= 0 || visibleHeight <= 0) {
            return 0;
        }
        int previewRight = Math.max(leftPadding + 1, visibleWidth - rightPadding);
        int previewBottom = Math.max(topPadding + 1, visibleHeight - bottomPadding);
        float halfHexWidth = drawingProperties.hexWidth / 2f;
        float halfHexHeight = drawingProperties.hexHeight / 2f;
        float verticalStep = (drawingProperties.hexHeight * 3f) / 4f;
        int centerX = drawingProperties.evenRowOrigin.x;
        int centerY = drawingProperties.evenRowOrigin.y;
        boolean evenRow = true;
        int visibleHexagons = 0;

        while (visibleHexagons < drawingProperties.numHexs) {
            if (centerY + halfHexHeight > previewBottom) {
                break;
            }
            visibleHexagons++;
            int nextCenterX = Math.round(centerX + drawingProperties.hexWidth);
            if (nextCenterX + halfHexWidth > previewRight) {
                evenRow = !evenRow;
                centerX = evenRow ? drawingProperties.evenRowOrigin.x : drawingProperties.oddRowOrigin.x;
                centerY = Math.round(centerY + verticalStep);
            } else {
                centerX = nextCenterX;
            }
        }

        return visibleHexagons;
    }
}
