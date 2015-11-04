package net.miginfocom.layout;

import java.util.HashMap;
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

/** This class contains static methods to be used by IDE vendors to convert to and from String/API constraints.
 * <p>
 * <b>Note that {@link LayoutUtil#setDesignTime(ContainerWrapper, boolean)} should be set to <code>true</code> for this class'
 * methods to work.</b>
 */
public class IDEUtil
{
	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue ZERO = UnitValue.ZERO;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue TOP = UnitValue.TOP;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue LEADING = UnitValue.LEADING;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue LEFT = UnitValue.LEFT;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue CENTER = UnitValue.CENTER;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue TRAILING = UnitValue.TRAILING;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue RIGHT = UnitValue.RIGHT;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue BOTTOM = UnitValue.BOTTOM;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue LABEL = UnitValue.LABEL;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue INF = UnitValue.INF;

	/** A direct reference to the corresponding value for predefined UnitValues in {@link UnitValue}.
	 */
	public static final UnitValue BASELINE_IDENTITY = UnitValue.BASELINE_IDENTITY;

	private final static String[] X_Y_STRINGS = new String[] {"x", "y", "x2", "y2"};

	/** Returns the version of IDEUtil
	 * @return The version.
	 */
	public String getIDEUtilVersion()
	{
		return "1.0";
	}

	/** Returns the grid cells that the components in <code>parentContainer</code> has.
	 * @param parentContainer The parent container. It is an object since MigLayout is GUI toolkit
	 * independent.
	 * @return A new hashmap with the components mapped to an array [x, y, spanx, spany].
	 * <p>
	 * Dock components will always have x and y less than -30000 or more than 30000. This is since they
	 * are actually part of the grid, but on the outer edges.
	 * <p>
	 * Components that span the "rest of the row/column" have really large span values. Actually 30000-x or
	 * 30000-y.
	 * <p>
	 * Generally, the grid does not need to have the upper left at 0, 0. Though it normally does if you
	 * don't set the cells explicitly to other values. Rows and columns that are completely empty and
	 * that does not have an explicit row/column constraint will be totally disregarded.
	 */
	public static HashMap<Object, int[]> getGridPositions(Object parentContainer)
	{
		return Grid.getGridPositions(parentContainer);
	}

	/** Returns the sizes of the rows and gaps for a container.
	 * There will be two arrays returned [0] and [1].
	 * <p>
	 * The first array will be the indexes of the rows where indexes that
	 * are less than 30000 or larger than 30000 is docking rows. There might be extra docking rows that aren't
	 * visible but they always have size 0. Non docking indexes will probably always be 0, 1, 2, 3, etc..
	 * <p>
	 * The second array is the sizes of the form:<br>
	 * <code>[left inset][row size 1][gap 1][row size 2][gap 2][row size n][right inset]</code>.
	 * <p>
	 * The returned sizes will be the ones calculated in the last layout cycle.
	 * @param parentContainer The container to retuern the row sizes and gaps for. In Swing it will be a {@link java.awt.Container} and
	 * in SWT it will be a {@link org.eclipse.swt.widgets.Composite}.
	 * @return The sizes or <code>null</code> if {@link LayoutUtil#isDesignTime(ContainerWrapper)} is <code>false</code> or
	 * <code>parentContainer</code> does not have a MigLayout layout manager.
	 * The returned sizes will be the ones calculated in the last layout cycle.
	 * @see LayoutUtil#isDesignTime(ContainerWrapper)
	 */
	public static int[][] getRowSizes(Object parentContainer)
	{
		return Grid.getSizesAndIndexes(parentContainer, true);
	}

