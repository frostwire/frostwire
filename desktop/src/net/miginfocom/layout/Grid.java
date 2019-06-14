package net.miginfocom.layout;

import java.util.*;
/*
 * License (BSD):
 * ==============
 *
 * Copyright (c) 2004, Mikael Grev, MiG InfoCom AB. (miglayout (at) miginfocom (dot) com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * Neither the name of the MiG InfoCom AB nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 * @version 1.0
 * @author Mikael Grev, MiG InfoCom AB
 *         Date: 2006-sep-08
 */

/**
 * Holds components in a grid. Does most of the logic behind the layout manager.
 */
public final class Grid {
    static final boolean TEST_GAPS = true;
    private static final Float[] GROW_100 = new Float[]{ResizeConstraint.WEIGHT_100};
    private static final DimConstraint DOCK_DIM_CONSTRAINT = new DimConstraint();
    /**
     * This is the maximum grid position for "normal" components. Docking components use the space out to
     * <code>MAX_DOCK_GRID</code> and below 0.
     */
    private static final int MAX_GRID = 30000;
    /**
     * Docking components will use the grid coordinates <code>-MAX_DOCK_GRID -> 0</code> and <code>MAX_GRID -> MAX_DOCK_GRID</code>.
     */
    private static final int MAX_DOCK_GRID = 32767;
    /**
     * A constraint used for gaps.
     */
    private static final ResizeConstraint GAP_RC_CONST = new ResizeConstraint(200, ResizeConstraint.WEIGHT_100, 50, null);
    private static final ResizeConstraint GAP_RC_CONST_PUSH = new ResizeConstraint(200, ResizeConstraint.WEIGHT_100, 50, ResizeConstraint.WEIGHT_100);
    private static WeakHashMap[] PARENT_ROWCOL_SIZES_MAP = null;
    private static WeakHashMap<Object, LinkedHashMap<Integer, Cell>> PARENT_GRIDPOS_MAP = null;

    static {
        DOCK_DIM_CONSTRAINT.setGrowPriority(0);
    }

    /**
     * The constraints. Never <code>null</code>.
     */
    private final LC lc;
    /**
     * The parent that is layout out and this grid is done for. Never <code>null</code>.
     */
    private final ContainerWrapper container;
    /**
     * An x, y array implemented as a sparse array to accommodate for any grid size without wasting memory (or rather 15 bit (0-MAX_GRID * 0-MAX_GRID).
     */
    private final LinkedHashMap<Integer, Cell> grid = new LinkedHashMap<>();   // [(y << 16) + x] -> Cell. null key for absolute positioned compwraps
    /**
     * The size of the grid. Row count and column count.
     */
    private final TreeSet<Integer> rowIndexes = new TreeSet<>(), colIndexes = new TreeSet<>();
    /**
     * The row and column specifications.
     */
    private final AC rowConstr, colConstr;
    /**
     * Components that are connections in one dimension (such as baseline alignment for instance) are grouped together and stored here.
     * One for each row/column.
     */
    private final ArrayList<LinkedDimGroup>[] colGroupLists, rowGroupLists;   //[(start)row/col number]
    private final int dockOffY, dockOffX;
    private final Float[] pushXs, pushYs;
    private final ArrayList<LayoutCallback> callbackList;
    private HashMap<Integer, BoundSize> wrapGapMap = null;   // Row or Column index depending in the dimension that "wraps". Normally row indexes but may be column indexes if "flowy". 0 means before first row/col.
    /**
     * The in the constructor calculated min/pref/max sizes of the rows and columns.
     */
    private FlowSizeSpec colFlowSpecs = null, rowFlowSpecs = null;
    /**
     * The in the constructor calculated min/pref/max size of the whole grid.
     */
    private int[] width = null, height = null;
    /**
     * If debug is on contains the bounds for things to paint when calling {@link ContainerWrapper#paintDebugCell(int, int, int, int)}
     */
    private ArrayList<int[]> debugRects = null; // [x, y, width, height]
    /**
     * If any of the absolute coordinates for component bounds has links the name of the target is in this Set.
     * Since it requires some memory and computations this is checked at the creation so that
     * the link information is only created if needed later.
     * <p>
     * The boolean is true for groups id:s and null for normal id:s.
     */
    private HashMap<String, Boolean> linkTargetIDs = null;

    /**
     * Constructor.
     *
     * @param container    The container that will be laid out.
     * @param lc           The form flow constraints.
     * @param rowConstr    The rows specifications. If more cell rows are required, the last element will be used for when there is no corresponding element in this array.
     * @param colConstr    The columns specifications. If more cell rows are required, the last element will be used for when there is no corresponding element in this array.
     * @param ccMap        The map containing the parsed constraints for each child component of <code>parent</code>. Will not be alterted.
     * @param callbackList A list of callbacks or <code>null</code> if none. Will not be alterted.
     */
    public Grid(ContainerWrapper container, LC lc, AC rowConstr, AC colConstr, Map<ComponentWrapper, CC> ccMap, ArrayList<LayoutCallback> callbackList) {
        this.lc = lc;
        this.rowConstr = rowConstr;
        this.colConstr = colConstr;
        this.container = container;
        this.callbackList = callbackList;
        int wrap = lc.getWrapAfter() != 0 ? lc.getWrapAfter() : (lc.isFlowX() ? colConstr : rowConstr).getConstaints().length;
        final ComponentWrapper[] comps = container.getComponents();
        boolean hasTagged = false;  // So we do not have to sort if it will not do any good
        boolean hasPushX = false, hasPushY = false;
        boolean hitEndOfRow = false;
        final int[] cellXY = new int[2];
        final ArrayList<int[]> spannedRects = new ArrayList<>(2);
        final DimConstraint[] specs = (lc.isFlowX() ? rowConstr : colConstr).getConstaints();
        int sizeGroupsX = 0, sizeGroupsY = 0;
        int[] dockInsets = null;    // top, left, bottom, right insets for docks.
        LinkHandler.clearTemporaryBounds(container.getLayout());
        for (int i = 0; i < comps.length; ) {
            ComponentWrapper comp = comps[i];
            CC rootCc = getCC(comp, ccMap);
            addLinkIDs(rootCc);
            int hideMode = comp.isVisible() ? -1 : rootCc.getHideMode() != -1 ? rootCc.getHideMode() : lc.getHideMode();
            if (hideMode == 3) { // To work with situations where there are components that does not have a layout manager, or not this one.
                setLinkedBounds(comp, rootCc, comp.getX(), comp.getY(), comp.getWidth(), comp.getHeight(), rootCc.isExternal());
                i++;
                continue;   // The "external" component should not be handled further.
            }
            if (rootCc.getHorizontal().getSizeGroup() != null)
                sizeGroupsX++;
            if (rootCc.getVertical().getSizeGroup() != null)
                sizeGroupsY++;
            // Special treatment of absolute positioned components.
            UnitValue[] pos = getPos(comp, rootCc);
            BoundSize[] cbSz = getCallbackSize(comp);
            if (pos != null || rootCc.isExternal()) {
                CompWrap cw = new CompWrap(comp, rootCc, hideMode, pos, cbSz);
                Cell cell = grid.get(null);
                if (cell == null) {
                    grid.put(null, new Cell(cw));
                } else {
                    cell.compWraps.add(cw);
                }
                if (!rootCc.isBoundsInGrid() || rootCc.isExternal()) {
                    setLinkedBounds(comp, rootCc, comp.getX(), comp.getY(), comp.getWidth(), comp.getHeight(), rootCc.isExternal());
                    i++;
                    continue;
                }
            }
            if (rootCc.getDockSide() != -1) {
                if (dockInsets == null)
                    dockInsets = new int[]{-MAX_DOCK_GRID, -MAX_DOCK_GRID, MAX_DOCK_GRID, MAX_DOCK_GRID};
                addDockingCell(dockInsets, rootCc.getDockSide(), new CompWrap(comp, rootCc, hideMode, pos, cbSz));
                i++;
                continue;
            }
            Boolean cellFlowX = rootCc.getFlowX();
            Cell cell = null;
            if (rootCc.isNewline()) {
                wrap(cellXY, rootCc.getNewlineGapSize());
            } else if (hitEndOfRow) {
                wrap(cellXY, null);
            }
            hitEndOfRow = false;
            final boolean rowNoGrid = lc.isNoGrid() || ((DimConstraint) LayoutUtil.getIndexSafe(specs, lc.isFlowX() ? cellXY[1] : cellXY[0])).isNoGrid();
            // Move to a free y, x  if no absolute grid specified
            int cx = rootCc.getCellX();
            int cy = rootCc.getCellY();
            if ((cx < 0 || cy < 0) && !rowNoGrid && rootCc.getSkip() == 0) { // 3.7.2: If skip, don't find an empty cell first.
                while (!isCellFree(cellXY[1], cellXY[0], spannedRects)) {
                    if (Math.abs(increase(cellXY, 1)) >= wrap)
                        wrap(cellXY, null);
                }
            } else {
                if (cx >= 0 && cy >= 0) {
                    if (cy >= 0) {
                        cellXY[0] = cx;
                        cellXY[1] = cy;
                    } else {    // Only one coordinate is specified. Use the current row (flowx) or column (flowy) to fill in.
                        if (lc.isFlowX()) {
                            cellXY[0] = cx;
                        } else {
                            cellXY[1] = cx;
                        }
                    }
                }
                cell = getCell(cellXY[1], cellXY[0]);   // Might be null
            }
            // Skip a number of cells. Changed for 3.6.1 to take wrap into account and thus "skip" to the next and possibly more rows.
            for (int s = 0, skipCount = rootCc.getSkip(); s < skipCount; s++) {
                do {
                    if (Math.abs(increase(cellXY, 1)) >= wrap)
                        wrap(cellXY, null);
                } while (!isCellFree(cellXY[1], cellXY[0], spannedRects));
            }
            // If cell is not created yet, create it and set it.
            if (cell == null) {
                int spanx = Math.min(rowNoGrid && lc.isFlowX() ? LayoutUtil.INF : rootCc.getSpanX(), MAX_GRID - cellXY[0]);
                int spany = Math.min(rowNoGrid && !lc.isFlowX() ? LayoutUtil.INF : rootCc.getSpanY(), MAX_GRID - cellXY[1]);
                cell = new Cell(spanx, spany, cellFlowX != null ? cellFlowX : lc.isFlowX());
                setCell(cellXY[1], cellXY[0], cell);
                // Add a rectangle so we can know that spanned cells occupy more space.
                if (spanx > 1 || spany > 1)
                    spannedRects.add(new int[]{cellXY[0], cellXY[1], spanx, spany});
            }
            // Add the one, or all, components that split the grid position to the same Cell.
            boolean wrapHandled = false;
            int splitLeft = rowNoGrid ? LayoutUtil.INF : rootCc.getSplit() - 1;
            boolean splitExit = false;
            final boolean spanRestOfRow = (lc.isFlowX() ? rootCc.getSpanX() : rootCc.getSpanY()) == LayoutUtil.INF;
            for (; splitLeft >= 0 && i < comps.length; splitLeft--) {
                ComponentWrapper compAdd = comps[i];
                CC cc = getCC(compAdd, ccMap);
                addLinkIDs(cc);
                boolean visible = compAdd.isVisible();
                hideMode = visible ? -1 : cc.getHideMode() != -1 ? cc.getHideMode() : lc.getHideMode();
                if (cc.isExternal() || hideMode == 3) {
                    i++;
                    splitLeft++;    // Added for 3.5.5 so that these components does not "take" a split slot.
                    continue;       // To work with situations where there are components that does not have a layout manager, or not this one.
                }
                hasPushX |= (visible || hideMode > 1) && (cc.getPushX() != null);
                hasPushY |= (visible || hideMode > 1) && (cc.getPushY() != null);
                if (cc != rootCc) { // If not first in a cell
                    if (cc.isNewline() || !cc.isBoundsInGrid() || cc.getDockSide() != -1)
                        break;
                    if (splitLeft > 0 && cc.getSkip() > 0) {
                        splitExit = true;
                        break;
                    }
                    pos = getPos(compAdd, cc);
                    cbSz = getCallbackSize(compAdd);
                }
                CompWrap cw = new CompWrap(compAdd, cc, hideMode, pos, cbSz);
                cell.compWraps.add(cw);
                cell.hasTagged |= cc.getTag() != null;
                hasTagged |= cell.hasTagged;
                if (cc != rootCc) {
                    if (cc.getHorizontal().getSizeGroup() != null)
                        sizeGroupsX++;
                    if (cc.getVertical().getSizeGroup() != null)
                        sizeGroupsY++;
                }
                i++;
                if ((cc.isWrap() || (spanRestOfRow && splitLeft == 0))) {
                    if (cc.isWrap()) {
                        wrap(cellXY, cc.getWrapGapSize());
                    } else {
                        hitEndOfRow = true;
                    }
                    wrapHandled = true;
                    break;
                }
            }
            if (!wrapHandled && !rowNoGrid) {
                int span = lc.isFlowX() ? cell.spanx : cell.spany;
                if (Math.abs((lc.isFlowX() ? cellXY[0] : cellXY[1])) + span >= wrap) {
                    hitEndOfRow = true;
                } else {
                    increase(cellXY, splitExit ? span - 1 : span);
                }
            }
        }
        // If there were size groups, calculate the largest values in the groups (for min/pref/max) and enforce them on the rest in the group.
        if (sizeGroupsX > 0 || sizeGroupsY > 0) {
            HashMap<String, int[]> sizeGroupMapX = sizeGroupsX > 0 ? new HashMap<>(sizeGroupsX) : null;
            HashMap<String, int[]> sizeGroupMapY = sizeGroupsY > 0 ? new HashMap<>(sizeGroupsY) : null;
            ArrayList<CompWrap> sizeGroupCWs = new ArrayList<>(Math.max(sizeGroupsX, sizeGroupsY));
            for (Cell cell : grid.values()) {
                for (int i = 0; i < cell.compWraps.size(); i++) {
                    CompWrap cw = cell.compWraps.get(i);
                    String sgx = cw.cc.getHorizontal().getSizeGroup();
                    String sgy = cw.cc.getVertical().getSizeGroup();
                    if (sgx != null || sgy != null) {
                        if (sgx != null && sizeGroupMapX != null)
                            addToSizeGroup(sizeGroupMapX, sgx, cw.horSizes);
                        if (sgy != null && sizeGroupMapY != null)
                            addToSizeGroup(sizeGroupMapY, sgy, cw.verSizes);
                        sizeGroupCWs.add(cw);
                    }
                }
            }
            // Set/equalize the sizeGroups to same the values.
            for (CompWrap cw : sizeGroupCWs) {
                if (sizeGroupMapX != null)
                    cw.setSizes(sizeGroupMapX.get(cw.cc.getHorizontal().getSizeGroup()), true);  // Target method handles null sizes
                if (sizeGroupMapY != null)
                    cw.setSizes(sizeGroupMapY.get(cw.cc.getVertical().getSizeGroup()), false); // Target method handles null sizes
            }
        } // Component loop
        // If there were size groups, calculate the largest values in the groups (for min/pref/max) and enforce them on the rest in the group.
        if (sizeGroupsX > 0 || sizeGroupsY > 0) {
            HashMap<String, int[]> sizeGroupMapX = sizeGroupsX > 0 ? new HashMap<>(sizeGroupsX) : null;
            HashMap<String, int[]> sizeGroupMapY = sizeGroupsY > 0 ? new HashMap<>(sizeGroupsY) : null;
            ArrayList<CompWrap> sizeGroupCWs = new ArrayList<>(Math.max(sizeGroupsX, sizeGroupsY));
            for (Cell cell : grid.values()) {
                for (int i = 0; i < cell.compWraps.size(); i++) {
                    CompWrap cw = cell.compWraps.get(i);
                    String sgx = cw.cc.getHorizontal().getSizeGroup();
                    String sgy = cw.cc.getVertical().getSizeGroup();
                    if (sgx != null || sgy != null) {
                        if (sgx != null && sizeGroupMapX != null)
                            addToSizeGroup(sizeGroupMapX, sgx, cw.horSizes);
                        if (sgy != null && sizeGroupMapY != null)
                            addToSizeGroup(sizeGroupMapY, sgy, cw.verSizes);
                        sizeGroupCWs.add(cw);
                    }
                }
            }
            // Set/equalize the sizeGroups to same the values.
            for (CompWrap cw : sizeGroupCWs) {
                if (sizeGroupMapX != null)
                    cw.setSizes(sizeGroupMapX.get(cw.cc.getHorizontal().getSizeGroup()), true);  // Target method handles null sizes
                if (sizeGroupMapY != null)
                    cw.setSizes(sizeGroupMapY.get(cw.cc.getVertical().getSizeGroup()), false); // Target method handles null sizes
            }
        }
        if (hasTagged)
            sortCellsByPlatform(grid.values(), container);
        // Calculate gaps now that the cells are filled and we know all adjacent components.
        boolean ltr = LayoutUtil.isLeftToRight(lc, container);
        for (Cell cell : grid.values()) {
            ArrayList<CompWrap> cws = cell.compWraps;
            for (int i = 0, lastI = cws.size() - 1; i <= lastI; i++) {
                CompWrap cw = cws.get(i);
                ComponentWrapper cwBef = i > 0 ? cws.get(i - 1).comp : null;
                ComponentWrapper cwAft = i < lastI ? cws.get(i + 1).comp : null;
                String tag = getCC(cw.comp, ccMap).getTag();
                CC ccBef = cwBef != null ? getCC(cwBef, ccMap) : null;
                CC ccAft = cwAft != null ? getCC(cwAft, ccMap) : null;
                cw.calcGaps(cwBef, ccBef, cwAft, ccAft, tag, cell.flowx, ltr);
            }
        }
        dockOffX = getDockInsets(colIndexes);
        dockOffY = getDockInsets(rowIndexes);
        // Add synthetic indexes for empty rows and columns so they can get a size
        for (int i = 0, iSz = rowConstr.getCount(); i < iSz; i++)
            rowIndexes.add(i);
        for (int i = 0, iSz = colConstr.getCount(); i < iSz; i++)
            colIndexes.add(i);
        colGroupLists = divideIntoLinkedGroups(false);
        rowGroupLists = divideIntoLinkedGroups(true);
        pushXs = hasPushX || lc.isFillX() ? getDefaultPushWeights(false) : null;
        pushYs = hasPushY || lc.isFillY() ? getDefaultPushWeights(true) : null;
        if (LayoutUtil.isDesignTime(container))
            saveGrid(container, grid);
    }

