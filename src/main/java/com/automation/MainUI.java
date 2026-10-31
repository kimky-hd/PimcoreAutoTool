package com.automation;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giao diện chính của tool (Java Swing) hỗ trợ xử lý hàng loạt 1-10 bài Google Docs.
 */
public class MainUI extends JFrame implements FlowListener {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG_CHARS = 400_000;
    public static final int MAX_DOC_URLS = 10;

    private static final Pattern DOC_ID_PATTERN = Pattern.compile("/d/([a-zA-Z0-9_-]+)");

    // ---- Cấu hình đầu vào ----
    private final JTextArea txtDocUrls = new JTextArea(5, 45);
    private final JLabel lblLinkCount = new JLabel("Số link hợp lệ: 0 / 10 (chưa có link nào)");
    private final JCheckBox chkSaveHtmlToDoc = new JCheckBox(
            "Tạo thẻ 'html' trong Google Doc", true);
    private final JCheckBox chkPasteToAppScript = new JCheckBox(
            "Dán vào App Script", true);
    private final JCheckBox chkPauseAfterArticle = new JCheckBox(
            "Tạm dừng sau mỗi bài", true);

    private final JTextField txtFolder = new JTextField("2025", 10);
    private final JTextField txtDate = new JTextField("28/12/2025", 10);
    private final JSpinner spSyncWait = new JSpinner(new SpinnerNumberModel(25, 1, 3600, 1));
    private final JTextField txtLanguage = new JTextField("Vietnamese", 10);
    private final JTextField txtChannel = new JTextField("Web", 10);
    private final JComboBox<PasteMode> cbPasteMode = new JComboBox<>(PasteMode.values());

    // ---- Nút điều khiển ----
    private final JButton btnChrome = new JButton("1. Mở Chrome Debug");
    private final JButton btnMockTabs = new JButton("🧪 Mở 2 Tab Mock Test");
    private final JButton btnStart = new JButton("2. Bắt đầu chạy (Start)");
    private final JButton btnPause = new JButton("Tạm dừng");
    private final JButton btnStop = new JButton("Dừng khẩn cấp (Stop)");
    private final JButton btnOpenSelectors = new JButton("Mở selectors.properties");
    private final JButton btnReloadSelectors = new JButton("Nạp lại selectors");
    private final JButton btnClearLog = new JButton("Xoá log");

    // ---- Tab HTML Đã Chuẩn Hóa ----
    private final JComboBox<ArticleHtmlItem> cbArticles = new JComboBox<>();
    private final JButton btnCopyCurrentHtml = new JButton("📋 Copy HTML bài này");
    private final JButton btnOpenOutputDir = new JButton("📁 Mở thư mục output_html");
    private final JLabel lblFileSavedInfo = new JLabel("Chưa có bài nào được xử lý.");
    private final JTextArea txtHtmlContent = new JTextArea();

    // ---- Log & trạng thái ----
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final JTextArea logArea = new JTextArea();
    private final JLabel lblStatus = new JLabel("Sẵn sàng");
    private final JLabel lblSelectorSource = new JLabel();
    private final JProgressBar progressBar = new JProgressBar();

    private final Selectors selectors;
    private final Preferences prefs = Preferences.userNodeForPackage(MainUI.class);
    private final Path logFile;

    private volatile AutomationFlow currentFlow;
    private volatile Thread flowThread;

