package net.miginfocom.layout;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

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
 * A constraint that holds the column <b>or</b> row constraints for the grid. It also holds the gaps between the rows and columns.
 * <p>
 * This class is a holder and builder for a number of {@link net.miginfocom.layout.DimConstraint}s.
 * <p>
 * For a more thorough explanation of what these constraints do, and how to build the constraints, see the White Paper or Cheat Sheet at www.migcomponents.com.
 * <p>
 * Note that there are two way to build this constraint. Through String (e.g. <code>"[100]3[200,fill]"</code> or through API (E.g.
 * <code>new AxisConstraint().size("100").gap("3").size("200").fill()</code>.
 */
public final class AC implements Externalizable {
    private final ArrayList<DimConstraint> cList = new ArrayList<>(8);

    /**
     * Constructor. Creates an instance that can be configured manually. Will be initialized with a default
     * {@link net.miginfocom.layout.DimConstraint}.
     */
    AC() {
        cList.add(new DimConstraint());
    }

    /**
     * Property. The different {@link net.miginfocom.layout.DimConstraint}s that this object consists of.
     * These <code><DimConstraints/code> contains all information in this class.
     * <p>
     * Yes, we are embarrassingly aware that the method is misspelled.
     *
     * @return The different {@link net.miginfocom.layout.DimConstraint}s that this object consists of. A new list and
     * never <code>null</code>.
     */
    final DimConstraint[] getConstaints() {
        return cList.toArray(new DimConstraint[0]);
    }

    /**
     * Sets the different {@link net.miginfocom.layout.DimConstraint}s that this object should consists of.
     * <p>
     * Yes, we are embarrassingly aware that the method is misspelled.
     *
     * @param constr The different {@link net.miginfocom.layout.DimConstraint}s that this object consists of. The list
     *               will be copied for storage. <code>null</code> or and emty array will reset the constraints to one <code>DimConstraint</code>
     *               with default values.
     */
    final void setConstaints(DimConstraint[] constr) {
        if (constr == null || constr.length < 1)
            constr = new DimConstraint[]{new DimConstraint()};
        cList.clear();
        cList.ensureCapacity(constr.length);
        cList.addAll(Arrays.asList(constr));
    }

    /**
     * Returns the number of rows/columns that this constraints currently have.
     *
     * @return The number of rows/columns that this constraints currently have. At least 1.
     */
    public int getCount() {
        return cList.size();
    }

    /**
     * Specifies that the indicated rows'/columns' component should grow by default. It does not affect the size of the row/column.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param indexes The index(es) (0-based) of the columns/rows that should be affected by this constraint.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     */
    public final AC fill(int... indexes) {
        for (int i = indexes.length - 1; i >= 0; i--) {
            int ix = indexes[i];
            makeSize(ix);
            cList.get(ix).setFill(true);
        }
        return this;
    }

    /**
     * Specifies the indicated rows'/columns' min and/or preferred and/or max size. E.g. <code>"10px"</code> or <code>"50:100:200"</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param size    The minimum and/or preferred and/or maximum size of this row. The string will be interpreted
     *                as a <b>BoundSize</b>. For more info on how <b>BoundSize</b> is formatted see the documentation.
     * @param indexes The index(es) (0-based) of the columns/rows that should be affected by this constraint.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     */
    public final AC size(String size, int... indexes) {
        BoundSize bs = ConstraintParser.parseBoundSize(size, false, true);
        for (int i = indexes.length - 1; i >= 0; i--) {
            int ix = indexes[i];
            makeSize(ix);
            cList.get(ix).setSize(bs);
        }
        return this;
    }

    /**
     * Specifies the indicated rows'/columns' gap size to <code>size</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param size    minimum and/or preferred and/or maximum size of the gap between this and the next row/column.
     *                The string will be interpreted as a <b>BoundSize</b>. For more info on how <b>BoundSize</b> is formatted see the documentation.
     * @param indexes The index(es) (0-based) of the columns/rows that should be affected by this constraint.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     */
    public final AC gap(String size, int... indexes) {
        BoundSize bsa = size != null ? ConstraintParser.parseBoundSize(size, true, true) : null;
        for (int i = indexes.length - 1; i >= 0; i--) {
            int ix = indexes[i];
            makeSize(ix);
            if (bsa != null)
                cList.get(ix).setGapAfter(bsa);
        }
        return this;
    }

    /**
     * Specifies the indicated rows'/columns' columns default alignment <b>for its components</b>. It does not affect the positioning
     * or size of the columns/row itself. For columns it is the horizonal alignment (e.g. "left") and for rows it is the vertical
     * alignment (e.g. "top").
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param side    The default side to align the components. E.g. "top" or "left", or "before" or "after" or "bottom" or "right".
     * @param indexes The index(es) (0-based) of the columns/rows that should be affected by this constraint.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     */
    public final AC align(String side, int... indexes) {
        UnitValue al = ConstraintParser.parseAlignKeywords(side, true);
        if (al == null)
            al = ConstraintParser.parseAlignKeywords(side, false);
        for (int i = indexes.length - 1; i >= 0; i--) {
            int ix = indexes[i];
            makeSize(ix);
            cList.get(ix).setAlign(al);
        }
        return this;
    }

    /**
     * Specifies the indicated rows'/columns' grow weight within columns/rows with the same <code>grow priority</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the white paper or cheat Sheet at www.migcomponents.com.
     *
     * @param w       The new grow weight.
     * @param indexes The index(es) (0-based) of the columns/rows that should be affected by this constraint.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     */
    public final AC grow(float w, int... indexes) {
        Float gw = w;
        for (int i = indexes.length - 1; i >= 0; i--) {
            int ix = indexes[i];
            makeSize(ix);
            cList.get(ix).setGrow(gw);
        }
        return this;
    }

    /**
     * Specifies that the current row/column's shrink weight withing the columns/rows with the same <code>shrink priority</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the White Paper or Cheat Sheet at www.migcomponents.com.
     *
     * @param w The shrink weight.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     * @since 3.7.2
     */
    private AC shrink(float w) {
        int curIx = 0;
        return shrink(w, curIx);
    }

    /**
     * Specifies the indicated rows'/columns' shrink weight withing the columns/rows with the same <code>shrink priority</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the White Paper or Cheat Sheet at www.migcomponents.com.
     *
     * @param w       The shrink weight.
     * @param indexes The index(es) (0-based) of the columns/rows that should be affected by this constraint.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     * @since 3.7.2
     */
    private AC shrink(float w, int... indexes) {
        Float sw = w;
        for (int i = indexes.length - 1; i >= 0; i--) {
            int ix = indexes[i];
            makeSize(ix);
            cList.get(ix).setShrink(sw);
        }
        return this;
    }

    /**
     * Specifies that the current row/column's shrink weight withing the columns/rows with the same <code>shrink priority</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the White Paper or Cheat Sheet at www.migcomponents.com.
     *
     * @param w The shrink weight.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     * @deprecated in 3.7.2. Use {@link #shrink(float)} instead.
     */
    @Deprecated
    public final AC shrinkWeight(float w) {
        return shrink(w);
    }

    /**
     * Specifies the indicated rows'/columns' shrink weight withing the columns/rows with the same <code>shrink priority</code>.
     * <p>
     * For a more thorough explanation of what this constraint does see the White Paper or Cheat Sheet at www.migcomponents.com.
     *
     * @param w       The shrink weight.
     * @param indexes The index(es) (0-based) of the columns/rows that should be affected by this constraint.
     * @return <code>this</code> so it is possible to chain calls. E.g. <code>new AxisConstraint().noGrid().gap().fill()</code>.
     * @deprecated in 3.7.2. Use {@link #shrink(float, int...)} instead.
     */
    @Deprecated
    public final AC shrinkWeight(float w, int... indexes) {
        return shrink(w, indexes);
    }

    private void makeSize(int sz) {
        if (cList.size() <= sz) {
            cList.ensureCapacity(sz);
            for (int i = cList.size(); i <= sz; i++)
                cList.add(new DimConstraint());
        }
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
        if (getClass() == AC.class)
            LayoutUtil.writeAsXML(out, this);
    }
}