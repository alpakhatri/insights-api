package com.retailmedia.insights.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final long ONE_SECOND = 1_000_000_000L;

    private final AtomicLong now = new AtomicLong();

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TokenBucket bucket = new TokenBucket(3, 1, now::get);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsOverTime() {
        TokenBucket bucket = new TokenBucket(1, 2, now::get);
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        now.addAndGet(ONE_SECOND / 2); // 2 tokens/s -> one token after 0.5 s

        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void neverRefillsAboveCapacity() {
        TokenBucket bucket = new TokenBucket(2, 10, now::get);
        now.addAndGet(60 * ONE_SECOND);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void reportsSecondsUntilNextToken() {
        TokenBucket bucket = new TokenBucket(1, 0.25, now::get); // one token every 4 s
        bucket.tryConsume();

        assertThat(bucket.secondsUntilNextToken()).isEqualTo(4);
    }
}
