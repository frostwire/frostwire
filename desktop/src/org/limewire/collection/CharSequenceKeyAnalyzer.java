package org.limewire.collection;

import org.limewire.collection.PatriciaTrie.KeyAnalyzer;

/**
 * Analyzes <code>CharSequence</code> keys with case sensitivity. With 
 * <code>CharSequenceKeyAnalyzer</code> you can
 * compare, check prefix, and determine the index of a bit.
 * <p>
 * A typical use case for a <code>CharSequenceKeyAnalyzer</code> is with a 
 * {@link PatriciaTrie}.
 * <pre>
    PatriciaTrie&lt;String, String&gt; trie = new PatriciaTrie&lt;String, String&gt;(new CharSequenceKeyAnalyzer());
    
    trie.put("Lime", "Lime");
    trie.put("LimeWire", "LimeWire");
    trie.put("LimeRadio", "LimeRadio");
    trie.put("Lax", "Lax");
    trie.put("Lake", "Lake");
    trie.put("Lovely", "Lovely");

    System.out.println(trie.select("Lo"));
    System.out.println(trie.select("Lime"));

    System.out.println(trie.getPrefixedBy("La").toString());            

    Output:
        Lovely
        Lime
        {Lake=Lake, Lax=Lax}

 * </pre>
 */
public class CharSequenceKeyAnalyzer implements KeyAnalyzer<CharSequence> {
    
    private static final long serialVersionUID = -7032449491269434877L;
    
    private static final int[] BITS = createIntBitMask(16);
    
    public static int[] createIntBitMask(int bitCount) {
        int[] bits = new int[bitCount];
        for(int i = 0; i < bitCount; i++) {
            bits[i] = 1 << (bitCount - i - 1);
        }
        return bits;
    } 
    public int length(CharSequence key) {
        return (key != null ? key.length() * 16 : 0);
    }
    
    public int bitIndex(CharSequence key,   int keyOff, int keyLength,
                        CharSequence found, int foundOff, int foundKeyLength) {
        boolean allNull = true;
        
        if(keyOff % 16 != 0 || foundOff % 16 != 0 ||
           keyLength % 16 != 0 || foundKeyLength % 16 != 0)
            throw new IllegalArgumentException("offsets & lengths must be at character boundaries");
        
        int off1 = keyOff / 16;
        int off2 = foundOff / 16;
        int len1 = keyLength / 16 + off1;
        int len2 = foundKeyLength / 16 + off2;
        int length = Math.max(len1, len2);
        
        // Look at each character, and if they're different
        // then figure out which bit makes the difference
        // and return it.
        char k = 0, f = 0;
        for(int i = 0; i < length; i++) {
            int kOff = i + off1;
            int fOff = i + off2;
            
            if(kOff >= len1)
                k = 0;
            else
                k = key.charAt(kOff);
            
            if(found == null || fOff >= len2)
                f = 0;
            else
                f = found.charAt(fOff);
            
            if(k != f) {
               int x = k ^ f;
               return i * 16 + (Integer.numberOfLeadingZeros(x) - 16);
            }
            
            if(k != 0)
                allNull = false;
            
        }
        
        if (allNull) {
            return KeyAnalyzer.NULL_BIT_KEY;
        }
        
        return KeyAnalyzer.EQUAL_BIT_KEY;
    }
    
    public boolean isBitSet(CharSequence key, int keyLength, int bitIndex) {
        if (key == null || bitIndex >= keyLength) {
            return false;
        }
        
        int index = bitIndex / BITS.length;
        int bit = bitIndex - index * BITS.length;
        return (key.charAt(index) & BITS[bit]) != 0;
    }

    public int compare(CharSequence o1, CharSequence o2) {
        return o1.toString().compareTo(o2.toString());
    }

    public int bitsPerElement() {
        return 16;
    }

    public boolean isPrefix(CharSequence prefix, int offset, int length, CharSequence key) {
        if(offset % 16 != 0 || length % 16 != 0)
            throw new IllegalArgumentException("Cannot determine prefix outside of character boundaries");
        String s1 = prefix.subSequence(offset / 16, length / 16).toString();
        String s2 = key.toString();
        return s2.startsWith(s1);
    }
}

