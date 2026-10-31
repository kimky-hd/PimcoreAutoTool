package com.automation;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ chuẩn hóa mã HTML thô từ Pimcore sang định dạng HTML Template chuẩn.
 * <p>
 * Tính năng chính:
 * 1. Xóa thuộc tính rác id, class, dir, style thừa từ Google Doc / Pimcore.
 * 2. Chuyển b và span in đậm thành strong; i và span in nghiêng thành em.
 * 3. Khắc phục triệt để lỗi thẻ &lt;/strong&gt; bị ngược/dư ngay sau &lt;h2&gt; và &lt;h3&gt;.
 * 4. Chuẩn hóa H2: &lt;h2 style="text-align:justify"&gt;&lt;strong&gt;...&lt;/strong&gt;&lt;/h2&gt;.
 * 5. Chuẩn hóa H3: &lt;h3 style="text-align:justify"&gt;&lt;span style="color:#000000"&gt;&lt;strong&gt;...&lt;/strong&gt;&lt;/span&gt;&lt;/h3&gt;.
 * 6. Chuẩn hóa ảnh: &lt;p style="text-align:center"&gt;&lt;img alt="" src="..." style="width:600px" /&gt;&lt;/p&gt;.
 * 7. Chuẩn hóa chú thích ảnh: &lt;p style="text-align:center"&gt;&lt;em&gt;...&lt;/em&gt;&lt;/p&gt;.
 * 8. Chuẩn hóa đoạn văn thường: &lt;p style="text-align:justify"&gt;...&lt;/p&gt;.
 * 9. Cách nhau đúng 1 dòng trống (\n\n) giữa các khối.
 */
public final class HtmlTemplateFormatter {

    private static final Pattern RE_STRIP_ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern RE_MULTI_SPACES = Pattern.compile("\\s+");

    private HtmlTemplateFormatter() {
    }

