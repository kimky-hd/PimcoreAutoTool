package com.automation;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

/**
 * Tiện ích đọc/ghi System Clipboard của Windows qua
 * {@code Toolkit.getDefaultToolkit().getSystemClipboard()} – giống hệt thao tác Copy/Paste tay.
 * Có cơ chế thử lại vì Clipboard có thể đang bị ứng dụng khác chiếm giữ trong tích tắc.
 */
public final class ClipboardUtil {

    private static final int MAX_RETRY = 15;
    private static final long RETRY_DELAY_MS = 80;

    private ClipboardUtil() {
    }

    private static Clipboard clipboard() {
        return Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    /** Đưa chuỗi vào System Clipboard (tương đương Ctrl+C). */
    public static void setText(String text) {
        String value = text == null ? "" : text;
        IllegalStateException last = null;
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                StringSelection selection = new StringSelection(value);
                clipboard().setContents(selection, selection);
                return;
            } catch (IllegalStateException busy) {
                last = busy;
                pause();
            }
        }
        throw new IllegalStateException("Không ghi được vào Clipboard (đang bị chiếm giữ)", last);
    }

    /** Đọc chuỗi hiện có trong System Clipboard, trả về "" nếu không có dữ liệu chữ. */
    public static String getText() {
        Exception last = null;
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                Clipboard cb = clipboard();
                if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    Object data = cb.getData(DataFlavor.stringFlavor);
                    return data == null ? "" : data.toString();
                }
                return "";
            } catch (IllegalStateException busy) {
                last = busy;
                pause();
            } catch (Exception other) {
                last = other;
                pause();
            }
        }
        throw new IllegalStateException("Không đọc được Clipboard", last);
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

