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

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Retry policy for a retryable code.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 */
public class RetryPolicy implements Serializable {

    private static final long serialVersionUID = -7686775565967305047L;

    private final int maxAttempts;
    private final long delay;
    private final TimeUnit timeUnit;
    private final Map<Class<? extends Exception>, Boolean> exceptionRules;
    private final long maxDelay;
    private final BigDecimal multiplier;

    /**
     * Create a new {@link RetryPolicy}.
     *
     * @param maxAttempts total number of attempts, including the first call
     * @param delay to wait between retries
     * @param timeUnit of the delay to wait between retries
     */
    public RetryPolicy(final int maxAttempts, final long delay, final TimeUnit timeUnit) {
        this(maxAttempts, delay, timeUnit, Map.of());
    }

    /**
     * Create a fixed-delay policy with exception rules. True allows retries and false
     * stops them. Unmatched exceptions remain retryable.
     */
    public RetryPolicy(final int maxAttempts, final long delay, final TimeUnit timeUnit,
                       final Map<Class<? extends Exception>, Boolean> exceptionRules) {
        this(maxAttempts, delay, timeUnit, exceptionRules, 0, null);
    }

    private RetryPolicy(final int maxAttempts, final long delay, final TimeUnit timeUnit,
                        final Map<Class<? extends Exception>, Boolean> exceptionRules,
                        final long maxDelay, final BigDecimal multiplier) {
        this.maxAttempts = maxAttempts;
        this.delay = delay;
        this.timeUnit = timeUnit;
        this.exceptionRules = Map.copyOf(exceptionRules);
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
    }

    /**
     * Create an exponential backoff policy. Both delays use the supplied time unit.
     * Pass an empty map to retry all exceptions.
     */
    public static RetryPolicy exponentialBackoff(final int maxAttempts, final long initialDelay,
                                                 final double multiplier, final long maxDelay,
                                                 final TimeUnit timeUnit,
                                                 final Map<Class<? extends Exception>, Boolean> exceptionRules) {
        if (maxAttempts < 1 || initialDelay <= 0 || maxDelay < initialDelay
                || !Double.isFinite(multiplier) || multiplier <= 1) {
            throw new IllegalArgumentException("Invalid exponential backoff configuration");
        }
        return new RetryPolicy(maxAttempts, initialDelay, Objects.requireNonNull(timeUnit),
                exceptionRules, maxDelay, BigDecimal.valueOf(multiplier));
    }

    boolean isRetryable(final Exception exception) {
        // Policies deserialized from older versions have no exception rules.
        if (exceptionRules != null) {
            for (Class<?> type = exception.getClass(); type != Throwable.class; type = type.getSuperclass()) {
                Boolean retryable = exceptionRules.get(type);
                if (retryable != null) {
                    return retryable;
                }
            }
        }
        return true;
    }

    long delayMillis(final int retryNumber) {
        if (multiplier == null) {
            return getTimeUnit().toMillis(getDelay());
        }
        BigDecimal cap = BigDecimal.valueOf(maxDelay);
        BigDecimal wait = BigDecimal.valueOf(delay);
        BigDecimal factor = multiplier;
        // Square the multiplier in bounded steps so even very large retry numbers
        // cannot create an unbounded intermediate value.
        for (int exponent = retryNumber - 1; exponent > 0; exponent >>>= 1) {
            if ((exponent & 1) != 0) {
                wait = wait.multiply(factor, MathContext.DECIMAL128).min(cap);
            }
            factor = factor.multiply(factor, MathContext.DECIMAL128).min(cap);
        }
        // Apply fractional growth before converting to whole milliseconds.
        return wait.multiply(BigDecimal.valueOf(timeUnit.toNanos(1)))
                .movePointLeft(6).min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getDelay() {
        return delay;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    @Override
    public String toString() {
        return "{ maxAttempts = " + maxAttempts + ", delay = " + delay + " " + timeUnit + " }";
    }
}
