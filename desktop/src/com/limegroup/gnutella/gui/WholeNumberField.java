package com.limegroup.gnutella.gui;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class WholeNumberField extends LimeTextField {
    /**
     *
     */
    private static final long serialVersionUID = 5004162823300212516L;
    private final Toolkit toolkit;
    private final NumberFormat integerFormatter;

    WholeNumberField(int value, int columns) {
        super(columns);
        toolkit = Toolkit.getDefaultToolkit();
        integerFormatter = NumberFormat.getNumberInstance(Locale.US);
        integerFormatter.setParseIntegerOnly(true);
        setValue(value);
    }

    WholeNumberField(int columns) {
        super(columns);
        toolkit = Toolkit.getDefaultToolkit();
        integerFormatter = NumberFormat.getNumberInstance(Locale.US);
        integerFormatter.setParseIntegerOnly(true);
    }

    public int getValue() {
        int retVal = 0;
        try {
            retVal = integerFormatter.parse(getText()).intValue();
        } catch (ParseException e) {
            // This should never happen because insertString allows
            // only properly formatted data to get in the field.
            toolkit.beep();
        }
        return retVal;
    }

    public void setValue(int value) {
        setText(integerFormatter.format(value));
    }

    protected Document createDefaultModel() {
        return new WholeNumberDocument();
    }

    protected class WholeNumberDocument extends PlainDocument {
        /**
         *
         */
        private static final long serialVersionUID = 4371239029188384061L;

        public void insertString(int offs,
                                 String str,
                                 AttributeSet a)
                throws BadLocationException {
            if (str == null)
                return;
            char[] source = str.toCharArray();
            char[] result = new char[source.length];
            int j = 0;
            for (int i = 0; i < result.length; i++) {
                if (Character.isDigit(source[i]))
                    result[j++] = source[i];
                else {
                    // Don't beep on the stupid comma's it puts in.
                    if (source[i] == ',' && (((result.length - i) % 4) == 0) &&
                            (result.length != i - 1))
                        continue;
                    toolkit.beep();
                    //System.err.println("insertString: " + source[i]);
                }
            }
            super.insertString(offs, new String(result, 0, j), a);
        }
    }
}
