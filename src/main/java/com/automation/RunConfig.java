package com.automation;

/**
 * Cấu hình một lượt chạy, lấy từ giao diện.
 *
 * @param sheetUrl        URL Google Sheet (để trống = dùng tab Sheet đang mở)
 * @param pimcoreFolder   Tên thư mục trên cây Pimcore để chuột phải (mặc định 2025)
 * @param docLinkColumn   Cột chứa link Google Doc trên Sheet (ví dụ B). Để trống = không chọn ô qua Name Box
 * @param startRow        Dòng của bài đầu tiên trên Sheet
 * @param displayDate     Thời gian hiển thị (mặc định 28/12/2025)
 * @param syncWaitSeconds Thời gian chờ sau khi bấm Gửi (mặc định 25 giây)
 * @param articleCount    Số bài cần chạy lặp lại
 * @param language        Giá trị chọn ở "Ngôn ngữ hiển thị"
 * @param accessChannel   Giá trị chọn ở "Access Channel"
 * @param pasteMode       Phương thức nhập liệu
 */
public record RunConfig(
        String sheetUrl,
        String pimcoreFolder,
        String docLinkColumn,
        int startRow,
        String displayDate,
        int syncWaitSeconds,
        int articleCount,
        String language,
        String accessChannel,
        PasteMode pasteMode) {
}

