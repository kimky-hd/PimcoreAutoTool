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
        assertTrue(result.contains("<p style=\"text-align:justify\">Đoạn văn này có chứa <strong>từ khóa quan trọng</strong> và <a href=\"https://example.com\">đường liên kết</a>.</p>"), "Lỗi đoạn văn cuối");
    }

    @Test
    void testTableAndHeadingLinksAndStats() {
        String rawHtml = """
                <h2>1. Bảng giá vé tại <a href="https://sungroup.com.vn" target="_blank" class="noisy">Sun World</a></h2>
                <table class="c12" style="width:100%; border:1px solid #ccc">
                  <tbody>
                    <tr class="c3"><th class="c4"><p><span style="font-weight:700">Loại vé</span></p></th><th colspan="2"><p>Giá niêm yết</p></th></tr>
                    <tr><td><p>Người lớn</p></td><td><p>850.000 VNĐ</p></td><td><p>Khứ hồi</p></td></tr>
                  </tbody>
                </table>
                """;

        String result = HtmlTemplateFormatter.formatCleanTemplate(rawHtml);
        assertTrue(result.contains("<h2 style=\"text-align:justify\"><strong>1. Bảng giá vé tại <a href=\"https://sungroup.com.vn\">Sun World</a></strong></h2>"), "Lỗi link trong H2");
        assertTrue(result.contains("<table>"), "Phải giữ thẻ table");
        assertTrue(result.contains("<th><strong>Loại vé</strong></th>"), "Phải giữ thẻ th và strong trong bảng");
        assertTrue(result.contains("<th colspan=\"2\">Giá niêm yết</th>"), "Phải giữ colspan trong bảng");
        assertTrue(result.contains("<td>850.000 VNĐ</td>"), "Phải giữ thẻ td trong bảng");

        var stats = HtmlTemplateFormatter.inspectHtmlStatsAndWarnings(result);
        assertFalse(stats.isEmpty());
        assertTrue(stats.get(0).contains("Bảng <table>=1"));

        String dirtyTitle = "Đi Phú Quốc mùa nào đẹp nhất? Kinh nghiệm A/Z: 2025 *Mới*";
        String safeKey = AutomationFlow.sanitizeObjectKey(dirtyTitle, "1a2b3c4d5e");
        assertFalse(safeKey.contains("?"));
        assertFalse(safeKey.contains("/"));
        assertFalse(safeKey.contains(":"));
        assertFalse(safeKey.contains("*"));
        assertTrue(safeKey.contains("Đi Phú Quốc mùa nào đẹp nhất"));
    }
}
