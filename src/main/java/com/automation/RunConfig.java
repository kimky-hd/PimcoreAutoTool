package com.automation;

import java.util.List;

/**
 * Cấu hình một lượt chạy lấy từ giao diện.
 *
 * @param docUrls                Danh sách từ 1 đến 10 đường link Google Doc
 * @param pimcoreFolder          Tên thư mục trên cây Pimcore để chuột phải (mặc định 2025)
 * @param displayDate            Thời gian hiển thị (mặc định yyyy-MM-dd, ví dụ 2025-12-28)
 * @param syncWaitSeconds        Thời gian chờ sau khi bấm Gửi (mặc định 25 giây)
 * @param language               Giá trị chọn ở "Ngôn ngữ hiển thị"
 * @param accessChannel          Giá trị chọn ở "Access Channel"
 * @param pasteMode              Phương thức nhập liệu
 * @param pauseAfterEachArticle  Tạm dừng chờ xác nhận sau khi hoàn thành mỗi bài
 * @param saveHtmlToDocTab       Tạo thẻ 'html' và dán code vào Google Doc
 * @param pasteToAppScript       Dán code vào Web App Script (mục 'sửa source')
 */
public record RunConfig(
        List<String> docUrls,
        String pimcoreFolder,
        String displayDate,
        int syncWaitSeconds,
        String language,
        String accessChannel,
        PasteMode pasteMode,
        boolean pauseAfterEachArticle,
        boolean saveHtmlToDocTab,
        boolean pasteToAppScript) {

    public RunConfig(
            List<String> docUrls,
            String pimcoreFolder,
            String displayDate,
            int syncWaitSeconds,
            String language,
            String accessChannel,
            PasteMode pasteMode,
            boolean pauseAfterEachArticle) {
        this(docUrls, pimcoreFolder, displayDate, syncWaitSeconds, language, accessChannel,
                pasteMode, pauseAfterEachArticle, true, true);
    }

    public int articleCount() {
        return docUrls == null ? 0 : docUrls.size();
    }
}
