/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Lets a search parser harness see the exceptions the parser swallows. The {@code parseResults}
 * implementations wrap the whole page in {@code catch (Exception e)} and log it with {@code
 * LOG.error(msg, e)}, so a bug that throws on one row of a page throws away every row parsed from
 * it, and the fuzzer never sees it. This handler records each throwable logged at {@code SEVERE} by
 * the target's logger; the harness rethrows the ones the page format does not explain.
 *
 * <p>The fuzz tasks run with {@code java.util.logging} at {@code SEVERE} ({@code
 * common/fuzz/logging.properties}), so only these page-level records reach the handler.
 */
public final class SevereLogTrap extends Handler {

  /**
   * The body is not the JSON the engine documents: Gson rejects it ({@link JsonParseException}), or
   * one of Gson's tree accessors throws because an element has another type than the parser asked
   * for. Either way the page holds nothing the parser could have used.
   */
  public static final Predicate<Throwable> NOT_THE_DOCUMENTED_JSON =
      t -> t instanceof JsonParseException || thrownInsideGson(t);

  private final java.util.logging.Logger logger;
  private final List<Throwable> thrown = new ArrayList<>();

  private SevereLogTrap(java.util.logging.Logger logger) {
    this.logger = logger;
    setLevel(Level.SEVERE);
  }

  /**
   * Attaches a trap to the logger that {@code com.frostwire.util.Logger} creates for the class, in
   * place of the console: printing a stack trace for every malformed input fills gigabytes of log
   * at fuzzing speed.
   */
  public static SevereLogTrap attachTo(Class<?> loggingClass) {
    java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger(loggingClass.getSimpleName());
    SevereLogTrap trap = new SevereLogTrap(logger);
    logger.setUseParentHandlers(false);
    logger.addHandler(trap);
    return trap;
  }

  /** Forgets what earlier inputs logged. Call before running the target on a new input. */
  public void clear() {
    thrown.clear();
  }

  /**
   * Rethrows, unchanged so the fuzzer reports its own stack, the first swallowed throwable that
   * {@code explainedByInput} does not accept.
   */
  public void rethrowUnexplained(Predicate<Throwable> explainedByInput) {
    for (Throwable t : thrown) {
      if (!explainedByInput.test(t)) {
        thrown.clear();
        if (t instanceof RuntimeException) {
          throw (RuntimeException) t;
        }
        if (t instanceof Error) {
          throw (Error) t;
        }
        throw new IllegalStateException("parser swallowed a checked exception", t);
      }
    }
    thrown.clear();
  }

  @Override
  public void publish(LogRecord record) {
    if (record.getThrown() != null && record.getLevel().intValue() >= Level.SEVERE.intValue()) {
      thrown.add(record.getThrown());
    }
  }

  @Override
  public void flush() {}

  @Override
  public void close() {
    logger.removeHandler(this);
  }

  private static boolean thrownInsideGson(Throwable t) {
    StackTraceElement[] stack = t.getStackTrace();
    return stack.length > 0 && stack[0].getClassName().startsWith("com.google.gson.");
  }
}
