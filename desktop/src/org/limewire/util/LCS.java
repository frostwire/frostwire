/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.limewire.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
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
        return seq.getHtml(true);
    }

    public static <E> List<E> LongestCommonSubsequence(E[] s1, E[] s2) {
        int[][] num = new int[s1.length + 1][s2.length + 1]; //2D array, initialized to 0

        //Actual algorithm
        for (int i = 1; i <= s1.length; i++) {
            for (int j = 1; j <= s2.length; j++) {
                if (s1[i - 1].equals(s2[j - 1])) {
                    num[i][j] = 1 + num[i - 1][j - 1];
                } else {
                    num[i][j] = Math.max(num[i - 1][j], num[i][j - 1]);
                }
            }
        }

        //System.out.println("length of LCS = " + num[s1.length][s2.length]);

        int s1position = s1.length, s2position = s2.length;
        List<E> result = new LinkedList<E>();

        while (s1position != 0 && s2position != 0) {
            if (s1[s1position - 1].equals(s2[s2position - 1])) {
                result.add(s1[s1position - 1]);
                s1position--;
                s2position--;
            } else if (num[s1position][s2position - 1] >= num[s1position - 1][s2position]) {
                s2position--;
            } else {
                s1position--;
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * A class to compute the longest common subsequence in two strings.  
     * Algorithms from Wikipedia:
     * http://en.wikipedia.org/wiki/Longest_common_subsequence_problem
     * 
     * @author jhess
     *
     */
    public static abstract class LongestCommonSubsequence<VALUE> {
        private int[][] c;
        private ArrayList<DiffEntry<VALUE>> diff;
        private ArrayList<VALUE> backtrack;

        /**
         * A constructor for classes inheriting this one, allowing them to 
         * do some initialization before setting the values of X and Y.  Once 
         * the initialization is complete, the inheriting class must call
         * initValues(VALUE[] x, VALUE[] y)
         */
        protected LongestCommonSubsequence() {

        }

        protected abstract int lengthOfY();

        protected abstract int lengthOfX();

        protected abstract VALUE valueOfX(int index);

        protected abstract VALUE valueOfY(int index);

        protected boolean equals(VALUE x1, VALUE y1) {
            return (null == x1 && null == y1) || x1.equals(y1);
        }

        protected boolean isXYEqual(int i, int j) {
            return equals(valueOfXInternal(i), valueOfYInternal(j));
        }

        private VALUE valueOfXInternal(int i) {
            return valueOfX(i - 1);
        }

        private VALUE valueOfYInternal(int j) {
            return valueOfY(j - 1);
        }

        public void calculateLcs() {
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

        public int getLcsLength() {
            calculateLcs();

            return c[lengthOfX()][lengthOfY()];
        }

        public int getMinEditDistance() {
            calculateLcs();
            return lengthOfX() + lengthOfY() - 2 * Math.abs(getLcsLength());
        }

        public List<VALUE> backtrack() {
            calculateLcs();
            if (this.backtrack == null) {
                this.backtrack = new ArrayList<VALUE>();
                backtrack(lengthOfX(), lengthOfY());
            }
            return this.backtrack;
        }

        public void backtrack(int i, int j) {
            calculateLcs();

            if (i == 0 || j == 0) {
                return;
            } else if (isXYEqual(i, j)) {
                backtrack(i - 1, j - 1);
                backtrack.add(valueOfXInternal(i));
            } else {
                if (c[i][j - 1] > c[i - 1][j]) {
                    backtrack(i, j - 1);
                } else {
                    backtrack(i - 1, j);
                }
            }
        }

        public List<DiffEntry<VALUE>> diff() {
            calculateLcs();

            if (this.diff == null) {
                this.diff = new ArrayList<DiffEntry<VALUE>>();
                diff(lengthOfX(), lengthOfY());
            }
            return this.diff;
        }

        private void diff(int i, int j) {
            calculateLcs();

            while (!(i == 0 && j == 0)) {
                if (i > 0 && j > 0 && isXYEqual(i, j)) {
                    this.diff.add(new DiffEntry<VALUE>(DiffType.NONE, valueOfYInternal(j)));
                    i--;
                    j--;

                } else {
                    if (j > 0 && (i == 0 || c[i][j - 1] >= c[i - 1][j])) {
                        this.diff.add(new DiffEntry<VALUE>(DiffType.ADD, valueOfYInternal(j)));
                        j--;

                    } else if (i > 0 && (j == 0 || c[i][j - 1] < c[i - 1][j])) {

                        this.diff.add(new DiffEntry<VALUE>(DiffType.REMOVE, valueOfXInternal(i)));
                        i--;
                    }
                }
            }

            Collections.reverse(this.diff);
        }

        @Override
        public String toString() {
            calculateLcs();

            StringBuffer buf = new StringBuffer();
            buf.append("  ");
            for (int j = 1; j <= lengthOfY(); j++) {
                buf.append(valueOfYInternal(j));
            }
            buf.append("\n");
            buf.append(" ");
            for (int j = 0; j < c[0].length; j++) {
                buf.append(Integer.toString(c[0][j]));
            }
            buf.append("\n");
            for (int i = 1; i < c.length; i++) {
                buf.append(valueOfXInternal(i));
                for (int j = 0; j < c[i].length; j++) {
                    buf.append(Integer.toString(c[i][j]));
                }
                buf.append("\n");
            }
            return buf.toString();
        }

        public static enum DiffType {
            ADD("+", "add"), REMOVE("-", "remove"), NONE(" ", "none");

            private String val;
            private String name;

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

        public static class DiffEntry<VALUE> {
            private DiffType type;
            private VALUE value;

            public DiffEntry(DiffType type, VALUE value) {
                super();
                this.type = type;
                this.value = value;
            }

            public DiffType getType() {
                return type;
            }

            public void setType(DiffType type) {
                this.type = type;
            }

            public VALUE getValue() {
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

    public static class LcsString extends LongestCommonSubsequence<Character> {
        
        private String x;
        private String y;
        private final boolean ignoreCase;

        public LcsString(String from, String to, boolean ignoreCase) {
            this.x = from;
            this.y = to;
            this.ignoreCase = ignoreCase;
        }
        
        public LcsString(String from, String to) {
            this(from, to, false);
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

        public String getHtmlDiff() {
            DiffType type = null;
            List<DiffEntry<Character>> diffs = diff();
            StringBuffer buf = new StringBuffer();

            for (DiffEntry<Character> entry : diffs) {
                if (type != entry.getType()) {
                    if (type != null) {
                        buf.append("</span>");
                    }
                    buf.append("<span class=\"" + entry.getType().getName() + "\">");
                    type = entry.getType();
                }
                buf.append(escapeHtml(entry.getValue()));
            }
            buf.append("</span>");
            return buf.toString();
        }

        private String getHtml(boolean reverse) {
            DiffType type = null;
            List<DiffEntry<Character>> diffs = diff();
            if (reverse) {
                Collections.reverse(diffs);
            }
            StringBuffer buf = new StringBuffer();

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
                        type = null;
                        break;
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
