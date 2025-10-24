package net.miginfocom.layout;

import java.io.*;
import java.util.ArrayList;
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
 * A simple value holder for one component's constraint.
 */
public final class CC implements Externalizable {
    private static final BoundSize DEF_GAP = BoundSize.NULL_SIZE;    // Only used to denote default wrap/newline gap.
    // ***** Tmp cache field
    private static final String[] EMPTY_ARR = new String[0];
    // See the getters and setters for information about the properties below.
    private int dock = -1;
    private UnitValue[] pos = null; // [x1, y1, x2, y2]
    private UnitValue[] padding = null;   // top, left, bottom, right
    private Boolean flowX = null;
    private int skip = 0;
    private int split = 1;
    private int spanX = 1, spanY = 1;
    private int cellX = -1, cellY = 0; // If cellX is -1 then cellY is also considered -1. cellY is never negative.
    private String tag = null;
    private String id = null;
    private int hideMode = -1;
    private final DimConstraint hor = new DimConstraint();
    private final DimConstraint ver = new DimConstraint();
    private BoundSize newline = null;
    private BoundSize wrap = null;
    private boolean boundsInGrid = true;
    private boolean external = false;
    private Float pushX = null, pushY = null;
    private transient String[] linkTargets = null;

    String[] getLinkTargets() {
        if (linkTargets == null) {
            final ArrayList<String> targets = new ArrayList<>(2);
            if (pos != null) {
                for (UnitValue po : pos) addLinkTargetIDs(targets, po);
            }
            linkTargets = targets.size() == 0 ? EMPTY_ARR : targets.toArray(new String[0]);
        }
        return linkTargets;
    }

    private void addLinkTargetIDs(ArrayList<String> targets, UnitValue uv) {
        if (uv != null) {
            String linkId = uv.getLinkTargetId();
            if (linkId != null) {
                targets.add(linkId);
            } else {
                for (int i = uv.getSubUnitCount() - 1; i >= 0; i--) {
                    UnitValue subUv = uv.getSubUnitValue(i);
                    if (subUv.isLinkedDeep())
                        addLinkTargetIDs(targets, subUv);
                }
            }
        }
    }
    // **********************************************************
    // Bean properties
    // **********************************************************

    /**
     * Returns the horizontal dimension constraint for this component constraint. It has constraints for the horizontal size
     * and grow/shink priorities and weights.
     * <p>
     * Note! If any changes is to be made it must be made direct when the object is returned. It is not allowed to save the
     * constraint for later use.
     *
     * @return The current dimension constraint. Never <code>null</code>.
     */
    public DimConstraint getHorizontal() {
        return hor;
    }

    /**
     * Returns the vertical dimension constraint for this component constraint. It has constraints for the vertical size
     * and grow/shrink priorities and weights.
     * <p>
     * Note! If any changes is to be made it must be made direct when the object is returned. It is not allowed to save the
     * constraint for later use.
     *
     * @return The current dimension constraint. Never <code>null</code>.
     */
    public DimConstraint getVertical() {
        return ver;
    }

    /**
     * Returns the vertical or horizontal dim constraint.
     * <p>
     * Note! If any changes is to be made it must be made direct when the object is returned. It is not allowed to save the
     * constraint for later use.
     *
     * @param isHor If the horizontal constraint should be returned.
     * @return The dim constraint. Never <code>null</code>.
     */
    DimConstraint getDimConstraint(boolean isHor) {
        return isHor ? hor : ver;
    }

    /**
     * Returns the absolute positioning of one or more of the edges. This will be applied last in the layout cycle and will not
     * affect the flow or grid positions. The positioning is relative to the parent and can not (as padding) be used
     * to adjust the edges relative to the old value. May be <code>null</code> and elements may be <code>null</code>.
     * <code>null</code> value(s) for the x2 and y2 will be interpreted as to keep the preferred size and thus the x1
     * and x2 will just absolutely positions the component.
     * <p>
     * Note that {@link #setBoundsInGrid(boolean)} changes the interpretation of thisproperty slightly.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value as a new array, free to modify.
     */
    public UnitValue[] getPos() {
        return pos != null ? new UnitValue[]{pos[0], pos[1], pos[2], pos[3]} : null;
    }

