package com.automation;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Khởi chạy Chrome ở chế độ Remote Debugging (cổng 9222) với profile riêng
 * C:\ChromeAutomationProfile để Selenium kết nối vào và dùng lại các phiên đăng nhập.
 */
public final class ChromeLauncher {

    public static final String HOST = "127.0.0.1";
    public static final int PORT = 9222;
    public static final String DEBUGGER_ADDRESS = HOST + ":" + PORT;
    public static final String PROFILE_DIR = "C:\\ChromeAutomationProfile";

    private ChromeLauncher() {
    }

    /** Kiểm tra cổng 127.0.0.1:9222 đã mở (Chrome Debug đang chạy) hay chưa. */
    public static boolean isDebugPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 800);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Tìm chrome.exe: ưu tiên đường dẫn cấu hình, sau đó các vị trí cài đặt mặc định. */
    public static Path findChrome(String configuredPath) {
        List<String> candidates = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            candidates.add(configuredPath.trim());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            candidates.add("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe");
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                candidates.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
            }
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                candidates.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
            }
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            if (programFilesX86 != null) {
                candidates.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
            }
        } else if (os.contains("mac")) {
            candidates.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
        } else {
            candidates.add("/usr/bin/google-chrome");
            candidates.add("/usr/bin/google-chrome-stable");
            candidates.add("/usr/bin/chromium-browser");
        }
        for (String candidate : candidates) {
            Path p = Paths.get(candidate);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Chạy lệnh:
     * chrome.exe --remote-debugging-port=9222 --user-data-dir=C:\ChromeAutomationProfile [url...]
     */
    public static Process launch(String configuredPath, List<String> startUrls) throws IOException {
        Path chrome = findChrome(configuredPath);
        if (chrome == null) {
            throw new IOException("Không tìm thấy chrome.exe. Hãy điền 'chrome.path' trong selectors.properties");
        }
        File profile = new File(PROFILE_DIR);
        if (!profile.exists() && !profile.mkdirs()) {
            throw new IOException("Không tạo được thư mục profile " + PROFILE_DIR);
        }
        List<String> command = new ArrayList<>();
        command.add(chrome.toString());
        command.add("--remote-debugging-port=" + PORT);
        command.add("--user-data-dir=" + PROFILE_DIR);
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        if (startUrls != null) {
            for (String url : startUrls) {
                if (url != null && !url.isBlank()) {
                    command.add(url.trim());
                }
            }
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    /** Chuỗi lệnh dùng để hiển thị log. */
    public static String describeCommand(String configuredPath) {
        Path chrome = findChrome(configuredPath);
        return "\"" + (chrome == null ? "chrome.exe" : chrome) + "\" --remote-debugging-port=" + PORT
                + " --user-data-dir=" + PROFILE_DIR;
    }
}

