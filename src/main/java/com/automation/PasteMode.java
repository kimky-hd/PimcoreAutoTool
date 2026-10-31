package com.automation;

/** Cách đưa chữ vào ô nhập liệu. */
public enum PasteMode {

    /** Ctrl+V từ System Clipboard, nếu kiểm tra thấy chưa dán được thì gõ lại bằng sendKeys(). */
    CLIPBOARD_THEN_SENDKEYS("Clipboard (Ctrl+V), lỗi thì sendKeys"),
    /** Chỉ dùng Ctrl+V từ System Clipboard. */
    CLIPBOARD_ONLY("Chỉ Clipboard (Ctrl+V)"),
    /** Chỉ gõ bằng sendKeys(). */
    SENDKEYS_ONLY("Chỉ sendKeys()");

    private final String label;

    PasteMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}