    /**
     * Sets absolute positioning of one or more of the edges. This will be applied last in the layout cycle and will not
     * affect the flow or grid positions. The positioning is relative to the parent and can not (as padding) be used
     * to adjust the edges relative to the old value. May be <code>null</code> and elements may be <code>null</code>.
     * <code>null</code> value(s) for the x2 and y2 will be interpreted as to keep the preferred size and thus the x1
     * and x2 will just absolutely positions the component.
     * <p>
     * Note that {@link #setBoundsInGrid(boolean)} changes the interpretation of thisproperty slightly.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param pos <code>UnitValue[] {x, y, x2, y2}</code>. Must be <code>null</code> or of length 4. Elements can be <code>null</code>.
     */
    public void setPos(UnitValue[] pos) {
        this.pos = pos != null ? new UnitValue[]{pos[0], pos[1], pos[2], pos[3]} : null;
        linkTargets = null;
    }

    /**
     * Returns if the absolute <code>pos</code> value should be corrections to the component that is in a normal cell. If <code>false</code>
     * the value of <code>pos</code> is truly absolute in that it will not affect the grid or have a default bounds in the grid.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     * @see #getPos()
     */
    boolean isBoundsInGrid() {
        return boundsInGrid;
    }

    /**
     * Sets if the absolute <code>pos</code> value should be corrections to the component that is in a normal cell. If <code>false</code>
     * the value of <code>pos</code> is truly absolute in that it will not affect the grid or have a default bounds in the grid.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param b <code>true</code> for bounds taken from the grid position. <code>false</code> is default.
     * @see #setPos(UnitValue[])
     */
    void setBoundsInGrid(boolean b) {
        this.boundsInGrid = b;
    }

    /**
     * Returns the absolute cell position in the grid or <code>-1</code> if cell positioning is not used.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    int getCellX() {
        return cellX;
    }

    /**
     * Set an absolute cell x-position in the grid. If &gt;= 0 this point points to the absolute cell that this constaint's component should occupy.
     * If there's already a component in that cell they will split the cell. The flow will then continue after this cell.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param x The x-position or <code>-1</code> to disable cell positioning.
     */
    void setCellX(int x) {
        cellX = x;
    }

    /**
     * Returns the absolute cell position in the grid or <code>-1</code> if cell positioning is not used.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    int getCellY() {
        return cellX < 0 ? -1 : cellY;
    }

    /**
     * Set an absolute cell x-position in the grid. If &gt;= 0 this point points to the absolute cell that this constaint's component should occupy.
     * If there's already a component in that cell they will split the cell. The flow will then continue after this cell.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param y The y-position or <code>-1</code> to disable cell positioning.
     */
    void setCellY(int y) {
        if (y < 0)
            cellX = -1;
        cellY = Math.max(y, 0);
    }

    /**
     * Sets the docking side. -1 means no docking.<br>
     * Valid sides are: <code> north = 0, west = 1, south = 2, east = 3</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current side.
     */
    int getDockSide() {
        return dock;
    }

    /**
     * Sets the docking side. -1 means no docking.<br>
     * Valid sides are: <code> north = 0, west = 1, south = 2, east = 3</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param side -1 or 0-3.
     */
    void setDockSide(int side) {
        if (side < -1 || side > 3)
            throw new IllegalArgumentException("Illegal dock side: " + side);
        dock = side;
    }

    /**
     * Returns if this component should have its bounds handled by an external source and not this layout manager.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    public boolean isExternal() {
        return external;
    }

    /**
     * If this boolean is true this component is not handled in any way by the layout manager and the component can have its bounds set by an external
     * handler which is normally by the use of some <code>component.setBounds(x, y, width, height)</code> directly (for Swing).
     * <p>
     * The bounds <b>will not</b> affect the minimum and preferred size of the container.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param b <code>true</code> means that the bounds are not changed.
     */
    public void setExternal(boolean b) {
        this.external = b;
    }

