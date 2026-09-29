package com.tenpo.challenge.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class FailuresTest {

    @Test
    void describesAFailureWithoutCauseAsItself() {
        assertThat(Failures.describe(new IllegalStateException("database down")))
                .isEqualTo("java.lang.IllegalStateException: database down");
    }

    @Test
    void appendsTheRootCauseOfAChain() {
        ConnectException root = new ConnectException("Connection refused");
        IllegalStateException failure = new IllegalStateException("Unable to connect",
                new UncheckedIOException("I/O error", new IOException("wrapped", root)));

        assertThat(Failures.describe(failure)).isEqualTo(
                "java.lang.IllegalStateException: Unable to connect (cause: java.net.ConnectException: Connection refused)");
    }

    @Test
    void describesADirectCauseAsTheRootCause() {
        IllegalStateException failure = new IllegalStateException("outer", new IllegalArgumentException("inner"));

        assertThat(Failures.describe(failure))
                .isEqualTo("java.lang.IllegalStateException: outer (cause: java.lang.IllegalArgumentException: inner)");
    }

    @Test
    void staysOnOneLineWithoutStackTrace() {
        String description = Failures.describe(new IllegalStateException("outer", new RuntimeException("inner")));

        assertThat(description).doesNotContain("\n").doesNotContain("\tat ");
    }
}