    /**
     * Chuẩn hóa toàn bộ mã HTML thô sang định dạng chuẩn Template.
     *
     * @param rawHtml mã HTML thô lấy từ ô Source Pimcore
     * @return mã HTML chuẩn Template sạch sẽ
     */
    public static String formatCleanTemplate(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        // Bước 1: Tiền xử lý chuỗi thô để loại bỏ thẻ </strong> bị ngược/dư xuất hiện ngay sau <h2>, <h3>
        String html = preCleanRawHtml(rawHtml);

        // Bước 2: Nạp vào Jsoup
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        Element body = doc.body();

        // Bước 3: Dọn dẹp thẻ rác không dùng (script, style, link, meta...)
        body.select("script, style, meta, link, noscript, iframe").remove();

        // Bước 4: Chuẩn hóa các thẻ inline (b -> strong, i -> em, xử lý span có style bold/italic)
        normalizeInlineElements(body);

        // Bước 5: Tách danh sách khối phần tử ở cấp trên cùng
        List<Element> blockElements = new ArrayList<>();
        collectTopLevelBlocks(body, blockElements);

        // Bước 6: Duyệt từng khối và render theo Template
        List<String> outputBlocks = new ArrayList<>();
        boolean prevBlockWasImage = false;

        for (Element block : blockElements) {
            String tag = block.tagName().toLowerCase(Locale.ROOT);

            // ---- Trường hợp A: Khối chứa ảnh <img> ----
            Elements imgs = block.select("img");
            if (!imgs.isEmpty() || tag.equals("img")) {
                List<String> imgSrcs = new ArrayList<>();
                if (tag.equals("img")) {
                    String src = block.attr("src").trim();
                    if (!src.isEmpty()) {
                        imgSrcs.add(src);
                    }
                } else {
                    for (Element img : imgs) {
                        String src = img.attr("src").trim();
                        if (!src.isEmpty()) {
                            imgSrcs.add(src);
                        }
                    }
                }

                for (String src : imgSrcs) {
                    outputBlocks.add("<p style=\"text-align:center\"><img alt=\"\" src=\"" + src + "\" style=\"width:600px\" /></p>");
                }

                // Kiểm tra xem đoạn <p> chứa ảnh có còn văn bản chú thích đi kèm không
                if (!tag.equals("img")) {
                    block.select("img").remove();
                    String remainingText = cleanPlainText(block.text());
                    if (!remainingText.isEmpty()) {
                        outputBlocks.add("<p style=\"text-align:center\"><em>" + remainingText + "</em></p>");
                        prevBlockWasImage = false; // đã có chú thích đi kèm
                        continue;
                    }
                }

                prevBlockWasImage = !imgSrcs.isEmpty();
                continue;
            }

            // ---- Trường hợp B: Heading 2 (h2 hoặc h1) ----
            if (tag.equals("h2") || tag.equals("h1")) {
                prevBlockWasImage = false;
                String inner = cleanHeadingInner(block);
                if (!inner.isEmpty()) {
                    outputBlocks.add("<h2 style=\"text-align:justify\"><strong>" + inner + "</strong></h2>");
                }
                continue;
            }

            // ---- Trường hợp C: Heading 3 (h3, h4, h5, h6) ----
            if (tag.equals("h3") || tag.equals("h4") || tag.equals("h5") || tag.equals("h6")) {
                prevBlockWasImage = false;
                String inner = cleanHeadingInner(block);
                if (!inner.isEmpty()) {
                    outputBlocks.add("<h3 style=\"text-align:justify\"><span style=\"color:#000000\"><strong>"
                            + inner + "</strong></span></h3>");
                }
                continue;
            }

            // ---- Trường hợp D: Chú thích ảnh (nằm ngay sau ảnh hoặc có chữ Nguồn hoặc in nghiêng toàn bộ) ----
            if (isImageCaption(block, prevBlockWasImage)) {
                String caption = cleanCaptionInner(block);
                if (!caption.isEmpty()) {
                    outputBlocks.add("<p style=\"text-align:center\"><em>" + caption + "</em></p>");
                }
                prevBlockWasImage = false;
                continue;
            }

            // Đã qua khối ảnh mà không phải chú thích -> tắt cờ ảnh
            prevBlockWasImage = false;

            // ---- Trường hợp E: Danh sách <ul> / <ol> ----
            if (tag.equals("ul") || tag.equals("ol")) {
                String listHtml = cleanListBlock(block, tag);
                if (!listHtml.isEmpty()) {
                    outputBlocks.add(listHtml);
                }
                continue;
            }

            // ---- Trường hợp F: Đoạn văn bản thường (<p> hoặc khối văn bản khác) ----
            String pContent = cleanParagraphInner(block);
            if (!pContent.isEmpty()) {
                outputBlocks.add("<p style=\"text-align:justify\">" + pContent + "</p>");
            }
        }

        // Bước 7: Nối các khối cách nhau đúng 1 dòng trống (\n\n)
        return String.join("\n\n", outputBlocks);
    }

    // =========================================================================
    //  HÀM XỬ LÝ CHI TIẾT
    // =========================================================================

