package com.frostwire.alexandria.db;

import com.frostwire.alexandria.Library;

import java.util.List;

public class LibraryDB {
    public static void fill(LibraryDatabase db, Library obj) {
        // this is a special case, since we will have only one library per database
        List<List<Object>> result = db.query("SELECT libraryId, name, version FROM Library");
        if (result.size() > 0) {
            List<Object> row = result.get(0);
            fill(row, obj);
        }
    }

    private static void fill(List<Object> row, Library obj) {
        int id = (Integer) row.get(0);
        String name = (String) row.get(1);
        int version = (Integer) row.get(2);
        obj.setId(id);
        obj.setName(name);
        obj.setVersion(version);
    }
}