    public MainUI() {
        super("Pimcore Auto Tool - Batch 1-10 Google Docs (Selenium 4 + Chrome Debug 9222)");
        AppIconHelper.applyToFrame(this);
        this.selectors = new Selectors();
        this.logFile = Paths.get(System.getProperty("user.dir"), "logs",
                "automation_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".log");
        loadPreferences();
        buildLayout();
        bindActions();
        setRunningState(false);
        updateSelectorSource();
        updateLinkCount();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        setMinimumSize(new Dimension(1000, 750));
        setSize(1120, 820);
        setLocationRelativeTo(null);

        onLog("Tool sẵn sàng. Quy trình: (1) Mở Chrome Debug -> mở sẵn tab Pimcore, Web App Script "
                + "-> (2) Dán 1-10 link Docs -> (3) Bắt đầu chạy.");
        onLog("Selectors đang dùng: " + selectors.getSource());
    }

    // =====================================================================
    //  GIAO DIỆN
    // =====================================================================

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        // ---- Khung cấu hình ----
        JPanel cfg = new JPanel(new GridBagLayout());
        cfg.setBorder(BorderFactory.createTitledBorder("Cấu hình chạy (Hỗ trợ 1 - 10 Google Docs)"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Row 0: Danh sách Google Docs (1-10 link)
        addLabel(cfg, c, 0, row, "Danh sách Google Docs:\n(1-10 link, mỗi dòng 1 link)");
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 5;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.BOTH;
        txtDocUrls.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtDocUrls.setLineWrap(false);
        JScrollPane scrollDocUrls = new JScrollPane(txtDocUrls);
        scrollDocUrls.setPreferredSize(new Dimension(500, 95));
        cfg.add(scrollDocUrls, c);
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        row++;
        // Row 1: Đếm link & Các tùy chọn thực thi
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 2;
        cfg.add(lblLinkCount, c);

        c.gridx = 3;
        c.gridy = row;
        c.gridwidth = 3;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chkSaveHtmlToDoc.setFont(chkSaveHtmlToDoc.getFont().deriveFont(Font.BOLD));
        chkSaveHtmlToDoc.setForeground(new Color(0, 110, 50));
        chkPasteToAppScript.setFont(chkPasteToAppScript.getFont().deriveFont(Font.PLAIN));
        chkPauseAfterArticle.setFont(chkPauseAfterArticle.getFont().deriveFont(Font.BOLD));
        chkPauseAfterArticle.setForeground(new Color(0, 90, 170));

        optionsPanel.add(chkSaveHtmlToDoc);
        optionsPanel.add(chkPasteToAppScript);
        optionsPanel.add(chkPauseAfterArticle);
        cfg.add(optionsPanel, c);
        c.gridwidth = 1;

        row++;
        // Row 2: Thư mục Pimcore, Thời gian, Thời gian chờ Sync
        addLabel(cfg, c, 0, row, "Thư mục Pimcore:");
        addField(cfg, c, 1, row, txtFolder);
        addLabel(cfg, c, 2, row, "Thời gian hiển thị:");
        addField(cfg, c, 3, row, txtDate);
        addLabel(cfg, c, 4, row, "Chờ Sync (giây):");
        addField(cfg, c, 5, row, spSyncWait);

        row++;
        // Row 3: Ngôn ngữ, Channel, Phương thức nhập
        addLabel(cfg, c, 0, row, "Ngôn ngữ hiển thị:");
        addField(cfg, c, 1, row, txtLanguage);
        addLabel(cfg, c, 2, row, "Access Channel:");
        addField(cfg, c, 3, row, txtChannel);
        addLabel(cfg, c, 4, row, "Phương thức nhập:");
        addField(cfg, c, 5, row, cbPasteMode);

        row++;
        // Row 4: Selectors source info
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 6;
        lblSelectorSource.setForeground(new Color(90, 90, 90));
        cfg.add(lblSelectorSource, c);
        c.gridwidth = 1;

        // ---- Nút điều khiển ----
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttons.setBorder(BorderFactory.createTitledBorder("Điều khiển"));
        Font bold = btnStart.getFont().deriveFont(Font.BOLD, 13f);
        btnChrome.setFont(bold);
        btnMockTabs.setFont(bold);
        btnMockTabs.setForeground(new Color(10, 80, 160));
        btnStart.setFont(bold);
        btnStop.setFont(bold);
        btnStart.setForeground(new Color(0, 110, 0));
        btnStop.setForeground(new Color(180, 0, 0));
        buttons.add(btnChrome);
        buttons.add(btnMockTabs);
        buttons.add(btnStart);
        buttons.add(btnPause);
        buttons.add(btnStop);
        buttons.add(new JLabel("   "));
        buttons.add(btnOpenSelectors);
        buttons.add(btnReloadSelectors);
        buttons.add(btnClearLog);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(cfg, BorderLayout.CENTER);
        north.add(buttons, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);

        // ---- Tabbed Center ----
        // Tab 1: Tiến trình & Log
        JPanel logTab = new JPanel(new BorderLayout(4, 4));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(250, 250, 247));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollLog = new JScrollPane(logArea);
        logTab.add(scrollLog, BorderLayout.CENTER);
        tabbedPane.addTab("📋 Tiến trình & Log", logTab);

        // Tab 2: HTML Đã Chuẩn Hóa
        JPanel htmlTab = new JPanel(new BorderLayout(6, 6));
        htmlTab.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel htmlToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        htmlToolbar.add(new JLabel("Chọn bài viết:"));
        cbArticles.setPreferredSize(new Dimension(420, 26));
        htmlToolbar.add(cbArticles);
        btnCopyCurrentHtml.setFont(btnCopyCurrentHtml.getFont().deriveFont(Font.BOLD));
        htmlToolbar.add(btnCopyCurrentHtml);
        htmlToolbar.add(btnOpenOutputDir);

        JPanel htmlHeader = new JPanel(new BorderLayout(4, 4));
        htmlHeader.add(htmlToolbar, BorderLayout.NORTH);
        lblFileSavedInfo.setFont(lblFileSavedInfo.getFont().deriveFont(Font.ITALIC, 12f));
        lblFileSavedInfo.setForeground(new Color(70, 70, 70));
        lblFileSavedInfo.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 0));
        htmlHeader.add(lblFileSavedInfo, BorderLayout.SOUTH);