    /**
     * Tiền xử lý chuỗi HTML thô để sửa lỗi đóng thẻ &lt;/strong&gt; bị ngược/dư,
     * đồng thời loại bỏ thẻ strong/b mở không đóng bên trong các thẻ Heading (tránh rò rỉ làm bao bọc các đoạn sau).
     */
    private static String preCleanRawHtml(String raw) {
        if (raw == null) {
            return "";
        }
        // Xử lý toàn bộ các thẻ Heading h1 - h6: gỡ bỏ toàn bộ thẻ strong/b mở & đóng bên trong
        Pattern headingPattern = Pattern.compile("(?is)(<h[1-6][^>]*>)(.*?)(</h[1-6]>)");
        Matcher m = headingPattern.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String openTag = m.group(1);
            String inner = m.group(2);
            String closeTag = m.group(3);
            String cleanInner = inner.replaceAll("(?i)</?strong[^>]*>", "").replaceAll("(?i)</?b[^>]*>", "");
            m.appendReplacement(sb, Matcher.quoteReplacement(openTag + cleanInner + closeTag));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Chuẩn hóa các thẻ b, i, span có font-weight / font-style thành strong và em.
     */
    private static void normalizeInlineElements(Element root) {
        // Chuyển <b> -> <strong>
        for (Element b : root.select("b")) {
            b.tagName("strong");
        }
        // Chuyển <i> -> <em>
        for (Element i : root.select("i")) {
            i.tagName("em");
        }

        // Duyệt span để chuyển đổi styling inline sang semantic tags
        for (Element span : root.select("span")) {
            String style = span.attr("style").toLowerCase(Locale.ROOT);
            boolean isBold = style.contains("font-weight:700") || style.contains("font-weight: 700")
                    || style.contains("font-weight:bold") || style.contains("font-weight: bold")
                    || style.contains("font-weight:800") || style.contains("font-weight:900");
            boolean isItalic = style.contains("font-style:italic") || style.contains("font-style: italic");

            if (isBold && isItalic) {
                span.tagName("strong");
                cleanAllAttributes(span);
                span.wrap("<em></em>");
            } else if (isBold) {
                span.tagName("strong");
                cleanAllAttributes(span);
            } else if (isItalic) {
                span.tagName("em");
                cleanAllAttributes(span);
            } else {
                // Span rác không mang ý nghĩa bold/italic -> mở bọc (unwrap)
                span.unwrap();
            }
        }

        // Xóa toàn bộ id, class, dir, style thừa trên strong và em
        for (Element el : root.select("strong, em")) {
            cleanAllAttributes(el);
        }
        // Xóa id, class, dir trên a, chỉ giữ lại href
        for (Element a : root.select("a")) {
            String href = a.attr("href");
            cleanAllAttributes(a);
            if (!href.isBlank()) {
                a.attr("href", href);
            }
        }
    }

    /**
     * Thu thập danh sách các khối phần tử (Block elements) cấp cao nhất.
     */
    private static void collectTopLevelBlocks(Element parent, List<Element> result) {
        for (Element child : parent.children()) {
            String tag = child.tagName().toLowerCase(Locale.ROOT);
            if (tag.equals("p") || tag.equals("h1") || tag.equals("h2") || tag.equals("h3")
                    || tag.equals("h4") || tag.equals("h5") || tag.equals("h6")
                    || tag.equals("ul") || tag.equals("ol") || tag.equals("table")
                    || tag.equals("blockquote") || tag.equals("img")) {
                result.add(child);
            } else if (tag.equals("div") || tag.equals("section") || tag.equals("article")
                    || tag.equals("main") || tag.equals("header")) {
                // Nếu div chứa các thẻ con là p, h2, h3, img... -> đệ quy lấy con
                Elements subBlocks = child.select("> p, > h1, > h2, > h3, > h4, > h5, > h6, > ul, > ol, > table, > img, > div");
                if (!subBlocks.isEmpty()) {
                    collectTopLevelBlocks(child, result);
                } else {
                    // Div chứa văn bản thuần -> coi như 1 đoạn p
                    result.add(child);
                }
            } else {
                // Các thẻ khác
                result.add(child);
            }
        }
    }

    /**
     * Lấy nội dung bên trong của Heading: loại bỏ hoàn toàn thẻ strong/b bên trong để tránh lồng nhau.
     */
    private static String cleanHeadingInner(Element heading) {
        // Sao chép element để không ảnh hưởng DOM gốc
        Element clone = heading.clone();
        // Mở bọc toàn bộ thẻ strong, b, em, i bên trong heading
        for (Element s : clone.select("strong, b")) {
            s.unwrap();
        }
        // Loại bỏ mọi thuộc tính
        for (Element el : clone.getAllElements()) {
            cleanAllAttributes(el);
        }
        String html = clone.html().trim();
        // Xóa bỏ bất kỳ ký tự hoặc thẻ thừa nào còn sót lại
        html = html.replaceAll("(?i)</?strong[^>]*>", "").replaceAll("(?i)</?b[^>]*>", "");
        html = cleanPlainText(html);
        return html;
    }

    /**
     * Kiểm tra xem khối có phải là dòng chú thích ảnh hay không.
     */
    private static boolean isImageCaption(Element block, boolean prevBlockWasImage) {
        String text = cleanPlainText(block.text()).toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return false;
        }

        // Điều kiện 1: Chứa chữ (nguồn: hoặc ảnh: hoặc nguồn ảnh
        if (text.contains("(nguồn:") || text.contains("nguồn:") || text.contains("(nguồn")
                || text.startsWith("ảnh:") || text.startsWith("nguồn") || text.contains("(ảnh:")) {
            return true;
        }

        // Điều kiện 2: Toàn bộ nội dung khối được in nghiêng (em/i)
        Elements ems = block.select("em, i");
        if (!ems.isEmpty()) {
            String emText = cleanPlainText(ems.text()).toLowerCase(Locale.ROOT);
            if (emText.equals(text) && text.length() < 300) {
                return true;
            }
        }

        // Điều kiện 3: Đoạn văn bản nằm ngay liền sau ảnh và có độ dài hợp lý cho chú thích (< 250 ký tự)
        if (prevBlockWasImage && text.length() <= 250) {
            // Không phải là tiêu đề dạng số lớn hoặc câu hỏi mở
            return !text.startsWith("bài viết") && !text.startsWith("xem thêm");
        }

        return false;
    }

