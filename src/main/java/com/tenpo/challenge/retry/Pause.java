package com.tenpo.challenge.retry;

import java.time.Duration;

/**
 * Waits between retry attempts. Injected so tests never sleep.
 */
@FunctionalInterface
public interface Pause {

    /**
     * Pauses the current thread for the given duration.
     */
    Pause SLEEP = Thread::sleep;

    void pause(Duration duration) throws InterruptedException;
}
