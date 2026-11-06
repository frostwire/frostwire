/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.util.Hex;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PowTestIdentitiesTest {

  @Test
  void everySeedMeetsTheIdentityDifficultyAndIsDistinct() {
    Set<String> pubs = new HashSet<>();
    for (int i = 0; i < PowTestIdentities.size(); i++) {
      IdentityKeys identity = PowTestIdentities.get(i);
      assertTrue(
          IdentityKeys.countLeadingZeroBits(identity.nodeId())
              >= KarmaConstants.IDENTITY_DIFFICULTY,
          "seed " + i + " is below the identity difficulty");
      assertTrue(pubs.add(Hex.encode(identity.ed25519PubRaw())), "seed " + i + " is a duplicate");
    }
  }
}
