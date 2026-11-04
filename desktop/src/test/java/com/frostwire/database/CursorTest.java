/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Cursor reads typed values from a real SQLite result set and never throws JDBC errors. */
class CursorTest {

  @TempDir Path tmp;

  private Connection connection;

  @BeforeEach
  void openDatabase() throws SQLException {
    connection = DriverManager.getConnection("jdbc:sqlite:" + tmp.resolve("cursor.db"));
    try (Statement s = connection.createStatement()) {
      s.execute(
          "CREATE TABLE songs (id INTEGER PRIMARY KEY, title VARCHAR, size INTEGER,"
              + " track INTEGER, liked BOOLEAN, cover BLOB)");
      s.execute(
          "INSERT INTO songs VALUES (1, 'first', 5000000000, 7, 1, X'CAFE'),"
              + " (2, 'second', 42, 3, 0, NULL)");
    }
  }

  @AfterEach
  void closeDatabase() throws SQLException {
    connection.close();
  }

  private Cursor query(String sql) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    return new Cursor(statement, statement.executeQuery());
  }

  @Test
  void typedGettersReadTheCurrentRow() throws SQLException {
    Cursor c = query("SELECT * FROM songs ORDER BY id");

    assertTrue(c.moveToNext());
    assertEquals(1, c.getInt(c.getColumnIndex("id")));
    assertEquals("first", c.getString(c.getColumnIndex("title")));
    assertEquals(5_000_000_000L, c.getLong(c.getColumnIndex("size")));
    assertEquals(7, c.getByte(c.getColumnIndex("track")));
    assertTrue(c.getBoolean(c.getColumnIndex("liked")));
    assertArrayEquals(new byte[] {(byte) 0xCA, (byte) 0xFE}, c.getBytes(c.getColumnIndex("cover")));

    assertTrue(c.moveToNext());
    assertEquals("second", c.getString(c.getColumnIndex("title")));
    assertFalse(c.getBoolean(c.getColumnIndex("liked")));
    assertNull(c.getBytes(c.getColumnIndex("cover")));
    c.close();
  }

  @Test
  void columnIndexesAreTheJdbcOnesAndUnknownColumnsAreMinusOne() throws SQLException {
    Cursor c = query("SELECT id, title FROM songs");

    assertEquals(1, c.getColumnIndex("id"));
    assertEquals(2, c.getColumnIndex("title"));
    assertEquals(-1, c.getColumnIndex("no_such_column"));
    c.close();
  }

  @Test
  void moveToNextVisitsEveryRowThenReportsTheEnd() throws SQLException {
    Cursor c = query("SELECT title FROM songs ORDER BY id");

    assertTrue(c.moveToNext());
    assertEquals("first", c.getString(1));
    assertTrue(c.moveToNext());
    assertEquals("second", c.getString(1));
    assertFalse(c.moveToNext());
    c.close();
  }

  @Test
  void moveToPositionReportsFailureOnForwardOnlyResults() throws SQLException {
    Cursor c = query("SELECT title FROM songs ORDER BY id");

    // SQLite result sets only move forward one row at a time.
    assertFalse(c.moveToPosition(1));
    c.close();
  }

  @Test
  void closeReleasesTheResultSetAndItsStatement() throws SQLException {
    PreparedStatement statement = connection.prepareStatement("SELECT * FROM songs");
    ResultSet rs = statement.executeQuery();
    Cursor c = new Cursor(statement, rs);

    c.close();

    assertTrue(rs.isClosed());
    assertTrue(statement.isClosed());
  }

  @Test
  void aClosedCursorReturnsDefaultsInsteadOfThrowing() throws SQLException {
    Cursor c = query("SELECT * FROM songs");
    c.close();

    assertEquals(0, c.getInt(1));
    assertEquals(0L, c.getLong(1));
    assertNull(c.getString(2));
    assertEquals(0, c.getByte(1));
    assertFalse(c.getBoolean(5));
    assertNull(c.getBytes(6));
    assertFalse(c.moveToNext());
    assertFalse(c.moveToPosition(1));
    assertEquals(-1, c.getColumnIndex("id"));
  }

  @Test
  void closeStillClosesTheStatementWhenTheResultSetFailsToClose() throws SQLException {
    PreparedStatement statement = connection.prepareStatement("SELECT * FROM songs");
    ResultSet failingResultSet = failingOnClose(ResultSet.class);
    Cursor c = new Cursor(statement, failingResultSet);

    assertDoesNotThrow(c::close);
    assertTrue(statement.isClosed());
  }

  @Test
  void closeSwallowsAStatementThatFailsToClose() throws SQLException {
    PreparedStatement real = connection.prepareStatement("SELECT * FROM songs");
    ResultSet rs = real.executeQuery();
    Cursor c = new Cursor(failingOnClose(Statement.class), rs);

    assertDoesNotThrow(c::close);
    assertTrue(rs.isClosed());
    real.close();
  }

  /** A JDBC object whose close() throws, standing in for a driver that fails to release it. */
  private static <T> T failingOnClose(Class<T> type) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          if (method.getName().equals("close")) {
            throw new SQLException("close failed");
          }
          throw new UnsupportedOperationException(method.getName());
        };
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }
}
