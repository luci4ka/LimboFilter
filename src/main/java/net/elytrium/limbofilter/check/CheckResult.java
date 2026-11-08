package net.elytrium.limbofilter.check;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Результат проверки: либо OK, либо причина провала.
 */
public final class CheckResult {

    private static final CheckResult OK = new CheckResult(null);

    @Nullable
    private final String reason;

    private CheckResult(@Nullable String reason) {
        this.reason = reason;
    }

    public static CheckResult ok() {
        return OK;
    }

    public static CheckResult fail(String reason) {
        return new CheckResult(reason);
    }

    public boolean isFailed() {
        return this.reason != null;
    }

    @Nullable
    public String getReason() {
        return this.reason;
    }
}