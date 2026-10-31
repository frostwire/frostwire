/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.frostwire.search.internetarchive.InternetArchiveSearchPattern;
import com.frostwire.util.http.HttpClient;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * archive.org now and then resets an HTTP/2 stream (PROTOCOL_ERROR); OkHttp doesn't retry that,
 * so the whole engine returned nothing. A search GET that fails to connect is tried once more; a
 * timeout is not, since that would double the wait.
 */
class SearchPerformerRetryTest {

  private final AtomicInteger gets = new AtomicInteger();

  @Test
  void aDroppedRequestIsTriedOnceMore() throws Exception {
    SearchPerformer performer =
        performer(new IOException("stream was reset: PROTOCOL_ERROR"), "results");
    assertEquals("results", performer.fetch("https://example.org/search"));
    assertEquals(2, gets.get());
  }

  @Test
  void aRequestThatFailsTwiceFails() {
    SearchPerformer performer =
        performer(new IOException("reset"), new IOException("reset again"));
    IOException e =
        assertThrows(IOException.class, () -> performer.fetch("https://example.org/search"));
    assertEquals("reset again", e.getMessage());
    assertEquals(2, gets.get());
  }

  @Test
  void aTimeoutIsNotRetried() {
    SearchPerformer performer = performer(new SocketTimeoutException("timeout"), "results");
    assertThrows(
        SearchTimeoutException.class, () -> performer.fetch("https://example.org/search"));
    assertEquals(1, gets.get());
  }

  private SearchPerformer performer(Object... answers) {
    Deque<Object> queue = new ArrayDeque<>(List.of(answers));
    HttpClient client =
        (HttpClient)
            Proxy.newProxyInstance(
                HttpClient.class.getClassLoader(),
                new Class<?>[] {HttpClient.class},
                (proxy, method, args) -> {
                  if (!method.getName().equals("get")) {
                    throw new UnsupportedOperationException(method.getName());
                  }
                  gets.incrementAndGet();
                  Object answer = queue.removeFirst();
                  if (answer instanceof IOException io) {
                    throw io;
                  }
                  return answer;
                });
    return new SearchPerformer(
        1, "q", "q", new InternetArchiveSearchPattern(), null, 1000, client);
  }
}
