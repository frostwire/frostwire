/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.limewire.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * http://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Longest_common_subsequence#Java
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
     * http://en.wikipedia.org/wiki/Longest_common_subsequence_problem
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
            return (null == x1 && null == y1) || x1.equals(y1);
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

            public String getVal() {
                return val;
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
                return (null == x1 && null == y1) || Character.toLowerCase(x1) == Character.toLowerCase(y1);
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
                        case REMOVE:
                        case ADD:
                            type = null;
                            break;
                        default:
                            buf.append("<b>");
                            type = entry.getType();
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
            return "<html>" + buf.toString() + "</html>";
        }

        private String escapeHtml(Character ch) {
            switch (ch) {
                case '<':
                    return "&lt;";
                case '>':
                    return "&gt;";
                case '"':
                    return "\\&quot;";
                default:
                    return ch.toString();
            }
        }
    }
}