    private static CC getCC(ComponentWrapper comp, Map<ComponentWrapper, CC> ccMap) {
        CC cc = ccMap.get(comp);
        return cc != null ? cc : new CC();
    }

    private static int getDockInsets(TreeSet<Integer> set) {
        int c = 0;
        for (Integer i : set) {
            if (i < -MAX_GRID) {
                c++;
            } else {
                break;  // Since they are sorted we can break
            }
        }
        return c;
    }

    /**
     * Sort components (normally buttons in a button bar) so they appear in the correct order.
     *
     * @param cells  The cells to sort.
     * @param parent The parent.
     */
    private static void sortCellsByPlatform(Collection<Cell> cells, ContainerWrapper parent) {
        String order = PlatformDefaults.getButtonOrder();
        String orderLo = order.toLowerCase();
        int unrelSize = PlatformDefaults.convertToPixels(1, "u", true, 0, parent, null);
        if (unrelSize == UnitConverter.UNABLE)
            throw new IllegalArgumentException("'unrelated' not recognized by PlatformDefaults!");
        int[] gapUnrel = new int[]{unrelSize, unrelSize, LayoutUtil.NOT_SET};
        int[] flGap = new int[]{0, 0, LayoutUtil.NOT_SET};
        for (Cell cell : cells) {
            if (!cell.hasTagged)
                continue;
            CompWrap prevCW = null;
            boolean nextUnrel = false;
            boolean nextPush = false;
            ArrayList<CompWrap> sortedList = new ArrayList<>(cell.compWraps.size());
            for (int i = 0, iSz = orderLo.length(); i < iSz; i++) {
                char c = orderLo.charAt(i);
                if (c == '+' || c == '_') {
                    nextUnrel = true;
                    if (c == '+')
                        nextPush = true;
                } else {
                    String tag = PlatformDefaults.getTagForChar(c);
                    if (tag != null) {
                        for (int j = 0, jSz = cell.compWraps.size(); j < jSz; j++) {
                            CompWrap cw = cell.compWraps.get(j);
                            if (tag.equals(cw.cc.getTag())) {
                                if (Character.isUpperCase(order.charAt(i))) {
                                    int min = PlatformDefaults.getMinimumButtonWidth().getPixels(0, parent, cw.comp);
                                    if (min > cw.horSizes[LayoutUtil.MIN])
                                        cw.horSizes[LayoutUtil.MIN] = min;
                                    correctMinMax(cw.horSizes);
                                }
                                sortedList.add(cw);
                                if (nextUnrel) {
                                    (prevCW != null ? prevCW : cw).mergeGapSizes(gapUnrel, cell.flowx, prevCW == null);
                                    if (nextPush) {
                                        cw.forcedPushGaps = 1;
                                        nextUnrel = false;
                                        nextPush = false;
                                    }
                                }
                                // "unknown" components will always get an Unrelated gap.
                                if (c == 'u')
                                    nextUnrel = true;
                                prevCW = cw;
                            }
                        }
                    }
                }
            }
            // If we have a gap that was supposed to push but no more components was found to but the "gap before" then compensate.
            if (sortedList.size() > 0) {
                CompWrap cw = sortedList.get(sortedList.size() - 1);
                if (nextUnrel) {
                    cw.mergeGapSizes(gapUnrel, cell.flowx, false);
                    if (nextPush)
                        cw.forcedPushGaps |= 2;
                }
                // Remove first and last gap if not set explicitly.
                if (cw.cc.getHorizontal().getGapAfter() == null)
                    cw.setGaps(flGap, 3);
                cw = sortedList.get(0);
                if (cw.cc.getHorizontal().getGapBefore() == null)
                    cw.setGaps(flGap, 1);
            }
            // Exchange the unsorted CompWraps for the sorted one.
            if (cell.compWraps.size() == sortedList.size()) {
                cell.compWraps.clear();
            } else {
                cell.compWraps.removeAll(sortedList);
            }
            cell.compWraps.addAll(sortedList);
        }
    }

    /**
     * Returns the {@link net.miginfocom.layout.Grid.LinkedDimGroup} that has the {@link net.miginfocom.layout.Grid.CompWrap}
     * <code>cw</code>.
     *
     * @param groupLists The lists to search in.
     * @param cw         The component wrap to find.
     * @return The linked group or <code>null</code> if none had the component wrap.
     */
    private static LinkedDimGroup getGroupContaining(ArrayList<LinkedDimGroup>[] groupLists, CompWrap cw) {
        for (ArrayList<LinkedDimGroup> groups : groupLists) {
            for (LinkedDimGroup group : groups) {
                ArrayList<CompWrap> cwList = group._compWraps;
                for (CompWrap compWrap : cwList) {
                    if (compWrap == cw)
                        return group;
                }
            }
        }
        return null;
    }

