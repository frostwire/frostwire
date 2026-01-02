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

package com.frostwire.util;

import org.junit.jupiter.api.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify that the SSL hostname verifier doesn't throw ConcurrentModificationException
 * when multiple threads access it concurrently.
 */
public class SslTest {

    @Test
    public void testHostnameVerifierNoConcurrentModificationException() throws InterruptedException {
        HostnameVerifier verifier = Ssl.fwHostnameVerifier();
        
        // Add a test domain that will trigger subdomain checking
        Ssl.addValidDomain("testdomain.com");
        
        int numThreads = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads * operationsPerThread);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // Test concurrent access to verify method
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        // Test with subdomains that will trigger the iteration and modification
                        String hostname = "subdomain" + threadId + "_" + j + ".testdomain.com";
                        verifier.verify(hostname, null);
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("ConcurrentModification")) {
                            errorCount.incrementAndGet();
                        }
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        // Wait for all operations to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Operations timed out");
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Verify no ConcurrentModificationException occurred
        assertEquals(0, errorCount.get(), "ConcurrentModificationException occurred " + errorCount.get() + " times");
    }
    
    @Test
    public void testHostnameIsValidNoConcurrentModificationException() throws InterruptedException {
        // Add a test domain that will trigger subdomain checking
        Ssl.addValidDomain("testdomain2.com");
        
        int numThreads = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads * operationsPerThread);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // Test concurrent access to hostnameIsValid method
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        // Test with subdomains that will trigger the iteration and modification
                        String hostname = "subdomain" + threadId + "_" + j + ".testdomain2.com";
                        // Access through reflection or direct call if available
                        Ssl.fwHostnameVerifier().verify(hostname, null);
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("ConcurrentModification")) {
                            errorCount.incrementAndGet();
                        }
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        // Wait for all operations to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Operations timed out");
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Verify no ConcurrentModificationException occurred
        assertEquals(0, errorCount.get(), "ConcurrentModificationException occurred " + errorCount.get() + " times");
    }
    
    @Test
    public void testBasicHostnameVerification() {
        HostnameVerifier verifier = Ssl.fwHostnameVerifier();
        
        // Test with a known valid domain
        assertTrue(verifier.verify("frostwire.com", null));
        assertTrue(verifier.verify("www.frostwire.com", null));
        
        // Test with an invalid domain (not in the list)
        assertFalse(verifier.verify("notinlist.example", null));
    }
    
    @Test
    public void testSubdomainRecognition() {
        HostnameVerifier verifier = Ssl.fwHostnameVerifier();
        
        // Add a custom domain
        Ssl.addValidDomain("customdomain.com");
        
        // Verify the custom domain works
        assertTrue(verifier.verify("customdomain.com", null));
        
        // Verify subdomain works (should be added during verification)
        assertTrue(verifier.verify("sub.customdomain.com", null));
        
        // Verify it was cached and works again
        assertTrue(verifier.verify("sub.customdomain.com", null));
    }
}