	/** Returns the sizes of the columns and gaps for a container.
	 * There will be two arrays returned [0] and [1].
	 * <p>
	 * The first array will be the indexes of the columns where indexes that
	 * are less than 30000 or larger than 30000 is docking columns. There might be extra docking columns that aren't
	 * visible but they always have size 0. Non docking indexes will probably always be 0, 1, 2, 3, etc..
	 * <p>
	 * The second array is the sizes of the form:<br>
	 * <code>[top inset][column size 1][gap 1][column size 2][gap 2][column size n][bottom inset]</code>.
	 * <p>
	 * The returned sizes will be the ones calculated in the last layout cycle.
	 * @param parentContainer The container to retuern the column sizes and gaps for. In Swing it will be a {@link java.awt.Container} and
	 * in SWT it will be a {@link org.eclipse.swt.widgets.Composite}.
	 * @return The sizes and indexes or <code>null</code> if {@link LayoutUtil#isDesignTime(ContainerWrapper)} is <code>false</code> or
	 * <code>parentContainer</code> does not have a MigLayout layout manager.
	 * The returned sizes will be the ones calculated in the last layout cycle.
	 * @see LayoutUtil#isDesignTime(ContainerWrapper)
	 */
	public static int[][] getColumnSizes(Object parentContainer)
	{
		return Grid.getSizesAndIndexes(parentContainer, false);
	}

	/** Returns the a constraint string that can be re-parsed to be the exact same AxisConstraint.
	 * @param ac The axis constraint to return as a constraint string.
	 * @param asAPI If the returned string should be of API type (e.g. .flowX().gap("rel").align("right")) or
	 * as a String type (e.g. "flowx, gap rel, right").
	 * @param isCols The the constraint should be returned for columns rather than rows.
	 * @return A String. Never <code>null</code>.
	 */
	public static String getConstraintString(AC ac, boolean asAPI, boolean isCols)
	{
		StringBuffer sb = new StringBuffer(32);

		DimConstraint[] dims = ac.getConstaints();
		BoundSize defGap = isCols ? PlatformDefaults.getGridGapX() : PlatformDefaults.getGridGapY();

		for (int i = 0; i < dims.length; i++) {
			DimConstraint dc = dims[i];

			addRowDimConstraintString(dc, sb, asAPI);

			if (i < dims.length - 1) {
				BoundSize gap = dc.getGapAfter();

				if (gap == defGap || gap == null)
					gap = dims[i + 1].getGapBefore();

				if (gap != null) {
					String gapStr = getBS(gap);
					if (asAPI) {
						sb.append(".gap(\"").append(gapStr).append("\")");
					} else {
						sb.append(gapStr);
					}
				} else {
					if (asAPI)
						sb.append(".gap()");
				}
			}
		}

		return sb.toString();
	}

