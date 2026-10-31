package com.automation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HtmlTemplateFormatterTest {

    @Test
    void testFormatCleanTemplate() {
        String sampleRawHtml = """
                <h2></strong><strong>1. Giới thiệu dự án</strong></h2>
                <p dir="ltr" id="doc-123" class="c5"><span style="font-weight:700">Pimcore</span> là nền tảng quản trị nội dung mạnh mẽ. Dưới đây là ví dụ về <span style="font-style:italic">tính năng</span> nổi bật.</p>
                <p><img src="https://cdn.example.com/images/architecture.png" style="width:100%" /></p>
                <p><em>(Nguồn: Tài liệu kỹ thuật Pimcore)</em></p>
                <h3><span style="font-size:14pt"><strong>2. Chi tiết cấu hình</strong></span></h3>
                <p>Đoạn văn này có chứa <strong>từ khóa quan trọng</strong> và <a href="https://example.com" target="_blank" class="link-item">đường liên kết</a>.</p>
                """;

        String result = HtmlTemplateFormatter.formatCleanTemplate(sampleRawHtml);
        System.out.println("=== KẾT QUẢ CHUẨN HÓA TEMPLATE ===");
        System.out.println(result);
        System.out.println("==================================");

        // Kiểm tra các quy tắc
        assertTrue(result.contains("<h2 style=\"text-align:justify\"><strong>1. Giới thiệu dự án</strong></h2>"), "Lỗi H2");
        assertFalse(result.contains("</strong><strong>"), "Lỗi thẻ strong lồng thừa");
        assertFalse(result.contains("</h2></strong>"), "Lỗi thẻ đóng ngược");
        assertTrue(result.contains("<h3 style=\"text-align:justify\"><span style=\"color:#000000\"><strong>2. Chi tiết cấu hình</strong></span></h3>"), "Lỗi H3");
        assertTrue(result.contains("<p style=\"text-align:center\"><img alt=\"\" src=\"https://cdn.example.com/images/architecture.png\" style=\"width:600px\" /></p>"), "Lỗi ảnh");
        assertTrue(result.contains("<p style=\"text-align:center\"><em>(Nguồn: Tài liệu kỹ thuật Pimcore)</em></p>"), "Lỗi chú thích ảnh");
        assertTrue(result.contains("<p style=\"text-align:justify\"><strong>Pimcore</strong> là nền tảng quản trị nội dung mạnh mẽ. Dưới đây là ví dụ về <em>tính năng</em> nổi bật.</p>"), "Lỗi đoạn văn P");
        assertTrue(result.contains("<p style=\"text-align:justify\">Đoạn văn này có chứa <strong>từ khóa quan trọng</strong> và <a href=\"https://example.com\""), "Lỗi đoạn văn cuối");
    }
}
