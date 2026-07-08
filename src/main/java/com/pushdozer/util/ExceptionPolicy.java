package com.pushdozer.util;

import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * 区分「预期运行失败」与「编程错误」，避免大范围 {@code catch (Exception)} 掩盖真实 bug。
 */
public final class ExceptionPolicy {

    private ExceptionPolicy() {}

    /**
     * 典型的编程错误：坐标/索引越界、空指针、状态不一致等，应向上传播而非吞掉。
     */
    public static boolean isProgrammingError(Throwable throwable) {
        return throwable instanceof NullPointerException
            || throwable instanceof IndexOutOfBoundsException
            || throwable instanceof ClassCastException
            || throwable instanceof IllegalStateException
            || throwable instanceof UnsupportedOperationException
            || throwable instanceof ArithmeticException
            || throwable instanceof AssertionError;
    }

    /**
     * 可预期的运行期失败：无效用户配置、IO/JSON 解析、注册表查找失败等。
     */
    public static boolean isExpectedOperationalFailure(Throwable throwable) {
        if (throwable instanceof IOException || throwable instanceof JsonParseException) {
            return true;
        }
        if (throwable instanceof IllegalArgumentException) {
            String message = throwable.getMessage();
            return message != null && (
                message.contains("Non [a-z0-9/._-] character")
                    || message.contains("Invalid identifier")
                    || message.contains("No key")
            );
        }
        return false;
    }

    public static void rethrowIfProgrammingError(Throwable throwable) {
        if (throwable instanceof Error error) {
            throw error;
        }
        if (isProgrammingError(throwable) && throwable instanceof RuntimeException runtime) {
            throw runtime;
        }
    }

    /**
     * 对单项世界/网络操作：预期失败仅记日志并跳过，编程错误立即抛出。
     */
    public static void runPerItem(String context, Runnable action, Logger logger) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            rethrowIfProgrammingError(exception);
            if (isExpectedOperationalFailure(exception)) {
                logger.debug("{}: {}", context, exception.getMessage());
            } else {
                logger.warn("{}: {}", context, exception.getMessage());
            }
        }
    }

    /**
     * 记录预期失败；未知或编程错误向上抛出。
     */
    public static void logBenignOrRethrow(String context, Throwable throwable, Logger logger) {
        rethrowIfProgrammingError(throwable);
        if (isExpectedOperationalFailure(throwable)) {
            logger.warn("{}: {}", context, throwable.getMessage());
            return;
        }
        if (throwable instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new IllegalStateException(context, throwable);
    }
}
