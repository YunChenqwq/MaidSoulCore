package com.yunchen.maidsoulcore.core.runtime;

public interface RuntimeTraceSink {
    void trace(String stage, String detail);

    static RuntimeTraceSink noop() {
        return (stage, detail) -> {
        };
    }
}