	/** Adds the a constraint string that can be re-parsed to be the exact same DimConstraint.
	 * @param dc The layout constraint to return as a constraint string.
	 * @param asAPI If the returned string should be of API type (e.g. .flowX().gap("rel").align("right")) or
	 * as a String type (e.g. "flowx, gap rel, right").
	 */
	private static void addRowDimConstraintString(DimConstraint dc, StringBuffer sb, boolean asAPI)
	{
		int gp = dc.getGrowPriority();

		int firstComma = sb.length();

		BoundSize size = dc.getSize();
		if (size.isUnset() == false) {
			if (asAPI) {
				sb.append(".size(\"").append(getBS(size)).append("\")");
			} else {
				sb.append(',').append(getBS(size));
			}
		}

		if (gp != 100) {
			if (asAPI) {
				sb.append(".growPrio(").append(gp).append("\")");
			} else {
				sb.append(",growprio ").append(gp);
			}
		}

		Float gw = dc.getGrow();
		if (gw != null) {
			String g = gw != 100f ? floatToString(gw, asAPI) : "";
			if (asAPI) {
				if (g.length() == 0) {
					sb.append(".grow()");
				} else {
					sb.append(".grow(\"").append(g).append("\")");
				}
			} else {
				sb.append(",grow").append(g.length() > 0 ? (" " + g) : "");
			}
		}

		int sp = dc.getShrinkPriority();
		if (sp != 100) {
			if (asAPI) {
				sb.append(".shrinkPrio(").append(sp).append("\")");
			} else {
				sb.append(",shrinkprio ").append(sp);
			}
		}

		Float sw = dc.getShrink();
		if (sw != null && sw.intValue() != 100) {
			String s = floatToString(sw, asAPI);
			if (asAPI) {
				sb.append(".shrink(\"").append(s).append("\")");
			} else {
				sb.append(",shrink ").append(s);
			}
		}

		String eg = dc.getEndGroup();
		if (eg != null) {
			if (asAPI) {
				sb.append(".endGroup(\"").append(eg).append("\")");
			} else {
				sb.append(",endgroup ").append(eg);
			}
		}

		String sg = dc.getSizeGroup();
		if (sg != null) {
			if (asAPI) {
				sb.append(".sizeGroup(\"").append(sg).append("\")");
			} else {
				sb.append(",sizegroup ").append(sg);
			}
		}

		UnitValue al = dc.getAlign();
		if (al != null) {
			if (asAPI) {
				sb.append(".align(\"").append(getUV(al)).append("\")");
			} else {
				String s = getUV(al);
				String alKw = (s.equals("top") || s.equals("bottom") || s.equals("left") || s.equals("label") ||
							   s.equals("leading") || s.equals("center") || s.equals("trailing") ||
							   s.equals("right") || s.equals("baseline")) ? "" : "align ";
				sb.append(',').append(alKw).append(s);
			}
		}

		if (dc.isNoGrid()) {
			if (asAPI) {
				sb.append(".noGrid()");
			} else {
				sb.append(",nogrid");
			}
		}

		if (dc.isFill()) {
			if (asAPI) {
				sb.append(".fill()");
			} else {
				sb.append(",fill");
			}
		}

		if (asAPI == false) {
			if (sb.length() > firstComma) {
				sb.setCharAt(firstComma, '[');
				sb.append(']');
			} else {
				sb.append("[]");
			}
		}
	}

	/** Returns the a constraint string that can be re-parsed to be the exact same DimConstraint.
	 * @param dc The layout constraint to return as a constraint string.
	 * @param asAPI If the returned string should be of API type (e.g. .flowX().gap("rel").align("right")) or
	 * as a String type (e.g. "flowx, gap rel, right").
	 * @param isHor The the DimConstraint is decoration something horizontal (column or x).
	 * @param noGrowAdd If <code>true</code> no grow constraints will be added.
	 * @return A constraint string. Never <code>null</code>.
	 */
	private static void addComponentDimConstraintString(DimConstraint dc, StringBuffer sb, boolean asAPI, boolean isHor, boolean noGrowAdd)
	{
		int gp = dc.getGrowPriority();
		if (gp != 100) {
			if (asAPI) {
				sb.append(isHor ? ".growPrioX(" : ".growPrioY(").append(gp).append(')');
			} else {
				sb.append(isHor ? ",growpriox " : ",growprioy ").append(gp);
			}
		}

		if (noGrowAdd == false) {
			Float gw = dc.getGrow();
			if (gw != null) {
				String g = gw != 100f ? floatToString(gw, asAPI) : "";
				if (asAPI) {
					sb.append(isHor ? ".growX(" : ".growY(").append(g).append(')');
				} else {
					sb.append(isHor ? ",growx" : ",growy").append(g.length() > 0 ? (" " + g) : "");
				}
			}
		}

		int sp = dc.getShrinkPriority();
		if (sp != 100) {
			if (asAPI) {
				sb.append(isHor ? ".shrinkPrioX(" : ".shrinkPrioY(").append(sp).append(')');
			} else {
				sb.append(isHor ? ",shrinkpriox " : ",shrinkprioy ").append(sp);
			}
		}

		Float sw = dc.getShrink();
		if (sw != null && sw.intValue() != 100) {
			String s = floatToString(sw, asAPI);
			if (asAPI) {
				sb.append(isHor ? ".shrinkX(" : ".shrinkY(").append(s).append(')');
			} else {
				sb.append(isHor ? ",shrinkx " : ",shrinky ").append(s);
			}
		}

		String eg = dc.getEndGroup();
		if (eg != null) {
			if (asAPI) {
				sb.append(isHor ? ".endGroupX(\"" : ".endGroupY(\"").append(eg).append("\")");
			} else {
				sb.append(isHor ? ",endgroupx " : ",endgroupy ").append(eg);
			}
		}

		String sg = dc.getSizeGroup();
		if (sg != null) {
			if (asAPI) {
				sb.append(isHor ? ".sizeGroupX(\"" : ".sizeGroupY(\"").append(sg).append("\")");
			} else {
				sb.append(isHor ? ",sizegroupx " : ",sizegroupy ").append(sg);
			}
		}

		appendBoundSize(dc.getSize(), sb, isHor, asAPI);

		UnitValue al = dc.getAlign();
		if (al != null) {
			if (asAPI) {
				sb.append(isHor ? ".alignX(\"" : ".alignY(\"").append(getUV(al)).append("\")");
			} else {
				sb.append(isHor ? ",alignx " : ",aligny ").append(getUV(al));
			}
		}

		BoundSize gapBef = dc.getGapBefore();
		BoundSize gapAft= dc.getGapAfter();
		if (gapBef != null || gapAft != null) {
			if (asAPI) {
				sb.append(isHor ? ".gapX(\"" : ".gapY(\"").append(getBS(gapBef)).append("\", \"").append(getBS(gapAft)).append("\")");
			} else {
				sb.append(isHor ? ",gapx " : ",gapy ").append(getBS(gapBef));
				if (gapAft != null)
					sb.append(' ').append(getBS(gapAft));
			}
		}
	}

