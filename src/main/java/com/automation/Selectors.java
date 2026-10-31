package com.automation;

import org.openqa.selenium.By;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Nạp toàn bộ XPath / CSS Selector và thông số thời gian từ file {@code selectors.properties}.
 * <p>
 * Ưu tiên file nằm cạnh file .jar (thư mục làm việc), nếu không có thì dùng bản mặc định
 * đóng gói trong .jar. Người dùng chỉ cần F12 -> Copy XPath -> dán vào file là chạy.
 */
public final class Selectors {

    public static final String FILE_NAME = "selectors.properties";

    private final Properties props = new Properties();
    private volatile String source = "";

    public Selectors() {
        reload();
    }

    /** Đường dẫn file selectors bên ngoài (cùng thư mục chạy tool). */
    public static Path externalPath() {
        return Paths.get(System.getProperty("user.dir"), FILE_NAME).toAbsolutePath();
    }

    /** Đọc lại file cấu hình (gọi mỗi lần bấm Start để nhận thay đổi mới nhất). */
    public synchronized void reload() {
        Properties loaded = new Properties();
        Path external = externalPath();
        try {
            if (Files.isRegularFile(external)) {
                try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                    loaded.load(reader);
                }
                source = external.toString();
            } else {
                try (InputStream in = Selectors.class.getResourceAsStream("/" + FILE_NAME)) {
                    if (in == null) {
                        throw new IllegalStateException("Không tìm thấy " + FILE_NAME + " trong file .jar");
                    }
                    try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        loaded.load(reader);
                    }
                }
                source = "Mặc định trong .jar (chưa có file " + FILE_NAME + " bên ngoài)";
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi đọc " + FILE_NAME + ": " + e.getMessage(), e);
        }
        props.clear();
        props.putAll(loaded);
    }

    /** Xuất bản mặc định ra cạnh file .jar nếu chưa có, trả về đường dẫn file. */
    public Path exportDefaultIfMissing() throws IOException {
        Path external = externalPath();
        if (!Files.exists(external)) {
            try (InputStream in = Selectors.class.getResourceAsStream("/" + FILE_NAME)) {
                if (in == null) {
                    throw new IOException("Không tìm thấy " + FILE_NAME + " trong file .jar");
                }
                Files.copy(in, external);
            }
        }
        return external;
    }

    public String getSource() {
        return source;
    }

    /** Lấy giá trị bắt buộc phải có. */
    public synchronized String raw(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Thiếu key '" + key + "' trong " + FILE_NAME);
        }
        return value.trim();
    }

    public synchronized String get(String key, String defaultValue) {
        String value = props.getProperty(key);
        return value == null ? defaultValue : value.trim();
    }

    public boolean isBlank(String key) {
        return get(key, "").isEmpty();
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key, "");
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String value = get(key, "");
        if (value.isEmpty()) {
            return defaultValue;
        }
        return value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes");
    }

    /** Danh sách giá trị phân tách bằng dấu phẩy (bỏ phần tử rỗng). */
    public List<String> getList(String key) {
        List<String> list = new ArrayList<>();
        for (String part : get(key, "").split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Lấy selector đã thay biến. Tham số dạng cặp: "folder", "2025", "value", "Web" ...
     */
    public String resolve(String key, String... placeholders) {
        String value = raw(key);
        if (value.isEmpty()) {
            throw new IllegalStateException("Selector '" + key + "' đang để trống trong " + FILE_NAME);
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            value = value.replace("{" + placeholders[i] + "}", placeholders[i + 1] == null ? "" : placeholders[i + 1]);
        }
        return value;
    }

    /** Chuyển key thành đối tượng {@link By} của Selenium. */
    public By by(String key, String... placeholders) {
        return toBy(resolve(key, placeholders));
    }

    /** Phân tích chuỗi selector có tiền tố xpath:/css:/id:/name: thành {@link By}. */
    public static By toBy(String selector) {
        String s = selector.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("xpath:")) {
            return By.xpath(s.substring(6).trim());
        }
        if (lower.startsWith("css:")) {
            return By.cssSelector(s.substring(4).trim());
        }
        if (lower.startsWith("id:")) {
            return By.id(s.substring(3).trim());
        }
        if (lower.startsWith("name:")) {
            return By.name(s.substring(5).trim());
        }
        if (s.startsWith("/") || s.startsWith("(") || s.startsWith("./")) {
            return By.xpath(s);
        }
        return By.cssSelector(s);
    }
}

