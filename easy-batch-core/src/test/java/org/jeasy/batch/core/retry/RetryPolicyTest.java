/*
 * The MIT License
 *
 *   Copyright (c) 2021, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */
package org.jeasy.batch.core.retry;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.*;

public class RetryPolicyTest {

    @Test
    public void classifiesTheOuterExceptionUsingTheNearestRule() {
        RetryPolicy policy = new RetryPolicy(3, 0, MILLISECONDS,
                Map.of(IOException.class, true, FileNotFoundException.class, false,
                        IllegalStateException.class, false));

        assertFalse(policy.isRetryable(new FileNotFoundException()));
        assertTrue(policy.isRetryable(new SocketTimeoutException()));
        assertFalse(policy.isRetryable(new IllegalStateException(new IOException())));
        assertTrue(policy.isRetryable(new IllegalArgumentException()));
    }

    @Test
    public void growsAndCapsEachPlannedDelay() {
        RetryPolicy policy = RetryPolicy.exponentialBackoff(5, 100, 2, 250, MILLISECONDS, Map.of());
        assertEquals(100, policy.delayMillis(1));
        assertEquals(200, policy.delayMillis(2));
        assertEquals(250, policy.delayMillis(3));
        assertEquals(250, policy.delayMillis(4));
        assertEquals(100, policy.delayMillis(1));
    }

    @Test
    public void roundsAfterGrowthAndSaturatesOnConversion() {
        RetryPolicy fractional = RetryPolicy.exponentialBackoff(3, 750, 1.5, 2000, MICROSECONDS, Map.of());
        assertEquals(0, fractional.delayMillis(1));
        assertEquals(1, fractional.delayMillis(2));

        RetryPolicy huge = RetryPolicy.exponentialBackoff(Integer.MAX_VALUE, 1,
                Double.MAX_VALUE, Long.MAX_VALUE, DAYS, Map.of());
        assertEquals(Long.MAX_VALUE, huge.delayMillis(Integer.MAX_VALUE));
    }

    @Test
    public void rejectsInvalidBackoffAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> RetryPolicy.exponentialBackoff(3, 100, 2, 99, MILLISECONDS, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RetryPolicy.exponentialBackoff(3, 100, Double.NaN, 250, MILLISECONDS, Map.of()));
    }
}
