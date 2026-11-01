package com.automation;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidSelectorException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Bộ hàm thao tác trình duyệt "như người thật": chờ phần tử hiển thị, rê chuột + click,
 * chuột phải, gõ phím, Copy (Ctrl+C) / Paste (Ctrl+V) qua System Clipboard,
 * chuyển tab, tìm phần tử trong iframe lồng nhau.
 */
public class BrowserHelper {

    private static final int MAX_FRAME_DEPTH = 4;

    private final ChromeDriver driver;
    private final Selectors sel;
    private final RunControl control;
    private final Consumer<String> log;

    public BrowserHelper(ChromeDriver driver, Selectors sel, RunControl control, Consumer<String> log) {
        this.driver = driver;
        this.sel = sel;
        this.control = control;
        this.log = log;
    }

    public int defaultTimeout() {
        return sel.getInt("timing.defaultTimeoutSec", 20);
    }

    /**
     * Tự động kiểm tra và xử lý hộp thoại Alert của trình duyệt (nếu có).
     * @return nội dung của alert hoặc null nếu không có alert nào.
     */
    public String handleAlertIfPresent(boolean accept) {
        try {
            Alert alert = driver.switchTo().alert();
            String text = alert.getText();
            if (accept) {
                alert.accept();
            } else {
                alert.dismiss();
            }
            log.accept("   [ALERT BROWSER] Đã tự động xử lý hộp thoại: \"" + text + "\"");
            return text;
        } catch (NoAlertPresentException | NullPointerException ignored) {
            return null;
        } catch (WebDriverException e) {
            return null;
        }
    }

    // =====================================================================
    //  TÌM PHẦN TỬ
    // =====================================================================

    /** Chờ phần tử hiển thị trong trang/frame hiện tại, hết thời gian thì ném SelectorException. */
    public WebElement waitVisible(String key, String... placeholders) {
        return waitVisible(key, defaultTimeout(), placeholders);
    }

    public WebElement waitVisible(String key, int timeoutSec, String... placeholders) {
        WebElement el = tryWaitVisible(key, timeoutSec, placeholders);
        if (el == null) {
            throw new SelectorException(key, sel.resolve(key, placeholders),
                    "không thấy phần tử HIỂN THỊ sau " + timeoutSec + " giây");
        }
        return el;
    }

    /** Chờ phần tử hiển thị, hết thời gian trả về null (không ném lỗi). */
    public WebElement tryWaitVisible(String key, int timeoutSec, String... placeholders) {
        By by = sel.by(key, placeholders);
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        long pausedAtStart = control.totalPausedMillis();
        while (true) {
            control.checkpoint();
            WebElement el = firstVisible(by, key, placeholders);
            if (el != null) {
                return el;
            }
            long pausedDuring = control.totalPausedMillis() - pausedAtStart;
            if (System.currentTimeMillis() - pausedDuring >= deadline) {
                return null;
            }
            control.sleep(250);
        }
    }

    /**
     * Chờ phần tử hiển thị ở trang chính HOẶC bên trong iframe (lồng tối đa 4 cấp).
     * Khi tìm thấy, driver được giữ nguyên ở frame chứa phần tử để thao tác tiếp.
     */
    public WebElement waitVisibleInAnyFrame(String key, String... placeholders) {
        return waitVisibleInAnyFrame(key, defaultTimeout(), placeholders);
    }

    /** Tìm phần tử hiển thị cả trong các frame lồng nhau với số giây chờ tuỳ chọn (trả về null nếu không thấy). */
    public WebElement tryWaitVisibleInAnyFrame(String key, int timeoutSec, String... placeholders) {
        try {
            return waitVisibleInAnyFrame(key, timeoutSec, placeholders);
        } catch (Exception e) {
            return null;
        }
    }

