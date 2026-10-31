package com.automation;

import java.nio.file.Path;

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

    /** Báo cáo bài viết đã có HTML chuẩn hóa và file đã lưu vào output_html/. */
    void onArticleHtmlReady(int articleNo, int totalArticles, String docId, String title, String cleanHtml, Path savedFilePath);
}
