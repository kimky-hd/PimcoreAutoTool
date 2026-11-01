package com.automation;

import org.openqa.selenium.Keys;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kịch bản tự động hóa luồng bài viết (hỗ trợ từ 1 đến 10 Google Doc):
 * - Bước A: Mở Google Doc -> lấy tiêu đề -> đóng ngay tab để giải phóng RAM.
 * - Bước B: Tạo bài trên Pimcore (Title, SEO Title, Date, Language, Channel).
 * - Bước C: Đồng bộ dữ liệu bằng docId -> chờ đếm ngược 25 giây.
 * - Bước D: Lấy Source HTML thô -> đóng tab Pimcore -> chuẩn hóa Template bằng Jsoup
 *           -> lưu file output_html/ -> dán vào App Script (tạm dừng nếu có bật).
 */
public class AutomationFlow implements Runnable {

    public static final int TOTAL_STEPS = 25;

    private static final Pattern DOC_ID_EDIT = Pattern.compile("/d/([^/]+)/edit");
    private static final Pattern DOC_ID_ANY = Pattern.compile("/document/(?:u/\\d+/)?d/([a-zA-Z0-9_-]+)");

    /** Quét DOM trình soạn thảo Google Doc (chế độ render HTML) để tìm chữ đậm đầu tiên. */
    private static final String JS_DOC_EDITOR_FIRST_BOLD = """
            var root = document.querySelector('.kix-appview-editor');
            if (!root) { return ''; }
            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
            while (walker.nextNode()) {
                var node = walker.currentNode;
                var txt = (node.nodeValue || '').replace(/[\\u200B-\\u200D\\uFEFF\\u00A0]/g, ' ').trim();
                if (!txt) { continue; }
                var el = node.parentElement;
                if (!el || !el.closest('.kix-lineview')) { continue; }
                var fw = window.getComputedStyle(el).fontWeight;
                var num = parseInt(fw, 10);
                if (fw === 'bold' || fw === 'bolder' || num >= 600) {
                    var block = el.closest('.kix-paragraphrenderer') || el.closest('.kix-lineview') || el;
                    return (block.innerText || block.textContent || '').replace(/[\\u200B-\\u200D\\uFEFF]/g, '').replace(/\\s+/g, ' ').trim();
                }
            }
            return '';
            """;

    /** Quét trang mobilebasic (bản HTML thuần của Google Doc) để tìm chữ đậm đầu tiên. */
    private static final String JS_MOBILEBASIC_FIRST_BOLD = """
            var root = document.querySelector('.doc-content') || document.querySelector('#contents') || document.body;
            var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
            while (walker.nextNode()) {
                var node = walker.currentNode;
                var txt = (node.nodeValue || '').replace(/[\\u200B-\\u200D\\uFEFF\\u00A0]/g, ' ').trim();
                if (!txt) { continue; }
                var el = node.parentElement;
                if (!el) { continue; }
                var tag = el.tagName.toUpperCase();
                if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'TITLE') { continue; }
                var fw = window.getComputedStyle(el).fontWeight;
                var num = parseInt(fw, 10);
                if (fw === 'bold' || fw === 'bolder' || num >= 600) {
                    var block = el.closest('p,h1,h2,h3,h4,h5,h6,li') || el;
                    return (block.innerText || block.textContent || '').replace(/[\\u200B-\\u200D\\uFEFF]/g, '').replace(/\\s+/g, ' ').trim();
                }
            }
            return '';
            """;

    @FunctionalInterface
    private interface StepBody {
        void run() throws Exception;
    }

    private final RunConfig config;
    private final Selectors sel;
    private final FlowListener listener;
    private final RunControl control = new RunControl();

    private volatile ChromeDriverService service;
    private ChromeDriver driver;
    private BrowserHelper ui;

    // Trạng thái tab dùng chung
    private String pimcoreHandle;
    private String appScriptHandle;

    // Trạng thái bài viết đang xử lý
    private int articleIndex;
    private String currentDocUrl;
    private String docId;
    private String articleTitle;
    private String docTabHandle; // Giữ tab Google Doc của bài viết hiện tại để tái sử dụng ngay ở Bước 21
    private String sourceHtml;
    private String cleanHtml;
    private int completedArticles;
    private final java.util.List<String> failedArticles = new java.util.ArrayList<>();

    public AutomationFlow(RunConfig config, Selectors selectors, FlowListener listener) {
        this.config = config;
        this.sel = selectors;
        this.listener = listener;
    }

    public RunControl control() {
        return control;
    }

    /** Dừng khẩn cấp cứng: tắt ChromeDriver để huỷ lệnh Selenium đang treo (Chrome vẫn giữ nguyên). */
    public void forceStop() {
        control.requestStop();
        ChromeDriverService s = service;
        if (s != null) {
            try {
                s.stop();
            } catch (RuntimeException ignored) {
                // đã dừng
            }
        }
    }

    // =====================================================================
    //  VÒNG ĐỜI
    // =====================================================================