	private static void appendBoundSize(BoundSize size, StringBuffer sb, boolean isHor, boolean asAPI)
	{
		if (size.isUnset() == false) {
			if (size.getPreferred() == null) {
				if (size.getMin() == null) {
					if (asAPI) {
						sb.append(isHor ? ".maxWidth(\"" : ".maxHeight(\"").append(getUV(size.getMax())).append("\")");
					} else {
						sb.append(isHor ? ",wmax " : ",hmax ").append(getUV(size.getMax()));
					}

				} else if (size.getMax() == null) {
					if (asAPI) {
						sb.append(isHor ? ".minWidth(\"" : ".minHeight(\"").append(getUV(size.getMin())).append("\")");
					} else {
						sb.append(isHor ? ",wmin " : ",hmin ").append(getUV(size.getMin()));
					}
				} else { // None are null
					if (asAPI) {
						sb.append(isHor ? ".width(\"" : ".height(\"").append(getUV(size.getMin())).append("::").append(getUV(size.getMax())).append("\")");
					} else {
						sb.append(isHor ? ",width " : ",height ").append(getUV(size.getMin())).append("::").append(getUV(size.getMax()));
					}
				}
			} else {
				if (asAPI) {
					sb.append(isHor ? ".width(\"" : ".height(\"").append(getBS(size)).append("\")");
				} else {
					sb.append(isHor ? ",width " : ",height ").append(getBS(size));
				}
			}
		}
	}