    /**
     * Lấy nội dung chú thích ảnh: gỡ bỏ các thẻ em/i bao bọc sẵn để tránh lồng thẻ.
     */
    private static String cleanCaptionInner(Element captionBlock) {
        Element clone = captionBlock.clone();
        for (Element em : clone.select("em, i")) {
            em.unwrap();
        }
        for (Element el : clone.getAllElements()) {
            cleanAllAttributes(el);
        }
        return cleanPlainText(clone.text());
    }

    /**
     * Chuẩn hóa nội dung đoạn văn bản thường: giữ lại strong, em, a chuẩn, gỡ bỏ span và rác.
     */
    private static String cleanParagraphInner(Element pBlock) {
        Element clone = pBlock.clone();

        // Gỡ bỏ span rác
        for (Element span : clone.select("span")) {
            span.unwrap();
        }

        // Xóa toàn bộ thuộc tính rác trên các thẻ con
        for (Element el : clone.getAllElements()) {
            if (!el.tagName().equalsIgnoreCase("a")) {
                cleanAllAttributes(el);
            } else {
                String href = el.attr("href");
                cleanAllAttributes(el);
                if (!href.isBlank()) {
                    el.attr("href", href);
                }
            }
        }

        // Gỡ bỏ các thẻ rỗng như <strong></strong>, <em></em>
        for (Element empty : clone.select("strong:empty, em:empty, b:empty, i:empty")) {
            empty.remove();
        }

        String innerHtml = clone.html().trim();
        // Kiểm tra xem đoạn văn có chữ thật không (loại bỏ &nbsp; và ký tự tàng hình)
        String checkText = cleanPlainText(clone.text());
        if (checkText.isEmpty()) {
            return "";
        }

        return innerHtml;
    }

    /**
     * Chuẩn hóa khối danh sách <ul> / <ol>.
     */
    private static String cleanListBlock(Element listBlock, String listTag) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(listTag).append(">\n");
        int count = 0;
        for (Element li : listBlock.select("> li")) {
            Element clone = li.clone();
            cleanAllAttributes(clone);
            String itemText = cleanParagraphInner(clone);
            if (!itemText.isEmpty()) {
                sb.append("  <li style=\"text-align:justify\">").append(itemText).append("</li>\n");
                count++;
            }
        }
        sb.append("</").append(listTag).append(">");
        return count > 0 ? sb.toString() : "";
    }

    private static void cleanAllAttributes(Element el) {
        el.removeAttr("id");
        el.removeAttr("class");
        el.removeAttr("style");
        el.removeAttr("dir");
        el.removeAttr("lang");
        el.removeAttr("data-mce-style");
        el.removeAttr("contenteditable");
    }

    private static String cleanPlainText(String s) {
        if (s == null) {
            return "";
        }
        String clean = RE_STRIP_ZERO_WIDTH.matcher(s).replaceAll("");
        clean = clean.replace('\u00A0', ' ');
        clean = RE_MULTI_SPACES.matcher(clean).replaceAll(" ");
        return clean.trim();
    }
}