    /**
     * Returns if the flow in the <b>cell</b> is in the horizontal dimension. Vertical if <code>false</code>. Only the first
     * component is a cell can set the flow.
     * <p>
     * If <code>null</code> the flow direction is inherited by from the {@link net.miginfocom.layout.LC}.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    Boolean getFlowX() {
        return flowX;
    }

    /**
     * Sets if the flow in the <b>cell</b> is in the horizontal dimension. Vertical if <code>false</code>. Only the first
     * component is a cell can set the flow.
     * <p>
     * If <code>null</code> the flow direction is inherited by from the {@link net.miginfocom.layout.LC}.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param b <code>Boolean.TRUE</code> means horizontal flow in the cell.
     */
    void setFlowX(Boolean b) {
        this.flowX = b;
    }

    /**
     * Sets how a component that is hidden (not visible) should be treated by default.
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The mode:<br>
     * 0 == Normal. Bounds will be calculated as if the component was visible.<br>
     * 1 == If hidden the size will be 0, 0 but the gaps remain.<br>
     * 2 == If hidden the size will be 0, 0 and gaps set to zero.<br>
     * 3 == If hidden the component will be disregarded completely and not take up a cell in the grid..
     */
    int getHideMode() {
        return hideMode;
    }

    /**
     * Sets how a component that is hidden (not visible) should be treated by default.
     *
     * @param mode The mode:<br>
     *             0 == Normal. Bounds will be calculated as if the component was visible.<br>
     *             1 == If hidden the size will be 0, 0 but the gaps remain.<br>
     *             2 == If hidden the size will be 0, 0 and gaps set to zero.<br>
     *             3 == If hidden the component will be disregarded completely and not take up a cell in the grid..
     */
    void setHideMode(int mode) {
        if (mode < -1 || mode > 3)
            throw new IllegalArgumentException("Wrong hideMode: " + mode);
        hideMode = mode;
    }

    /**
     * Returns the id used to reference this component in some constraints.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The id or <code>null</code>. May consist of a groupID and an componentID which are separated by a dot: ".". E.g. "grp1.id1".
     * The dot should never be first or last if present.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id used to reference this component in some constraints.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param id The id or <code>null</code>. May consist of a groupID and an componentID which are separated by a dot: ".". E.g. "grp1.id1".
     *           The dot should never be first or last if present.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the absolute resizing in the last stage of the layout cycle. May be <code>null</code> and elements may be <code>null</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value. <code>null</code> or of length 4.
     */
    public UnitValue[] getPadding() {
        return padding != null ? new UnitValue[]{padding[0], padding[1], padding[2], padding[3]} : null;
    }

    /**
     * Sets the absolute resizing in the last stage of the layout cycle. These values are added to the edges and can thus for
     * instance be used to grow or reduce the size or move the component an absolute number of pixels. May be <code>null</code>
     * and elements may be <code>null</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param sides top, left, bottom right. Must be <code>null</code> or of length 4.
     */
    public void setPadding(UnitValue[] sides) {
        this.padding = sides != null ? new UnitValue[]{sides[0], sides[1], sides[2], sides[3]} : null;
    }

    /**
     * Returns how many cells in the grid that should be skipped <b>before</b> the component that this constraint belongs to.
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value. 0 if no skip.
     */
    public int getSkip() {
        return skip;
    }

    /**
     * Sets how many cells in the grid that should be skipped <b>before</b> the component that this constraint belongs to.
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param cells How many cells in the grid that should be skipped <b>before</b> the component that this constraint belongs to
     */
    public void setSkip(int cells) {
        this.skip = cells;
    }