	/** Returns the a constraint string that can be re-parsed to be the exact same LayoutConstraint.
	 * @param cc The component constraint to return as a constraint string.
	 * @param asAPI If the returned string should be of API type (e.g. .flowX().gap("rel").align("right")) or
	 * as a String type (e.g. "flowx, gap rel, right").
	 * @return A String. Never <code>null</code>.
	 */
	public static String getConstraintString(CC cc, boolean asAPI)
	{
		StringBuffer sb = new StringBuffer(16);

		if (cc.isNewline())
			sb.append(asAPI ? ".newline()" : ",newline");

		if (cc.isExternal())
			sb.append(asAPI ? ".external()" : ",external");

		Boolean flowX = cc.getFlowX();
		if (flowX != null) {
			if (asAPI) {
				sb.append(flowX ? ".flowX()" : ".flowY()");
			} else {
				sb.append(flowX ? ",flowx" : ",flowy");
			}
		}

		UnitValue[] pad = cc.getPadding();
		if (pad != null) {
			sb.append(asAPI ? ".pad(\"" : ",pad ");
			for (int i = 0; i < pad.length; i++)
				sb.append(getUV(pad[i])).append(i < pad.length - 1 ? " " : "");
			if (asAPI)
				sb.append("\")");
		}

		UnitValue[] pos = cc.getPos();
		if (pos != null) {
			if (cc.isBoundsInGrid()) {
				for (int i = 0; i < 4; i++) {
					if (pos[i] != null) {
						if (asAPI) {
							sb.append('.').append(X_Y_STRINGS[i]).append("(\"").append(getUV(pos[i])).append("\")");
						} else {
							sb.append(',').append(X_Y_STRINGS[i]).append(getUV(pos[i]));
						}
					}
				}
			} else {
				sb.append(asAPI ? ".pos(\"" : ",pos ");
				int iSz = (pos[2] != null || pos[3] != null) ? 4 : 2;  // "pos x y" vs "pos x1 y1 x2 y2".
				for (int i = 0; i < iSz; i++)
					sb.append(getUV(pos[i])).append(i < iSz - 1 ? " " : "");

				if (asAPI)
					sb.append("\")");
			}
		}

		String id = cc.getId();
		if (id != null) {
			if (asAPI) {
				sb.append(".id(\"").append(id).append("\")");
			} else {
				sb.append(",id ").append(id);
			}
		}

		String tag = cc.getTag();
		if (tag != null) {
			if (asAPI) {
				sb.append(".tag(\"").append(tag).append("\")");
			} else {
				sb.append(",tag ").append(tag);
			}
		}

		int hideMode = cc.getHideMode();
		if (hideMode >= 0) {
			if (asAPI) {
				sb.append(".hidemode(").append(hideMode).append(')');
			} else {
				sb.append(",hidemode ").append(hideMode);
			}
		}

		int skip = cc.getSkip();
		if (skip > 0) {
			if (asAPI) {
				sb.append(".skip(").append(skip).append(')');
			} else {
				sb.append(",skip ").append(skip);
			}
		}

		int split = cc.getSplit();
		if (split > 1) {
			String s = split == LayoutUtil.INF ? "" : String.valueOf(split);
			if (asAPI) {
				sb.append(".split(").append(s).append(')');
			} else {
				sb.append(",split ").append(s);
			}
		}

		int cx = cc.getCellX();
		int cy = cc.getCellY();
		int spanX = cc.getSpanX();
		int spanY = cc.getSpanY();
		if (cx >= 0 && cy >= 0) {
			if (asAPI) {
				sb.append(".cell(").append(cx).append(", ").append(cy);
				if (spanX > 1 || spanY > 1)
					sb.append(", ").append(spanX).append(", ").append(spanY);
				sb.append(')');
			} else {
				sb.append(",cell ").append(cx).append(' ').append(cy);
				if (spanX > 1 || spanY > 1)
					sb.append(' ').append(spanX).append(' ').append(spanY);
			}
		} else if (spanX > 1 || spanY > 1) {
			if (spanX > 1 && spanY > 1) {
				sb.append(asAPI ? ".span(" : ",span ").append(spanX).append(asAPI ? ", " : " ").append(spanY);
			} else if (spanX > 1) {
				sb.append(asAPI ? ".spanX(" : ",spanx ").append(spanX == LayoutUtil.INF ? "" : (String.valueOf(spanX)));
			} else if (spanY > 1) {
				sb.append(asAPI ? ".spanY(" : ",spany ").append(spanY == LayoutUtil.INF ? "" : (String.valueOf(spanY)));
			}
			if (asAPI)
				sb.append(')');
		}

		Float pushX = cc.getPushX();
		Float pushY = cc.getPushY();
		if (pushX != null || pushY != null) {
			if (pushX != null && pushY != null) {
				sb.append(asAPI ? ".push(" : ",push ");
				if (pushX != 100.0 || pushY != 100.0)
					sb.append(pushX).append(asAPI ? ", " : " ").append(pushY);
			} else if (pushX != null) {
				sb.append(asAPI ? ".pushX(" : ",pushx ").append(pushX == 100 ? "" : (String.valueOf(pushX)));
			} else if (pushY != null) {
				sb.append(asAPI ? ".pushY(" : ",pushy ").append(pushY == 100 ? "" : (String.valueOf(pushY)));
			}
			if (asAPI)
				sb.append(')');
		}

		int dock = cc.getDockSide();
		if (dock >= 0) {
			String ds = CC.DOCK_SIDES[dock];
			if (asAPI) {
				sb.append(".dock").append(Character.toUpperCase(ds.charAt(0))).append(ds.substring(1)).append("()");
			} else {
				sb.append(",").append(ds);
			}
		}

		boolean noGrowAdd = cc.getHorizontal().getGrow() != null && cc.getHorizontal().getGrow().intValue() == 100 &&
		                    cc.getVertical().getGrow() != null && cc.getVertical().getGrow().intValue() == 100;

		addComponentDimConstraintString(cc.getHorizontal(), sb, asAPI, true, noGrowAdd);
		addComponentDimConstraintString(cc.getVertical(), sb, asAPI, false, noGrowAdd);
		if (noGrowAdd)
			sb.append(asAPI ? ".grow()" : ",grow");   // Combine ".growX().growY()" into ".grow()".

		if (cc.isWrap())
			sb.append(asAPI ? ".wrap()" : ",wrap");

		String s = sb.toString();
		return s.length() == 0 || s.charAt(0) != ',' ? s : s.substring(1);
	}

