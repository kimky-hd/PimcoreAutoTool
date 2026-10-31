package com.automation;

/** Ném ra khi người dùng bấm "Dừng khẩn cấp (Stop)". */
public class AutomationStoppedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AutomationStoppedException() {
        super("Người dùng đã bấm Dừng khẩn cấp");
    }
}