    @Override
    public void run() {
        control.bindWorker(Thread.currentThread());
        boolean success = false;
        String summary = "";
        try {
            log("==================== BẮT ĐẦU CHẠY ====================");
            log("Selectors: " + sel.getSource());
            log("Cấu hình: Số lượng bài=" + config.articleCount()
                    + " | Thư mục Pimcore=" + config.pimcoreFolder()
                    + " | Thời gian=" + config.displayDate()
                    + " | Chờ Sync=" + config.syncWaitSeconds() + "s"
                    + " | Lưu tab Google Doc=" + (config.saveHtmlToDocTab() ? "CÓ" : "KHÔNG")
                    + " | Dán App Script=" + (config.pasteToAppScript() ? "CÓ" : "KHÔNG")
                    + " | Nhập liệu=" + config.pasteMode()
                    + " | Tạm dừng sau mỗi bài=" + (config.pauseAfterEachArticle() ? "CÓ" : "KHÔNG"));
            connect();

            for (articleIndex = 0; articleIndex < config.articleCount(); articleIndex++) {
                control.checkpoint();
                resetArticleState();
                currentDocUrl = config.docUrls().get(articleIndex).trim();

                log("");
                log("-------------------- BÀI " + (articleIndex + 1) + "/" + config.articleCount()
                        + " --------------------");
                log("Link Google Doc: " + currentDocUrl);

                try {
                    runArticle();
                    completedArticles++;
                    log("==> HOÀN THÀNH BÀI " + (articleIndex + 1) + ": \"" + articleTitle + "\"");
                } catch (AutomationStoppedException e) {
                    throw e;
                } catch (StepFailedException e) {
                    if (control.isStopRequested()) {
                        throw new AutomationStoppedException();
                    }
                    String errItem = "Bài " + (articleIndex + 1) + " (Bước " + e.getStep() + ": " + e.getMessage() + ")";
                    failedArticles.add(errItem);
                    log("==> [BỎ QUA BÀI LỖI] " + errItem);
                    if (articleIndex < config.articleCount() - 1) {
                        log("    Đang dọn dẹp trạng thái trình duyệt để tiếp tục chạy Bài " + (articleIndex + 2) + "...");
                        recoverBrowserStateAfterArticleError();
                    }
                } catch (Exception e) {
                    if (control.isStopRequested()) {
                        throw new AutomationStoppedException();
                    }
                    String errItem = "Bài " + (articleIndex + 1) + " (" + BrowserHelper.shortMessage(e) + ")";
                    failedArticles.add(errItem);
                    log("==> [BỎ QUA BÀI LỖI] " + errItem);
                    if (articleIndex < config.articleCount() - 1) {
                        log("    Đang dọn dẹp trạng thái trình duyệt để tiếp tục chạy Bài " + (articleIndex + 2) + "...");
                        recoverBrowserStateAfterArticleError();
                    }
                }

                // Nghỉ nhẹ giữa các bài
                if (articleIndex < config.articleCount() - 1) {
                    control.sleep(sel.getInt("timing.betweenArticlesMs", 2000));
                }
            }
            if (failedArticles.isEmpty()) {
                success = true;
                summary = "Hoàn thành toàn bộ " + completedArticles + "/" + config.articleCount() + " bài viết.";
            } else {
                success = completedArticles > 0;
                summary = "Hoàn thành " + completedArticles + "/" + config.articleCount()
                        + " bài. Thất bại " + failedArticles.size() + " bài: " + String.join(" | ", failedArticles);
            }
        } catch (AutomationStoppedException e) {
            summary = "ĐÃ DỪNG theo yêu cầu. Hoàn thành " + completedArticles + "/" + config.articleCount() + " bài.";
        } catch (Exception e) {
            if (control.isStopRequested()) {
                summary = "ĐÃ DỪNG theo yêu cầu. Hoàn thành " + completedArticles + "/" + config.articleCount() + " bài.";
            } else {
                summary = "LỖI KẾT NỐI: " + BrowserHelper.shortMessage(e);
            }
        } finally {
            Thread.interrupted();
            disconnect();
        }
        log("==================== KẾT THÚC: " + summary);
        listener.onFinished(success, summary);
    }