    /**
     * Returns the number of cells the cell that this constraint's component will span in the indicated dimension. <code>1</code> is default and
     * means that it only spans the current cell. <code>LayoutUtil.INF</code> is used to indicate a span to the end of the column/row.
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    int getSpanX() {
        return spanX;
    }

    /**
     * Sets the number of cells the cell that this constraint's component will span in the indicated dimension. <code>1</code> is default and
     * means that it only spans the current cell. <code>LayoutUtil.INF</code> is used to indicate a span to the end of the column/row.
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param cells The number of cells to span (i.e. merge).
     */
    void setSpanX(int cells) {
        this.spanX = cells;
    }

    /**
     * Returns the number of cells the cell that this constraint's component will span in the indicated dimension. <code>1</code> is default and
     * means that it only spans the current cell. <code>LayoutUtil.INF</code> is used to indicate a span to the end of the column/row.
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    int getSpanY() {
        return spanY;
    }

    /**
     * Sets the number of cells the cell that this constraint's component will span in the indicated dimension. <code>1</code> is default and
     * means that it only spans the current cell. <code>LayoutUtil.INF</code> is used to indicate a span to the end of the column/row.
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param cells The number of cells to span (i.e. merge).
     */
    void setSpanY(int cells) {
        this.spanY = cells;
    }

    /**
     * "pushx" indicates that the column that this component is in (this first if the component spans) should default to growing.
     * If any other column has been set to grow this push value on the component does nothing as the column's explicit grow weight
     * will take precedence. Push is normally used when the grid has not been defined in the layout.
     * <p>
     * If multiple components in a column has push weights set the largest one will be used for the column.
     *
     * @return The current push value. Default is <code>null</code>.
     */
    Float getPushX() {
        return pushX;
    }

    /**
     * "pushx" indicates that the column that this component is in (this first if the component spans) should default to growing.
     * If any other column has been set to grow this push value on the component does nothing as the column's explicit grow weight
     * will take precedence. Push is normally used when the grid has not been defined in the layout.
     * <p>
     * If multiple components in a column has push weights set the largest one will be used for the column.
     *
     * @param weight The new push value. Default is <code>null</code>.
     */
    void setPushX(Float weight) {
        this.pushX = weight;
    }

    /**
     * "pushx" indicates that the row that this component is in (this first if the component spans) should default to growing.
     * If any other row has been set to grow this push value on the component does nothing as the row's explicit grow weight
     * will take precedence. Push is normally used when the grid has not been defined in the layout.
     * <p>
     * If multiple components in a row has push weights set the largest one will be used for the row.
     *
     * @return The current push value. Default is <code>null</code>.
     */
    Float getPushY() {
        return pushY;
    }

    /**
     * "pushx" indicates that the row that this component is in (this first if the component spans) should default to growing.
     * If any other row has been set to grow this push value on the component does nothing as the row's explicit grow weight
     * will take precedence. Push is normally used when the grid has not been defined in the layout.
     * <p>
     * If multiple components in a row has push weights set the largest one will be used for the row.
     *
     * @param weight The new push value. Default is <code>null</code>.
     */
    void setPushY(Float weight) {
        this.pushY = weight;
    }

    /**
     * Returns in how many parts the current cell (that this constraint's component will be in) should be split in. If for instance
     * it is split in two, the next component will also share the same cell. Note that the cell can also span a number of
     * cells, which means that you can for instance span three cells and split that big cell for two components. Split can be
     * set to a very high value to make all components in the same row/column share the same cell (e.g. <code>LayoutUtil.INF</code>).
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    public int getSplit() {
        return split;
    }

    /**
     * Sets in how many parts the current cell (that this constraint's component will be in) should be split in. If for instance
     * it is split in two, the next component will also share the same cell. Note that the cell can also span a number of
     * cells, which means that you can for instance span three cells and split that big cell for two components. Split can be
     * set to a very high value to make all components in the same row/column share the same cell (e.g. <code>LayoutUtil.INF</code>).
     * <p>
     * Note that only the first component will be checked for this property.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param parts The number of parts (i.e. component slots) the cell should be divided into.
     */
    public void setSplit(int parts) {
        this.split = parts;
    }