    private static void addToSizeGroup(HashMap<String, int[]> sizeGroups, String sizeGroup, int[] size) {
        int[] sgSize = sizeGroups.get(sizeGroup);
        if (sgSize == null) {
            sizeGroups.put(sizeGroup, new int[]{size[LayoutUtil.MIN], size[LayoutUtil.PREF], size[LayoutUtil.MAX]});
        } else {
            sgSize[LayoutUtil.MIN] = Math.max(size[LayoutUtil.MIN], sgSize[LayoutUtil.MIN]);
            sgSize[LayoutUtil.PREF] = Math.max(size[LayoutUtil.PREF], sgSize[LayoutUtil.PREF]);
            sgSize[LayoutUtil.MAX] = Math.min(size[LayoutUtil.MAX], sgSize[LayoutUtil.MAX]);
        }
    }

    private static HashMap<String, Integer> addToEndGroup(HashMap<String, Integer> endGroups, String endGroup, int end) {
        if (endGroup != null) {
            if (endGroups == null)
                endGroups = new HashMap<>(2);
            Integer oldEnd = endGroups.get(endGroup);
            if (oldEnd == null || end > oldEnd)
                endGroups.put(endGroup, end);
        }
        return endGroups;
    }

    private static int getParentSize(ComponentWrapper cw, boolean isHor) {
        ComponentWrapper p = cw.getParent();
        return p != null ? (isHor ? cw.getWidth() : cw.getHeight()) : 0;
    }

    private static ResizeConstraint[] getRowResizeConstraints(DimConstraint[] specs) {
        ResizeConstraint[] resConsts = new ResizeConstraint[specs.length];
        for (int i = 0; i < resConsts.length; i++)
            resConsts[i] = specs[i].resize;
        return resConsts;
    }

    private static ResizeConstraint[] getComponentResizeConstraints(ArrayList<CompWrap> compWraps, boolean isHor) {
        ResizeConstraint[] resConsts = new ResizeConstraint[compWraps.size()];
        for (int i = 0; i < resConsts.length; i++) {
            CC fc = compWraps.get(i).cc;
            resConsts[i] = fc.getDimConstraint(isHor).resize;
            // Always grow docking components in the correct dimension.
            int dock = fc.getDockSide();
            if (isHor ? (dock == 0 || dock == 2) : (dock == 1 || dock == 3)) {
                ResizeConstraint dc = resConsts[i];
                resConsts[i] = new ResizeConstraint(dc.shrinkPrio, dc.shrink, dc.growPrio, ResizeConstraint.WEIGHT_100);
            }
        }
        return resConsts;
    }

    private static boolean[] getComponentGapPush(ArrayList<CompWrap> compWraps, boolean isHor) {
        // Make one element bigger and or the after gap with the next before gap.
        boolean[] barr = new boolean[compWraps.size() + 1];
        for (int i = 0; i < barr.length; i++) {
            boolean push = i > 0 && compWraps.get(i - 1).isPushGap(isHor, false);
            if (!push && i < (barr.length - 1))
                push = compWraps.get(i).isPushGap(isHor, true);
            barr[i] = push;
        }
        return barr;
    }

    private static int[][] getGaps(ArrayList<CompWrap> compWraps, boolean isHor) {
        int compCount = compWraps.size();
        int[][] retValues = new int[compCount + 1][];
        retValues[0] = compWraps.get(0).getGaps(isHor, true);
        for (int i = 0; i < compCount; i++) {
            int[] gap1 = compWraps.get(i).getGaps(isHor, false);
            int[] gap2 = i < compCount - 1 ? compWraps.get(i + 1).getGaps(isHor, true) : null;
            retValues[i + 1] = mergeSizes(gap1, gap2);
        }
        return retValues;
    }

    /**
     * Spanning is specified in the uncompressed grid number. They can for instance be more than 60000 for the outer
     * edge dock grid cells. When the grid is compressed and indexed after only the cells that area occupied the span
     * is erratic. This method use the row/col indexes and corrects the span to be correct for the compressed grid.
     *
     * @param span    The span un the uncompressed grid. <code>LayoutUtil.INF</code> will be interpreted to span the rest
     *                of the column/row excluding the surrounding docking components.
     * @param indexes The indexes in the correct dimension.
     * @return The converted span.
     */
    private static int convertSpanToSparseGrid(int curIx, int span, TreeSet<Integer> indexes) {
        int lastIx = curIx + span;
        int retSpan = 1;
        for (Integer ix : indexes) {
            if (ix <= curIx)
                continue;   // We have not arrived to the correct index yet
            if (ix >= lastIx)
                break;
            retSpan++;
        }
        return retSpan;
    }

    private static void layoutBaseline(ContainerWrapper parent, ArrayList<CompWrap> compWraps, DimConstraint dc, int start, int size, int spanCount) {
        int[] aboveBelow = getBaselineAboveBelow(compWraps, LayoutUtil.PREF, true);
        int blRowSize = aboveBelow[0] + aboveBelow[1];
        CC cc = compWraps.get(0).cc;
        // Align for the whole baseline component array
        UnitValue align = cc.getVertical().getAlign();
        if (spanCount == 1 && align == null)
            align = dc.getAlignOrDefault(false);
        if (align == UnitValue.BASELINE_IDENTITY)
            align = UnitValue.CENTER;
        int offset = start + aboveBelow[0] + (align != null ? Math.max(0, align.getPixels(size - blRowSize, parent, null)) : 0);
        for (CompWrap cw : compWraps) {
            cw.y += offset;
            if (cw.y + cw.h > start + size)
                cw.h = start + size - cw.y;
        }
    }

    private static void layoutSerial(ContainerWrapper parent, ArrayList<CompWrap> compWraps, DimConstraint dc, int start, int size, boolean isHor, boolean fromEnd) {
        FlowSizeSpec fss = mergeSizesGapsAndResConstrs(
                getComponentResizeConstraints(compWraps, isHor),
                getComponentGapPush(compWraps, isHor),
                getComponentSizes(compWraps, isHor),
                getGaps(compWraps, isHor));
        Float[] pushW = dc.isFill() ? GROW_100 : null;
        int[] sizes = LayoutUtil.calculateSerial(fss.sizes, fss.resConstsInclGaps, pushW, LayoutUtil.PREF, size);
        setCompWrapBounds(parent, sizes, compWraps, dc.getAlignOrDefault(isHor), start, size, isHor, fromEnd);
    }

    private static void setCompWrapBounds(ContainerWrapper parent, int[] allSizes, ArrayList<CompWrap> compWraps, UnitValue rowAlign, int start, int size, boolean isHor, boolean fromEnd) {
        int totSize = LayoutUtil.sum(allSizes);
        CC cc = compWraps.get(0).cc;
        UnitValue align = correctAlign(cc, rowAlign, isHor, fromEnd);
        int cSt = start;
        int slack = size - totSize;
        if (slack > 0 && align != null) {
            int al = Math.min(slack, Math.max(0, align.getPixels(slack, parent, null)));
            cSt += (fromEnd ? -al : al);
        }
        for (int i = 0, bIx = 0, iSz = compWraps.size(); i < iSz; i++) {
            CompWrap cw = compWraps.get(i);
            if (fromEnd) {
                cSt -= allSizes[bIx++];
                cw.setDimBounds(cSt - allSizes[bIx], allSizes[bIx], isHor);
                cSt -= allSizes[bIx++];
            } else {
                cSt += allSizes[bIx++];
                cw.setDimBounds(cSt, allSizes[bIx], isHor);
                cSt += allSizes[bIx++];
            }
        }
    }

    private static void layoutParallel(ContainerWrapper parent, ArrayList<CompWrap> compWraps, DimConstraint dc, int start, int size, boolean isHor, boolean fromEnd) {
        int[][] sizes = new int[compWraps.size()][];    // [compIx][gapBef,compSize,gapAft]
        for (int i = 0; i < sizes.length; i++) {
            CompWrap cw = compWraps.get(i);
            DimConstraint cDc = cw.cc.getDimConstraint(isHor);
            ResizeConstraint[] resConstr = new ResizeConstraint[]{
                    cw.isPushGap(isHor, true) ? GAP_RC_CONST_PUSH : GAP_RC_CONST,
                    cDc.resize,
                    cw.isPushGap(isHor, false) ? GAP_RC_CONST_PUSH : GAP_RC_CONST,
            };
            int[][] sz = new int[][]{
                    cw.getGaps(isHor, true), (isHor ? cw.horSizes : cw.verSizes), cw.getGaps(isHor, false)
            };
            Float[] pushW = dc.isFill() ? GROW_100 : null;
            sizes[i] = LayoutUtil.calculateSerial(sz, resConstr, pushW, LayoutUtil.PREF, size);
        }
        UnitValue rowAlign = dc.getAlignOrDefault(isHor);
        setCompWrapBounds(parent, sizes, compWraps, rowAlign, start, size, isHor, fromEnd);
    }

    private static void setCompWrapBounds(ContainerWrapper parent, int[][] sizes, ArrayList<CompWrap> compWraps, UnitValue rowAlign, int start, int size, boolean isHor, boolean fromEnd) {
        for (int i = 0; i < sizes.length; i++) {
            CompWrap cw = compWraps.get(i);
            UnitValue align = correctAlign(cw.cc, rowAlign, isHor, fromEnd);
            int[] cSizes = sizes[i];
            int gapBef = cSizes[0];
            int cSize = cSizes[1];  // No Math.min(size, cSizes[1]) here!
            int gapAft = cSizes[2];
            int cSt = fromEnd ? start - gapBef : start + gapBef;
            int slack = size - cSize - gapBef - gapAft;
            if (slack > 0 && align != null) {
                int al = Math.min(slack, Math.max(0, align.getPixels(slack, parent, null)));
                cSt += (fromEnd ? -al : al);
            }
            cw.setDimBounds(fromEnd ? cSt - cSize : cSt, cSize, isHor);
        }
    }

    private static UnitValue correctAlign(CC cc, UnitValue rowAlign, boolean isHor, boolean fromEnd) {
        UnitValue align = (isHor ? cc.getHorizontal() : cc.getVertical()).getAlign();
        if (align == null)
            align = rowAlign;
        if (align == UnitValue.BASELINE_IDENTITY)
            align = UnitValue.CENTER;
        if (fromEnd) {
            if (align == UnitValue.LEFT)
                align = UnitValue.RIGHT;
            else if (align == UnitValue.RIGHT)
                align = UnitValue.LEFT;
        }
        return align;
    }

    private static int[] getBaselineAboveBelow(ArrayList<CompWrap> compWraps, int sType, boolean centerBaseline) {
        int maxAbove = Short.MIN_VALUE;
        int maxBelow = Short.MIN_VALUE;
        for (CompWrap cw : compWraps) {
            int height = cw.getSize(sType, false);
            if (height >= LayoutUtil.INF)
                return new int[]{LayoutUtil.INF / 2, LayoutUtil.INF / 2};
            int baseline = cw.getBaseline(sType);
            int above = baseline + cw.getGapBefore(sType, false);
            maxAbove = Math.max(above, maxAbove);
            maxBelow = Math.max(height - baseline + cw.getGapAfter(sType, false), maxBelow);
            if (centerBaseline)
                cw.setDimBounds(-baseline, height, false);
        }
        return new int[]{maxAbove, maxBelow};
    }

    private static int getTotalSizeParallel(ArrayList<CompWrap> compWraps, int sType, boolean isHor) {
        int size = sType == LayoutUtil.MAX ? LayoutUtil.INF : 0;
        for (CompWrap cw : compWraps) {
            int cwSize = cw.getSizeInclGaps(sType, isHor);
            if (cwSize >= LayoutUtil.INF)
                return LayoutUtil.INF;
            if (sType == LayoutUtil.MAX ? cwSize < size : cwSize > size)
                size = cwSize;
        }
        return constrainSize(size);
    }

