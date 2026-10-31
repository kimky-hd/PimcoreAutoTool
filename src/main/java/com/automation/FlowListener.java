package com.automation;

/** Kênh báo cáo từ luồng automation lên giao diện. */
public interface FlowListener {

    /** Một dòng log. */
    void onLog(String message);

    /** Đang bắt đầu một bước. */
    void onStep(int articleNo, int totalArticles, int stepNo, int totalSteps, String title);

    /** Cập nhật dòng trạng thái (ví dụ đếm ngược). */
    void onStatus(String status);

    /** Kết thúc lượt chạy. */
    void onFinished(boolean success, String summary);
}

