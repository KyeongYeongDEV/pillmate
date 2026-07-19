package com.pillmate.common.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

/**
 * io.sentry.Dsn 생성자는 패키지-private이라 리플렉션으로 라이브러리 파서를 그대로 재사용한다
 * (별도 정규식 재구현 금지 — 상위 스펙 요구사항).
 */
public final class SentryDsnValidator {

    private static final String DSN_CLASS_NAME = "io.sentry.Dsn";

    private SentryDsnValidator() {
    }

    public static Optional<String> invalidReason(String dsn) {
        try {
            Constructor<?> dsnConstructor = Class.forName(DSN_CLASS_NAME).getDeclaredConstructor(String.class);
            dsnConstructor.setAccessible(true);
            dsnConstructor.newInstance(dsn);
            return Optional.empty();
        } catch (InvocationTargetException e) {
            return Optional.of(rootCauseName(e));
        } catch (ReflectiveOperationException e) {
            return Optional.of(e.getClass().getSimpleName());
        }
    }

    private static String rootCauseName(InvocationTargetException e) {
        Throwable cause = e.getCause();
        return cause != null ? cause.getClass().getSimpleName() : e.getClass().getSimpleName();
    }
}