    private static int getTotalSizeSerial(ArrayList<CompWrap> compWraps, int sType, boolean isHor) {
        int totSize = 0;
        for (int i = 0, iSz = compWraps.size(), lastGapAfter = 0; i < iSz; i++) {
            CompWrap wrap = compWraps.get(i);
            int gapBef = wrap.getGapBefore(sType, isHor);
            if (gapBef > lastGapAfter)
                totSize += gapBef - lastGapAfter;
            totSize += wrap.getSize(sType, isHor);
            totSize += (lastGapAfter = wrap.getGapAfter(sType, isHor));
            if (totSize >= LayoutUtil.INF)
                return LayoutUtil.INF;
        }
        return constrainSize(totSize);
    }

    private static int getTotalGroupsSizeParallel(ArrayList<LinkedDimGroup> groups, int sType) {
        int size = sType == LayoutUtil.MAX ? LayoutUtil.INF : 0;
        for (LinkedDimGroup group : groups) {
            if (1 == group.span) {
                int grpSize = group.getMinPrefMax()[sType];
                if (grpSize >= LayoutUtil.INF)
                    return LayoutUtil.INF;
                if (sType == LayoutUtil.MAX ? grpSize < size : grpSize > size)
                    size = grpSize;
            }
        }
        return constrainSize(size);
    }

    /**
     * @return Might contain LayoutUtil.NOT_SET
     */
    private static int[][] getComponentSizes(ArrayList<CompWrap> compWraps, boolean isHor) {
        int[][] compSizes = new int[compWraps.size()][];
        for (int i = 0; i < compSizes.length; i++) {
            CompWrap cw = compWraps.get(i);
            compSizes[i] = isHor ? cw.horSizes : cw.verSizes;
        }
        return compSizes;
    }

