/*
 * File    : DisplayFormatters.java
 * Created : 07-Oct-2003
 * By      : gardnerpar
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.gudy.azureus2.core3.util;

import java.text.NumberFormat;
import java.util.Arrays;

public class DisplayFormatters {
    final private static boolean ROUND_NO = true;
    final private static boolean TRUNCZEROS_NO = false;
    final private static boolean TRUNCZEROS_YES = true;
    private final static int UNIT_B = 0;
    private final static int UNIT_KB = 1;
    private final static int UNIT_MB = 2;
    private final static int UNIT_GB = 3;
    private final static int UNIT_TB = 4;
    final private static int[] UNITS_PRECISION = {0, // B
            1, //KB
            2, //MB
            2, //GB
            3 //TB
    };
    final private static NumberFormat[] cached_number_formats = new NumberFormat[20];
    private static final int unitsStopAt = UNIT_TB;
    private static String[] units;
    private static String[] units_rate;

    static {
        setUnits();
    }

    private static void setUnits() {
        // (1) http://physics.nist.gov/cuu/Units/binary.html
        // (2) http://www.isi.edu/isd/LOOM/documentation/unit-definitions.text
        units = new String[unitsStopAt + 1];
        String[] units_bits = new String[unitsStopAt + 1];
        units_rate = new String[unitsStopAt + 1];
        switch (unitsStopAt) {
            case UNIT_TB:
                units[UNIT_TB] = getUnit("TB");
                units_bits[UNIT_TB] = getUnit("Tbit");
                units_rate[UNIT_TB] = getUnit("TB");
            case UNIT_GB:
                units[UNIT_GB] = getUnit("GB");
                units_bits[UNIT_GB] = getUnit("Gbit");
                units_rate[UNIT_GB] = getUnit("GB");
            case UNIT_MB:
                units[UNIT_MB] = getUnit("MB");
                units_bits[UNIT_MB] = getUnit("Mbit");
                units_rate[UNIT_MB] = getUnit("MB");
            case UNIT_KB:
                // yes, the k should be lower case
                units[UNIT_KB] = getUnit("kB");
                units_bits[UNIT_KB] = getUnit("kbit");
                units_rate[UNIT_KB] = getUnit("kB");
            case UNIT_B:
                units[UNIT_B] = getUnit("B");
                units_bits[UNIT_B] = getUnit("bit");
                units_rate[UNIT_B] = getUnit("B");
        }
        String per_sec = "/s";
        for (int i = 0; i <= unitsStopAt; i++) {
            units[i] = units[i];
            units_rate[i] = units_rate[i] + per_sec;
        }
        Arrays.fill(cached_number_formats, null);
        NumberFormat percentage_format = NumberFormat.getPercentInstance();
        percentage_format.setMinimumFractionDigits(1);
        percentage_format.setMaximumFractionDigits(1);
    }

    private static String getUnit(String key) {
        return " " + key;
    }

    private static String formatByteCountToKiBEtc(
            long n) {
        return formatByteCountToKiBEtc(n, true, DisplayFormatters.TRUNCZEROS_NO, -1);
    }

    private static String formatByteCountToKiBEtc(
            long n,
            boolean rate,
            boolean bTruncateZeros,
            int precision) {
        double dbl = n;
        int unitIndex = UNIT_B;
        long div = 1000;
        while (dbl >= div && unitIndex < unitsStopAt) {
            dbl /= div;
            unitIndex++;
        }
        if (precision < 0) {
            precision = UNITS_PRECISION[unitIndex];
        }
        // round for rating, because when the user enters something like 7.3kbps
        // they don't want it truncated and displayed as 7.2
        // (7.3*1024 = 7475.2; 7475/1024.0 = 7.2998;  trunc(7.2998, 1 prec.) == 7.2
        //
        // Truncate for rest, otherwise we get complaints like:
        // "I have a 1.0GB torrent and it says I've downloaded 1.0GB.. why isn't
        //  it complete? waaah"
        return formatDecimal(dbl, precision, bTruncateZeros, rate)
                + (rate ? units_rate[unitIndex] : units[unitIndex]);
    }

    public static String formatByteCountToKiBEtcPerSec(long n) {
        return formatByteCountToKiBEtc(n);
    }
    //
    // End methods
    //

    /**
     * Format a real number to the precision specified.  Does not round the number
     * or truncate trailing zeros.
     *
     * @param value     real number to format
     * @param precision # of digits after the decimal place
     * @return formatted string
     */
    private static String
    formatDecimal(
            double value,
            int precision) {
        return formatDecimal(value, precision, TRUNCZEROS_NO, ROUND_NO);
    }

    /**
     * Format a real number
     *
     * @param value          real number to format
     * @param precision      max # of digits after the decimal place
     * @param bTruncateZeros remove any trailing zeros after decimal place
     * @param bRound         Whether the number will be rounded to the precision, or
     *                       truncated off.
     * @return formatted string
     */
    private static String
    formatDecimal(
            double value,
            int precision,
            boolean bTruncateZeros,
            boolean bRound) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Constants.INFINITY_STRING;
        }
        double tValue;
        if (bRound) {
            tValue = value;
        } else {
            // NumberFormat rounds, so truncate at precision
            if (precision == 0) {
                tValue = (long) value;
            } else {
                double shift = Math.pow(10, precision);
                tValue = ((long) (value * shift)) / shift;
            }
        }
        int cache_index = (precision << 2) + ((bTruncateZeros ? 1 : 0) << 1)
                + (bRound ? 1 : 0);
        NumberFormat nf = null;
        if (cache_index < cached_number_formats.length) {
            nf = cached_number_formats[cache_index];
        }
        if (nf == null) {
            nf = NumberFormat.getNumberInstance();
            nf.setGroupingUsed(false); // no commas
            if (!bTruncateZeros) {
                nf.setMinimumFractionDigits(precision);
            }
            if (bRound) {
                nf.setMaximumFractionDigits(precision);
            }
            if (cache_index < cached_number_formats.length) {
                cached_number_formats[cache_index] = nf;
            }
        }
        return nf.format(tValue);
    }

    // Used to test fractions and displayformatter.
    // Keep until everything works okay.
    public static void main(String[] args) {
        // set decimal display to ","
        //Locale.setDefault(Locale.GERMAN);
        double d = 0.000003991630774821635;
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(6);
        nf.setMinimumFractionDigits(6);
        String s = nf.format(d);
        System.out.println("Actual: " + d);  // Displays 3.991630774821635E-6
        System.out.println("NF/6:   " + s);  // Displays 0.000004
        // should display 0.000003
        System.out.println("DF:     " + DisplayFormatters.formatDecimal(d, 6));
        // should display 0
        System.out.println("DF 0:   " + DisplayFormatters.formatDecimal(d, 0));
        // should display 0.000000
        System.out.println("0.000000:" + DisplayFormatters.formatDecimal(0, 6));
        // should display 0.001
        System.out.println("0.001:" + DisplayFormatters.formatDecimal(0.001, 6, TRUNCZEROS_YES, ROUND_NO));
        // should display 0
        System.out.println("0:" + DisplayFormatters.formatDecimal(0, 0));
        // should display 123456
        System.out.println("123456:" + DisplayFormatters.formatDecimal(123456, 0));
        // should display 123456
        System.out.println("123456:" + DisplayFormatters.formatDecimal(123456.999, 0));
        //noinspection divzero
        System.out.println(DisplayFormatters.formatDecimal(0.0 / 0, 3));
    }
}