	/** Returns the a constraint string that can be re-parsed to be the exact same LayoutConstraint.
	 * @param lc The layout constraint to return as a constraint string.
	 * @param asAPI If the returned string should be of API type (e.g. .flowX().gap("rel").align("right")) or
	 * as a String type (e.g. "flowx, gap rel, right").
	 * @return A String. Never <code>null</code>.
	 */
	public static String getConstraintString(LC lc, boolean asAPI)
	{
		StringBuffer sb = new StringBuffer(16);

		if (lc.isFlowX() == false)
			sb.append(asAPI ? ".flowY()" : ",flowy");

		boolean fillX = lc.isFillX();
		boolean fillY = lc.isFillY();
		if (fillX || fillY) {
			if (fillX == fillY) {
				sb.append(asAPI ? ".fill()" : ",fill");
			} else {
				sb.append(asAPI ? (fillX ? ".fillX()" : ".fillY()") : (fillX ? ",fillx" : ",filly"));
			}
		}

		Boolean leftToRight = lc.getLeftToRight();
		if (leftToRight != null) {
			if (asAPI) {
				sb.append(".leftToRight(").append(leftToRight).append(')');
			} else {
				sb.append(leftToRight ? ",ltr" : ",rtl");
			}
		}

		if (!lc.getPackWidth().isUnset() || !lc.getPackHeight().isUnset()) {
			if (asAPI) {
				String w = getBS(lc.getPackWidth());
				String h = getBS(lc.getPackHeight());
				sb.append(".pack(");
				if (w.equals("pref") && h.equals("pref")) {
					sb.append(')');
				} else {
					sb.append('\"').append(w).append("\", \"").append(h).append("\")");
				}
			} else {
				sb.append(",pack");
				String size = getBS(lc.getPackWidth()) + " " + getBS(lc.getPackHeight());
				if (size.equals("pref pref") == false)
					sb.append(' ').append(size);
			}
		}

		if (lc.getPackWidthAlign() != 0.5f || lc.getPackHeightAlign() != 1f) {
			if (asAPI) {
				sb.append(".packAlign(").append(floatToString(lc.getPackWidthAlign(), asAPI)).append(", ").append(floatToString(lc.getPackHeightAlign(), asAPI)).append(')');
			} else {
				sb.append(",packalign ").append(floatToString(lc.getPackWidthAlign(), asAPI)).append(' ').append(floatToString(lc.getPackHeightAlign(), asAPI));
			}
		}

		if (lc.isTopToBottom() == false)
			sb.append(asAPI ? ".bottomToTop()" : ",btt");

		UnitValue[] insets = lc.getInsets();
		if (insets != null) {
			String cs = LayoutUtil.getCCString(insets);
			if (cs != null) {
				if (asAPI) {
					sb.append(".insets(\"").append(cs).append("\")");
				} else {
					sb.append(",insets ").append(cs);
				}
			} else {
				sb.append(asAPI ? ".insets(\"" : ",insets ");
				for (int i = 0; i < insets.length; i++)
					sb.append(getUV(insets[i])).append(i < insets.length - 1 ? " " : "");
				if (asAPI)
					sb.append("\")");
			}
		}

		if (lc.isNoGrid())
			sb.append(asAPI ? ".noGrid()" : ",nogrid");

		if (lc.isVisualPadding() == false)
			sb.append(asAPI ? ".noVisualPadding()" : ",novisualpadding");

		int hideMode = lc.getHideMode();
		if (hideMode > 0) {
			if (asAPI) {
				sb.append(".hideMode(").append(hideMode).append(')');
			} else {
				sb.append(",hideMode ").append(hideMode);
			}
		}

		appendBoundSize(lc.getWidth(), sb, true, asAPI);
		appendBoundSize(lc.getHeight(), sb, false, asAPI);

		UnitValue alignX = lc.getAlignX();
		UnitValue alignY = lc.getAlignY();
		if (alignX != null || alignY != null) {
			if (alignX != null && alignY != null) {
				sb.append(asAPI ? ".align(\"" : ",align ").append(getUV(alignX)).append(' ').append(getUV(alignY));
			} else if (alignX != null) {
				sb.append(asAPI ? ".alignX(\"" : ",alignx ").append(getUV(alignX));
			} else if (alignY != null) {
				sb.append(asAPI ? ".alignY(\"" : ",aligny ").append(getUV(alignY));
			}
			if (asAPI)
				sb.append("\")");
		}

		BoundSize gridGapX = lc.getGridGapX();
		BoundSize gridGapY = lc.getGridGapY();
		if (gridGapX != null || gridGapY != null) {
			if (gridGapX != null && gridGapY != null) {
				sb.append(asAPI ? ".gridGap(\"" : ",gap ").append(getBS(gridGapX)).append(' ').append(getBS(gridGapY));
			} else if (gridGapX != null) {
				sb.append(asAPI ? ".gridGapX(\"" : ",gapx ").append(getBS(gridGapX));
			} else if (gridGapY != null) {
				sb.append(asAPI ? ".gridGapY(\"" : ",gapy ").append(getBS(gridGapY));
			}
			if (asAPI)
				sb.append("\")");
		}

		int wrapAfter = lc.getWrapAfter();
		if (wrapAfter != LayoutUtil.INF) {
			String ws = wrapAfter > 0 ? String.valueOf(wrapAfter) : "";
			if (asAPI) {
				sb.append(".wrap(").append(ws).append(')');
			} else {
				sb.append(",wrap ").append(ws);
			}
		}

		int debugMillis = lc.getDebugMillis();
		if (debugMillis > 0) {
			if (asAPI) {
				sb.append(".debug(").append(debugMillis).append(')');
			} else {
				sb.append(",debug ").append(debugMillis);
			}
		}

		String s = sb.toString();
		return s.length() == 0 || s.charAt(0) != ',' ? s : s.substring(1);
	}

	private static String getUV(UnitValue uv)
	{
		return uv != null ? uv.getConstraintString() : "null";
	}

	private static String getBS(BoundSize bs)
	{
		return bs != null ? bs.getConstraintString() : "null";
	}

	/** Converts a <code>float</code> to a string and is removing the ".0" if the float is an integer.
	 * @param f the float.
	 * @return <code>f</code> as a string. Never <code>null</code>.
	 */
	private static String floatToString(float f, boolean asAPI)
	{
		String valS = String.valueOf(f);
		return valS.endsWith(".0") ? valS.substring(0, valS.length() - 2) : (valS + (asAPI ? "f" : ""));
	}
}