    /**
     * Merges sizes and gaps together with Resize Constraints. For gaps {@link #GAP_RC_CONST} is used.
     *
     * @param resConstr       One resize constriant for every row/component. Can be lesser in length and the last element should be used for missing elements.
     * @param gapPush         If the corresponding gap should be considered pushing and thus want to take free space if left over. Should be one more than resConstrs!
     * @param minPrefMaxSizes The sizes (min/pref/max) for every row/component.
     * @param gapSizes        The gaps before and after each row/component packed in one double sized array.
     * @return A holder for the merged values.
     */
    private static FlowSizeSpec mergeSizesGapsAndResConstrs(ResizeConstraint[] resConstr, boolean[] gapPush, int[][] minPrefMaxSizes, int[][] gapSizes) {
        int[][] sizes = new int[(minPrefMaxSizes.length << 1) + 1][];  // Make room for gaps around.
        ResizeConstraint[] resConstsInclGaps = new ResizeConstraint[sizes.length];
        sizes[0] = gapSizes[0];
        for (int i = 0, crIx = 1; i < minPrefMaxSizes.length; i++, crIx += 2) {
            // Component bounds and constraints
            resConstsInclGaps[crIx] = resConstr[i];
            sizes[crIx] = minPrefMaxSizes[i];
            sizes[crIx + 1] = gapSizes[i + 1];
            if (sizes[crIx - 1] != null)
                resConstsInclGaps[crIx - 1] = gapPush[i < gapPush.length ? i : gapPush.length - 1] ? GAP_RC_CONST_PUSH : GAP_RC_CONST;
            if (i == (minPrefMaxSizes.length - 1) && sizes[crIx + 1] != null)
                resConstsInclGaps[crIx + 1] = gapPush[(i + 1) < gapPush.length ? (i + 1) : gapPush.length - 1] ? GAP_RC_CONST_PUSH : GAP_RC_CONST;
        }
        // Check for null and set it to 0, 0, 0.
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] == null)
                sizes[i] = new int[3];
        }
        return new FlowSizeSpec(sizes, resConstsInclGaps);
    }

    private static int[] mergeSizes(int[] oldValues, int[] newValues) {
        if (oldValues == null)
            return newValues;
        if (newValues == null)
            return oldValues;
        int[] ret = new int[oldValues.length];
        for (int i = 0; i < ret.length; i++)
            ret[i] = mergeSizes(oldValues[i], newValues[i]);
        return ret;
    }

    private static int mergeSizes(int oldValue, int newValue) {
        if (oldValue == LayoutUtil.NOT_SET || oldValue == newValue)
            return newValue;
        if (newValue == LayoutUtil.NOT_SET)
            return oldValue;
        return Math.max(oldValue, newValue);
    }

    private static int constrainSize(int s) {
        return s > 0 ? (Math.min(s, LayoutUtil.INF)) : 0;
    }

    private static void correctMinMax(int[] s) {
        if (s[LayoutUtil.MIN] > s[LayoutUtil.MAX])
            s[LayoutUtil.MIN] = s[LayoutUtil.MAX];  // Since MAX is almost always explicitly set use that
        if (s[LayoutUtil.PREF] < s[LayoutUtil.MIN])
            s[LayoutUtil.PREF] = s[LayoutUtil.MIN];
        if (s[LayoutUtil.PREF] > s[LayoutUtil.MAX])
            s[LayoutUtil.PREF] = s[LayoutUtil.MAX];
    }

    private static Float[] extractSubArray(DimConstraint[] specs, Float[] arr, int ix, int len) {
        if (arr == null || arr.length < ix + len) {
            Float[] growLastArr = new Float[len];
            // Handle a group where some rows (first one/few and/or last one/few) are docks.
            for (int i = ix + len - 1; i >= 0; i -= 2) {
                int specIx = (i >> 1);
                if (specs[specIx] != DOCK_DIM_CONSTRAINT) {
                    growLastArr[i - ix] = ResizeConstraint.WEIGHT_100;
                    return growLastArr;
                }
            }
            return growLastArr;
        }
        Float[] newArr = new Float[len];
        System.arraycopy(arr, ix, newArr, 0, len);
        return newArr;
    }

    private static synchronized void putSizesAndIndexes(Object parComp, int[] sizes, int[] ixArr, boolean isRows) {
        if (PARENT_ROWCOL_SIZES_MAP == null)    // Lazy since only if designing in IDEs
            PARENT_ROWCOL_SIZES_MAP = new WeakHashMap[]{new WeakHashMap(4), new WeakHashMap(4)};
        PARENT_ROWCOL_SIZES_MAP[isRows ? 0 : 1].put(parComp, new int[][]{ixArr, sizes});
    }

    private static synchronized void saveGrid(ComponentWrapper parComp, LinkedHashMap<Integer, Cell> grid) {
        if (PARENT_GRIDPOS_MAP == null)    // Lazy since only if designing in IDEs
            PARENT_GRIDPOS_MAP = new WeakHashMap<>();
        PARENT_GRIDPOS_MAP.put(parComp.getComponent(), grid);
    }

    static synchronized HashMap<Object, int[]> getGridPositions(Object parComp) {
        if (PARENT_GRIDPOS_MAP == null)
            return null;
        LinkedHashMap<Integer, Cell> grid = PARENT_GRIDPOS_MAP.get(parComp);
        if (grid == null)
            return null;
        HashMap<Object, int[]> retMap = new HashMap<>();
        for (Map.Entry<Integer, Cell> e : grid.entrySet()) {
            Cell cell = e.getValue();
            Integer xyInt = e.getKey();
            if (xyInt != null) {
                int x = xyInt & 0x0000ffff;
                int y = xyInt >> 16;
                for (CompWrap cw : cell.compWraps)
                    retMap.put(cw.comp.getComponent(), new int[]{x, y, cell.spanx, cell.spany});
            }
        }
        return retMap;
    }

    private void addLinkIDs(CC cc) {
        String[] linkIDs = cc.getLinkTargets();
        for (String linkID : linkIDs) {
            if (linkTargetIDs == null)
                linkTargetIDs = new HashMap<>();
            linkTargetIDs.put(linkID, null);
        }
    }

    /**
     * If the container (parent) that this grid is laying out has changed its bounds, call this method to
     * clear any cached values.
     */
    public void invalidateContainerSize() {
        colFlowSpecs = null;
    }

    /**
     * Does the actual layout. Uses many values calculated in the constructor.
     *
     * @param bounds          The bounds to layout against. Normally that of the parent. [x, y, width, height].
     * @param alignX          The alignment for the x-axis.
     * @param alignY          The alignment for the y-axis.
     * @param debug           If debug information should be saved in {@link #debugRects}.
     * @param checkPrefChange If a check should be done to see if the setting of any new bounds changes the preferred size
     *                        of a component.
     * @return If the layout has probably changed the preferred size and there is need for a new layout (normally only SWT).
     */
    public boolean layout(int[] bounds, UnitValue alignX, UnitValue alignY, boolean debug, boolean checkPrefChange) {
        if (debug)
            debugRects = new ArrayList<>();
        checkSizeCalcs();
        resetLinkValues(true, true);
        layoutInOneDim(bounds[2], alignX, false, pushXs);
        layoutInOneDim(bounds[3], alignY, true, pushYs);
        HashMap<String, Integer> endGrpXMap = null, endGrpYMap = null;
        int compCount = container.getComponentCount();
        // Transfer the calculated bound from the ComponentWrappers to the actual Components.
        boolean layoutAgain = false;
        if (compCount > 0) {
            for (int j = 0; j < (linkTargetIDs != null ? 2 : 1); j++) {   // First do the calculations (maybe more than once) then set the bounds when done
                boolean doAgain;
                int count = 0;
                do {
                    doAgain = false;
                    for (Cell cell : grid.values()) {
                        ArrayList<CompWrap> compWraps = cell.compWraps;
                        for (CompWrap cw : compWraps) {
                            if (j == 0) {
                                doAgain |= doAbsoluteCorrections(cw, bounds);
                                if (!doAgain) { // If we are going to do this again, do not bother this time around
                                    if (cw.cc.getHorizontal().getEndGroup() != null)
                                        endGrpXMap = addToEndGroup(endGrpXMap, cw.cc.getHorizontal().getEndGroup(), cw.x + cw.w);
                                    if (cw.cc.getVertical().getEndGroup() != null)
                                        endGrpYMap = addToEndGroup(endGrpYMap, cw.cc.getVertical().getEndGroup(), cw.y + cw.h);
                                }
                                // @since 3.7.2 Needed or absolute "pos" pointing to "visual" or "container" didn't work if
                                // their bounds changed during the layout cycle. At least not in SWT.
                                if (linkTargetIDs != null && (linkTargetIDs.containsKey("visual") || linkTargetIDs.containsKey("container")))
                                    layoutAgain = true;
                            }
                            if (linkTargetIDs == null || j == 1) {
                                if (cw.cc.getHorizontal().getEndGroup() != null)
                                    cw.w = endGrpXMap.get(cw.cc.getHorizontal().getEndGroup()) - cw.x;
                                if (cw.cc.getVertical().getEndGroup() != null)
                                    cw.h = endGrpYMap.get(cw.cc.getVertical().getEndGroup()) - cw.y;
                                cw.x += bounds[0];
                                cw.y += bounds[1];
                                layoutAgain |= cw.transferBounds(checkPrefChange && !layoutAgain);
                                if (callbackList != null) {
                                    for (LayoutCallback callback : callbackList)
                                        callback.correctBounds(cw.comp);
                                }
                            }
                        }
                    }
                    clearGroupLinkBounds();
                    if (++count > ((compCount << 3) + 10)) {
                        System.err.println("Unstable cyclic dependency in absolute linked values!");
                        break;
                    }
                } while (doAgain);
            }
        }
        // Add debug shapes for the "cells". Use the CompWraps as base for inding the cells.
        if (debug) {
            for (Cell cell : grid.values()) {
                ArrayList<CompWrap> compWraps = cell.compWraps;
                for (CompWrap cw : compWraps) {
                    LinkedDimGroup hGrp = getGroupContaining(colGroupLists, cw);
                    LinkedDimGroup vGrp = getGroupContaining(rowGroupLists, cw);
                    if (hGrp != null && vGrp != null)
                        debugRects.add(new int[]{hGrp.lStart + bounds[0] - (hGrp.fromEnd ? hGrp.lSize : 0), vGrp.lStart + bounds[1] - (vGrp.fromEnd ? vGrp.lSize : 0), hGrp.lSize, vGrp.lSize});
                }
            }
        }
        return layoutAgain;
    }

    public void paintDebug() {
        if (debugRects != null) {
            container.paintDebugOutline();
            ArrayList<int[]> painted = new ArrayList<>();
            for (int[] r : debugRects) {
                if (!painted.contains(r)) {
                    container.paintDebugCell(r[0], r[1], r[2], r[3]);
                    painted.add(r);
                }
            }
            for (Cell cell : grid.values()) {
                ArrayList<CompWrap> compWraps = cell.compWraps;
                for (CompWrap compWrap : compWraps) compWrap.comp.paintDebugOutline();
            }
        }
    }

    public ContainerWrapper getContainer() {
        return container;
    }

    public final int[] getWidth() {
        checkSizeCalcs();
        return width.clone();
    }

    public final int[] getHeight() {
        checkSizeCalcs();
        return height.clone();
    }

    private void checkSizeCalcs() {
        if (colFlowSpecs == null) {
            colFlowSpecs = calcRowsOrColsSizes(true);
            rowFlowSpecs = calcRowsOrColsSizes(false);
            width = getMinPrefMaxSumSize(true);
            height = getMinPrefMaxSumSize(false);
            if (linkTargetIDs == null) {
                resetLinkValues(false, true);
            } else {
                // This call makes some components flicker on SWT. They get their bounds changed twice since
                // the change might affect the absolute size adjustment below. There's no way around this that
                // I know of.
                layout(new int[4], null, null, false, false);
                resetLinkValues(false, false);
            }
            adjustSizeForAbsolute(true);
            adjustSizeForAbsolute(false);
        }
    }

    private UnitValue[] getPos(ComponentWrapper cw, CC cc) {
        UnitValue[] cbPos = null;
        if (callbackList != null) {
            for (int i = 0; i < callbackList.size() && cbPos == null; i++)
                cbPos = callbackList.get(i).getPosition(cw);   // NOT a copy!
        }
        // If one is null, return the other (which many also be null)
        UnitValue[] ccPos = cc.getPos();    // A copy!!
        if (cbPos == null || ccPos == null)
            return cbPos != null ? cbPos : ccPos;
        // Merge
        for (int i = 0; i < 4; i++) {
            UnitValue cbUv = cbPos[i];
            if (cbUv != null)
                ccPos[i] = cbUv;
        }
        return ccPos;
    }

    private BoundSize[] getCallbackSize(ComponentWrapper cw) {
        if (callbackList != null) {
            for (LayoutCallback callback : callbackList) {
                BoundSize[] bs = callback.getSize(cw);   // NOT a copy!
                if (bs != null)
                    return bs;
            }
        }
        return null;
    }

    /**
     * @param cw       Never <code>null</code>.
     * @param cc       Never <code>null</code>.
     * @param external The bounds should be stored even if they are not in {@link #linkTargetIDs}.
     * @return If a change has been made.
     */
    private boolean setLinkedBounds(ComponentWrapper cw, CC cc, int x, int y, int w, int h, boolean external) {
        String id = cc.getId() != null ? cc.getId() : cw.getLinkId();
        if (id == null)
            return false;
        String gid = null;
        int grIx = id.indexOf('.');
        if (grIx != -1) {
            gid = id.substring(0, grIx);
            id = id.substring(grIx + 1);
        }
        Object lay = container.getLayout();
        boolean changed = false;
        if (external || (linkTargetIDs != null && linkTargetIDs.containsKey(id)))
            changed = LinkHandler.setBounds(lay, id, x, y, w, h, !external, false);
        if (gid != null && (external || (linkTargetIDs != null && linkTargetIDs.containsKey(gid)))) {
            if (linkTargetIDs == null)
                linkTargetIDs = new HashMap<>(4);
            linkTargetIDs.put(gid, Boolean.TRUE);
            changed |= LinkHandler.setBounds(lay, gid, x, y, w, h, !external, true);
        }
        return changed;
    }
    //***************************************************************************************
    //* Helper Methods
    //***************************************************************************************

    /**
     * Go to next cell.
     *
     * @param p   The point to increase
     * @param cnt How many cells to advance.
     * @return The new value in the "incresing" dimension.
     */
    private int increase(int[] p, int cnt) {
        return lc.isFlowX() ? (p[0] += cnt) : (p[1] += cnt);
    }

    /**
     * Wraps to the next row or column depending on if horizontal flow or vertical flow is used.
     *
     * @param cellXY  The point to wrap and thus set either x or y to 0 and increase the other one.
     * @param gapSize The gaps size specified in a "wrap XXX" or "newline XXX" or <code>null</code> if none.
     */
    private void wrap(int[] cellXY, BoundSize gapSize) {
        boolean flowx = lc.isFlowX();
        cellXY[0] = flowx ? 0 : cellXY[0] + 1;
        cellXY[1] = flowx ? cellXY[1] + 1 : 0;
        if (gapSize != null) {
            if (wrapGapMap == null)
                wrapGapMap = new HashMap<>(8);
            wrapGapMap.put(cellXY[flowx ? 1 : 0], gapSize);
        }
        // add the row/column so that the gap in the last row/col will not be removed.
        if (flowx) {
            rowIndexes.add(cellXY[1]);
        } else {
            colIndexes.add(cellXY[0]);
        }
    }

    private Float[] getDefaultPushWeights(boolean isRows) {
        ArrayList<LinkedDimGroup>[] groupLists = isRows ? rowGroupLists : colGroupLists;
        Float[] pushWeightArr = GROW_100;   // Only create specific if any of the components have grow.
        for (int i = 0, ix = 1; i < groupLists.length; i++, ix += 2) {
            ArrayList<LinkedDimGroup> grps = groupLists[i];
            Float rowPushWeight = null;
            for (LinkedDimGroup grp : grps) {
                for (int c = 0; c < grp._compWraps.size(); c++) {
                    CompWrap cw = grp._compWraps.get(c);
                    int hideMode = cw.comp.isVisible() ? -1 : cw.cc.getHideMode() != -1 ? cw.cc.getHideMode() : lc.getHideMode();
                    Float pushWeight = hideMode < 2 ? (isRows ? cw.cc.getPushY() : cw.cc.getPushX()) : null;
                    if (rowPushWeight == null || (pushWeight != null && pushWeight > rowPushWeight))
                        rowPushWeight = pushWeight;
                }
            }
            if (rowPushWeight != null) {
                if (pushWeightArr == GROW_100)
                    pushWeightArr = new Float[(groupLists.length << 1) + 1];
                pushWeightArr[ix] = rowPushWeight;
            }
        }
        return pushWeightArr;
    }

    private void clearGroupLinkBounds() {
        if (linkTargetIDs == null)
            return;
        for (Map.Entry<String, Boolean> o : linkTargetIDs.entrySet()) {
            if (o.getValue() == Boolean.TRUE)
                LinkHandler.clearBounds(container.getLayout(), o.getKey());
        }
    }

    private void resetLinkValues(boolean parentSize, boolean compLinks) {
        Object lay = container.getLayout();
        if (compLinks)
            LinkHandler.clearTemporaryBounds(lay);
        boolean defIns = !hasDocks();
        int parW = parentSize ? lc.getWidth().constrain(container.getWidth(), getParentSize(container, true), container) : 0;
        int parH = parentSize ? lc.getHeight().constrain(container.getHeight(), getParentSize(container, false), container) : 0;
        int insX = LayoutUtil.getInsets(lc, 0, defIns).getPixels(0, container, null);
        int insY = LayoutUtil.getInsets(lc, 1, defIns).getPixels(0, container, null);
        int visW = parW - insX - LayoutUtil.getInsets(lc, 2, defIns).getPixels(0, container, null);
        int visH = parH - insY - LayoutUtil.getInsets(lc, 3, defIns).getPixels(0, container, null);
        LinkHandler.setBounds(lay, "visual", insX, insY, visW, visH, true, false);
        LinkHandler.setBounds(lay, "container", 0, 0, parW, parH, true, false);
    }

    private boolean doAbsoluteCorrections(CompWrap cw, int[] bounds) {
        boolean changed = false;
        int[] stSz = getAbsoluteDimBounds(cw, bounds[2], true);
        if (stSz != null)
            cw.setDimBounds(stSz[0], stSz[1], true);
        stSz = getAbsoluteDimBounds(cw, bounds[3], false);
        if (stSz != null)
            cw.setDimBounds(stSz[0], stSz[1], false);
        // If there is a link id, store the new bounds.
        if (linkTargetIDs != null)
            changed = setLinkedBounds(cw.comp, cw.cc, cw.x, cw.y, cw.w, cw.h, false);
        return changed;
    }

    private void adjustSizeForAbsolute(boolean isHor) {
        int[] curSizes = isHor ? width : height;
        Cell absCell = grid.get(null);
        if (absCell == null || absCell.compWraps.size() == 0)
            return;
        ArrayList<CompWrap> cws = absCell.compWraps;
        int maxEnd = 0;
        for (int j = 0, cwSz = absCell.compWraps.size(); j < cwSz + 3; j++) {  // "Do Again" max absCell.compWraps.size() + 3 times.
            boolean doAgain = false;
            for (int i = 0; i < cwSz; i++) {
                CompWrap cw = cws.get(i);
                int[] stSz = getAbsoluteDimBounds(cw, 0, isHor);
                int end = stSz[0] + stSz[1];
                if (maxEnd < end)
                    maxEnd = end;
                // If there is a link id, store the new bounds.
                if (linkTargetIDs != null)
                    doAgain |= setLinkedBounds(cw.comp, cw.cc, stSz[0], stSz[0], stSz[1], stSz[1], false);
            }
            if (!doAgain)
                break;
            // We need to check this again since the coords may be smaller this round.
            maxEnd = 0;
            clearGroupLinkBounds();
        }
        maxEnd += LayoutUtil.getInsets(lc, isHor ? 3 : 2, !hasDocks()).getPixels(0, container, null);
        if (curSizes[LayoutUtil.MIN] < maxEnd)
            curSizes[LayoutUtil.MIN] = maxEnd;
        if (curSizes[LayoutUtil.PREF] < maxEnd)
            curSizes[LayoutUtil.PREF] = maxEnd;
    }

    private int[] getAbsoluteDimBounds(CompWrap cw, int refSize, boolean isHor) {
        if (cw.cc.isExternal()) {
            if (isHor) {
                return new int[]{cw.comp.getX(), cw.comp.getWidth()};
            } else {
                return new int[]{cw.comp.getY(), cw.comp.getHeight()};
            }
        }
        int[] plafPad = lc.isVisualPadding() ? cw.comp.getVisualPadding() : null;
        UnitValue[] pad = cw.cc.getPadding();
        // If no changes do not create a lot of objects
        if (cw.pos == null && plafPad == null && pad == null)
            return null;
        // Set start
        int st = isHor ? cw.x : cw.y;
        int sz = isHor ? cw.w : cw.h;
        // If absolute, use those coordinates instead.
        if (cw.pos != null) {
            UnitValue stUV = cw.pos[isHor ? 0 : 1];
            UnitValue endUV = cw.pos[isHor ? 2 : 3];
            int minSz = cw.getSize(LayoutUtil.MIN, isHor);
            int maxSz = cw.getSize(LayoutUtil.MAX, isHor);
            sz = Math.min(Math.max(cw.getSize(LayoutUtil.PREF, isHor), minSz), maxSz);
            if (stUV != null) {
                st = stUV.getPixels(stUV.getUnit() == UnitValue.ALIGN ? sz : refSize, container, cw.comp);
                if (endUV != null)  // if (endUV == null && cw.cc.isBoundsIsGrid() == true)
                    sz = Math.min(Math.max((isHor ? (cw.x + cw.w) : (cw.y + cw.h)) - st, minSz), maxSz);
            }
            if (endUV != null) {
                if (stUV != null) {   // if (stUV != null || cw.cc.isBoundsIsGrid()) {
                    sz = Math.min(Math.max(endUV.getPixels(refSize, container, cw.comp) - st, minSz), maxSz);
                } else {
                    st = endUV.getPixels(refSize, container, cw.comp) - sz;
                }
            }
        }
        // If constraint has padding -> correct the start/size
        if (pad != null) {
            UnitValue uv = pad[isHor ? 1 : 0];
            int p = uv != null ? uv.getPixels(refSize, container, cw.comp) : 0;
            st += p;
            uv = pad[isHor ? 3 : 2];
            sz += -p + (uv != null ? uv.getPixels(refSize, container, cw.comp) : 0);
        }
        // If the plaf converter has padding -> correct the start/size
        if (plafPad != null) {
            int p = plafPad[isHor ? 1 : 0];
            st += p;
            sz += -p + (plafPad[isHor ? 3 : 2]);
        }
        return new int[]{st, sz};
    }

    private void layoutInOneDim(int refSize, UnitValue align, boolean isRows, Float[] defaultPushWeights) {
        boolean fromEnd = !(isRows ? lc.isTopToBottom() : LayoutUtil.isLeftToRight(lc, container));
        DimConstraint[] primDCs = (isRows ? rowConstr : colConstr).getConstaints();
        FlowSizeSpec fss = isRows ? rowFlowSpecs : colFlowSpecs;
        ArrayList<LinkedDimGroup>[] rowCols = isRows ? rowGroupLists : colGroupLists;
        int[] rowColSizes = LayoutUtil.calculateSerial(fss.sizes, fss.resConstsInclGaps, defaultPushWeights, LayoutUtil.PREF, refSize);
        if (LayoutUtil.isDesignTime(container)) {
            TreeSet<Integer> indexes = isRows ? rowIndexes : colIndexes;
            int[] ixArr = new int[indexes.size()];
            int ix = 0;
            for (Integer i : indexes)
                ixArr[ix++] = i;
            putSizesAndIndexes(container.getComponent(), rowColSizes, ixArr, isRows);
        }
        int curPos = align != null ? align.getPixels(refSize - LayoutUtil.sum(rowColSizes), container, null) : 0;
        if (fromEnd)
            curPos = refSize - curPos;
        for (int i = 0; i < rowCols.length; i++) {
            ArrayList<LinkedDimGroup> linkedGroups = rowCols[i];
            int scIx = i - (isRows ? dockOffY : dockOffX);
            int bIx = i << 1;
            int bIx2 = bIx + 1;
            curPos += (fromEnd ? -rowColSizes[bIx] : rowColSizes[bIx]);
            DimConstraint primDC = scIx >= 0 ? primDCs[scIx >= primDCs.length ? primDCs.length - 1 : scIx] : DOCK_DIM_CONSTRAINT;
            int rowSize = rowColSizes[bIx2];
            for (LinkedDimGroup group : linkedGroups) {
                int groupSize = rowSize;
                if (group.span > 1)
                    groupSize = LayoutUtil.sum(rowColSizes, bIx2, Math.min((group.span << 1) - 1, rowColSizes.length - bIx2 - 1));
                group.layout(primDC, curPos, groupSize, group.span);
            }
            curPos += (fromEnd ? -rowSize : rowSize);
        }
    }

    /**
     * Calculates Min, Preferred and Max size for the columns OR rows.
     *
     * @param isHor If it is the horizontal dimension to calculate.
     * @return The sizes in a {@link net.miginfocom.layout.Grid.FlowSizeSpec}.
     */
    private FlowSizeSpec calcRowsOrColsSizes(boolean isHor) {
        ArrayList<LinkedDimGroup>[] groupsLists = isHor ? colGroupLists : rowGroupLists;
        Float[] defPush = isHor ? pushXs : pushYs;
        int refSize = isHor ? container.getWidth() : container.getHeight();
        BoundSize cSz = isHor ? lc.getWidth() : lc.getHeight();
        if (!cSz.isUnset())
            refSize = cSz.constrain(refSize, getParentSize(container, isHor), container);
        DimConstraint[] primDCs = (isHor ? colConstr : rowConstr).getConstaints();
        TreeSet<Integer> primIndexes = isHor ? colIndexes : rowIndexes;
        int[][] rowColBoundSizes = new int[primIndexes.size()][];
        HashMap<String, int[]> sizeGroupMap = new HashMap<>(2);
        DimConstraint[] allDCs = new DimConstraint[primIndexes.size()];
        Iterator<Integer> primIt = primIndexes.iterator();
        for (int r = 0; r < rowColBoundSizes.length; r++) {
            int cellIx = primIt.next();
            int[] rowColSizes = new int[3];
            if (cellIx >= -MAX_GRID && cellIx <= MAX_GRID) {  // If not dock cell
                allDCs[r] = primDCs[cellIx >= primDCs.length ? primDCs.length - 1 : cellIx];
            } else {
                allDCs[r] = DOCK_DIM_CONSTRAINT;
            }
            ArrayList<LinkedDimGroup> groups = groupsLists[r];
            int[] groupSizes = new int[]{
                    getTotalGroupsSizeParallel(groups, LayoutUtil.MIN),
                    getTotalGroupsSizeParallel(groups, LayoutUtil.PREF),
                    LayoutUtil.INF};
            correctMinMax(groupSizes);
            BoundSize dimSize = allDCs[r].getSize();
            for (int sType = LayoutUtil.MIN; sType <= LayoutUtil.MAX; sType++) {
                int rowColSize = groupSizes[sType];
                UnitValue uv = dimSize.getSize(sType);
                if (uv != null) {
                    // If the size of the column is a link to some other size, use that instead
                    int unit = uv.getUnit();
                    if (unit == UnitValue.PREF_SIZE) {
                        rowColSize = groupSizes[LayoutUtil.PREF];
                    } else if (unit == UnitValue.MIN_SIZE) {
                        rowColSize = groupSizes[LayoutUtil.MIN];
                    } else if (unit == UnitValue.MAX_SIZE) {
                        rowColSize = groupSizes[LayoutUtil.MAX];
                    } else {
                        rowColSize = uv.getPixels(refSize, container, null);
                    }
                } else if (cellIx >= -MAX_GRID && cellIx <= MAX_GRID && rowColSize == 0) {
                    rowColSize = LayoutUtil.isDesignTime(container) ? LayoutUtil.getDesignTimeEmptySize() : 0;    // Empty rows with no size set gets XX pixels if design time
                }
                rowColSizes[sType] = rowColSize;
            }
            correctMinMax(rowColSizes);
            addToSizeGroup(sizeGroupMap, allDCs[r].getSizeGroup(), rowColSizes);
            rowColBoundSizes[r] = rowColSizes;
        }
        // Set/equalize the size groups to same the values.
        if (sizeGroupMap.size() > 0) {
            for (int r = 0; r < rowColBoundSizes.length; r++) {
                if (allDCs[r].getSizeGroup() != null)
                    rowColBoundSizes[r] = sizeGroupMap.get(allDCs[r].getSizeGroup());
            }
        }
        // Add the gaps
        ResizeConstraint[] resConstrs = getRowResizeConstraints(allDCs);
        boolean[] fillInPushGaps = new boolean[allDCs.length + 1];
        int[][] gapSizes = getRowGaps(allDCs, refSize, isHor, fillInPushGaps);
        FlowSizeSpec fss = mergeSizesGapsAndResConstrs(resConstrs, fillInPushGaps, rowColBoundSizes, gapSizes);
        // Spanning components are not handled yet. Check and adjust the multi-row min/pref they enforce.
        adjustMinPrefForSpanningComps(allDCs, defPush, fss, groupsLists);
        return fss;
    }

    private int[] getMinPrefMaxSumSize(boolean isHor) {
        int[][] sizes = isHor ? colFlowSpecs.sizes : rowFlowSpecs.sizes;
        int[] retSizes = new int[3];
        BoundSize sz = isHor ? lc.getWidth() : lc.getHeight();
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] != null) {
                int[] size = sizes[i];
                for (int sType = LayoutUtil.MIN; sType <= LayoutUtil.MAX; sType++) {
                    if (sz.getSize(sType) != null) {
                        if (i == 0)
                            retSizes[sType] = sz.getSize(sType).getPixels(getParentSize(container, isHor), container, null);
                    } else {
                        int s = size[sType];
                        if (s != LayoutUtil.NOT_SET) {
                            if (sType == LayoutUtil.PREF) {
                                int bnd = size[LayoutUtil.MAX];
                                if (bnd != LayoutUtil.NOT_SET && bnd < s)
                                    s = bnd;
                                bnd = size[LayoutUtil.MIN];
                                if (bnd > s)    // Includes s == LayoutUtil.NOT_SET since < 0.
                                    s = bnd;
                            }
                            retSizes[sType] += s;   // MAX compensated below.
                        }
                        // So that MAX is always correct.
                        if (size[LayoutUtil.MAX] == LayoutUtil.NOT_SET || retSizes[LayoutUtil.MAX] > LayoutUtil.INF)
                            retSizes[LayoutUtil.MAX] = LayoutUtil.INF;
                    }
                }
            }
        }
        correctMinMax(retSizes);
        return retSizes;
    }

    /**
     * Returns the row gaps in pixel sizes. One more than there are <code>specs</code> sent in.
     *
     * @return The row gaps in pixel sizes. One more than there are <code>specs</code> sent in.
     */
    private int[][] getRowGaps(DimConstraint[] specs, int refSize, boolean isHor, boolean[] fillInPushGaps) {
        BoundSize defGap = isHor ? lc.getGridGapX() : lc.getGridGapY();
        if (defGap == null)
            defGap = isHor ? PlatformDefaults.getGridGapX() : PlatformDefaults.getGridGapY();
        int[] defGapArr = defGap.getPixelSizes(refSize, container, null);
        boolean defIns = !hasDocks();
        UnitValue firstGap = LayoutUtil.getInsets(lc, isHor ? 1 : 0, defIns);
        UnitValue lastGap = LayoutUtil.getInsets(lc, isHor ? 3 : 2, defIns);
        int[][] retValues = new int[specs.length + 1][];
        for (int i = 0, wgIx = 0; i < retValues.length; i++) {
            DimConstraint specBefore = i > 0 ? specs[i - 1] : null;
            DimConstraint specAfter = i < specs.length ? specs[i] : null;
            // No gap if between docking components.
            boolean edgeBefore = (specBefore == DOCK_DIM_CONSTRAINT || specBefore == null);
            boolean edgeAfter = (specAfter == DOCK_DIM_CONSTRAINT || specAfter == null);
            if (edgeBefore && edgeAfter)
                continue;
            BoundSize wrapGapSize = (wrapGapMap == null || isHor == lc.isFlowX() ? null : wrapGapMap.get(wgIx++));
            if (wrapGapSize == null) {
                int[] gapBefore = specBefore != null ? specBefore.getRowGaps(container, null, refSize, false) : null;
                int[] gapAfter = specAfter != null ? specAfter.getRowGaps(container, null, refSize, true) : null;
                if (edgeBefore && gapAfter == null && firstGap != null) {
                    int bef = firstGap.getPixels(refSize, container, null);
                    retValues[i] = new int[]{bef, bef, bef};
                } else if (edgeAfter && gapBefore == null && firstGap != null) {
                    int aft = lastGap.getPixels(refSize, container, null);
                    retValues[i] = new int[]{aft, aft, aft};
                } else {
                    retValues[i] = gapAfter != gapBefore ? mergeSizes(gapAfter, gapBefore) : new int[]{defGapArr[0], defGapArr[1], defGapArr[2]};
                }
                if (specBefore != null && specBefore.isGapAfterPush() || specAfter != null && specAfter.isGapBeforePush())
                    fillInPushGaps[i] = true;
            } else {
                if (wrapGapSize.isUnset()) {
                    retValues[i] = new int[]{defGapArr[0], defGapArr[1], defGapArr[2]};
                } else {
                    retValues[i] = wrapGapSize.getPixelSizes(refSize, container, null);
                }
                fillInPushGaps[i] = wrapGapSize.getGapPush();
            }
        }
        return retValues;
    }

    private boolean hasDocks() {
        return (dockOffX > 0 || dockOffY > 0 || rowIndexes.last() > MAX_GRID || colIndexes.last() > MAX_GRID);
    }

    /**
     * Adjust min/pref size for columns(or rows) that has components that spans multiple columns (or rows).
     *
     * @param specs   The specs for the columns or rows. Last index will be used if <code>count</code> is greater than this array's length.
     * @param defPush The default grow weight if the specs does not have anyone that will grow. Comes from "push" in the CC.
     */
    private void adjustMinPrefForSpanningComps(DimConstraint[] specs, Float[] defPush, FlowSizeSpec fss, ArrayList<LinkedDimGroup>[] groupsLists) {
        for (int r = groupsLists.length - 1; r >= 0; r--) { // Since 3.7.3 Iterate from end to start. Will solve some multiple spanning components hard to solve problems.
            ArrayList<LinkedDimGroup> groups = groupsLists[r];
            for (LinkedDimGroup group : groups) {
                if (group.span == 1)
                    continue;
                int[] sizes = group.getMinPrefMax();
                for (int s = LayoutUtil.MIN; s <= LayoutUtil.PREF; s++) {
                    int cSize = sizes[s];
                    if (cSize == LayoutUtil.NOT_SET)
                        continue;
                    int rowSize = 0;
                    int sIx = (r << 1) + 1;
                    int len = Math.min((group.span << 1), fss.sizes.length - sIx) - 1;
                    for (int j = sIx; j < sIx + len; j++) {
                        int sz = fss.sizes[j][s];
                        if (sz != LayoutUtil.NOT_SET)
                            rowSize += sz;
                    }
                    if (rowSize < cSize && len > 0) {
                        for (int eagerness = 0, newRowSize = 0; eagerness < 4 && newRowSize < cSize; eagerness++)
                            newRowSize = fss.expandSizes(specs, defPush, cSize, sIx, len, s, eagerness);
                    }
                }
            }
        }
    }

    /**
     * For one dimension divide the component wraps into logical groups. One group for component wraps that share a common something,
     * line the property to layout by base line.
     *
     * @param isRows If rows, and not columns, are to be divided.
     * @return One <code>ArrayList<LinkedDimGroup></code> for every row/column.
     */
    private ArrayList<LinkedDimGroup>[] divideIntoLinkedGroups(boolean isRows) {
        boolean fromEnd = !(isRows ? lc.isTopToBottom() : LayoutUtil.isLeftToRight(lc, container));
        TreeSet<Integer> primIndexes = isRows ? rowIndexes : colIndexes;
        TreeSet<Integer> secIndexes = isRows ? colIndexes : rowIndexes;
        DimConstraint[] primDCs = (isRows ? rowConstr : colConstr).getConstaints();
        //noinspection unchecked
        ArrayList<LinkedDimGroup>[] groupLists = new ArrayList[primIndexes.size()];
        int gIx = 0;
        for (int i : primIndexes) {
            DimConstraint dc;
            if (i >= -MAX_GRID && i <= MAX_GRID) {  // If not dock cell
                dc = primDCs[i >= primDCs.length ? primDCs.length - 1 : i];
            } else {
                dc = DOCK_DIM_CONSTRAINT;
            }
            ArrayList<LinkedDimGroup> groupList = new ArrayList<>(2);
            groupLists[gIx++] = groupList;
            for (Integer ix : secIndexes) {
                Cell cell = isRows ? getCell(i, ix) : getCell(ix, i);
                if (cell == null || cell.compWraps.size() == 0)
                    continue;
                int span = (isRows ? cell.spany : cell.spanx);
                if (span > 1)
                    span = convertSpanToSparseGrid(i, span, primIndexes);
                boolean isPar = (cell.flowx == isRows);
                if ((!isPar && cell.compWraps.size() > 1) || span > 1) {
                    int linkType = isPar ? LinkedDimGroup.TYPE_PARALLEL : LinkedDimGroup.TYPE_SERIAL;
                    LinkedDimGroup lg = new LinkedDimGroup("p," + ix, span, linkType, !isRows, fromEnd);
                    lg.setCompWraps(cell.compWraps);
                    groupList.add(lg);
                } else {
                    for (int cwIx = 0; cwIx < cell.compWraps.size(); cwIx++) {
                        CompWrap cw = cell.compWraps.get(cwIx);
                        boolean rowBaselineAlign = (isRows && lc.isTopToBottom() && dc.getAlignOrDefault(!isRows) == UnitValue.BASELINE_IDENTITY); // Disable baseline for bottomToTop since I can not verify it working.
                        boolean isBaseline = isRows && cw.isBaselineAlign(rowBaselineAlign);
                        String linkCtx = isBaseline ? "baseline" : null;
                        // Find a group with same link context and put it in that group.
                        boolean foundList = false;
                        for (int glIx = 0, lastGl = groupList.size() - 1; glIx <= lastGl; glIx++) {
                            LinkedDimGroup group = groupList.get(glIx);
                            if (Objects.equals(linkCtx, group.linkCtx)) {
                                group.addCompWrap(cw);
                                foundList = true;
                                break;
                            }
                        }
                        // If none found and at last add a new group.
                        if (!foundList) {
                            int linkType = isBaseline ? LinkedDimGroup.TYPE_BASELINE : LinkedDimGroup.TYPE_PARALLEL;
                            LinkedDimGroup lg = new LinkedDimGroup(linkCtx, 1, linkType, !isRows, fromEnd);
                            lg.addCompWrap(cw);
                            groupList.add(lg);
                        }
                    }
                }
            }
        }
        return groupLists;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isCellFree(int r, int c, ArrayList<int[]> occupiedRects) {
        if (getCell(r, c) != null)
            return false;
        for (int[] rect : occupiedRects) {
            if (rect[0] <= c && rect[1] <= r && rect[0] + rect[2] > c && rect[1] + rect[3] > r)
                return false;
        }
        return true;
    }

    private Cell getCell(int r, int c) {
        return grid.get((r << 16) + c);
    }

    private void setCell(int r, int c, Cell cell) {
        if (c < 0 || r < 0)
            throw new IllegalArgumentException("Cell position cannot be negative. row: " + r + ", col: " + c);
        if (c > MAX_GRID || r > MAX_GRID)
            throw new IllegalArgumentException("Cell position out of bounds. Out of cells. row: " + r + ", col: " + c);
        rowIndexes.add(r);
        colIndexes.add(c);
        grid.put((r << 16) + c, cell);
    }

    /**
     * Adds a docking cell. That cell is outside the normal cell indexes.
     *
     * @param dockInsets The current dock insets. Will be updated!
     * @param side       top == 0, left == 1, bottom = 2, right = 3.
     * @param cw         The compwrap to put in a cell and add.
     */
    private void addDockingCell(int[] dockInsets, int side, CompWrap cw) {
        int r, c, spanx = 1, spany = 1;
        switch (side) {
            case 0:
            case 2:
                r = side == 0 ? dockInsets[0]++ : dockInsets[2]--;
                c = dockInsets[1];
                spanx = dockInsets[3] - dockInsets[1] + 1;  // The +1 is for cell 0.
                colIndexes.add(dockInsets[3]); // Make sure there is a receiving cell
                break;
            case 1:
            case 3:
                c = side == 1 ? dockInsets[1]++ : dockInsets[3]--;
                r = dockInsets[0];
                spany = dockInsets[2] - dockInsets[0] + 1;  // The +1 is for cell 0.
                rowIndexes.add(dockInsets[2]); // Make sure there is a receiving cell
                break;
            default:
                throw new IllegalArgumentException("Internal error 123.");
        }
        rowIndexes.add(r);
        colIndexes.add(c);
        grid.put((r << 16) + c, new Cell(cw, spanx, spany, spanx > 1));
    }

    /**
     * A simple representation of a cell in the grid. Contains a number of component wraps and if they span more than one cell.
     */
    private static class Cell {
        private final int spanx, spany;
        private final boolean flowx;
        private final ArrayList<CompWrap> compWraps = new ArrayList<>(1);
        private boolean hasTagged = false;  // If one or more components have styles and need to be checked by the component sorter

        private Cell(CompWrap cw) {
            this(cw, 1, 1, true);
        }

        private Cell(int spanx, int spany, boolean flowx) {
            this(null, spanx, spany, flowx);
        }

        private Cell(CompWrap cw, int spanx, int spany, boolean flowx) {
            if (cw != null)
                compWraps.add(cw);
            this.spanx = spanx;
            this.spany = spany;
            this.flowx = flowx;
        }
    }

    /**
     * A number of component wraps that share a layout "something" <b>in one dimension</b>
     */
    private static class LinkedDimGroup {
        private static final int TYPE_SERIAL = 0;
        private static final int TYPE_PARALLEL = 1;
        private static final int TYPE_BASELINE = 2;
        private final String linkCtx;
        private final int span;
        private final int linkType;
        private final boolean isHor, fromEnd;
        private ArrayList<CompWrap> _compWraps = new ArrayList<>(4);
        private int[] sizes = null;
        private int lStart = 0, lSize = 0;  // Currently mostly for debug painting

        private LinkedDimGroup(String linkCtx, int span, int linkType, boolean isHor, boolean fromEnd) {
            this.linkCtx = linkCtx;
            this.span = span;
            this.linkType = linkType;
            this.isHor = isHor;
            this.fromEnd = fromEnd;
        }

        private void addCompWrap(CompWrap cw) {
            _compWraps.add(cw);
            sizes = null;
        }

        private void setCompWraps(ArrayList<CompWrap> cws) {
            if (_compWraps != cws) {
                _compWraps = cws;
                sizes = null;
            }
        }

        private void layout(DimConstraint dc, int start, int size, int spanCount) {
            lStart = start;
            lSize = size;
            if (_compWraps.size() == 0)
                return;
            ContainerWrapper parent = _compWraps.get(0).comp.getParent();
            if (linkType == TYPE_PARALLEL) {
                layoutParallel(parent, _compWraps, dc, start, size, isHor, fromEnd);
            } else if (linkType == TYPE_BASELINE) {
                layoutBaseline(parent, _compWraps, dc, start, size, spanCount);
            } else {
                layoutSerial(parent, _compWraps, dc, start, size, isHor, fromEnd);
            }
        }

        /**
         * Returns the min/pref/max sizes for this cell. Returned array <b>must not be altered</b>
         *
         * @return A shared min/pref/max array of sizes. Always of length 3 and never <code>null</code>. Will always be of type STATIC and PIXEL.
         */
        private int[] getMinPrefMax() {
            if (sizes == null && _compWraps.size() > 0) {
                sizes = new int[3];
                for (int sType = LayoutUtil.MIN; sType <= LayoutUtil.PREF; sType++) {
                    if (linkType == TYPE_PARALLEL) {
                        sizes[sType] = getTotalSizeParallel(_compWraps, sType, isHor);
                    } else if (linkType == TYPE_BASELINE) {
                        int[] aboveBelow = getBaselineAboveBelow(_compWraps, sType, false);
                        sizes[sType] = aboveBelow[0] + aboveBelow[1];
                    } else {
                        sizes[sType] = getTotalSizeSerial(_compWraps, sType, isHor);
                    }
                }
                sizes[LayoutUtil.MAX] = LayoutUtil.INF;
            }
            return sizes;
        }
    }

    /**
     * Wraps a {@link java.awt.Component} together with its constraint. Caches a lot of information about the component so
     * for instance not the preferred size has to be calculated more than once.
     */
    private final static class CompWrap {
        private final ComponentWrapper comp;
        private final CC cc;
        private final UnitValue[] pos;
        private final int[] horSizes = new int[3];
        private final int[] verSizes = new int[3];
        private int[][] gaps; // [top,left(actually before),bottom,right(actually after)][min,pref,max]
        private int x = LayoutUtil.NOT_SET, y = LayoutUtil.NOT_SET, w = LayoutUtil.NOT_SET, h = LayoutUtil.NOT_SET;
        private int forcedPushGaps = 0;   // 1 == before, 2 = after. Bitwise.

        private CompWrap(ComponentWrapper c, CC cc, int eHideMode, UnitValue[] pos, BoundSize[] callbackSz) {
            this.comp = c;
            this.cc = cc;
            this.pos = pos;
            if (eHideMode <= 0) {
                BoundSize hBS = (callbackSz != null && callbackSz[0] != null) ? callbackSz[0] : cc.getHorizontal().getSize();
                BoundSize vBS = (callbackSz != null && callbackSz[1] != null) ? callbackSz[1] : cc.getVertical().getSize();
                int wHint = -1, hHint = -1; // Added for v3.7
                if (comp.getWidth() > 0 && comp.getHeight() > 0) {
                    hHint = comp.getHeight();
                    wHint = comp.getWidth();
                }
                for (int i = LayoutUtil.MIN; i <= LayoutUtil.MAX; i++) {
                    horSizes[i] = getSize(hBS, i, true, hHint);
                    verSizes[i] = getSize(vBS, i, false, wHint > 0 ? wHint : horSizes[i]);
                }
                correctMinMax(horSizes);
                correctMinMax(verSizes);
            }
            if (eHideMode > 1) {
                gaps = new int[4][];
                Arrays.fill(gaps, new int[3]);
            }
        }

        private int getSize(BoundSize uvs, int sizeType, boolean isHor, int sizeHint) {
            if (uvs == null || uvs.getSize(sizeType) == null) {
                switch (sizeType) {
                    case LayoutUtil.MIN:
                        return isHor ? comp.getMinimumWidth(sizeHint) : comp.getMinimumHeight(sizeHint);
                    case LayoutUtil.PREF:
                        return isHor ? comp.getPreferredWidth(sizeHint) : comp.getPreferredHeight(sizeHint);
                    default:
                        return isHor ? comp.getMaximumWidth(sizeHint) : comp.getMaximumHeight(sizeHint);
                }
            }
            ContainerWrapper par = comp.getParent();
            return uvs.getSize(sizeType).getPixels(isHor ? par.getWidth() : par.getHeight(), par, comp);
        }

        private void calcGaps(ComponentWrapper before, CC befCC, ComponentWrapper after, CC aftCC, String tag, boolean flowX, boolean isLTR) {
            ContainerWrapper par = comp.getParent();
            int parW = par.getWidth();
            int parH = par.getHeight();
            BoundSize befGap = before != null ? (flowX ? befCC.getHorizontal() : befCC.getVertical()).getGapAfter() : null;
            BoundSize aftGap = after != null ? (flowX ? aftCC.getHorizontal() : aftCC.getVertical()).getGapBefore() : null;
            mergeGapSizes(cc.getVertical().getComponentGaps(par, comp, befGap, (flowX ? null : before), tag, parH, 0, isLTR), false, true);
            mergeGapSizes(cc.getHorizontal().getComponentGaps(par, comp, befGap, (flowX ? before : null), tag, parW, 1, isLTR), true, true);
            mergeGapSizes(cc.getVertical().getComponentGaps(par, comp, aftGap, (flowX ? null : after), tag, parH, 2, isLTR), false, false);
            mergeGapSizes(cc.getHorizontal().getComponentGaps(par, comp, aftGap, (flowX ? after : null), tag, parW, 3, isLTR), true, false);
        }

        private void setDimBounds(int start, int size, boolean isHor) {
            if (isHor) {
                x = start;
                w = size;
            } else {
                y = start;
                h = size;
            }
        }

        private boolean isPushGap(boolean isHor, boolean isBefore) {
            if (isHor && ((isBefore ? 1 : 2) & forcedPushGaps) != 0)
                return true;    // Forced
            DimConstraint dc = cc.getDimConstraint(isHor);
            BoundSize s = isBefore ? dc.getGapBefore() : dc.getGapAfter();
            return s != null && s.getGapPush();
        }

        /**
         * @return If the preferred size have changed because of the new bounds.
         */
        private boolean transferBounds(boolean checkPrefChange) {
            comp.setBounds(x, y, w, h);
            if (checkPrefChange && w != horSizes[LayoutUtil.PREF]) {
                BoundSize vSz = cc.getVertical().getSize();
                if (vSz.getPreferred() == null) {
                    return comp.getPreferredHeight(-1) != verSizes[LayoutUtil.PREF];
                }
            }
            return false;
        }

        private void setSizes(int[] sizes, boolean isHor) {
            if (sizes == null)
                return;
            int[] s = isHor ? horSizes : verSizes;
            s[LayoutUtil.MIN] = sizes[LayoutUtil.MIN];
            s[LayoutUtil.PREF] = sizes[LayoutUtil.PREF];
            s[LayoutUtil.MAX] = sizes[LayoutUtil.MAX];
        }

        private void setGaps(int[] minPrefMax, int ix) {
            if (gaps == null)
                gaps = new int[][]{null, null, null, null};
            gaps[ix] = minPrefMax;
        }

        private void mergeGapSizes(int[] sizes, boolean isHor, boolean isTL) {
            if (gaps == null)
                gaps = new int[][]{null, null, null, null};
            if (sizes == null)
                return;
            int gapIX = getGapIx(isHor, isTL);
            int[] oldGaps = gaps[gapIX];
            if (oldGaps == null) {
                oldGaps = new int[]{0, 0, LayoutUtil.INF};
                gaps[gapIX] = oldGaps;
            }
            oldGaps[LayoutUtil.MIN] = Math.max(sizes[LayoutUtil.MIN], oldGaps[LayoutUtil.MIN]);
            oldGaps[LayoutUtil.PREF] = Math.max(sizes[LayoutUtil.PREF], oldGaps[LayoutUtil.PREF]);
            oldGaps[LayoutUtil.MAX] = Math.min(sizes[LayoutUtil.MAX], oldGaps[LayoutUtil.MAX]);
        }

        private int getGapIx(boolean isHor, boolean isTL) {
            return isHor ? (isTL ? 1 : 3) : (isTL ? 0 : 2);
        }

        private int getSizeInclGaps(int sizeType, boolean isHor) {
            return filter(sizeType, getGapBefore(sizeType, isHor) + getSize(sizeType, isHor) + getGapAfter(sizeType, isHor));
        }

        private int getSize(int sizeType, boolean isHor) {
            return filter(sizeType, isHor ? horSizes[sizeType] : verSizes[sizeType]);
        }

        private int getGapBefore(int sizeType, boolean isHor) {
            int[] gaps = getGaps(isHor, true);
            return gaps != null ? filter(sizeType, gaps[sizeType]) : 0;
        }

        private int getGapAfter(int sizeType, boolean isHor) {
            int[] gaps = getGaps(isHor, false);
            return gaps != null ? filter(sizeType, gaps[sizeType]) : 0;
        }

        private int[] getGaps(boolean isHor, boolean isTL) {
            return gaps[getGapIx(isHor, isTL)];
        }

        private int filter(int sizeType, int size) {
            if (size == LayoutUtil.NOT_SET)
                return sizeType != LayoutUtil.MAX ? 0 : LayoutUtil.INF;
            return constrainSize(size);
        }

        private boolean isBaselineAlign(boolean defValue) {
            Float g = cc.getVertical().getGrow();
            if (g != null && g.intValue() != 0)
                return false;
            UnitValue al = cc.getVertical().getAlign();
            return (al != null ? al == UnitValue.BASELINE_IDENTITY : defValue) && comp.hasBaseline();
        }

        private int getBaseline(int sizeType) {
            return comp.getBaseline(getSize(sizeType, true), getSize(sizeType, false));
        }
    }

    private static final class FlowSizeSpec {
        private final int[][] sizes;  // [row/col index][min, pref, max]
        private final ResizeConstraint[] resConstsInclGaps;  // [row/col index]

        private FlowSizeSpec(int[][] sizes, ResizeConstraint[] resConstsInclGaps) {
            this.sizes = sizes;
            this.resConstsInclGaps = resConstsInclGaps;
        }

        /**
         * @param specs      The specs for the columns or rows. Last index will be used of <code>fromIx + len</code> is greater than this array's length.
         * @param targetSize The size to try to meet.
         * @param defGrow    The default grow weight if the specs does not have anyone that will grow. Comes from "push" in the CC.
         * @param eagerness  How eager the algorithm should be to try to expand the sizes.
         *                   <ul>
         *                   <li>0 - Grow only rows/columns which have the <code>sizeType</code> set to be the containing components AND which has a grow weight &gt; 0.
         *                   <li>1 - Grow only rows/columns which have the <code>sizeType</code> set to be the containing components AND which has a grow weight &gt; 0 OR unspecified.
         *                   <li>2 - Grow all rows/columns that have a grow weight &gt; 0.
         *                   <li>3 - Grow all rows/columns that have a grow weight &gt; 0 OR unspecified.
         *                   </ul>
         * @return The new size.
         */
        private int expandSizes(DimConstraint[] specs, Float[] defGrow, int targetSize, int fromIx, int len, int sizeType, int eagerness) {
            ResizeConstraint[] resConstr = new ResizeConstraint[len];
            int[][] sizesToExpand = new int[len][];
            for (int i = 0; i < len; i++) {
                int[] minPrefMax = sizes[i + fromIx];
                sizesToExpand[i] = new int[]{minPrefMax[sizeType], minPrefMax[LayoutUtil.PREF], minPrefMax[LayoutUtil.MAX]};
                if (eagerness <= 1 && i % 2 == 0) { // (i % 2 == 0) means only odd indexes, which is only rows/col indexes and not gaps.
                    int cIx = (i + fromIx - 1) >> 1;
                    DimConstraint spec = (DimConstraint) LayoutUtil.getIndexSafe(specs, cIx);
                    BoundSize sz = spec.getSize();
                    if ((sizeType == LayoutUtil.MIN && sz.getMin() != null && sz.getMin().getUnit() != UnitValue.MIN_SIZE) ||
                            (sizeType == LayoutUtil.PREF && sz.getPreferred() != null && sz.getPreferred().getUnit() != UnitValue.PREF_SIZE)) {
                        continue;
                    }
                }
                resConstr[i] = (ResizeConstraint) LayoutUtil.getIndexSafe(resConstsInclGaps, i + fromIx);
            }
            Float[] growW = (eagerness == 1 || eagerness == 3) ? extractSubArray(specs, defGrow, fromIx, len) : null;
            int[] newSizes = LayoutUtil.calculateSerial(sizesToExpand, resConstr, growW, LayoutUtil.PREF, targetSize);
            int newSize = 0;
            for (int i = 0; i < len; i++) {
                int s = newSizes[i];
                sizes[i + fromIx][sizeType] = s;
                newSize += s;
            }
            return newSize;
        }
    }
}
