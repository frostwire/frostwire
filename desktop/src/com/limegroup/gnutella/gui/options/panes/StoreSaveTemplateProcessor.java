package com.limegroup.gnutella.gui.options.panes;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.limegroup.gnutella.gui.I18n;

/**
 * This consumes a String template for describing how to save a file and returns a valid file path.  
 *	Some examples would be:
 * <ul>
 * <li></li>
 * <li>.</li>
 * <li>${artist}/${album}</li>
 * <li>${home}/Music</li>
 * </ul>
 */
public class StoreSaveTemplateProcessor {
    

    public final static String ARTIST_LABEL = "artist";
    public final static String ALBUM_LABEL = "album";
    public final static String HOME_LABEL = "home";
    
    private static interface States {
        int LOOKING_FOR_DOLLAR = 0;

        int HAVE_DOLLAR = 1;

        int INSIDE_DELIMS = 2;
    }

    /*
     * These are here for testing.
     */
    final static String TEMPLATE_PROCESSOR_MISSING_DELIMETER = "STORE_TEMPLATE_PROCESSOR_MISSING_DELIMETER";
    final static String TEMPLATE_PROCESSOR_UNKNOWN_REPLACEMENT = "STORE_TEMPLATE_PROCESSOR_UNKNOWN_REPLACEMENT";
    final static String TEMPLATE_PROCESSOR_UNCLOSED_VARIABLE = "STORE_TEMPLATE_PROCESSOR_UNCLOSED_VARIABLE";

    private final static List<Character> END_DELIMS = new ArrayList<Character>();
    static {
        END_DELIMS.add('{');
        END_DELIMS.add('}');
        END_DELIMS.add('(');
        END_DELIMS.add(')');
        END_DELIMS.add('[');
        END_DELIMS.add(']');
    }
    
    /**
     * Thrown for an invalid template.
     */
    public final static class IllegalTemplateException extends ParseException {
        
        /**
         * 
         */
        private static final long serialVersionUID = -3544104037008917572L;
        private final String template;
                
        public IllegalTemplateException(int pos, final String msg, final String template) {
            super(msg,pos); 
            this.template = template;
        }
        
        @Override
        public String getMessage() {
            final StringBuilder sb = new StringBuilder();
            //
            // This is used for testing
            //
            String s = null;
            try {
                s = I18n.tr(super.getMessage());
            } catch(Exception e){
                s = e.getLocalizedMessage();
            }
            sb.append(s);
            sb.append(System.getProperty("line.separator"));
            sb.append(template);
            sb.append(System.getProperty("line.separator"));
            for (int i=0, N=getErrorOffset(); i<N; i++) sb.append(' ');
            sb.append('^');
            return sb.toString();
        }

    }
    
    /**
     * Returns whether the template is valid. This is a convenience method to
     * parse a template and report an error. It calls
     * {@link #getOutputDirectory(String, Map, File, boolean)} with an empty map 
     * of substitutions and <code>complainAboutNoReplacements</code> <code>false</code> 
     * so we don't complain if we have a valid
     * 
     * @param template
     * @return whether the template is valid
     */
    public boolean isValid(String template) throws IllegalTemplateException{
        final Map<String, String> subs = new HashMap<String, String>();
        subs.put(ARTIST_LABEL, "");
        subs.put(ALBUM_LABEL, "");
        subs.put(HOME_LABEL, "");
        getOutputDirectory(template, subs, new File("."));
        return true;
    }
    
    /**
     * Returns an output directory specified by <code>template</code> using
     * <code>outDir</code> as the base directory.  Some sample templates are
     * <ul>
     * <li></li>
     * <li>.</li>
     * <li>${artist}/${album}</li>
     * <li>${home}/Music</li>
     * </ul>
     * Valid values for the keys of <code>substitutions</code> are
     * <ul>
     * <li>{@link #ARTIST_LABEL}</li>
     * <li>{@link #AlBUM_LABLE}</li>
     * <li>{@link #HOME_LABEL}</li>
     * </ul>
     * 
     * @param template - template to use to create the subfolder structure
     * @param substitutions - List of real values to replace template names with
     * @param outDir - subdirectory to save to below the template folders
     * @return - the complete path to the directory to save the file to, including
     *              any sub folders that may have been generated from the template
     * 
     * @throws IllegalTemplateException
     */
    public File getOutputDirectory(final String template, final Map<String,String> substitutions, 
            final File outDir) throws IllegalTemplateException {

        if (template == null) return outDir;
        if (template.equals("")) return outDir;
        if (template.equals(".")) return outDir;
        
        final StringBuffer buf = new StringBuffer();
        StringBuffer var = new StringBuffer();
        int s = States.LOOKING_FOR_DOLLAR;
        for (int i=0; i<template.length(); i++) {
            final char c = template.charAt(i);
            switch (s) {
            
            case States.LOOKING_FOR_DOLLAR:
                if (c == '$') {
                    s = States.HAVE_DOLLAR;
                } else {
                    buf.append(c);
                }
                break;
            
            case States.HAVE_DOLLAR:
                if (isDelim(c)) {
                    s = States.INSIDE_DELIMS;
                    var = new StringBuffer();
                } else if (!Character.isWhitespace(c)) {
                    throw new IllegalTemplateException(i,TEMPLATE_PROCESSOR_MISSING_DELIMETER,template);
                }
                break;
                
            case States.INSIDE_DELIMS:
                if (isDelim(c)) { // allow any one
                    final String variable = var.toString().replaceAll("\\s", "");
                    String replacement = substitutions.get(variable);
                    if (replacement == null) {
                        throw new IllegalTemplateException(i,TEMPLATE_PROCESSOR_UNKNOWN_REPLACEMENT, template);
                    }
                    buf.append(replacement);
                    s = States.LOOKING_FOR_DOLLAR;
                } else {
                    var.append(c);
                }
                break;  
            }
        }
        if (s == States.INSIDE_DELIMS) { 
            throw new IllegalTemplateException(template.length(),TEMPLATE_PROCESSOR_UNCLOSED_VARIABLE, template);
        }
        return new File(outDir, buf.toString());
    }
        
    /**
     * Returns <code>true</code> if <code>c</code> is a valid delimiter.
     * 
     * @param c <code>char</code> in question
     * @return <code>true</code> if <code>c</code> is a valid delimiter
     */
    private boolean isDelim(char c) {
        for( Character ch  : END_DELIMS) {
            if( ch.charValue() == c) 
                return true;
        }
        return false;
    }
}
