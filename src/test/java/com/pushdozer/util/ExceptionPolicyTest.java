package com.pushdozer.util;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExceptionPolicyTest {

    @Test
    void rethrowsProgrammingErrors() {
        assertThrows(NullPointerException.class,
            () -> ExceptionPolicy.rethrowIfProgrammingError(new NullPointerException("boom")));
        assertThrows(IndexOutOfBoundsException.class,
            () -> ExceptionPolicy.rethrowIfProgrammingError(new IndexOutOfBoundsException()));
    }

    @Test
    void runPerItemSwallowsExpectedOperationalFailures() {
        var logger = LoggerFactory.getLogger("test");
        assertDoesNotThrow(() -> ExceptionPolicy.runPerItem("io", () -> {
            throw new IOException("disk full");
        }, logger));
        assertDoesNotThrow(() -> ExceptionPolicy.runPerItem("json", () -> {
            throw new JsonSyntaxException("bad json");
        }, logger));
    }

    @Test
    void runPerItemRethrowsProgrammingErrors() {
        var logger = LoggerFactory.getLogger("test");
        assertThrows(IllegalStateException.class, () -> ExceptionPolicy.runPerItem("state", () -> {
            throw new IllegalStateException("inconsistent");
        }, logger));
    }
}