    /**
     * Tags the component with metadata. Currently only used to tag buttons with for instance "cancel" or "ok" to make them
     * show up in the correct order depending on platform. See {@link PlatformDefaults#setButtonOrder(String)} for information.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value. May be <code>null</code>.
     */
    public String getTag() {
        return tag;
    }

    /**
     * Optinal tag that gives more context to this constraint's component. It is for instance used to tag buttons in a
     * button bar with the button type such as "ok", "help" or "cancel".
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param tag The new tag. May be <code>null</code>.
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Returns if the flow should wrap to the next line/column <b>after</b> the component that this constraint belongs to.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    public boolean isWrap() {
        return wrap != null;
    }

    /**
     * Sets if the flow should wrap to the next line/column <b>after</b> the component that this constraint belongs to.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param b <code>true</code> means wrap after.
     */
    public void setWrap(boolean b) {
        wrap = b ? (wrap == null ? DEF_GAP : wrap) : null;
    }

    /**
     * Returns the wrap size if it is a custom size. If wrap was set to true with {@link #setWrap(boolean)} then this method will
     * return <code>null</code> since that means that the gap size should be the default one as defined in the rows spec.
     *
     * @return The custom gap size. NOTE! Will return <code>null</code> for both no wrap <b>and</b> default wrap.
     * @see #isWrap()
     * @see #setWrap(boolean)
     * @since 2.4.2
     */
    BoundSize getWrapGapSize() {
        return wrap == DEF_GAP ? null : wrap;
    }

    /**
     * Set the wrap size and turns wrap on if <code>!= null</code>.
     *
     * @param s The custom gap size. NOTE! <code>null</code> will not turn on or off wrap, it will only set the wrap gap size to "default".
     *          A non-null value will turn on wrap though.
     * @see #isWrap()
     * @see #setWrap(boolean)
     * @since 2.4.2
     */
    void setWrapGapSize(BoundSize s) {
        wrap = s == null ? (wrap != null ? DEF_GAP : null) : s;
    }

    /**
     * Returns if the flow should wrap to the next line/column <b>before</b> the component that this constraint belongs to.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @return The current value.
     */
    boolean isNewline() {
        return newline != null;
    }

    /**
     * Sets if the flow should wrap to the next line/column <b>before</b> the component that this constraint belongs to.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param b <code>true</code> means wrap before.
     */
    void setNewline(boolean b) {
        newline = b ? (newline == null ? DEF_GAP : newline) : null;
    }

    /**
     * Returns the newline size if it is a custom size. If newline was set to true with {@link #setNewline(boolean)} then this method will
     * return <code>null</code> since that means that the gap size should be the default one as defined in the rows spec.
     *
     * @return The custom gap size. NOTE! Will return <code>null</code> for both no newline <b>and</b> default newline.
     * @see #isNewline()
     * @see #setNewline(boolean)
     * @since 2.4.2
     */
    BoundSize getNewlineGapSize() {
        return newline == DEF_GAP ? null : newline;
    }

    /**
     * Set the newline size and turns newline on if <code>!= null</code>.
     *
     * @param s The custom gap size. NOTE! <code>null</code> will not turn on or off newline, it will only set the newline gap size to "default".
     *          A non-null value will turn on newline though.
     * @see #isNewline()
     * @see #setNewline(boolean)
     * @since 2.4.2
     */
    void setNewlineGapSize(BoundSize s) {
        newline = s == null ? (newline != null ? DEF_GAP : null) : s;
    }
    // ************************************************
    // Persistence Delegate and Serializable combined.
    // ************************************************

    private Object readResolve() {
        return LayoutUtil.getSerializedObject(this);
    }

    public void readExternal(ObjectInput in) throws IOException {
        LayoutUtil.setSerializedObject(this, LayoutUtil.readAsXML(in));
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        if (getClass() == CC.class)
            LayoutUtil.writeAsXML(out, this);
    }
}