        htmlTab.add(htmlHeader, BorderLayout.NORTH);

        txtHtmlContent.setEditable(false);
        txtHtmlContent.setLineWrap(true);
        txtHtmlContent.setWrapStyleWord(true);
        txtHtmlContent.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtHtmlContent.setBackground(new Color(252, 252, 250));
        JScrollPane scrollHtml = new JScrollPane(txtHtmlContent);
        htmlTab.add(scrollHtml, BorderLayout.CENTER);

        tabbedPane.addTab("📄 HTML Đã Chuẩn Hóa", htmlTab);

        root.add(tabbedPane, BorderLayout.CENTER);

        // ---- Thanh trạng thái ----
        JPanel south = new JPanel(new BorderLayout(8, 4));
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(340, 22));
        progressBar.setString("0 / 0 bước");
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD));
        south.add(lblStatus, BorderLayout.CENTER);
        south.add(progressBar, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
    }

    private static void addLabel(JPanel panel, GridBagConstraints c, int x, int y, String text) {
        c.gridx = x;
        c.gridy = y;
        c.weightx = 0;
        panel.add(new JLabel(text.contains("\n") ? "<html>" + text.replace("\n", "<br>") + "</html>" : text), c);
    }

    private static void addField(JPanel panel, GridBagConstraints c, int x, int y, java.awt.Component comp) {
        c.gridx = x;
        c.gridy = y;
        c.weightx = 0.3;
        panel.add(comp, c);
        c.weightx = 0;
    }

    private void bindActions() {
        btnChrome.addActionListener(e -> openChromeDebug());
        btnMockTabs.addActionListener(e -> openMockTabs());
        btnStart.addActionListener(e -> startFlow());
        btnPause.addActionListener(e -> togglePause());
        btnStop.addActionListener(e -> stopFlow());
        btnOpenSelectors.addActionListener(e -> openSelectorsFile());
        btnReloadSelectors.addActionListener(e -> reloadSelectors());
        btnClearLog.addActionListener(e -> logArea.setText(""));

        // Lắng nghe thay đổi ô dán Google Docs để cập nhật bộ đếm
        txtDocUrls.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateLinkCount(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateLinkCount(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateLinkCount(); }
        });

        // Chọn bài viết trong tab HTML
        cbArticles.addActionListener(e -> {
            ArticleHtmlItem selected = (ArticleHtmlItem) cbArticles.getSelectedItem();
            if (selected != null) {
                txtHtmlContent.setText(selected.cleanHtml());
                txtHtmlContent.setCaretPosition(0);
                lblFileSavedInfo.setText("Đã lưu tại: " + (selected.savedFilePath() != null
                        ? selected.savedFilePath().toAbsolutePath().toString() : "N/A"));
            }
        });

        // Copy HTML bài đang chọn
        btnCopyCurrentHtml.addActionListener(e -> {
            String html = txtHtmlContent.getText();
            if (html == null || html.isBlank()) {
                JOptionPane.showMessageDialog(this, "Chưa có nội dung HTML để copy!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                StringSelection selection = new StringSelection(html);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                ArticleHtmlItem selected = (ArticleHtmlItem) cbArticles.getSelectedItem();
                String name = (selected != null) ? "bài " + selected.articleNo() : "hiện tại";
                JOptionPane.showMessageDialog(this, "Đã copy toàn bộ mã HTML " + name + " vào Clipboard!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi copy vào Clipboard: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Mở thư mục output_html
        btnOpenOutputDir.addActionListener(e -> {
            try {
                Path outDir = Paths.get("output_html");
                if (!Files.exists(outDir)) {
                    Files.createDirectories(outDir);
                }
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(outDir.toFile());
                } else {
                    new ProcessBuilder("explorer.exe", outDir.toAbsolutePath().toString()).start();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không mở được thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void updateLinkCount() {
        List<String> urls = parseValidDocUrls();
        int count = urls.size();
        if (count == 0) {
            lblLinkCount.setText("Số link hợp lệ: 0 / " + MAX_DOC_URLS + " (chưa có link nào)");
            lblLinkCount.setForeground(Color.GRAY);
        } else if (count <= MAX_DOC_URLS) {
            lblLinkCount.setText("Số link hợp lệ: " + count + " / " + MAX_DOC_URLS);
            lblLinkCount.setForeground(new Color(0, 120, 0));
        } else {
            lblLinkCount.setText("Số link: " + count + " / " + MAX_DOC_URLS + " (CẢNH BÁO: Tối đa 10 link, link thừa sẽ bị bỏ qua!)");
            lblLinkCount.setForeground(new Color(180, 50, 0));
        }
    }

    /**
     * Tách từng dòng trong ô nhập Google Docs, lọc bỏ dòng trống và giữ lại link hợp lệ.
     */
    private List<String> parseValidDocUrls() {
        String text = txtDocUrls.getText();
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (DOC_ID_PATTERN.matcher(line).find() || line.contains("docs.google.com")
                    || line.matches("^[a-zA-Z0-9_-]{25,}$")) {
                list.add(line);
            }
        }
        return list;
    }

    private void setRunningState(boolean running) {
        btnStart.setEnabled(!running);
        btnMockTabs.setEnabled(!running);
        btnPause.setEnabled(running);
        btnStop.setEnabled(running);
        btnReloadSelectors.setEnabled(!running);
        txtDocUrls.setEnabled(!running);
        chkSaveHtmlToDoc.setEnabled(!running);
        chkPasteToAppScript.setEnabled(!running);
        chkPauseAfterArticle.setEnabled(!running);
        txtFolder.setEnabled(!running);
        txtDate.setEnabled(!running);
        spSyncWait.setEnabled(!running);
        txtLanguage.setEnabled(!running);
        txtChannel.setEnabled(!running);
        cbPasteMode.setEnabled(!running);
        if (!running) {
            btnPause.setText("Tạm dừng");
        }
    }

    private void updateSelectorSource() {
        lblSelectorSource.setText("File selectors: " + selectors.getSource());
    }

    // =====================================================================
    //  HÀNH ĐỘNG
    // =====================================================================

    /** Nút "1. Mở Chrome Debug". */
    private void openChromeDebug() {
        btnChrome.setEnabled(false);
        Thread t = new Thread(() -> {
            try {
                if (ChromeLauncher.isDebugPortOpen()) {
                    onLog("Chrome Debug ĐÃ chạy sẵn tại " + ChromeLauncher.DEBUGGER_ADDRESS + " -> không cần mở lại.");
                    return;
                }
                String chromePath = selectors.get("chrome.path", "");
                onLog("Đang mở Chrome: " + ChromeLauncher.describeCommand(chromePath));
                List<String> urls = new ArrayList<>();
                List<String> docUrls = parseValidDocUrls();
                if (!docUrls.isEmpty()) {
                    urls.add(docUrls.get(0));
                }
                ChromeLauncher.launch(chromePath, urls);
                for (int i = 0; i < 40; i++) {
                    Thread.sleep(500);
                    if (ChromeLauncher.isDebugPortOpen()) {
                        onLog("Chrome Debug đã sẵn sàng tại " + ChromeLauncher.DEBUGGER_ADDRESS
                                + ". Hãy đăng nhập và mở sẵn các tab: Pimcore, Web App Script.");
                        onStatus("Chrome Debug đang chạy");
                        return;
                    }
                }
                onLog("[LỖI] Đã chạy chrome.exe nhưng cổng 9222 không mở. Hãy TẮT HẾT cửa sổ Chrome "
                        + "đang dùng profile " + ChromeLauncher.PROFILE_DIR + " rồi thử lại.");
            } catch (IOException ex) {
                onLog("[LỖI] Không mở được Chrome: " + ex.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                SwingUtilities.invokeLater(() -> btnChrome.setEnabled(true));
            }
        }, "chrome-launcher");
        t.setDaemon(true);
        t.start();
    }

    /** Nút "🧪 Mở 2 Tab Mock Test (Pimcore & AppScript)". */
    private void openMockTabs() {
        btnMockTabs.setEnabled(false);
        Thread t = new Thread(() -> {
            try {
                Path pimcoreHtml = Paths.get("mock_environment", "mock_pimcore.html").toAbsolutePath();
                Path appscriptHtml = Paths.get("mock_environment", "mock_appscript.html").toAbsolutePath();

                if (!Files.exists(pimcoreHtml) || !Files.exists(appscriptHtml)) {
                    onLog("[LỖI] Không tìm thấy các file mock trong thư mục mock_environment/");
                    return;
                }

                String pimcoreUrl = pimcoreHtml.toUri().toString();
                String appscriptUrl = appscriptHtml.toUri().toString();

                if (!ChromeLauncher.isDebugPortOpen()) {
                    String chromePath = selectors.get("chrome.path", "");
                    onLog("Chrome Debug chưa mở -> Đang khởi chạy Chrome Debug với 2 tab Mock...");
                    List<String> urls = new ArrayList<>();
                    urls.add(pimcoreUrl);
                    urls.add(appscriptUrl);
                    List<String> docUrls = parseValidDocUrls();
                    if (!docUrls.isEmpty()) {
                        urls.add(docUrls.get(0));
                    }
                    ChromeLauncher.launch(chromePath, urls);
                    for (int i = 0; i < 40; i++) {
                        Thread.sleep(500);
                        if (ChromeLauncher.isDebugPortOpen()) {
                            onLog("Chrome Debug đã sẵn sàng! Đã nạp 2 tab Mock: Pimcore Admin và Web App Script.");
                            onStatus("Chrome Debug (Mock) đang chạy");
                            return;
                        }
                    }
                    onLog("[LỖI] Không kết nối được cổng 9222 sau khi khởi chạy Chrome.");
                } else {
                    onLog("Chrome Debug đang chạy -> Đang mở 2 tab Mock vào cửa sổ Chrome hiện tại...");
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    client.send(java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:9222/json/new?" + pimcoreUrl))
                            .PUT(java.net.http.HttpRequest.BodyPublishers.noBody())
                            .build(), java.net.http.HttpResponse.BodyHandlers.discarding());

                    client.send(java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:9222/json/new?" + appscriptUrl))
                            .PUT(java.net.http.HttpRequest.BodyPublishers.noBody())
                            .build(), java.net.http.HttpResponse.BodyHandlers.discarding());

                    onLog("Đã mở thành công 2 tab Mock: Pimcore Admin và Web App Script trên Chrome!");
                    onStatus("Đã nạp 2 tab Mock vào Chrome");
                }
            } catch (Exception ex) {
                onLog("[LỖI] Không mở được tab Mock: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> btnMockTabs.setEnabled(true));
            }
        }, "mock-tabs-launcher");
        t.setDaemon(true);
        t.start();
    }

    /** Nút "2. Bắt đầu chạy (Start)". */
    private void startFlow() {
        if (flowThread != null && flowThread.isAlive()) {
            return;
        }

        List<String> validUrls = parseValidDocUrls();
        if (validUrls.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui lòng dán ít nhất 1 đường link Google Docs (tối đa 10 link, mỗi dòng 1 link) vào ô nhập trước khi chạy!",
                    "Chưa có link bài viết", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (validUrls.size() > MAX_DOC_URLS) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Bạn đã nhập " + validUrls.size() + " link. Tool hỗ trợ tối đa 10 link trong 1 lượt chạy.\n"
                            + "Chỉ thực hiện chạy 10 link đầu tiên?",
                    "Xác nhận số lượng link", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            validUrls = new ArrayList<>(validUrls.subList(0, MAX_DOC_URLS));
        }

        commitSpinners();
        String date = txtDate.getText().trim();
        String folder = txtFolder.getText().trim();
        if (date.isEmpty() || folder.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Thời gian hiển thị và Thư mục Pimcore.",
                    "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!date.matches("\\d{1,2}/\\d{1,2}/\\d{4}.*")) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Thời gian \"" + date + "\" không theo dạng dd/MM/yyyy. Vẫn tiếp tục?",
                    "Kiểm tra định dạng ngày", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            selectors.reload();
            updateSelectorSource();
        } catch (RuntimeException ex) {
            onLog("[LỖI] Không nạp được selectors: " + ex.getMessage());
            return;
        }

        RunConfig config = new RunConfig(
                validUrls,
                folder,
                date,
                (Integer) spSyncWait.getValue(),
                txtLanguage.getText().trim(),
                txtChannel.getText().trim(),
                (PasteMode) cbPasteMode.getSelectedItem(),
                chkPauseAfterArticle.isSelected(),
                chkSaveHtmlToDoc.isSelected(),
                chkPasteToAppScript.isSelected()
        );
        savePreferences();

        int totalSteps = config.docUrls().size() * AutomationFlow.TOTAL_STEPS;
        progressBar.setMaximum(totalSteps);
        progressBar.setValue(0);
        progressBar.setString("0 / " + totalSteps + " bước");

        AutomationFlow flow = new AutomationFlow(config, selectors, this);
        currentFlow = flow;
        flowThread = new Thread(flow, "automation-flow");
        flowThread.setDaemon(true);
        setRunningState(true);
        lblStatus.setText("Đang chạy...");
        flowThread.start();
    }

    /** Nút "Tạm dừng / Tiếp tục". */
    private void togglePause() {
        AutomationFlow flow = currentFlow;
        if (flow == null) {
            return;
        }
        boolean nowPaused = !flow.control().isPaused();
        flow.control().setPaused(nowPaused);
        btnPause.setText(nowPaused ? "Tiếp tục" : "Tạm dừng");
        onLog(nowPaused ? "[TẠM DỪNG] Tool sẽ đứng lại ở thao tác kế tiếp. Bấm \"Tiếp tục\" để chạy tiếp."
                : "[TIẾP TỤC] Tool tiếp tục chạy.");
        onStatus(nowPaused ? "ĐANG TẠM DỪNG" : "Đang chạy...");
    }

    /** Nút "Dừng khẩn cấp (Stop)". */
    private void stopFlow() {
        AutomationFlow flow = currentFlow;
        Thread thread = flowThread;
        if (flow == null || thread == null) {
            return;
        }
        onLog("[STOP] Đã yêu cầu dừng khẩn cấp...");
        flow.control().requestStop();
        btnStop.setEnabled(false);
        btnPause.setEnabled(false);
        Thread killer = new Thread(() -> {
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (thread.isAlive()) {
                onLog("[STOP] Lệnh trình duyệt đang treo -> ngắt ChromeDriver (Chrome vẫn giữ nguyên).");
                flow.forceStop();
            }
        }, "force-stop");
        killer.setDaemon(true);
        killer.start();
    }

    private void openSelectorsFile() {
        try {
            Path file = selectors.exportDefaultIfMissing();
            selectors.reload();
            updateSelectorSource();
            onLog("File selectors: " + file);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                new ProcessBuilder("notepad.exe", file.toString()).start();
            }
        } catch (IOException | RuntimeException ex) {
            onLog("[LỖI] Không mở được selectors.properties: " + ex.getMessage());
        }
    }

    private void reloadSelectors() {
        try {
            selectors.reload();
            updateSelectorSource();
            onLog("Đã nạp lại selectors từ: " + selectors.getSource());
        } catch (RuntimeException ex) {
            onLog("[LỖI] Nạp selectors thất bại: " + ex.getMessage());
        }
    }

    private void onClose() {
        Thread thread = flowThread;
        if (thread != null && thread.isAlive()) {
            int choice = JOptionPane.showConfirmDialog(this, "Tool đang chạy. Dừng và thoát?",
                    "Thoát", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            AutomationFlow flow = currentFlow;
            if (flow != null) {
                flow.forceStop();
            }
        }
        savePreferences();
        dispose();
        System.exit(0);
    }

    // =====================================================================
    //  FlowListener (gọi từ luồng automation)
    // =====================================================================

    @Override
    public void onLog(String message) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + message;
        writeLogFile(line);
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            int len = logArea.getDocument().getLength();
            if (len > MAX_LOG_CHARS) {
                logArea.replaceRange("", 0, len - MAX_LOG_CHARS / 2);
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onStep(int articleNo, int totalArticles, int stepNo, int totalSteps, String title) {
        SwingUtilities.invokeLater(() -> {
            int done = (articleNo - 1) * totalSteps + (stepNo - 1);
            progressBar.setValue(done);
            progressBar.setString(done + " / " + progressBar.getMaximum() + " bước");
            lblStatus.setText("Bài " + articleNo + "/" + totalArticles + " - Bước " + stepNo + "/" + totalSteps + ": " + title);
        });
    }

    @Override
    public void onStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(status);
            AutomationFlow flow = currentFlow;
            if (flow != null && flow.control().isPaused()) {
                btnPause.setText("Tiếp tục");
            }
        });
    }

    @Override
    public void onFinished(boolean success, String summary) {
        SwingUtilities.invokeLater(() -> {
            if (success) {
                progressBar.setValue(progressBar.getMaximum());
                progressBar.setString(progressBar.getMaximum() + " / " + progressBar.getMaximum() + " bước");
            }
            lblStatus.setText((success ? "HOÀN THÀNH: " : "KẾT THÚC: ") + summary);
            lblStatus.setForeground(success ? new Color(0, 120, 0) : new Color(180, 0, 0));
            setRunningState(false);
            currentFlow = null;
            flowThread = null;
        });
    }

    @Override
    public void onArticleHtmlReady(int articleNo, int totalArticles, String docId, String title,
                                   String cleanHtml, Path savedFilePath) {
        SwingUtilities.invokeLater(() -> {
            ArticleHtmlItem item = new ArticleHtmlItem(articleNo, totalArticles, docId, title, cleanHtml, savedFilePath);
            cbArticles.addItem(item);
            cbArticles.setSelectedItem(item);
            txtHtmlContent.setText(cleanHtml);
            txtHtmlContent.setCaretPosition(0);
            lblFileSavedInfo.setText("Đã lưu tại: " + (savedFilePath != null ? savedFilePath.toAbsolutePath().toString() : "N/A"));
        });
    }

    private synchronized void writeLogFile(String line) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // ghi file log thất bại không ảnh hưởng tới việc chạy
        }
    }

    // =====================================================================
    //  LƯU / NẠP CẤU HÌNH GIAO DIỆN
    // =====================================================================

    private void commitSpinners() {
        try {
            spSyncWait.commitEdit();
        } catch (ParseException ignored) {
            // giữ giá trị hợp lệ gần nhất
        }
    }

    private void loadPreferences() {
        txtDocUrls.setText(prefs.get("docUrls", ""));
        chkSaveHtmlToDoc.setSelected(prefs.getBoolean("saveHtmlToDoc", true));
        chkPasteToAppScript.setSelected(prefs.getBoolean("pasteToAppScript", true));
        chkPauseAfterArticle.setSelected(prefs.getBoolean("pauseAfterArticle", true));
        txtFolder.setText(prefs.get("folder", "2025"));
        txtDate.setText(prefs.get("date", "28/12/2025"));
        spSyncWait.setValue(prefs.getInt("syncWait", 25));
        txtLanguage.setText(prefs.get("language", "Vietnamese"));
        txtChannel.setText(prefs.get("channel", "Web"));
        try {
            cbPasteMode.setSelectedItem(PasteMode.valueOf(prefs.get("pasteMode", PasteMode.CLIPBOARD_THEN_SENDKEYS.name())));
        } catch (IllegalArgumentException e) {
            cbPasteMode.setSelectedItem(PasteMode.CLIPBOARD_THEN_SENDKEYS);
        }
    }

    private void savePreferences() {
        commitSpinners();
        prefs.put("docUrls", txtDocUrls.getText().trim());
        prefs.putBoolean("saveHtmlToDoc", chkSaveHtmlToDoc.isSelected());
        prefs.putBoolean("pasteToAppScript", chkPasteToAppScript.isSelected());
        prefs.putBoolean("pauseAfterArticle", chkPauseAfterArticle.isSelected());
        prefs.put("folder", txtFolder.getText().trim());
        prefs.put("date", txtDate.getText().trim());
        prefs.putInt("syncWait", (Integer) spSyncWait.getValue());
        prefs.put("language", txtLanguage.getText().trim());
        prefs.put("channel", txtChannel.getText().trim());
        PasteMode mode = (PasteMode) cbPasteMode.getSelectedItem();
        prefs.put("pasteMode", mode == null ? PasteMode.CLIPBOARD_THEN_SENDKEYS.name() : mode.name());
    }

    // =====================================================================
    //  DATA CLASS CHO TAB HTML
    // =====================================================================

    public record ArticleHtmlItem(int articleNo, int totalArticles, String docId, String title,
                                  String cleanHtml, Path savedFilePath) {
        @Override
        public String toString() {
            String shortTitle = (title != null && !title.isBlank())
                    ? (title.length() > 38 ? title.substring(0, 35) + "..." : title)
                    : ("Doc " + docId);
            return "Bài " + articleNo + "/" + totalArticles + " [" + docId + "] - " + shortTitle;
        }
    }

    // =====================================================================
    //  MAIN
    // =====================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // dùng giao diện mặc định
            }
            new MainUI().setVisible(true);
        });
    }
}
