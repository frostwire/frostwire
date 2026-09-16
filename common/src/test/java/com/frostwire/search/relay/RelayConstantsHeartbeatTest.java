/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RelayConstantsHeartbeatTest {

  @Test
  void bucketIsStableWithinTheHourAndChangesAcrossIt() {
    long base = 1_700_000_000_000L;
    long startOfBucket = RelayConstants.heartbeatBucketIndex(base) * RelayConstants.HEARTBEAT_BUCKET_MS;
    assertEquals(
        RelayConstants.heartbeatBucketIndex(startOfBucket),
        RelayConstants.heartbeatBucketIndex(startOfBucket + RelayConstants.HEARTBEAT_BUCKET_MS - 1));
    assertNotEquals(
        RelayConstants.heartbeatBucketIndex(startOfBucket),
        RelayConstants.heartbeatBucketIndex(startOfBucket + RelayConstants.HEARTBEAT_BUCKET_MS));
  }

  @Test
  void topicNameCarriesPrefixAndBucketIndex() {
    long now = 1_700_000_000_000L;
    String topic = RelayConstants.heartbeatTopic(now);
    assertTrue(topic.startsWith(RelayConstants.TOPIC_HEARTBEAT_PREFIX + "-"), topic);
    assertEquals(
        RelayConstants.TOPIC_HEARTBEAT_PREFIX + "-" + RelayConstants.heartbeatBucketIndex(now), topic);
  }

  @Test
  void bucketIndexFloorsForPreEpochTimesInsteadOfTruncating() {
    assertEquals(0, RelayConstants.heartbeatBucketIndex(0));
    assertEquals(-1, RelayConstants.heartbeatBucketIndex(-1));
    assertEquals(-1, RelayConstants.heartbeatBucketIndex(-RelayConstants.HEARTBEAT_BUCKET_MS));
    assertEquals(-2, RelayConstants.heartbeatBucketIndex(-RelayConstants.HEARTBEAT_BUCKET_MS - 1));
  }
}