    public WebElement waitVisibleInAnyFrame(String key, int timeoutSec, String... placeholders) {
        By by = sel.by(key, placeholders);
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        long pausedAtStart = control.totalPausedMillis();
        while (true) {
            control.checkpoint();
            driver.switchTo().defaultContent();
            WebElement el = searchFrames(by, key, placeholders, 0);
            if (el != null) {
                return el;
            }
            long pausedDuring = control.totalPausedMillis() - pausedAtStart;
            if (System.currentTimeMillis() - pausedDuring >= deadline) {
                driver.switchTo().defaultContent();
                throw new SelectorException(key, sel.resolve(key, placeholders),
                        "không thấy phần tử HIỂN THỊ (đã tìm cả trong iframe) sau " + timeoutSec + " giây");
            }
            control.sleep(400);
        }
    }

    private WebElement searchFrames(By by, String key, String[] placeholders, int depth) {
        WebElement el = firstVisible(by, key, placeholders);
        if (el != null) {
            return el;
        }
        if (depth >= MAX_FRAME_DEPTH) {
            return null;
        }
        List<WebElement> frames;
        try {
            frames = driver.findElements(By.cssSelector("iframe, frame"));
        } catch (UnhandledAlertException e) {
            handleAlertIfPresent(true);
            try {
                frames = driver.findElements(By.cssSelector("iframe, frame"));
            } catch (WebDriverException ex) {
                return null;
            }
        } catch (WebDriverException e) {
            return null;
        }
        for (WebElement frame : frames) {
            try {
                if (!frame.isDisplayed()) {
                    continue;
                }
                driver.switchTo().frame(frame);
            } catch (WebDriverException e) {
                continue;
            }
            WebElement found;
            try {
                found = searchFrames(by, key, placeholders, depth + 1);
            } catch (WebDriverException e) {
                found = null;
            }
            if (found != null) {
                return found;
            }
            try {
                driver.switchTo().parentFrame();
            } catch (WebDriverException e) {
                driver.switchTo().defaultContent();
                return null;
            }
        }
        return null;
    }

    private WebElement firstVisible(By by, String key, String[] placeholders) {
        List<WebElement> elements;
        try {
            elements = driver.findElements(by);
        } catch (UnhandledAlertException e) {
            handleAlertIfPresent(true);
            try {
                elements = driver.findElements(by);
            } catch (WebDriverException ex) {
                return null;
            }
        } catch (InvalidSelectorException e) {
            throw new SelectorException(key, sel.resolve(key, placeholders), "cú pháp selector KHÔNG HỢP LỆ");
        } catch (WebDriverException e) {
            return null;
        }
        for (WebElement el : elements) {
            try {
                if (isReallyVisible(el)) {
                    return el;
                }
            } catch (StaleElementReferenceException ignored) {
                // phần tử vừa bị vẽ lại -> bỏ qua, vòng sau tìm lại
            }
        }
        return null;
    }

    /** Hiển thị thật trên màn hình (ExtJS giấu tab bằng cách đẩy ra toạ độ -10000px). */
    public boolean isReallyVisible(WebElement el) {
        if (!el.isDisplayed()) {
            return false;
        }
        Rectangle r = el.getRect();
        return r.getWidth() > 0 && r.getHeight() > 0 && r.getX() > -5000 && r.getY() > -5000;
    }

    /** Chờ đến khi không còn phần tử nào khớp selector hiển thị (ví dụ lớp loading). */
    public void waitUntilGone(String key, int timeoutSec) {
        if (sel.isBlank(key)) {
            return;
        }
        By by = sel.by(key);
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        long pausedAtStart = control.totalPausedMillis();
        while (true) {
            control.checkpoint();
            if (firstVisible(by, key, new String[0]) == null) {
                return;
            }
            long pausedDuring = control.totalPausedMillis() - pausedAtStart;
            if (System.currentTimeMillis() - pausedDuring >= deadline) {
                throw new SelectorException(key, sel.resolve(key),
                        "phần tử vẫn còn hiển thị sau " + timeoutSec + " giây (hệ thống xử lý quá lâu)");
            }
            control.sleep(300);
        }
    }

