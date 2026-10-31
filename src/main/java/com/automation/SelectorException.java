package com.automation;

/** Không tìm thấy / không thao tác được phần tử theo selector cấu hình. */
public class SelectorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String key;
    private final String selector;

    public SelectorException(String key, String selector, String reason) {
        super("Selector [" + key + "] = " + selector + " -> " + reason);
        this.key = key;
        this.selector = selector;
    }

    public String getKey() {
        return key;
    }

    public String getSelector() {
        return selector;
    }
}

