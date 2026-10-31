package com.automation;

/** Một bước trong kịch bản 27 bước bị lỗi – dừng toàn bộ lượt chạy để người dùng kiểm tra. */
public class StepFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int step;

    public StepFailedException(int step, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
    }

    public int getStep() {
        return step;
    }
}