    // =====================================================================
    //  CHUỘT
    // =====================================================================

    /** Rê chuột tới phần tử rồi click trái (giống người). Có 2 phương án dự phòng. */
    public void click(WebElement el) {
        control.checkpoint();
        try {
            new Actions(driver).moveToElement(el).pause(Duration.ofMillis(120)).click().perform();
            return;
        } catch (StaleElementReferenceException e) {
            throw e;
        } catch (WebDriverException e) {
            log.accept("      (Rê chuột + click lỗi: " + shortMessage(e) + " -> thử element.click())");
        }
        try {
            el.click();
            return;
        } catch (StaleElementReferenceException e) {
            throw e;
        } catch (WebDriverException e) {
            log.accept("      (element.click() lỗi: " + shortMessage(e) + " -> thử click bằng JavaScript)");
        }
        js("arguments[0].click();", el);
    }

    /** Tìm theo key rồi click, tự tìm lại nếu phần tử bị vẽ lại (stale). */
    public WebElement clickKey(String key, String... placeholders) {
        StaleElementReferenceException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            WebElement el = waitVisible(key, placeholders);
            try {
                click(el);
                return el;
            } catch (StaleElementReferenceException e) {
                last = e;
                control.sleep(300);
            }
        }
        throw new SelectorException(key, sel.resolve(key, placeholders),
                "phần tử liên tục bị vẽ lại, không click được: " + shortMessage(last));
    }

    /** Giống clickKey nhưng tìm cả trong iframe. */
    public WebElement clickKeyInAnyFrame(String key, String... placeholders) {
        StaleElementReferenceException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            WebElement el = waitVisibleInAnyFrame(key, placeholders);
            try {
                click(el);
                return el;
            } catch (StaleElementReferenceException e) {
                last = e;
                control.sleep(300);
            }
        }
        throw new SelectorException(key, sel.resolve(key, placeholders),
                "phần tử liên tục bị vẽ lại, không click được: " + shortMessage(last));
    }

    /** Click chuột phải bằng Actions.contextClick(). */
    public void contextClick(WebElement el) {
        control.checkpoint();
        new Actions(driver).moveToElement(el).pause(Duration.ofMillis(150)).contextClick(el).perform();
    }

    // =====================================================================
    //  BÀN PHÍM & HỆ ĐIỀU HÀNH
    // =====================================================================

    public static final boolean IS_MAC = System.getProperty("os.name", "")
            .toLowerCase(java.util.Locale.ROOT).contains("mac");

    /** Phím bổ trợ Ctrl trên Windows/Linux hoặc Cmd (Command) trên macOS. */
    public static Keys modifierKey() {
        return IS_MAC ? Keys.COMMAND : Keys.CONTROL;
    }

    /** Nhấn tổ hợp Ctrl + phím (hoặc Cmd + phím trên Mac) vào phần tử đang được focus. */
    public void ctrl(String letter) {
        control.checkpoint();
        Keys mod = modifierKey();
        new Actions(driver).keyDown(mod).sendKeys(letter).keyUp(mod).perform();
    }

    /** Nhấn một phím (Enter, Tab, Delete ...) vào phần tử đang focus. */
    public void press(CharSequence key) {
        control.checkpoint();
        new Actions(driver).sendKeys(key).perform();
    }

    /** Ctrl+A rồi Delete để xoá nội dung cũ của ô đang focus. */
    public void selectAllAndDelete() {
        ctrl("a");
        control.sleep(120);
        press(Keys.DELETE);
        control.sleep(120);
    }

    /** Click vào ô, (xoá nội dung cũ) rồi gõ chữ bằng sendKeys(). */
    public void typeText(WebElement el, String text, boolean clearFirst) {
        click(el);
        control.sleep(150);
        if (clearFirst) {
            selectAllAndDelete();
        }
        if (isTypable(el)) {
            el.sendKeys(text);
        } else {
            new Actions(driver).sendKeys(text).perform();
        }
    }

    // =====================================================================
    //  PASTE (Ctrl+V / Cmd+V từ System Clipboard) + sendKeys() DỰ PHÒNG
    // =====================================================================

    /**
     * Cách 1: đưa chữ vào System Clipboard rồi nhấn Ctrl+V (Cmd+V trên Mac) giống thao tác tay.
     */
    public void pasteViaClipboard(WebElement el, String text) {
        ClipboardUtil.setText(text);
        control.sleep(150);
        Keys mod = modifierKey();
        if (isTypable(el)) {
            el.sendKeys(Keys.chord(mod, "v"));
        } else {
            ctrl("v");
        }
        control.sleep(400);
    }

    /** Cách 2 (dự phòng): gõ từng ký tự bằng sendKeys(). */
    public void typeViaSendKeys(WebElement el, String text) {
        if (isTypable(el)) {
            el.sendKeys(text);
        } else {
            new Actions(driver).sendKeys(text).perform();
        }
        control.sleep(300);
    }

    /**
     * Click vào ô -> (Ctrl+A, Delete) -> dán theo phương thức đã chọn -> kiểm tra lại giá trị.
     * Nếu Ctrl+V không ăn và chế độ cho phép, tự gõ lại bằng sendKeys().
     */
    public void pasteText(WebElement el, String text, boolean clearFirst, PasteMode mode) {
        click(el);
        control.sleep(150);
        if (clearFirst) {
            selectAllAndDelete();
        }

        String expected = text;
        Object maxLenObj = safeJs("return (arguments[0].maxLength !== undefined && arguments[0].maxLength > 0) ? arguments[0].maxLength : -1;", el);
        if (maxLenObj instanceof Number n && n.intValue() > 0 && n.intValue() < text.length()) {
            expected = text.substring(0, n.intValue());
            log.accept("      (Lưu ý: ô giới hạn " + n.intValue() + " ký tự, nội dung sẽ bị cắt bớt)");
        }

        if (mode == PasteMode.SENDKEYS_ONLY) {
            typeViaSendKeys(el, text);
            log.accept("      Đã nhập bằng sendKeys() (" + text.length() + " ký tự)");
            verifyOrThrow(el, expected);
            return;
        }

        pasteViaClipboard(el, text);
        String actual = readEditorValue(el);
        if (contentMatches(actual, expected)) {
            log.accept("      Đã dán bằng Clipboard Ctrl+V (" + text.length() + " ký tự)");
            return;
        }
        if (mode == PasteMode.CLIPBOARD_ONLY) {
            throw new IllegalStateException("Ctrl+V không dán được nội dung. Giá trị hiện tại: " + preview(actual));
        }

        // Dự phòng 1: Gán trực tiếp qua JavaScript (nhanh tức thì với nội dung HTML dài và không phụ thuộc Clipboard)
        if (trySetEditorValueViaJs(el, text)) {
            String afterJs = readEditorValue(el);
            if (contentMatches(afterJs, expected)) {
                log.accept("      Đã gán trực tiếp nội dung qua JavaScript DOM (" + text.length() + " ký tự)");
                return;
            }
        }

        // Dự phòng 2: Gõ từng ký tự bằng sendKeys()
        log.accept("      Ctrl+V chưa dán đúng (giá trị: " + preview(actual) + ") -> dự phòng sendKeys()");
        click(el);
        control.sleep(120);
        selectAllAndDelete();
        typeViaSendKeys(el, text);
        verifyOrThrow(el, expected);
        log.accept("      Đã nhập bằng sendKeys() dự phòng (" + text.length() + " ký tự)");
    }

    private boolean trySetEditorValueViaJs(WebElement el, String text) {
        try {
            Object ok = safeJs("""
                    var el = arguments[0];
                    var val = arguments[1];
                    if (!el) return false;
                    var tag = (el.tagName || '').toUpperCase();
                    if (tag === 'INPUT' || tag === 'TEXTAREA') {
                        el.value = val;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        return true;
                    }
                    return false;
                    """, el, text);
            control.sleep(150);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            return false;
        }
    }

    private void verifyOrThrow(WebElement el, String expected) {
        String actual = readEditorValue(el);
        if (!contentMatches(actual, expected)) {
            throw new IllegalStateException("Nhập liệu không thành công. Mong muốn: " + preview(expected)
                    + " | Thực tế: " + preview(actual));
        }
    }

    // =====================================================================
    //  COPY: Ưu tiên đọc trực tiếp từ DOM/JS -> Dự phòng Ctrl+A, Ctrl+C
    // =====================================================================

    /**
     * Đọc toàn bộ nội dung của ô nhập / trình soạn thảo.
     * Ưu tiên đọc trực tiếp từ DOM (tránh xung đột System Clipboard khi người dùng đang thao tác máy).
     * Dự phòng: nếu không đọc được từ DOM thì mới dùng Ctrl+A -> Ctrl+C.
     */
    public String copyAllFrom(WebElement el) {
        String directDom = readEditorValue(el);
        if (directDom != null && !directDom.isBlank()) {
            log.accept("      Đã đọc trực tiếp nội dung từ DOM (" + directDom.length() + " ký tự, không chiếm dụng Clipboard)");
            return directDom;
        }

        click(el);
        control.sleep(200);
        ctrl("a");
        control.sleep(200);
        String marker = "__PIMCORE_AUTOTOOL_MARKER_" + System.nanoTime();
        ClipboardUtil.setText(marker);
        ctrl("c");

        String clip = null;
        long waitMs = sel.getInt("timing.clipboardWaitMs", 3000);
        long end = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < end) {
            control.sleep(150);
            String current = ClipboardUtil.getText();
            if (!marker.equals(current)) {
                clip = current;
                break;
            }
        }

        String dom = readEditorValue(el);
        if (clip != null && !clip.isBlank()) {
            if (dom != null && dom.trim().length() > clip.trim().length() * 1.05 + 10) {
                log.accept("      Clipboard ngắn hơn nội dung ô (" + clip.length() + " < " + dom.length()
                        + " ký tự) -> dùng nội dung đọc trực tiếp từ ô");
                return dom;
            }
            log.accept("      Đã copy bằng Ctrl+A, Ctrl+C (" + clip.length() + " ký tự)");
            return clip;
        }
        if (dom != null && !dom.isBlank()) {
            log.accept("      Ctrl+C không đưa dữ liệu vào Clipboard -> dự phòng đọc trực tiếp nội dung ô ("
                    + dom.length() + " ký tự)");
            return dom;
        }
        throw new IllegalStateException("Không đọc/copy được nội dung (ô và Clipboard đều rỗng)");
    }

    // =====================================================================
    //  ĐỌC GIÁ TRỊ Ô NHẬP / TRÌNH SOẠN THẢO
    // =====================================================================

    private static final String JS_READ_VALUE = """
            var el = arguments[0];
            if (!el) { return null; }
            var tag = (el.tagName || '').toUpperCase();
            if (tag === 'INPUT' || tag === 'TEXTAREA') { return el.value; }
            var cmHost = el.CodeMirror ? el : (el.closest ? el.closest('.CodeMirror') : null);
            if (cmHost && cmHost.CodeMirror) { return cmHost.CodeMirror.getValue(); }
            var cm6 = el.closest ? el.closest('.cm-editor') : null;
            if (cm6) { var content = cm6.querySelector('.cm-content'); if (content) { return content.innerText; } }
            var aceHost = el.classList && el.classList.contains('ace_editor') ? el : (el.closest ? el.closest('.ace_editor') : null);
            if (aceHost && window.ace) { return window.ace.edit(aceHost).getValue(); }
            var monacoHost = el.closest ? el.closest('.monaco-editor') : null;
            if (monacoHost && window.monaco && window.monaco.editor && window.monaco.editor.getEditors) {
                var eds = window.monaco.editor.getEditors();
                if (eds.length > 0) { return eds[0].getValue(); }
            }
            if (tag === 'IFRAME') {
                try { var d = el.contentDocument; if (d && d.body) { return d.body.innerHTML; } } catch (e) { return null; }
            }
            if (el.isContentEditable) { return el.innerText; }
            return null;
            """;

    /** Đọc giá trị hiện tại của ô (input, textarea, CodeMirror, Ace, Monaco, contenteditable). */
    public String readEditorValue(WebElement el) {
        Object value = safeJs(JS_READ_VALUE, el);
        return value == null ? null : value.toString();
    }

    private boolean isTypable(WebElement el) {
        try {
            String tag = el.getTagName().toLowerCase(Locale.ROOT);
            if (tag.equals("input") || tag.equals("textarea")) {
                return true;
            }
            Object editable = js("return arguments[0].isContentEditable === true;", el);
            return Boolean.TRUE.equals(editable);
        } catch (WebDriverException e) {
            return false;
        }
    }

    /** So khớp sau khi chuẩn hoá khoảng trắng. actual == null nghĩa là không đọc được -> chấp nhận. */
    public static boolean contentMatches(String actual, String expected) {
        if (actual == null) {
            return true;
        }
        String a = normalize(actual);
        String e = normalize(expected);
        if (e.isEmpty()) {
            return true;
        }
        if (a.equals(e)) {
            return true;
        }
        int k = Math.min(40, e.length());
        return a.length() >= e.length() * 0.9
                && a.contains(e.substring(0, k))
                && a.contains(e.substring(e.length() - k));
    }

    private static String normalize(String s) {
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    public static String preview(String s) {
        if (s == null) {
            return "(không đọc được)";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 80 ? "\"" + one.substring(0, 80) + "...\"" : "\"" + one + "\"";
    }

    // =====================================================================
    //  TAB
    // =====================================================================

    /** Tìm tab theo URL/tiêu đề (danh sách cách nhau dấu phẩy). Trả về window handle hoặc null. */
    public String findTab(List<String> urlTokens, List<String> titleTokens) {
        if (urlTokens.isEmpty() && titleTokens.isEmpty()) {
            throw new IllegalArgumentException("Chưa cấu hình điều kiện nhận diện tab (urlContains/titleContains)");
        }
        Set<String> handles = driver.getWindowHandles();

        // Cách 1: hỏi Chrome danh sách tab qua CDP (không phải lật qua từng tab)
        List<Map<String, Object>> targets = cdpPageTargets();
        if (targets != null) {
            for (Map<String, Object> target : targets) {
                String url = String.valueOf(target.get("url"));
                String title = String.valueOf(target.get("title"));
                if (!tabMatches(url, title, urlTokens, titleTokens)) {
                    continue;
                }
                String id = String.valueOf(target.get("targetId")).toUpperCase(Locale.ROOT);
                for (String h : handles) {
                    String hu = h.toUpperCase(Locale.ROOT);
                    if (hu.equals(id) || hu.endsWith(id)) {
                        return h;
                    }
                }
            }
        }

        // Cách 2: lần lượt chuyển qua từng tab để kiểm tra
        String original = currentHandleOrNull();
        for (String h : handles) {
            try {
                driver.switchTo().window(h);
                if (tabMatches(driver.getCurrentUrl(), driver.getTitle(), urlTokens, titleTokens)) {
                    return h;
                }
            } catch (NoSuchWindowException ignored) {
                // tab vừa bị đóng
            }
        }
        if (original != null) {
            try {
                driver.switchTo().window(original);
            } catch (WebDriverException ignored) {
                // tab gốc đã đóng
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cdpPageTargets() {
        try {
            Map<String, Object> result = driver.executeCdpCommand("Target.getTargets", new HashMap<>());
            Object infos = result.get("targetInfos");
            if (!(infos instanceof List<?> list)) {
                return null;
            }
            List<Map<String, Object>> pages = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && "page".equals(String.valueOf(m.get("type")))) {
                    pages.add((Map<String, Object>) m);
                }
            }
            return pages;
        } catch (WebDriverException | ClassCastException e) {
            return null;
        }
    }

    private static boolean tabMatches(String url, String title, List<String> urlTokens, List<String> titleTokens) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        for (String token : urlTokens) {
            if (u.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String token : titleTokens) {
            if (t.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Chuyển sang tab và đưa tab đó lên trước màn hình (giống người bấm vào tab). */
    public void activate(String handle) {
        control.checkpoint();
        driver.switchTo().window(handle);
        try {
            driver.executeCdpCommand("Page.bringToFront", new HashMap<>());
        } catch (WebDriverException ignored) {
            // không hỗ trợ -> vẫn đã switch về mặt Selenium
        }
        driver.switchTo().defaultContent();
    }

    public boolean handleExists(String handle) {
        return handle != null && driver.getWindowHandles().contains(handle);
    }

    public String currentHandleOrNull() {
        try {
            return driver.getWindowHandle();
        } catch (WebDriverException e) {
            return null;
        }
    }

    /** Chờ một tab mới xuất hiện so với danh sách trước khi click. */
    public String waitForNewTab(Set<String> before, int timeoutSec) {
        long end = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < end) {
            control.checkpoint();
            for (String h : driver.getWindowHandles()) {
                if (!before.contains(h)) {
                    return h;
                }
            }
            control.sleep(250);
        }
        return null;
    }

    /** Chờ trang tải xong (document.readyState == complete). */
    public void waitPageReady(int timeoutSec) {
        long end = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < end) {
            control.checkpoint();
            Object state = safeJs("return document.readyState;");
            if ("complete".equals(state)) {
                return;
            }
            control.sleep(300);
        }
        log.accept("      (Cảnh báo: trang chưa tải xong hoàn toàn sau " + timeoutSec + " giây, vẫn tiếp tục)");
    }

    /** Chờ URL tab hiện tại chứa chuỗi cho trước. */
    public void waitUrlContains(String part, int timeoutSec) {
        long end = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < end) {
            control.checkpoint();
            String url = driver.getCurrentUrl();
            if (url != null && url.contains(part)) {
                return;
            }
            control.sleep(300);
        }
        throw new IllegalStateException("URL không chứa '" + part + "' sau " + timeoutSec
                + " giây. URL hiện tại: " + driver.getCurrentUrl());
    }

    // =====================================================================
    //  KHÁC
    // =====================================================================

    public Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    public Object safeJs(String script, Object... args) {
        try {
            return js(script, args);
        } catch (WebDriverException e) {
            return null;
        }
    }

    /** Chụp màn hình khi lỗi, lưu vào thư mục screenshots cạnh tool. */
    public Path screenshot(String name) {
        try {
            File src = driver.getScreenshotAs(OutputType.FILE);
            Path dir = Paths.get(System.getProperty("user.dir"), "screenshots");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path target = dir.resolve(name + "_" + stamp + ".png");
            Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException | WebDriverException e) {
            return null;
        }
    }

    public static String shortMessage(Throwable e) {
        if (e == null) {
            return "";
        }
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return e.getClass().getSimpleName();
        }
        int nl = msg.indexOf('\n');
        String first = nl > 0 ? msg.substring(0, nl) : msg;
        return first.length() > 220 ? first.substring(0, 220) + "..." : first;
    }
}