    /** Dọn dẹp tab Google Doc dư thừa hoặc frame bị kẹt khi 1 bài bị lỗi giữa chừng để bài tiếp theo chạy sạch. */
    private void recoverBrowserStateAfterArticleError() {
        try {
            driver.switchTo().defaultContent();
        } catch (Exception ignored) {
        }
        try {
            if (docId != null && !docId.isBlank()) {
                for (String handle : driver.getWindowHandles()) {
                    try {
                        driver.switchTo().window(handle);
                        if (driver.getCurrentUrl().contains(docId)) {
                            driver.close();
                            log("    Đã đóng tab Google Doc của bài lỗi.");
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            switchToPimcore();
        } catch (Exception ignored) {
        }
    }

    private void connect() {
        if (!ChromeLauncher.isDebugPortOpen()) {
            throw new IllegalStateException("Không kết nối được Chrome Debug tại " + ChromeLauncher.DEBUGGER_ADDRESS
                    + ". Hãy bấm '1. Mở Chrome Debug' trước và đăng nhập sẵn các trang.");
        }
        log("Đang kết nối Selenium vào Chrome tại " + ChromeLauncher.DEBUGGER_ADDRESS + " ...");
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", ChromeLauncher.DEBUGGER_ADDRESS);
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.ACCEPT);
        service = new ChromeDriverService.Builder().build();
        driver = new ChromeDriver(service, options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        ui = new BrowserHelper(driver, sel, control, this::log);
        log("Kết nối thành công. Số tab đang mở trên Chrome: " + driver.getWindowHandles().size());
    }

    /** Chỉ tắt ChromeDriver, KHÔNG đóng Chrome của người dùng. */
    private void disconnect() {
        ChromeDriverService s = service;
        service = null;
        if (s != null) {
            try {
                s.stop();
                log("Đã ngắt kết nối Selenium (Chrome vẫn giữ nguyên các tab).");
            } catch (RuntimeException e) {
                log("Ngắt kết nối: " + BrowserHelper.shortMessage(e));
            }
        }
    }

    private void resetArticleState() {
        currentDocUrl = null;
        docId = null;
        articleTitle = null;
        docTabHandle = null;
        sourceHtml = null;
        cleanHtml = null;
    }

    // =====================================================================
    //  24 BƯỚC THỰC THI (A -> B -> C -> D)
    // =====================================================================

    private void runArticle() {
        final PasteMode mode = config.pasteMode();
        final int defaultTimeout = ui.defaultTimeout();
        final int pageLoadTimeout = sel.getInt("timing.pageLoadTimeoutSec", 40);

        // ---------------- BƯỚC A: MỞ GOOGLE DOC & LẤY DỮ LIỆU ----------------
        step(1, "Trích xuất Doc ID & Mở tab Google Doc", () -> {
            docId = extractDocId(currentDocUrl);
            if (docId == null || docId.isBlank()) {
                throw new IllegalStateException("Không trích xuất được ID từ link: " + currentDocUrl);
            }
            log("   Doc ID: " + docId);
            log("   Mở tab Google Doc mới trên Chrome...");
            driver.switchTo().newWindow(WindowType.TAB);
            docTabHandle = driver.getWindowHandle();
            driver.get(currentDocUrl);
            ui.waitUrlContains("/document/", pageLoadTimeout);
            ui.waitPageReady(pageLoadTimeout);
            ui.waitVisible("doc.readyIndicator", pageLoadTimeout);
            log("   Đã tải xong tab Google Doc: " + driver.getTitle());
        });

        step(2, "Copy tiêu đề bôi đậm đầu tiên & Chuyển sang Pimcore", () -> {
            articleTitle = readFirstBoldTitle();
            if (articleTitle == null || articleTitle.isBlank()) {
                throw new IllegalStateException("Không tìm thấy dòng chữ bôi đậm nào trong bài viết");
            }
            ClipboardUtil.setText(articleTitle);
            log("   articleTitle = \"" + articleTitle + "\" (đã đưa vào Clipboard)");

            // Tối ưu hóa: Nếu KHÔNG bật tùy chọn tạo thẻ HTML trong Google Doc, đóng ngay để tiết kiệm RAM.
            // Nếu CÓ bật tùy chọn tạo thẻ HTML, giữ tab mở ngầm để Bước 21 dán tức thì không cần load lại Doc.
            if (!config.saveHtmlToDocTab()) {
                try {
                    driver.close();
                    docTabHandle = null;
                    log("   Đã đóng tab Google Doc để giải phóng bộ nhớ RAM.");
                } catch (WebDriverException ignored) {
                }
            } else {
                log("   Giữ tab Google Doc mở ngầm để lát nữa cập nhật thẻ HTML (tiết kiệm thời gian tải lại).");
            }

            // Chuyển lại ngay về tab Pimcore
            switchToPimcore();
        });

        // ---------------- BƯỚC B: TẠO BÀI & CẤU HÌNH TRÊN PIMCORE ----------------
        step(3, "Chuyển sang tab Pimcore CMS", () -> {
            switchToPimcore();
            log("   Đang ở tab Pimcore: " + driver.getTitle());
        });

        step(4, "Chuột phải thư mục \"" + config.pimcoreFolder() + "\" -> Add Object -> News", () -> {
            WebElement folder = ui.waitVisible("pimcore.treeFolder", "folder", config.pimcoreFolder());
            ui.contextClick(folder);
            log("   Đã click chuột phải vào thư mục \"" + config.pimcoreFolder() + "\"");
            control.sleep(500);
            ui.clickKey("pimcore.menuAddObject");
            log("   Đã chọn \"Add Object\"");
            control.sleep(500);
            ui.clickKey("pimcore.menuNewsClass");
            log("   Đã chọn \"News\"");

            if (sel.getBool("pimcore.addObject.handlePrompt", true)) {
                WebElement keyInput = ui.tryWaitVisible("pimcore.addObject.promptInput",
                        sel.getInt("timing.promptWaitSec", 5));
                if (keyInput != null) {
                    String safeKey = sanitizeObjectKey(articleTitle, docId);
                    log("   Pimcore hỏi tên object -> dán tên đã lọc ký tự đặc biệt: \"" + safeKey + "\"");
                    ui.pasteText(keyInput, safeKey, true, mode);
                    control.sleep(300);
                    ui.clickKey("pimcore.addObject.promptOk");
                    log("   Đã bấm OK");
                } else {
                    log("   Không thấy hộp thoại hỏi tên object -> tiếp tục");
                }
            }
            control.sleep(800);
            ui.waitUntilGone("pimcore.loadingMask", sel.getInt("timing.saveMaskTimeoutSec", 90));
        });

        step(5, "Click ô \"Tiêu đề\" và Paste articleTitle", () -> {
            WebElement titleInput = ui.waitVisible("pimcore.titleInput", Math.max(defaultTimeout, 30));
            ui.pasteText(titleInput, articleTitle, true, mode);
        });

        step(6, "Click tab \"SEO\"", () -> ui.clickKey("pimcore.tabSeo"));

        step(7, "Click ô \"SEO Title\" và Paste articleTitle", () -> {
            WebElement seoInput = ui.waitVisible("pimcore.seoTitleInput");
            ui.pasteText(seoInput, articleTitle, true, mode);
        });

        step(8, "Click \"Save & Publish\" (chờ xử lý xong)", this::clickSaveAndPublish);

        step(9, "Click quay lại tab \"Base\"", () -> ui.clickKey("pimcore.tabBase"));

        step(10, "Chọn \"Thời gian\" = " + config.displayDate(), () -> {
            WebElement dateInput = ui.waitVisible("pimcore.dateInput");
            ui.typeText(dateInput, config.displayDate(), true);
            control.sleep(200);
            ui.press(Keys.TAB);
            control.sleep(300);
            log("   Giá trị ô Thời gian sau khi nhập: " + BrowserHelper.preview(ui.readEditorValue(dateInput)));
        });

        step(11, "Chọn \"" + config.language() + "\" ở \"Ngôn ngữ hiển thị\"",
                () -> selectFromList("pimcore.languageTrigger", "pimcore.languageItem", config.language()));

        step(12, "Chọn \"" + config.accessChannel() + "\" ở \"Access Channel\"",
                () -> selectFromList("pimcore.accessChannelTrigger", "pimcore.accessChannelItem", config.accessChannel()));

        step(13, "Click \"Save & Publish\"", this::clickSaveAndPublish);

        // ---------------- BƯỚC C: ĐỒNG BỘ GOOGLE DOC TRÊN PIMCORE ----------------
        step(14, "Click mục \"Sync data Google doc\"", () -> ui.clickKeyInAnyFrame("pimcore.syncGoogleDocMenu"));

        step(15, "Paste ID bài viết vào ô \"ID Google doc\"", () -> {
            control.sleep(500);
            WebElement idInput = ui.waitVisibleInAnyFrame("pimcore.syncGoogleDocIdInput");
            ui.pasteText(idInput, docId, true, mode);
        });

        step(16, "Ấn nút \"Gửi\" & Tự động thăm dò đồng bộ (Dynamic Polling)", () -> {
            ui.clickKeyInAnyFrame("pimcore.syncSendButton");
            log("   Đã bấm \"Gửi\". Bắt đầu tự động kiểm tra đồng bộ dữ liệu (không cần đợi cứng)...");
            driver.switchTo().defaultContent();

            // Chờ tối thiểu 4 giây đầu tiên để Pimcore bắt đầu gửi request đồng bộ
            control.sleep(4000);

            // Tự động thăm dò nội dung bài viết hoặc Source: nếu Pimcore tải xong sớm là đi tiếp ngay lập tức
            int maxCheckSeconds = Math.max(config.syncWaitSeconds(), 25);
            boolean syncReady = false;
            long deadline = System.currentTimeMillis() + (maxCheckSeconds * 1000L);

            while (System.currentTimeMillis() < deadline) {
                control.checkpoint();
                try {
                    // Kiểm tra khung soạn thảo bài viết xem đã có nội dung chữ hoặc ảnh đổ vào chưa
                    WebElement body = ui.tryWaitVisibleInAnyFrame("pimcore.articleBody", 1);
                    if (body != null) {
                        String text = body.getText().trim();
                        int imgTags = body.findElements(org.openqa.selenium.By.tagName("img")).size();
                        if (text.length() > 20 || imgTags > 0) {
                            log("   [ĐỒNG BỘ XONG SỚM] Phát hiện Pimcore đã nhận dữ liệu (" + text.length() + " ký tự, "
                                    + imgTags + " ảnh) -> Đi tiếp ngay!");
                            syncReady = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                long remain = Math.max(0, (deadline - System.currentTimeMillis()) / 1000);
                listener.onStatus("Bài " + (articleIndex + 1) + " - Đang chờ Sync (còn tối đa " + remain + "s)...");
                control.sleep(1500);
            }

            if (!syncReady) {
                log("   Hết thời gian thăm dò Sync (" + maxCheckSeconds + "s), chuyển sang kiểm tra ô Source.");
            }
            driver.switchTo().defaultContent();
        });

        // ---------------- BƯỚC D: LẤY SOURCE, CHUẨN HÓA TEMPLATE & DÁN APP SCRIPT ----------------
        step(17, "Ấn vào khung nội dung bài viết", () -> {
            WebElement body = ui.waitVisibleInAnyFrame("pimcore.articleBody");
            ui.click(body);
            control.sleep(500);
        });

        step(18, "Ấn nút \"Source\"", () -> {
            ui.clickKeyInAnyFrame("pimcore.sourceButton");
            control.sleep(800);
        });

        step(19, "Lấy toàn bộ mã HTML trong ô Source & Đóng tab Object Pimcore", () -> {
            WebElement sourceBox = ui.waitVisibleInAnyFrame("pimcore.sourceTextarea");
            try {
                sourceHtml = ui.copyAllFrom(sourceBox);
            } catch (Exception ignored) {
                sourceHtml = "";
            }

            // Cơ chế Smart Polling: nếu bài viết nặng (nhiều ảnh) khiến Pimcore chưa kịp đổ HTML ra sau 25s,
            // tự động đóng Source chờ thêm tối đa 6 lần x 5s = 30s rồi mở lại đọc.
            int maxRetries = 6;
            for (int retry = 1; (sourceHtml == null || sourceHtml.isBlank()) && retry <= maxRetries; retry++) {
                log("   [CHỜ THÊM SYNC - Lần " + retry + "/" + maxRetries
                        + "] Ô Source chưa có dữ liệu -> đóng Source chờ thêm 5 giây để Pimcore tải nốt bài/ảnh...");
                listener.onStatus("Bài " + (articleIndex + 1) + " - Bước 19: Chờ thêm Sync (" + retry + "/" + maxRetries + ")...");
                try {
                    driver.switchTo().defaultContent();
                    ui.clickKeyInAnyFrame("pimcore.sourceButton");
                } catch (Exception ignored) {
                }
                control.sleep(5000);
                driver.switchTo().defaultContent();
                ui.clickKeyInAnyFrame("pimcore.sourceButton");
                control.sleep(800);
                sourceBox = ui.waitVisibleInAnyFrame("pimcore.sourceTextarea");
                try {
                    sourceHtml = ui.copyAllFrom(sourceBox);
                } catch (Exception ignored) {
                    sourceHtml = "";
                }
            }

            if (sourceHtml == null || sourceHtml.isBlank()) {
                throw new IllegalStateException("Ô Source vẫn rỗng sau khi đã chờ thêm 30 giây - vui lòng kiểm tra lại ID Google Doc hoặc quyền truy cập file Doc");
            }
            log("   Đã lấy " + sourceHtml.length() + " ký tự HTML thô.");

            if (!sel.isBlank("pimcore.sourceDialogClose")) {
                ui.clickKeyInAnyFrame("pimcore.sourceDialogClose");
                log("   Đã đóng hộp thoại Source");
            }
            driver.switchTo().defaultContent();

            // Đóng tab Object bài viết trên giao diện Pimcore để giải phóng DOM tránh lag khi chạy 10 bài
            if (!sel.isBlank("pimcore.closeActiveTab")) {
                WebElement closeTab = ui.tryWaitVisible("pimcore.closeActiveTab", 2);
                if (closeTab != null) {
                    try {
                        ui.click(closeTab);
                        log("   Đã đóng tab Object trên Pimcore để giải phóng DOM.");
                    } catch (WebDriverException ignored) {
                        // bỏ qua nếu tab tự đóng
                    }
                }
            }
        });

        step(20, "Chuẩn hóa HTML Template bằng Jsoup & Xuất file output_html/", () -> {
            cleanHtml = HtmlTemplateFormatter.formatCleanTemplate(sourceHtml);
            if (cleanHtml.isBlank()) {
                throw new IllegalStateException("Kết quả chuẩn hóa HTML bị rỗng");
            }

            for (String statLine : HtmlTemplateFormatter.inspectHtmlStatsAndWarnings(cleanHtml)) {
                log("   " + statLine);
            }

            ClipboardUtil.setText(cleanHtml);
            log("   Đã chuẩn hóa " + cleanHtml.length() + " ký tự HTML Template (đã đưa vào Clipboard).");

            // Lưu file HTML vào thư mục output_html/
            Path outputDir = Paths.get(System.getProperty("user.dir"), "output_html");
            Files.createDirectories(outputDir);
            String safeId = docId.replaceAll("[^a-zA-Z0-9_-]", "");
            String fileName = "bai_" + (articleIndex + 1) + "_" + safeId + ".html";
            Path filePath = outputDir.resolve(fileName);
            Files.writeString(filePath, cleanHtml, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log("   [ĐÃ LƯU FILE]: " + filePath.toAbsolutePath());
            listener.onArticleHtmlReady(articleIndex + 1, config.articleCount(), docId, articleTitle, cleanHtml, filePath);
        });

        step(21, "Mở lại Google Doc, tạo thẻ 'html' & dán clean HTML vào", () -> {
            if (!config.saveHtmlToDocTab()) {
                log("   [BỎ QUA] Tùy chọn 'Tạo thẻ html trong Google Doc' đang tắt.");
                return;
            }
            saveHtmlToGoogleDocTab(currentDocUrl, cleanHtml);
        });

        step(22, "Chuyển sang tab \"Web App Script\"", () -> {
            if (!config.pasteToAppScript()) {
                log("   [BỎ QUA] Tùy chọn 'Dán vào App Script' đang tắt.");
                return;
            }
            String handle = ui.handleExists(appScriptHandle) ? appScriptHandle
                    : ui.findTab(sel.getList("tab.appscript.urlContains"), sel.getList("tab.appscript.titleContains"));
            if (handle == null) {
                throw new IllegalStateException("Không tìm thấy tab Web App Script đang mở (xem tab.appscript.* trong selectors.properties)");
            }
            ui.activate(handle);
            appScriptHandle = handle;
            log("   Đang ở tab: " + driver.getTitle());
        });

        step(23, "Ấn vào mục \"sửa source\" bên trái", () -> {
            if (!config.pasteToAppScript()) {
                log("   [BỎ QUA] Tùy chọn 'Dán vào App Script' đang tắt.");
                return;
            }
            ui.clickKeyInAnyFrame("appscript.menuEditSource");
            control.sleep(800);
        });

        step(24, "Xoá nội dung cũ và Paste clean HTML vào ô soạn thảo", () -> {
            if (!config.pasteToAppScript()) {
                log("   [BỎ QUA] Tùy chọn 'Dán vào App Script' đang tắt.");
                return;
            }
            WebElement editor = ui.waitVisibleInAnyFrame("appscript.sourceEditor");
            ui.pasteText(editor, cleanHtml, true, mode);
            log("   Đã dán " + cleanHtml.length() + " ký tự HTML chuẩn hóa vào mục \"sửa source\"");
            driver.switchTo().defaultContent();
        });

        step(25, "Kiểm tra tạm dừng chờ xác nhận (nếu bật tùy chọn)", () -> {
            if (config.pauseAfterEachArticle() && articleIndex < config.articleCount() - 1) {
                log("==========================================================================");
                log("[TẠM DỪNG] Đã hoàn thành Bài " + (articleIndex + 1) + "/" + config.articleCount()
                        + ". Tool tạm dừng chờ bạn kiểm tra kết quả.");
                log(">> Sau khi kiểm tra xong, hãy bấm nút 'Tiếp tục' trên Tool để chạy bài kế tiếp.");
                log("==========================================================================");
                listener.onStatus("TẠM DỪNG: Đã xong Bài " + (articleIndex + 1) + ". Bấm Tiếp tục để chạy bài " + (articleIndex + 2));
                control.setPaused(true);
                control.checkpoint(); // Dừng luồng tại đây cho đến khi người dùng bấm Tiếp tục
            } else {
                log("   Hoàn tất bài " + (articleIndex + 1) + ".");
            }
        });
    }

    /** Mở hoặc kích hoạt lại Google Doc, tạo hoặc chọn thẻ 'html' và paste nội dung HTML chuẩn hóa vào. */
    private void saveHtmlToGoogleDocTab(String docUrl, String cleanHtml) {
        String existingHandle = null;

        // Ưu tiên 1: Dùng ngay tab Google Doc đã mở từ Bước 1 (không cần load lại từ đầu, tiết kiệm 5-8 giây)
        if (docTabHandle != null && ui.handleExists(docTabHandle)) {
            existingHandle = docTabHandle;
            ui.activate(existingHandle);
            log("   [TỐI ƯU TỐC ĐỘ] Kích hoạt ngay tab Google Doc có sẵn: " + driver.getTitle());
        } else {
            // Ưu tiên 2: Tìm trong danh sách window handles
            for (String handle : driver.getWindowHandles()) {
                try {
                    driver.switchTo().window(handle);
                    if (driver.getCurrentUrl().contains(docId)) {
                        existingHandle = handle;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
            if (existingHandle != null) {
                ui.activate(existingHandle);
                log("   Kích hoạt tab Google Doc hiện có: " + driver.getTitle());
            } else {
                log("   Mở lại Google Doc trên tab mới: " + docUrl);
                driver.switchTo().newWindow(WindowType.TAB);
                existingHandle = driver.getWindowHandle();
                driver.get(docUrl);
                ui.waitVisible("doc.readyIndicator");
            }
        }
        control.sleep(800); // Chờ Google Docs ổn định DOM

        // 1. Kiểm tra xem sidebar Thẻ và đường viền có đang mở không
        List<WebElement> addBtns = driver.findElements(sel.by("doc.addTabBtn"));
        boolean sidebarOpen = !addBtns.isEmpty() && addBtns.get(0).isDisplayed();
        if (!sidebarOpen) {
            log("   Sidebar thẻ đang đóng -> tìm nút mở...");
            List<WebElement> openBtns = driver.findElements(sel.by("doc.tabsSidebarOpenBtn"));
            for (WebElement b : openBtns) {
                if (b.isDisplayed()) {
                    try {
                        b.click();
                        control.sleep(1000);
                        break;
                    } catch (Exception ex) {
                        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", b);
                        control.sleep(1000);
                        break;
                    }
                }
            }
        }

        // 2. Kiểm tra xem đã có thẻ tên 'html' chưa
        List<WebElement> chapters = driver.findElements(sel.by("doc.chapterItems"));
        WebElement targetChapter = null;
        for (WebElement c : chapters) {
            String aria = c.getAttribute("aria-label");
            List<WebElement> labels = c.findElements(org.openqa.selenium.By.cssSelector(".chapter-label-content"));
            String text = !labels.isEmpty() ? labels.get(0).getText().trim() : (aria != null ? aria : "");
            if ("html".equalsIgnoreCase(text)) {
                targetChapter = c;
                break;
            }
        }

        org.openqa.selenium.interactions.Actions actions = new org.openqa.selenium.interactions.Actions(driver);

        if (targetChapter != null) {
            log("   Đã có sẵn thẻ 'html' trong Google Doc -> chuyển sang thẻ này để cập nhật...");
            targetChapter.click();
            control.sleep(800);
        } else {
            log("   Chưa có thẻ 'html' -> bấm nút 'Thêm thẻ'...");
            WebElement addBtn = ui.waitVisible("doc.addTabBtn");
            addBtn.click();
            control.sleep(1500);

            // Tìm thẻ mới nhất (ở cuối danh sách)
            chapters = driver.findElements(sel.by("doc.chapterItems"));
            if (chapters.isEmpty()) {
                throw new IllegalStateException("Không tìm thấy danh sách thẻ sau khi bấm Thêm thẻ");
            }
            WebElement newChapter = chapters.get(chapters.size() - 1);

            // Chuột phải vào thẻ mới để mở menu Đổi tên
            actions.contextClick(newChapter).perform();
            control.sleep(800);

            // Click vào menu 'Đổi tên' / 'Rename'
            List<WebElement> menuItems = driver.findElements(sel.by("doc.renameMenuItem"));
            boolean clickedRename = false;
            for (WebElement item : menuItems) {
                if (item.isDisplayed()) {
                    item.click();
                    clickedRename = true;
                    break;
                }
            }
            if (!clickedRename && !menuItems.isEmpty()) {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", menuItems.get(menuItems.size() - 1));
            }
            control.sleep(600);

            // Gõ tên 'html' và Enter
            Keys mod = BrowserHelper.modifierKey();
            WebElement active = driver.switchTo().activeElement();
            active.sendKeys(Keys.chord(mod, "a"));
            active.sendKeys("html");
            active.sendKeys(Keys.ENTER);
            control.sleep(800);
            log("   Đã tạo và đổi tên thẻ mới thành 'html' thành công.");
        }

        // 3. Focus vào vùng soạn thảo (editor) của thẻ 'html' và dán code HTML
        WebElement editor = ui.waitVisible("doc.editor");
        actions.moveToElement(editor).click().perform();
        control.sleep(400);

        ClipboardUtil.setText(cleanHtml);
        Keys mod = BrowserHelper.modifierKey();
        actions.keyDown(mod).sendKeys("a").keyUp(mod).perform();
        control.sleep(300);
        actions.keyDown(mod).sendKeys("v").keyUp(mod).perform();
        control.sleep(1500); // Chờ Google Docs tự động lưu lên Cloud
        log("   [ĐÃ LƯU GOOGLE DOC] Đã dán thành công " + cleanHtml.length() + " ký tự HTML vào thẻ 'html'!");

        // Đóng tab nếu chưa phải bài cuối và có nhiều bài để tiết kiệm RAM
        if (articleIndex < config.articleCount() - 1) {
            try {
                driver.close();
                log("   Đã đóng tab Google Doc để giải phóng RAM cho bài tiếp theo.");
            } catch (Exception ignored) {
            }
        }
    }

    // =====================================================================
    //  HÀM HỖ TRỢ CHO CÁC BƯỚC
    // =====================================================================

    /** Thực thi một bước: log bắt đầu / thành công / lỗi (kèm selector) và chụp màn hình khi lỗi. */
    private void step(int no, String title, StepBody body) {
        control.checkpoint();
        listener.onStep(articleIndex + 1, config.articleCount(), no, TOTAL_STEPS, title);
        log(">> [Bài " + (articleIndex + 1) + "/" + config.articleCount() + "] Bước " + no + "/" + TOTAL_STEPS + ": " + title);
        long start = System.currentTimeMillis();
        try {
            body.run();
        } catch (AutomationStoppedException e) {
            throw e;
        } catch (UnhandledAlertException e) {
            log("   [ALERT BROWSER] Tự động đóng popup thông báo: \"" + e.getAlertText() + "\" và tiếp tục bước " + no + "...");
            if (ui != null) {
                ui.handleAlertIfPresent(true);
            }
            try {
                body.run();
            } catch (Exception retryEx) {
                handleStepFailure(no, retryEx);
            }
        } catch (Exception e) {
            handleStepFailure(no, e);
        }
        log("   [OK] Bước " + no + " hoàn tất (" + (System.currentTimeMillis() - start) + " ms)");
        control.sleep(sel.getInt("timing.actionDelayMs", 700));
    }

    private void handleStepFailure(int no, Exception e) {
        if (control.isStopRequested()) {
            throw new AutomationStoppedException();
        }
        String reason;
        if (e instanceof SelectorException se) {
            reason = "LỖI " + firstLine(se.getMessage());
        } else if (e instanceof WebDriverException) {
            reason = "LỖI TRÌNH DUYỆT: " + BrowserHelper.shortMessage(e);
        } else {
            reason = BrowserHelper.shortMessage(e);
        }
        log("   [LỖI] Bước " + no + " thất bại: " + reason);
        if (ui != null) {
            Path shot = ui.screenshot("loi_bai" + (articleIndex + 1) + "_buoc" + no);
            if (shot != null) {
                log("   Ảnh chụp màn hình lúc lỗi: " + shot);
            }
        }
        throw new StepFailedException(no, reason, e);
    }

    private void switchToPimcore() {
        String handle = ui.handleExists(pimcoreHandle) ? pimcoreHandle
                : ui.findTab(sel.getList("tab.pimcore.urlContains"), sel.getList("tab.pimcore.titleContains"));
        if (handle == null) {
            throw new IllegalStateException("Không tìm thấy tab Pimcore đang mở (xem tab.pimcore.* trong selectors.properties)");
        }
        ui.activate(handle);
        pimcoreHandle = handle;
    }

    private void clickSaveAndPublish() {
        ui.clickKey("pimcore.btnSavePublish");
        log("   Đã bấm \"Save & Publish\", đang chờ Pimcore xử lý...");
        control.sleep(800);
        ui.waitUntilGone("pimcore.loadingMask", sel.getInt("timing.saveMaskTimeoutSec", 90));
        if (!sel.isBlank("pimcore.saveSuccess")) {
            WebElement ok = ui.tryWaitVisible("pimcore.saveSuccess", 5);
            log(ok != null ? "   Pimcore báo lưu thành công" : "   (Không thấy thông báo lưu thành công - vẫn tiếp tục)");
        }
        control.sleep(sel.getInt("timing.afterSaveWaitMs", 2500));
    }

    /** Mở danh sách (nếu có nút mũi tên) rồi click chọn giá trị. */
    private void selectFromList(String triggerKey, String itemKey, String value) {
        if (!sel.isBlank(triggerKey)) {
            ui.clickKey(triggerKey);
            log("   Đã mở danh sách");
            control.sleep(500);
        }
        ui.clickKey(itemKey, "value", value);
        log("   Đã chọn \"" + value + "\"");
        control.sleep(300);
    }

    /** Lấy dòng tiêu đề bôi đậm đầu tiên trong Google Doc. */
    private String readFirstBoldTitle() {
        // Cách 1: selector người dùng cấu hình
        if (!sel.isBlank("doc.firstBoldTitle")) {
            WebElement el = ui.tryWaitVisible("doc.firstBoldTitle", sel.getInt("timing.docTitleSelectorWaitSec", 5));
            if (el != null) {
                String text = cleanText(el.getText());
                if (!text.isEmpty()) {
                    log("   Lấy tiêu đề bằng selector doc.firstBoldTitle");
                    return text;
                }
            }
            log("   Selector doc.firstBoldTitle không có kết quả -> thử cách tiếp theo");
        }

        // Cách 2: quét DOM của trình soạn thảo (Google Doc chế độ HTML)
        Object fromEditor = ui.safeJs(JS_DOC_EDITOR_FIRST_BOLD);
        String text = cleanText(fromEditor == null ? "" : fromEditor.toString());
        if (!text.isEmpty()) {
            log("   Lấy tiêu đề bằng cách quét chữ đậm trong trình soạn thảo");
            return text;
        }

        // Cách 3: Google Doc đang render dạng canvas -> đọc bản HTML (mobilebasic) trong tab phụ
        String id = extractDocId(driver.getCurrentUrl());
        if (id == null) {
            return "";
        }
        log("   Google Doc render dạng canvas (không đọc được chữ trên DOM) -> mở bản xem HTML (mobilebasic) trong tab phụ");
        String original = driver.getWindowHandle();
        driver.switchTo().newWindow(WindowType.TAB);
        try {
            driver.get("https://docs.google.com/document/d/" + id + "/mobilebasic");
            ui.waitPageReady(sel.getInt("timing.pageLoadTimeoutSec", 40));
            control.sleep(800);
            Object fromBasic = ui.safeJs(JS_MOBILEBASIC_FIRST_BOLD);
            text = cleanText(fromBasic == null ? "" : fromBasic.toString());
        } finally {
            try {
                driver.close();
            } catch (WebDriverException ignored) {
                // tab phụ đã đóng
            }
            ui.activate(original);
        }
        if (!text.isEmpty()) {
            log("   Đã đóng tab phụ, quay lại tab Google Doc");
        }
        return text;
    }

    // =====================================================================
    //  TIỆN ÍCH
    // =====================================================================

    /** Lấy ID Google Doc: phần giữa "/d/" và "/edit" hoặc sau "/d/" trong URL. */
    public static String extractDocId(String url) {
        if (url == null) {
            return null;
        }
        String id = extract(DOC_ID_EDIT, url);
        return id != null ? id : extract(DOC_ID_ANY, url);
    }

    /**
     * Làm sạch tên Object Key khi tạo bài mới trên Pimcore (loại bỏ các ký tự cấm như ?, /, :, *, ", <, >, |, #, %, &)
     * để Pimcore không báo lỗi tạo Object. (Các trường Tiêu đề và SEO Title bên trong vẫn giữ nguyên bản gốc).
     */
    public static String sanitizeObjectKey(String rawTitle, String docId) {
        String key = cleanText(rawTitle);
        // Thay thế các ký tự cấm trong URL / Pimcore Object Key bằng khoảng trắng hoặc gạch ngang
        key = key.replaceAll("[/?*:;\\\\\"<>|#%&+~`^\\[\\]{}()!@$=,]", " ");
        key = key.replaceAll("\\s+", " ").trim();
        // Xóa dấu chấm hoặc gạch ngang ở đầu/cuối
        key = key.replaceAll("^[.\\-\\s]+|[.\\-\\s]+$", "");
        if (key.length() > 110) {
            key = key.substring(0, 110).trim();
        }
        if (key.isEmpty()) {
            String suffix = (docId != null && docId.length() >= 6) ? docId.substring(0, 6) : String.valueOf(System.currentTimeMillis() % 100000);
            key = "bai-viet-" + suffix;
        }
        return key;
    }

    private static String extract(Pattern pattern, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String cleanText(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\u200B-\\u200D\\uFEFF]", "").replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        return nl > 0 ? s.substring(0, nl) : s;
    }

    private void log(String message) {
        listener.onLog(message);
    }
}
