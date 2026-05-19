package com.maidsoul.brain.llm;

public final class LlmRequestException extends RuntimeException {
    private final String requestKind;
    private final String failureKind;
    private final int attempt;
    private final long elapsedMillis;

    public LlmRequestException(String requestKind, String failureKind, int attempt, long elapsedMillis, String message, Throwable cause) {
        super(message, cause);
        this.requestKind = requestKind == null ? "unknown" : requestKind;
        this.failureKind = failureKind == null ? "unknown" : failureKind;
        this.attempt = attempt;
        this.elapsedMillis = elapsedMillis;
    }

    public String requestKind() {
        return requestKind;
    }

    public String failureKind() {
        return failureKind;
    }

    public int attempt() {
        return attempt;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public String traceText() {
        return requestKind + " " + failureKind + " attempt=" + attempt + " elapsed=" + elapsedMillis + "ms message=" + getMessage();
    }
}
