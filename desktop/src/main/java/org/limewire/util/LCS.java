/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package org.limewire.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <a href="http://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Longest_common_subsequence#Java">...</a>
 *
 * @author gubatron
 * @author aldenml
 */
public class LCS {
    public static String lcsHtml(String s1, String s2) {
        String rs1 = new StringBuffer(s1).reverse().toString();
        String rs2 = new StringBuffer(s2).reverse().toString();
        LcsString seq = new LcsString(rs1, rs2, true);
        return seq.getHtml();
    }

    /**
     * A class to compute the longest common subsequence in two strings.
     * Algorithms from Wikipedia:
     * <a href="https://en.wikipedia.org/wiki/Longest_common_subsequence_problem">...</a>
     *
     * @author jhess
     */
    public static abstract class LongestCommonSubsequence<VALUE> {
        private int[][] c;
        private ArrayList<DiffEntry<VALUE>> diff;

        /**
         * A constructor for classes inheriting this one, allowing them to
         * do some initialization before setting the values of X and Y.  Once
         * the initialization is complete, the inheriting class must call
         * initValues(VALUE[] x, VALUE[] y)
         */
        LongestCommonSubsequence() {
        }

        protected abstract int lengthOfY();

        protected abstract int lengthOfX();

        protected abstract VALUE valueOfX(int index);

        protected abstract VALUE valueOfY(int index);

        boolean equals(VALUE x1, VALUE y1) {
            if ((null == x1 && null == y1)) return true;
            assert x1 != null;
            return x1.equals(y1);
        }

        boolean isXYEqual(int i, int j) {
            return equals(valueOfXInternal(i), valueOfYInternal(j));
        }

        private VALUE valueOfXInternal(int i) {
            return valueOfX(i - 1);
        }

        private VALUE valueOfYInternal(int j) {
            return valueOfY(j - 1);
        }

        void calculateLcs() {
            if (c != null) {
                return;
            }
            c = new int[lengthOfX() + 1][];
            for (int i = 0; i < c.length; i++) {
                c[i] = new int[lengthOfY() + 1];
            }
            for (int i = 1; i < c.length; i++) {
                for (int j = 1; j < c[i].length; j++) {
                    if (isXYEqual(i, j)) {
                        c[i][j] = c[i - 1][j - 1] + 1;
                    } else {
                        c[i][j] = Math.max(c[i][j - 1], c[i - 1][j]);
                    }
                }
            }
        }

        List<DiffEntry<VALUE>> diff() {
            calculateLcs();
            if (this.diff == null) {
                this.diff = new ArrayList<>();
                diff(lengthOfX(), lengthOfY());
            }
            return this.diff;
        }

        private void diff(int i, int j) {
            calculateLcs();
            while (!(i == 0 && j == 0)) {
                if (i > 0 && j > 0 && isXYEqual(i, j)) {
                    this.diff.add(new DiffEntry<>(DiffType.NONE, valueOfYInternal(j)));
                    i--;
                    j--;
                } else {
                    if (j > 0 && (i == 0 || c[i][j - 1] >= c[i - 1][j])) {
                        this.diff.add(new DiffEntry<>(DiffType.ADD, valueOfYInternal(j)));
                        j--;
                    } else if (i > 0 && (j == 0 || c[i][j - 1] < c[i - 1][j])) {
                        this.diff.add(new DiffEntry<>(DiffType.REMOVE, valueOfXInternal(i)));
                        i--;
                    }
                }
            }
            Collections.reverse(this.diff);
        }

        @Override
        public String toString() {
            calculateLcs();
            StringBuilder buf = new StringBuilder();
            buf.append("  ");
            for (int j = 1; j <= lengthOfY(); j++) {
                buf.append(valueOfYInternal(j));
            }
            buf.append("\n");
            buf.append(" ");
            for (int j = 0; j < c[0].length; j++) {
                buf.append(c[0][j]);
            }
            buf.append("\n");
            for (int i = 1; i < c.length; i++) {
                buf.append(valueOfXInternal(i));
                for (int j = 0; j < c[i].length; j++) {
                    buf.append(c[i][j]);
                }
                buf.append("\n");
            }
            return buf.toString();
        }

        public enum DiffType {
            ADD("+", "add"), REMOVE("-", "remove"), NONE(" ", "none");
            private final String val;
            private final String name;

            DiffType(String val, String name) {
                this.val = val;
                this.name = name;
            }

            @Override
            public String toString() {
                return val;
            }

            public String getName() {
                return name;
            }

        }

        static class DiffEntry<VALUE> {
            private DiffType type;
            private VALUE value;

            DiffEntry(DiffType type, VALUE value) {
                super();
                this.type = type;
                this.value = value;
            }

            DiffType getType() {
                return type;
            }

            public void setType(DiffType type) {
                this.type = type;
            }

            VALUE getValue() {
                return value;
            }

            public void setValue(VALUE value) {
                this.value = value;
            }

            @Override
            public String toString() {
                return type.toString() + value.toString();
            }
        }
    }

    protected static class LcsString extends LongestCommonSubsequence<Character> {
        private final boolean ignoreCase;
        private final String x;
        private final String y;

        LcsString(String from, String to, boolean ignoreCase) {
            this.x = from;
            this.y = to;
            this.ignoreCase = ignoreCase;
        }

        protected int lengthOfY() {
            return y.length();
        }

        protected int lengthOfX() {
            return x.length();
        }

        protected Character valueOfX(int index) {
            return x.charAt(index);
        }

        protected Character valueOfY(int index) {
            return y.charAt(index);
        }

        @Override
        protected boolean equals(Character x1, Character y1) {
            if (ignoreCase) {
                return (null == x1 && null == y1) || (x1 != null && y1 != null && Character.toLowerCase(x1) == Character.toLowerCase(y1));
            } else {
                return super.equals(x1, y1);
            }
        }

        private String getHtml() {
            DiffType type = null;
            List<DiffEntry<Character>> diffs = diff();
            Collections.reverse(diffs);
            StringBuilder buf = new StringBuilder();
            for (DiffEntry<Character> entry : diffs) {
                if (type != entry.getType()) {
                    if (type != null) {
                        int indx = buf.lastIndexOf("<b>");
                        if (buf.length() - indx > 6) {
                            buf.append("</b>");
                        } else {
                            buf.replace(indx, indx + 3, "");
                        }
                    }
                    switch (entry.getType()) {
                        case REMOVE, ADD -> type = null;
                        default -> {
                            buf.append("<b>");
                            type = entry.getType();
                        }
                    }
                }
                switch (entry.getType()) {
                    case REMOVE:
                        break;
                    case ADD:
                    default:
                        buf.append(escapeHtml(entry.getValue()));
                }
            }
            if (type != null) {
                buf.append("<b>");
            }
            return "<html>" + buf + "</html>";
        }

        private String escapeHtml(Character ch) {
            return switch (ch) {
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "\\&quot;";
                default -> ch.toString();
            };
        }
    }
}
