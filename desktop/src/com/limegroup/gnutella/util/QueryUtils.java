package com.limegroup.gnutella.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.limewire.util.I18NConvert;
import org.limewire.util.StringUtils;

import com.limegroup.gnutella.settings.SearchSettings;

public class QueryUtils {
    
    public static final String DELIMITERS = " -._+/*()\\,";
    
    /**
     * Trivial words that are not considered keywords.
     */
    private static final List<String> TRIVIAL_WORDS;
    
    static {
        // must be lower-case
        TRIVIAL_WORDS = Arrays.asList("the", "an", "a", "and", "com", "net", "org", "vol", "volume");
    }
    

    /**
     * Gets the keywords in this filename, seperated by delimiters & illegal
     * characters.
     *
     * @param fileName
     * @param allowNumbers whether number keywords are retained and returned
     * in the result set
     * @return
     */
    public static final Set<String> keywords(String fileName, boolean allowNumbers) {
        //Remove extension
        fileName = QueryUtils.ripExtension(fileName);
    	
        //Separate by whitespace and _, etc.
        Set<String> ret=new LinkedHashSet<String>();
        String delim = DELIMITERS;
        char[] illegal = SearchSettings.ILLEGAL_CHARS.getValue();
        StringBuilder sb = new StringBuilder(delim.length() + illegal.length);
        sb.append(illegal).append(delim);
    
        StringTokenizer st = new StringTokenizer(fileName, sb.toString());
        while (st.hasMoreTokens()) {
            String currToken = st.nextToken().toLowerCase();
            if(!allowNumbers) {
                try {                
                    Double.valueOf(currToken); //NFE if number
                    continue;
                } catch (NumberFormatException normalWord) {}
            }
    		if (!TRIVIAL_WORDS.contains(currToken))
                ret.add(currToken);
        }
        return ret;
    }

    /**
     * Convenience wrapper for 
     * {@link keywords keywords(String, false)}.
     * @param fileName
     * @return
     */
    public static final Set<String> keywords(String fileName) {
    	return keywords(fileName, false);
    }

    /**
     * Removes illegal characters from the name, inserting spaces instead.
     */
    public static final String removeIllegalChars(String name) {
        String ret = "";
        
        String delim = DELIMITERS;
        char[] illegal = SearchSettings.ILLEGAL_CHARS.getValue();
        StringBuilder sb = new StringBuilder(delim.length() + illegal.length);
        sb.append(illegal).append(delim);
        StringTokenizer st = new StringTokenizer(name, sb.toString());        
        while(st.hasMoreTokens())
            ret += st.nextToken().trim() + " ";
        return ret.trim();
    }

    /**
     * Strips an extension off of a file's filename.
     */
    public static String ripExtension(String fileName) {
        String retString = null;
        int extStart = fileName.lastIndexOf('.');
        if (extStart == -1)
            retString = fileName;
        else
            retString = fileName.substring(0, extStart);
        return retString;
    }

    /**
     * 
     * Returns a string to be used for querying from the given name.
     *
     * @param name
     * @param allowNumbers whether numbers in the argument should be kept in
     * the result
     * @return
     */
    public static String createQueryString(String name, boolean allowNumbers) {
        if(name == null)
            throw new NullPointerException("null name");
        
        String retString = null;
        name = I18NConvert.instance().getNorm(name);
        int maxLen = SearchSettings.MAX_QUERY_LENGTH.getValue();
    
        //Get the set of keywords within the name.
        Set<String> keywords = keywords(name, allowNumbers);
    
        if (keywords.isEmpty()) { // no suitable non-number words
            retString = removeIllegalChars(name);
            retString = StringUtils.truncate(retString, maxLen);
        } else {
            StringBuilder sb = new StringBuilder();
            int numWritten = 0;
            for(String currKey : keywords) {
                if(numWritten >= maxLen)
                    break;
                
                // if we have space to add the keyword
                if ((numWritten + currKey.length()) < maxLen) {
                    if (numWritten > 0) { // add a space if we've written before
                        sb.append(" ");
                        numWritten++;
                    }
                    sb.append(currKey); // add the new keyword
                    numWritten += currKey.length();
                }
            }
    
            retString = sb.toString();
    
            //one small problem - if every keyword in the filename is
            //greater than MAX_LEN, then the string returned will be empty.
            //if this happens just truncate the first keyword....
            if (retString.equals("")) {
                retString = StringUtils.truncate(keywords.iterator().next(), maxLen);
            }
        }
    
        // Added a bunch of asserts to catch bugs.  There is some form of
        // input we are not considering in our algorithms....
        assert retString.length() <= maxLen : "Original filename: " + name + ", converted: " + retString;
        if(!keywords.isEmpty())
            assert !retString.equals("") : "Original filename: " + name;
    
        return retString;
    }

    /**
     * Convenience wrapper for 
     * {@link createQueryString createQueryString(String, false)}.
     * @param name
     * @return
     */
    public static String createQueryString(String name) {
    	return createQueryString(name, false);
    }

}
