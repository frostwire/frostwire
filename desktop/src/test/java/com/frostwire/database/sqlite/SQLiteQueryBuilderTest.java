/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.database.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.database.Cursor;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SQLiteQueryBuilder turns query clauses into SELECT statements and runs them on a database. */
class SQLiteQueryBuilderTest {

  @TempDir Path tmp;

  private SQLiteDatabase db;
  private SQLiteQueryBuilder qb;

  @BeforeEach
  void openDatabase() throws Exception {
    String path = tmp.resolve("query.db").toString();
    db = new SQLiteDatabase(path, DriverManager.getConnection("jdbc:sqlite:" + path));
    db.execSQL("CREATE TABLE cache_data (id INTEGER PRIMARY KEY, key VARCHAR, date_added INTEGER)");
    db.execSQL(
        "INSERT INTO cache_data (key, date_added) VALUES"
            + " ('a', 30), ('b', 10), ('a', 20), ('c', 40)");
    qb = new SQLiteQueryBuilder();
    qb.setTables("cache_data");
  }

  @AfterEach
  void closeDatabase() {
    db.close();
  }

  @SuppressWarnings("deprecation")
  private String build(
      String[] columns, String where, String groupBy, String having, String orderBy, String limit) {
    return qb.buildQuery(columns, where, null, groupBy, having, orderBy, limit);
  }

  private static List<String> keys(Cursor c) {
    List<String> keys = new ArrayList<>();
    while (c.moveToNext()) {
      keys.add(c.getString(c.getColumnIndex("key")));
    }
    c.close();
    return keys;
  }

  @Test
  void tablesDefaultToEmptyAndKeepWhatIsSet() {
    assertEquals("", new SQLiteQueryBuilder().getTables());
    assertEquals("cache_data", qb.getTables());
  }

  @Test
  void noColumnsSelectsEverything() {
    assertEquals("SELECT * FROM cache_data", build(null, null, null, null, null, null));
    assertEquals("SELECT * FROM cache_data", build(new String[0], "", "", "", "", ""));
  }

  @Test
  void everyClauseIsAppendedInSqlOrder() {
    assertEquals(
        "SELECT key, count(*) AS n FROM cache_data WHERE (date_added > 5)"
            + " GROUP BY key HAVING n > 1 ORDER BY key DESC LIMIT 10, 20",
        build(
            new String[] {"key", "count(*) AS n"},
            "date_added > 5",
            "key",
            "n > 1",
            "key DESC",
            "10, 20"));
  }

  @Test
  void nullColumnsAreSkipped() {
    assertEquals(
        "SELECT id, key FROM cache_data",
        build(new String[] {"id", null, "key"}, null, null, null, null, null));
  }

  @Test
  void havingWithoutGroupByIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> build(null, null, null, "count(*) > 1", null, null));
  }

  @Test
  void nonNumericLimitsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> build(null, null, null, null, null, "1; DROP TABLE x"));
    assertThrows(IllegalArgumentException.class, () -> build(null, null, null, null, null, "ten"));
  }

  @Test
  void queryReturnsTheMatchingRowsInTheRequestedOrder() {
    Cursor c =
        qb.query(db, new String[] {"key"}, "key = ?", new String[] {"a"}, null, null, "date_added");

    assertEquals(List.of("a", "a"), keys(c));
  }

  @Test
  void queryWithoutSelectionReturnsEveryRow() {
    Cursor c = qb.query(db, null, null, null, null, null, "date_added DESC");

    assertEquals(List.of("c", "a", "a", "b"), keys(c));
  }

  @Test
  void selectionArgumentsAreBoundNotSpliced() {
    Cursor c = qb.query(db, null, "key = ?", new String[] {"a' OR '1'='1"}, null, null, null);

    assertFalse(c.moveToNext());
    c.close();
  }

  @Test
  void queryGroupsAndFiltersGroups() {
    Cursor c =
        qb.query(db, new String[] {"key", "count(*) AS n"}, null, null, "key", "n > 1", null);

    assertTrue(c.moveToNext());
    assertEquals("a", c.getString(c.getColumnIndex("key")));
    assertEquals(2, c.getInt(c.getColumnIndex("n")));
    assertFalse(c.moveToNext());
    c.close();
  }

  @Test
  void queryAgainstAMissingTableReturnsNoCursor() {
    qb.setTables("no_such_table");

    assertNull(qb.query(db, null, null, null, null, null, null));
  }

  @Test
  void queryWithoutTablesReturnsNoCursor() {
    qb.setTables(null);

    assertNull(qb.query(db, null, null, null, null, null, null));
  }

  @Test
  void escapedSqlStringsAreQuotedWithInnerQuotesDoubled() {
    StringBuilder sb = new StringBuilder("key = ");

    DatabaseUtils.appendEscapedSQLString(sb, "it's");
    assertEquals("key = 'it''s'", sb.toString());

    sb.setLength(0);
    DatabaseUtils.appendEscapedSQLString(sb, "plain");
    assertEquals("'plain'", sb.toString());
  }

  @Test
  void escapedSqlStringsMatchTheirLiteralValueInSqlite() {
    StringBuilder where = new StringBuilder("key = ");
    DatabaseUtils.appendEscapedSQLString(where, "it's");
    db.execSQL("INSERT INTO cache_data (key, date_added) VALUES ('it''s', 50)");

    Cursor c = qb.query(db, null, where.toString(), null, null, null, null);

    assertEquals(List.of("it's"), keys(c));
  }
}
