package com.automation;

import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kịch bản 27 bước thực thi tuần tự đúng như thao tác tay:
 * Google Sheet -> Google Doc -> Pimcore -> Google Doc -> Pimcore -> Web App Script.
 */
public class AutomationFlow implements Runnable {

    public static final int TOTAL_STEPS = 27;

    private static final Pattern DOC_ID_EDIT = Pattern.compile("/d/([^/]+)/edit");
    private static final Pattern DOC_ID_ANY = Pattern.compile("/document/(?:u/\\d+/)?d/([a-zA-Z0-9_-]+)");
    private static final Pattern SHEET_ID = Pattern.compile("/spreadsheets/(?:u/\\d+/)?d/([a-zA-Z0-9_-]+)");

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

    // Trạng thái dùng chung giữa các bài
    private String sheetHandle;
    private String pimcoreHandle;
    private String appScriptHandle;

    // Trạng thái của bài đang chạy
    private int articleIndex;
    private String docHandle;
    private Set<String> handlesBeforeDocClick;
    private String docLinkHref;
    private String articleTitle;
    private String docId;
    private String sourceHtml;
    private int completedArticles;

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
            log("Cấu hình: Sheet=" + (config.sheetUrl().isBlank() ? "(dùng tab đang mở)" : config.sheetUrl())
                    + " | Thư mục=" + config.pimcoreFolder()
                    + " | Cột link=" + (config.docLinkColumn().isBlank() ? "(không dùng)" : config.docLinkColumn())
                    + " | Dòng bắt đầu=" + config.startRow()
                    + " | Thời gian=" + config.displayDate()
                    + " | Chờ Sync=" + config.syncWaitSeconds() + "s"
                    + " | Số bài=" + config.articleCount()
                    + " | Nhập liệu=" + config.pasteMode());
            connect();

