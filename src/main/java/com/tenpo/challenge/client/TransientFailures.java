package com.tenpo.challenge.client;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Classifies the failures produced by {@link RestPercentageClient}.
 */
public final class TransientFailures {

    private TransientFailures() {
    }

    /**
     * Whether repeating the call may succeed:
     * <ul>
     *     <li>transient: I/O errors such as connection failures and timeouts
     *     ({@link ResourceAccessException}) and 5xx responses ({@link HttpServerErrorException});</li>
     *     <li>not transient: 4xx responses, unreadable bodies and bodies without a percentage,
     *     since repeating the same call cannot fix them.</li>
     * </ul>
     */
    public static boolean isTransient(Throwable failure) {
        return failure instanceof ResourceAccessException || failure instanceof HttpServerErrorException;
    }
}
