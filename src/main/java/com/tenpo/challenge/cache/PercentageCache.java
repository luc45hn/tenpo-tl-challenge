package com.tenpo.challenge.cache;

import io.vavr.control.Option;

/**
 * Storage of the last percentage obtained from the external service. Implementations never
 * throw: storage failures degrade to an empty cache (on read) or are ignored (on write).
 */
public interface PercentageCache {

    /**
     * Returns the stored entry, fresh or not, or none if there is none or it cannot be read.
     */
    Option<CachedPercentage> find();

    /**
     * Stores the entry, replacing any previous one. Failures are ignored.
     */
    void save(CachedPercentage percentage);
}
