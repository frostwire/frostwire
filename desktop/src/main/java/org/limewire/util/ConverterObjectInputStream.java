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

import com.frostwire.util.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts older package and class names to the new equivalent name and is useful
 * in code refactoring. For example, if packages or classes are renamed,
 * <code>ObjectInputStream</code> fails to find
 * the older name. <code>ConverterObjectInputStream</code> looks up the old name
 * and if a new name exists, <code>ConverterObjectInputStream</code> internally
 * updates any references to the new name without the old name needing to be in the
 * classpath (and as long as serialVersionUID is the same; see {@link Serializable}
 * for more information).
 * <p>
 * <code>ConverterObjectInputStream</code> comes with a pre-set package and class
 * mapping between the old and new name, and you can add more lookups with
 * <p>
 * Pre-set package and class mapping:
 * | Old Name                                      	   	   	   	   	   	   	 | New Name                                                            |
 * | ----------------------------------------------------------------------- | ------------------------------------------------------------------- |
 * | com.limegroup.gnutella.util.FileComparator    	   	   	   	   	   	   	 | org.limewire.collection.FileComparator                              |
 * | com.limegroup.gnutella.downloader.Interval    	   	   	   	   	   	   	 | org.limewire.collection.Interval                                    |
 * | com.limegroup.gnutella.util.IntervalSet 	   	   	   	   	   	   	   	 | org.limewire.collection.IntervalSet                                 |
 * | com.limegroup.gnutella.util.Comparators$CaseInsensitiveStringComparator | org.limewire.collection.Comparators$CaseInsensitiveStringComparator |
 * | com.limegroup.gnutella.util.StringComparator 	   	   	   	   	   	   	 | org.limewire.collection.StringComparator                            |
 * | com.sun.java.util.collections 	   	   	   	   	   	   	   	   	   	   	 | java.util                                                           |
 * <p>
 * None of the earlier forms of the class need to exist in the classpath.
 */
class ConverterObjectInputStream extends ObjectInputStream {
    private static final Logger LOG = Logger.getLogger(ConverterObjectInputStream.class);
    private final Map<String, String> lookups = new HashMap<>(8);

    /**
     * Constructs a new <code>ConverterObjectInputStream</code> wrapping the
     * specified <code>InputStream</code>.
     */
    ConverterObjectInputStream(InputStream in) throws IOException {
        super(in);
        createLookups();
    }

    public void revertToDefault() {
        lookups.clear();
        createLookups();
    }

    /**
     * Adds all internal lookups.
     */
    private void createLookups() {
        lookups.put("com.limegroup.gnutella.util.FileComparator", "org.limewire.collection.FileComparator");
        lookups.put("com.limegroup.gnutella.util.Comparators$CaseInsensitiveStringComparator", "org.limewire.collection.Comparators$CaseInsensitiveStringComparator");
        lookups.put("com.limegroup.gnutella.util.StringComparator", "org.limewire.collection.StringComparator");
        lookups.put("com.sun.java.util.collections", "java.util");
    }

    /**
     * Overridden to manually alter the class descriptors.
     * Note this does NOT require the original class to be loadable.
     * <p>
     * Lookup works as follows:
     * <ul>
     * <li>The serialized (old) class name is looked up, if a corresponding new
     * class name exists the <code>ObjectStreamClass</code> object for it is returned.</li>
     * <li>The package name of the serialized class name is extracted and
     * looked up if a new package name exists, it is prepended to the name of
     * the class the corresponding class is loaded.</li>
     * <li>Otherwise the original ObjectStreamClass is returned.</li>
     * </ul>
     */
    protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        ObjectStreamClass read = super.readClassDescriptor();
        String className = read.getName();
        //LOG.debug("Looking up class: " + className);
        boolean array = className.startsWith("[L") && className.endsWith(";");
        if (array) {
            className = className.substring(2, className.length() - 1);
            LOG.debug("Stripping array form off, resulting in: " + className);
        }
        ObjectStreamClass clazzToReturn;
        String newName = lookups.get(className);
        if (newName != null) {
            clazzToReturn = ObjectStreamClass.lookup(Class.forName(newName));
        } else {
            int index = className.lastIndexOf('.');
            // use "" as lookup key for default package
            String oldPackage = index != -1 ? className.substring(0, index) : "";
            String newPackage = lookups.get(oldPackage);
            if (newPackage != null) {
                if (newPackage.length() == 0) {
                    // mapped to default package
                    clazzToReturn = ObjectStreamClass.lookup(Class.forName(className.substring(index + 1)));
                } else {
                    clazzToReturn = ObjectStreamClass.lookup(Class.forName(newPackage + '.' + className.substring(index + 1)));
                }
            } else {
                // Nothing it maps to -- must be the real name!
                clazzToReturn = read;
            }
        }
        //LOG.debug("Located substitute class: " + clazzToReturn.getName());
        // If it's an array, and we modified what we read off disk, convert
        // to array form.
        if (array && read != clazzToReturn) {
            clazzToReturn = ObjectStreamClass.lookup(Class.forName("[L" + clazzToReturn.getName() + ";"));
            LOG.debug("Re-added array wrapper, for class: " + clazzToReturn.getName());
        }
        return clazzToReturn;
    }
}
