package com.frostwire.tests;

import com.frostwire.jlibtorrent.swig.address;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.ip_filter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless test verifying that jlibtorrent's ip_filter correctly blocks
 * and allows IP addresses based on the flags passed to add_rule().
 *
 * This test caught a real bug: the codebase was passing flags=0 to add_rule(),
 * but in libtorrent 2.x flags=0 means "allow" and flags=blocked (1) means
 * "block". See libtorrent ip_filter.hpp: blocked = 1.
 */
public class IPFilterBlockingTest {

    @Test
    public void testBlockedSwigValueIsOne() {
        assertEquals(1, ip_filter.access_flags.blocked.swigValue(),
                "libtorrent ip_filter::blocked must be 1");
    }

    @Test
    public void testAccessReturnsBlockedForBlockedRange() {
        ip_filter filter = new ip_filter();
        error_code ec = new error_code();

        address start = address.from_string("10.0.0.0", ec);
        address end = address.from_string("10.0.0.255", ec);
        assertFalse(ec.failed(), "Failed to parse 10.0.0.0/24 range");

        filter.add_rule(start, end, ip_filter.access_flags.blocked.swigValue());

        address inside = address.from_string("10.0.0.50", ec);
        assertFalse(ec.failed(), "Failed to parse 10.0.0.50");

        long flags = filter.access(inside);
        assertEquals(ip_filter.access_flags.blocked.swigValue(), flags,
                "IP inside blocked range must have blocked flag set");
    }

    @Test
    public void testAccessReturnsZeroForAllowedIpOutsideBlockedRange() {
        ip_filter filter = new ip_filter();
        error_code ec = new error_code();

        address start = address.from_string("10.0.0.0", ec);
        address end = address.from_string("10.0.0.255", ec);
        assertFalse(ec.failed(), "Failed to parse 10.0.0.0/24 range");

        filter.add_rule(start, end, ip_filter.access_flags.blocked.swigValue());

        address outside = address.from_string("192.168.1.1", ec);
        assertFalse(ec.failed(), "Failed to parse 192.168.1.1");

        long flags = filter.access(outside);
        assertEquals(0, flags,
                "IP outside blocked range must have flags=0 (allowed)");
    }

    @Test
    public void testFlagsZeroMeansAllowNotBlock() {
        ip_filter filter = new ip_filter();
        error_code ec = new error_code();

        address start = address.from_string("10.0.0.0", ec);
        address end = address.from_string("10.0.0.255", ec);
        assertFalse(ec.failed(), "Failed to parse 10.0.0.0/24 range");

        // This is the BUG: passing 0 does NOT block — it explicitly allows
        filter.add_rule(start, end, 0);

        address inside = address.from_string("10.0.0.50", ec);
        assertFalse(ec.failed(), "Failed to parse 10.0.0.50");

        long flags = filter.access(inside);
        assertEquals(0, flags,
                "Passing flags=0 to add_rule() must result in allowed (0), not blocked");
    }

    @Test
    public void testMultipleRanges() {
        ip_filter filter = new ip_filter();
        error_code ec = new error_code();

        // Block 10.0.0.0/24
        filter.add_rule(address.from_string("10.0.0.0", ec),
                        address.from_string("10.0.0.255", ec),
                        ip_filter.access_flags.blocked.swigValue());

        // Block 172.16.5.0 - 172.16.5.255
        filter.add_rule(address.from_string("172.16.5.0", ec),
                        address.from_string("172.16.5.255", ec),
                        ip_filter.access_flags.blocked.swigValue());

        assertEquals(ip_filter.access_flags.blocked.swigValue(),
                     filter.access(address.from_string("10.0.0.1", ec)));
        assertEquals(ip_filter.access_flags.blocked.swigValue(),
                     filter.access(address.from_string("172.16.5.200", ec)));
        assertEquals(0,
                     filter.access(address.from_string("8.8.8.8", ec)));
        assertEquals(0,
                     filter.access(address.from_string("172.16.4.255", ec)));
    }
}
