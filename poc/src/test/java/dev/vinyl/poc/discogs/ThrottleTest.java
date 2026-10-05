package dev.vinyl.poc.discogs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ThrottleTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final List<Long> sleeps = new ArrayList<>();
    private final Throttle throttle = new Throttle(now::get, ms -> {
        sleeps.add(ms);
        now.addAndGet(ms);
    });

    @Test
    void firstRequestDoesNotWait() {
        throttle.beforeRequest();
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void secondRequestWaitsForTheMinimumInterval() {
        throttle.beforeRequest();
        now.addAndGet(300);
        throttle.beforeRequest();
        assertEquals(List.of(700L), sleeps);
    }

    @Test
    void noWaitWhenEnoughTimeHasPassed() {
        throttle.beforeRequest();
        now.addAndGet(1500);
        throttle.beforeRequest();
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void waitsAMinuteWhenNoRequestsRemain() {
        throttle.beforeRequest();
        throttle.afterResponse(0);
        now.addAndGet(2000);
        throttle.beforeRequest();
        assertEquals(List.of(58_000L), sleeps);
    }

    @Test
    void remainingRequestsDoNotBlock() {
        throttle.beforeRequest();
        throttle.afterResponse(12);
        now.addAndGet(1000);
        throttle.beforeRequest();
        assertTrue(sleeps.isEmpty());
    }
}