            for (articleIndex = 0; articleIndex < config.articleCount(); articleIndex++) {
                control.checkpoint();
                resetArticleState();
                log("");
                log("-------------------- BÀI " + (articleIndex + 1) + "/" + config.articleCount()
                        + " (dòng Sheet " + currentRow() + ") --------------------");
                runArticle();
                completedArticles++;
                log("==> HOÀN THÀNH BÀI " + (articleIndex + 1) + ": \"" + articleTitle + "\"");
                if (articleIndex < config.articleCount() - 1) {
                    control.sleep(sel.getInt("timing.betweenArticlesMs", 2000));
                }
            }
            success = true;
            summary = "Hoàn thành " + completedArticles + "/" + config.articleCount() + " bài.";
        } catch (AutomationStoppedException e) {
            summary = "ĐÃ DỪNG theo yêu cầu. Hoàn thành " + completedArticles + "/" + config.articleCount() + " bài.";
        } catch (StepFailedException e) {
            summary = "DỪNG DO LỖI ở Bước " + e.getStep() + ". Hoàn thành " + completedArticles + "/"
                    + config.articleCount() + " bài. Chi tiết: " + e.getMessage();
        } catch (Exception e) {
            if (control.isStopRequested()) {
                summary = "ĐÃ DỪNG theo yêu cầu. Hoàn thành " + completedArticles + "/" + config.articleCount() + " bài.";
            } else {
                summary = "LỖI: " + BrowserHelper.shortMessage(e);
            }
        } finally {
            Thread.interrupted();
            disconnect();
        }
        log("==================== KẾT THÚC: " + summary);
        listener.onFinished(success, summary);
    }

    private void connect() {
        if (!ChromeLauncher.isDebugPortOpen()) {
            throw new IllegalStateException("Không kết nối được Chrome Debug tại " + ChromeLauncher.DEBUGGER_ADDRESS
                    + ". Hãy bấm '1. Mở Chrome Debug' trước và đăng nhập sẵn các trang.");
        }
        log("Đang kết nối Selenium vào Chrome tại " + ChromeLauncher.DEBUGGER_ADDRESS + " ...");
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", ChromeLauncher.DEBUGGER_ADDRESS);
        service = new ChromeDriverService.Builder().build();
        driver = new ChromeDriver(service, options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        ui = new BrowserHelper(driver, sel, control, this::log);
        log("Kết nối thành công. Số tab đang mở: " + driver.getWindowHandles().size());
    }

    /** Chỉ tắt ChromeDriver, KHÔNG đóng Chrome của người dùng. */
    private void disconnect() {
        ChromeDriverService s = service;
        service = null;
        if (s != null) {
            try {
                s.stop();
                log("Đã ngắt kết nối Selenium (Chrome vẫn giữ nguyên).");
            } catch (RuntimeException e) {
                log("Ngắt kết nối: " + BrowserHelper.shortMessage(e));
            }
        }
    }

    private void resetArticleState() {
        docHandle = null;
        handlesBeforeDocClick = null;
        docLinkHref = null;
        articleTitle = null;
        docId = null;
        sourceHtml = null;
    }

    private int currentRow() {
        return config.startRow() + articleIndex;
    }

    // =====================================================================
    //  27 BƯỚC
    // =====================================================================

    private void runArticle() {
        final PasteMode mode = config.pasteMode();
        final int defaultTimeout = ui.defaultTimeout();
        final int pageLoadTimeout = sel.getInt("timing.pageLoadTimeoutSec", 40);

        // ---------------- GOOGLE SHEET ----------------
        step(1, "Chuyển sang tab Google Sheet", () -> {
            String handle = null;
            if (ui.handleExists(sheetHandle)) {
                handle = sheetHandle;
            } else {
                String sheetId = extract(SHEET_ID, config.sheetUrl());
                if (sheetId != null) {
                    handle = ui.findTab(List.of(sheetId), List.of());
                } else {
                    handle = ui.findTab(sel.getList("tab.sheet.urlContains"), sel.getList("tab.sheet.titleContains"));
                }
            }
            if (handle == null) {
                if (config.sheetUrl().isBlank()) {
                    throw new IllegalStateException("Không tìm thấy tab Google Sheet đang mở và ô URL Sheet đang trống");
                }
                log("   Không thấy tab Sheet đang mở -> mở URL Sheet trong tab mới");
                driver.switchTo().newWindow(WindowType.TAB);
                driver.get(config.sheetUrl().trim());
                handle = driver.getWindowHandle();
            }
            ui.activate(handle);
            ui.waitPageReady(pageLoadTimeout);
            sheetHandle = handle;
            ui.waitVisible("sheet.readyIndicator", pageLoadTimeout);
            log("   Đang ở tab: " + driver.getTitle());
        });

        step(2, "Click vào đường link bài viết Google Doc", () -> {
            String column = config.docLinkColumn().trim().toUpperCase(Locale.ROOT);
            String row = String.valueOf(currentRow());
            if (!column.isEmpty()) {
                String cell = column + row;
                WebElement nameBox = ui.waitVisible("sheet.nameBox");
                ui.click(nameBox);
                ui.selectAllAndDelete();
                ui.typeViaSendKeys(nameBox, cell);
                ui.press(Keys.ENTER);
                log("   Đã chọn ô " + cell + " (qua Name Box)");
                control.sleep(sel.getInt("timing.sheetLinkPopupMs", 1500));
            }
            handlesBeforeDocClick = new HashSet<>(driver.getWindowHandles());
            WebElement link = ui.waitVisible("sheet.docLink",
                    "row", row, "index", String.valueOf(articleIndex + 1));
            docLinkHref = unwrapGoogleRedirect(link.getDomProperty("href"));
            log("   Link bài viết: " + docLinkHref);
            ui.click(link);
        });

        step(3, "Chuyển sang tab Google Doc vừa mở", () -> {
            String newHandle = ui.waitForNewTab(handlesBeforeDocClick, sel.getInt("timing.newTabTimeoutSec", 15));
            if (newHandle == null) {
                if (docLinkHref == null || !docLinkHref.contains("/document/")) {
                    throw new IllegalStateException("Click link không mở tab mới và không đọc được link Google Doc");
                }
                log("   Click không mở tab mới -> mở link bài viết trong tab mới");
                driver.switchTo().newWindow(WindowType.TAB);
                driver.get(docLinkHref);
                newHandle = driver.getWindowHandle();
            }
            ui.activate(newHandle);
            docHandle = newHandle;
            ui.waitUrlContains("/document/", pageLoadTimeout);
            ui.waitPageReady(pageLoadTimeout);
            ui.waitVisible("doc.readyIndicator", pageLoadTimeout);
            log("   Đang ở tab Doc: " + driver.getTitle());
        });

        // ---------------- GOOGLE DOC ----------------
        step(4, "Copy dòng tiêu đề bôi đậm đầu tiên", () -> {
            articleTitle = readFirstBoldTitle();
            if (articleTitle == null || articleTitle.isBlank()) {
                throw new IllegalStateException("Không tìm thấy dòng chữ bôi đậm nào trong bài viết");
            }
            ClipboardUtil.setText(articleTitle);
            log("   articleTitle = \"" + articleTitle + "\" (đã đưa vào Clipboard)");
        });

        // ---------------- PIMCORE ----------------
        step(5, "Chuyển sang tab Pimcore", () -> {
            switchToPimcore();
            log("   Đang ở tab: " + driver.getTitle());
        });

        step(6, "Chuột phải thư mục \"" + config.pimcoreFolder() + "\" -> Add Object -> News", () -> {
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
                    log("   Pimcore hỏi tên object -> dán tiêu đề bài làm tên");
                    ui.pasteText(keyInput, articleTitle, true, mode);
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

        step(7, "Click ô \"Tiêu đề\" và Paste articleTitle", () -> {
            WebElement titleInput = ui.waitVisible("pimcore.titleInput", Math.max(defaultTimeout, 30));
            ui.pasteText(titleInput, articleTitle, true, mode);
        });

        step(8, "Click tab \"SEO\"", () -> ui.clickKey("pimcore.tabSeo"));

        step(9, "Click ô \"SEO Title\" và Paste articleTitle", () -> {
            WebElement seoInput = ui.waitVisible("pimcore.seoTitleInput");
            ui.pasteText(seoInput, articleTitle, true, mode);
        });

        step(10, "Click \"Save & Publish\" (chờ xử lý xong)", this::clickSaveAndPublish);

        step(11, "Click quay lại tab \"Base\"", () -> ui.clickKey("pimcore.tabBase"));

        step(12, "Chọn \"Thời gian\" = " + config.displayDate(), () -> {
            WebElement dateInput = ui.waitVisible("pimcore.dateInput");
            ui.typeText(dateInput, config.displayDate(), true);
            control.sleep(200);
            ui.press(Keys.TAB);
            control.sleep(300);
            log("   Giá trị ô Thời gian sau khi nhập: " + BrowserHelper.preview(ui.readEditorValue(dateInput)));
        });

        step(13, "Chọn \"" + config.language() + "\" ở \"Ngôn ngữ hiển thị\"",
                () -> selectFromList("pimcore.languageTrigger", "pimcore.languageItem", config.language()));

        step(14, "Chọn \"" + config.accessChannel() + "\" ở \"Access Channel\"",
                () -> selectFromList("pimcore.accessChannelTrigger", "pimcore.accessChannelItem", config.accessChannel()));

        step(15, "Click \"Save & Publish\"", this::clickSaveAndPublish);

        // ---------------- GOOGLE DOC ----------------
        step(16, "Chuyển lại tab Google Doc của Bước 3", () -> {
            if (!ui.handleExists(docHandle)) {
                throw new IllegalStateException("Tab Google Doc mở ở Bước 3 đã bị đóng");
            }
            ui.activate(docHandle);
            log("   Đang ở tab Doc: " + driver.getTitle());
        });

        step(17, "Copy ID bài viết từ URL", () -> {
            String url = driver.getCurrentUrl();
            docId = extractDocId(url);
            if (docId == null) {
                throw new IllegalStateException("Không trích xuất được ID từ URL: " + url);
            }
            ClipboardUtil.setText(docId);
            log("   ID bài viết = " + docId + " (đã đưa vào Clipboard)");
        });

        // ---------------- PIMCORE SYNC ----------------
        step(18, "Chuyển lại tab Pimcore", () -> {
            switchToPimcore();
            log("   Đang ở tab: " + driver.getTitle());
        });

        step(19, "Click mục \"Sync data Google doc\"", () -> ui.clickKeyInAnyFrame("pimcore.syncGoogleDocMenu"));

        step(20, "Paste ID bài viết vào ô \"ID Google doc\"", () -> {
            control.sleep(500);
            WebElement idInput = ui.waitVisibleInAnyFrame("pimcore.syncGoogleDocIdInput");
            ui.pasteText(idInput, docId, true, mode);
        });

        step(21, "Ấn nút \"Gửi\" và chờ " + config.syncWaitSeconds() + " giây", () -> {
            ui.clickKeyInAnyFrame("pimcore.syncSendButton");
            log("   Đã bấm \"Gửi\". Bắt đầu chờ hệ thống nhập dữ liệu bài viết...");
            for (int s = config.syncWaitSeconds(); s >= 1; s--) {
                log("   [CHỜ] Còn " + s + " giây...");
                listener.onStatus("Bài " + (articleIndex + 1) + " - Bước 21: đang chờ Sync, còn " + s + " giây");
                control.sleep(1000);
            }
            log("   Hết thời gian chờ Sync.");
            driver.switchTo().defaultContent();
        });

        step(22, "Ấn vào khung nội dung bài viết", () -> {
            WebElement body = ui.waitVisibleInAnyFrame("pimcore.articleBody");
            ui.click(body);
            control.sleep(500);
        });

        step(23, "Ấn nút \"Source\"", () -> {
            ui.clickKeyInAnyFrame("pimcore.sourceButton");
            control.sleep(800);
        });

        step(24, "Ctrl+A, Ctrl+C toàn bộ mã HTML trong ô Source", () -> {
            WebElement sourceBox = ui.waitVisibleInAnyFrame("pimcore.sourceTextarea");
            sourceHtml = ui.copyAllFrom(sourceBox);
            if (sourceHtml == null || sourceHtml.isBlank()) {
                throw new IllegalStateException("Ô Source rỗng - có thể Sync chưa nhập xong dữ liệu");
            }
            log("   Đã copy " + sourceHtml.length() + " ký tự HTML: " + BrowserHelper.preview(sourceHtml));
            if (!sel.isBlank("pimcore.sourceDialogClose")) {
                ui.clickKeyInAnyFrame("pimcore.sourceDialogClose");
                log("   Đã đóng hộp thoại Source");
            }
            driver.switchTo().defaultContent();
        });

        // ---------------- WEB APP SCRIPT ----------------
        step(25, "Chuyển sang tab \"Web App Script\"", () -> {
            String handle = ui.handleExists(appScriptHandle) ? appScriptHandle
                    : ui.findTab(sel.getList("tab.appscript.urlContains"), sel.getList("tab.appscript.titleContains"));
            if (handle == null) {
                throw new IllegalStateException("Không tìm thấy tab Web App Script đang mở (xem tab.appscript.* trong selectors.properties)");
            }
            ui.activate(handle);
            appScriptHandle = handle;
            log("   Đang ở tab: " + driver.getTitle());
        });

        step(26, "Ấn vào mục \"sửa source\" bên trái", () -> {
            ui.clickKeyInAnyFrame("appscript.menuEditSource");
            control.sleep(800);
        });

        step(27, "Xoá nội dung cũ và Paste source HTML vào ô soạn thảo", () -> {
            WebElement editor = ui.waitVisibleInAnyFrame("appscript.sourceEditor");
            ui.pasteText(editor, sourceHtml, true, mode);
            log("   Đã dán " + sourceHtml.length() + " ký tự HTML vào mục \"sửa source\"");
            driver.switchTo().defaultContent();
        });
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
        } catch (Exception e) {
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
        log("   [OK] Bước " + no + " hoàn tất (" + (System.currentTimeMillis() - start) + " ms)");
        control.sleep(sel.getInt("timing.actionDelayMs", 700));
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

    /** Bước 4: lấy dòng tiêu đề bôi đậm đầu tiên trong Google Doc. */
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

    /** Lấy ID Google Doc: phần giữa "/d/" và "/edit" trong URL. */
    public static String extractDocId(String url) {
        if (url == null) {
            return null;
        }
        String id = extract(DOC_ID_EDIT, url);
        return id != null ? id : extract(DOC_ID_ANY, url);
    }

    private static String extract(Pattern pattern, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** Bỏ lớp chuyển hướng https://www.google.com/url?q=... nếu có. */
    private static String unwrapGoogleRedirect(String href) {
        if (href == null) {
            return null;
        }
        int q = href.indexOf("/url?");
        if (q >= 0) {
            Matcher m = Pattern.compile("[?&]q=([^&]+)").matcher(href);
            if (m.find()) {
                return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
            }
        }
        return href;
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
