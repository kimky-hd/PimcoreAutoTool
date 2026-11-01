package com.automation;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
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
    private final JTextField txtDate = new JTextField("2025-12-28", 10);
    private final JButton btnCalendar = new JButton();
    private DatePickerPopup datePickerPopup;
    private final JSpinner spSyncWait = new JSpinner(new SpinnerNumberModel(25, 1, 3600, 1));
    private final JComboBox<String> cbLanguage = new JComboBox<>(new String[]{"Vietnamese", "English"});
    private final JComboBox<String> cbChannel = new JComboBox<>(new String[]{"Web", "App"});
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
    private final JButton btnToggleTheme = new JButton();

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
        cbLanguage.setEditable(true);
        cbChannel.setEditable(true);
        initDatePicker();
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
    //  GIAO DIỆN PHONG CÁCH INTELLIJ IDEA (DARCULA THEME)
    // =====================================================================

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout());
        setContentPane(root);

        // 1. TOOLBAR PHÍA TRÊN (Giống Navigation / Run Toolbar của IntelliJ)
        JToolBar topBar = new JToolBar();
        topBar.setFloatable(false);
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(48, 52, 60)));
        topBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JLabel appTitle = new JLabel("  Pimcore Automation Flow  ");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        appTitle.setForeground(new Color(180, 190, 205));
        topBar.add(appTitle);
        topBar.add(Box.createHorizontalStrut(8));

        // Gán Vector Icon và Text chuẩn IntelliJ
        btnChrome.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.CHROME_BLUE, 16));
        btnMockTabs.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.TEST_LAB, 16));
        btnStart.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.RUN_GREEN, 16));
        btnPause.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.PAUSE_AMBER, 16));
        btnStop.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.STOP_RED, 16));

        btnChrome.setText("1. Mở Chrome Debug");
        btnMockTabs.setText("Tab Mock Test");
        btnStart.setText("2. Chạy (Run)");
        btnPause.setText("Tạm dừng");
        btnStop.setText("Dừng (Stop)");

        styleToolButton(btnChrome, new Color(45, 68, 98), Color.WHITE);
        styleToolButton(btnMockTabs, new Color(55, 58, 62), new Color(190, 195, 200));
        styleToolButton(btnStart, new Color(38, 105, 55), Color.WHITE);
        styleToolButton(btnPause, new Color(55, 58, 62), new Color(190, 195, 200));
        styleToolButton(btnStop, new Color(140, 40, 40), Color.WHITE);

        topBar.add(btnChrome);
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(btnMockTabs);
        topBar.add(Box.createHorizontalStrut(12));
        topBar.add(btnStart);
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(btnPause);
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(btnStop);

        topBar.add(Box.createHorizontalGlue());

        btnOpenSelectors.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.SETTINGS_FILE, 15));
        btnReloadSelectors.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.RELOAD_ARROW, 15));
        btnClearLog.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.CLEAR_BIN, 15));

        btnOpenSelectors.setText("selectors.properties");
        btnReloadSelectors.setText("Nạp lại");
        btnClearLog.setText("Xóa log");

        styleFlatLinkButton(btnOpenSelectors);
        styleFlatLinkButton(btnReloadSelectors);
        styleFlatLinkButton(btnClearLog);
        styleFlatLinkButton(btnToggleTheme);
        updateThemeButtonUI();

        topBar.add(btnOpenSelectors);
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(btnReloadSelectors);
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(btnClearLog);
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(btnToggleTheme);

        root.add(topBar, BorderLayout.NORTH);

        // 2. KHUNG CẤU HÌNH (INSPECTION / SETTINGS PANEL)
        JPanel cfg = new JPanel(new GridBagLayout());
        cfg.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(48, 52, 60)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 8, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Row 0: Danh sách Google Docs
        addLabel(cfg, c, 0, row, "Danh sách Google Docs:\n(1-10 URL, mỗi dòng 1 bài)");
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 5;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.BOTH;
        txtDocUrls.setFont(IntelliJThemeHelper.JETBRAINS_MONO_PLAIN);
        txtDocUrls.setLineWrap(false);
        JScrollPane scrollDocUrls = new JScrollPane(txtDocUrls);
        scrollDocUrls.setPreferredSize(new Dimension(500, 85));
        cfg.add(scrollDocUrls, c);
        c.gridwidth = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        row++;
        // Row 1: Đếm link & Tùy chọn thực thi
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 2;
        cfg.add(lblLinkCount, c);

        c.gridx = 3;
        c.gridy = row;
        c.gridwidth = 3;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        optionsPanel.setOpaque(false);
        chkSaveHtmlToDoc.setFont(chkSaveHtmlToDoc.getFont().deriveFont(Font.BOLD));
        chkPauseAfterArticle.setFont(chkPauseAfterArticle.getFont().deriveFont(Font.BOLD));

        optionsPanel.add(chkSaveHtmlToDoc);
        optionsPanel.add(chkPasteToAppScript);
        optionsPanel.add(chkPauseAfterArticle);
        cfg.add(optionsPanel, c);
        c.gridwidth = 1;

        row++;
        // Row 2: Thư mục Pimcore, Thời gian, Hạn mức Timeout chờ Sync
        addLabel(cfg, c, 0, row, "Thư mục Pimcore:");
        addField(cfg, c, 1, row, txtFolder);
        addLabel(cfg, c, 2, row, "Thời gian hiển thị:");
        addField(cfg, c, 3, row, txtDate);
        addLabel(cfg, c, 4, row, "Sync Timeout tối đa (s):");
        spSyncWait.setToolTipText("Hạn mức chờ tối đa trước khi báo lỗi. Tool sẽ tự động đi tiếp ngay khi Pimcore tải xong dữ liệu (không bắt buộc đợi đủ số giây này).");
        addField(cfg, c, 5, row, spSyncWait);

        row++;
        // Row 3: Ngôn ngữ, Channel, Phương thức nhập
        addLabel(cfg, c, 0, row, "Ngôn ngữ hiển thị:");
        addField(cfg, c, 1, row, cbLanguage);
        addLabel(cfg, c, 2, row, "Access Channel:");
        addField(cfg, c, 3, row, cbChannel);
        addLabel(cfg, c, 4, row, "Phương thức nhập:");
        addField(cfg, c, 5, row, cbPasteMode);

        row++;
        // Row 4: Selectors file info
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 6;
        lblSelectorSource.setForeground(new Color(130, 135, 145));
        lblSelectorSource.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cfg.add(lblSelectorSource, c);
        c.gridwidth = 1;

        // 3. KHU VỰC TOOL WINDOWS / TABS (IntelliJ Style)
        tabbedPane.setBorder(BorderFactory.createEmptyBorder());

        // Tab 1: Terminal / Run Log
        JPanel logTab = new JPanel(new BorderLayout());
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(IntelliJThemeHelper.JETBRAINS_MONO_PLAIN);
        logArea.setBackground(new Color(30, 31, 34)); // Dark editor background
        logArea.setForeground(new Color(187, 187, 187));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.setBorder(BorderFactory.createEmptyBorder());
        logTab.add(scrollLog, BorderLayout.CENTER);
        tabbedPane.addTab("  Run: Execution Log  ", logTab);

        // Tab 2: HTML Output / Editor
        JPanel htmlTab = new JPanel(new BorderLayout());
        JPanel htmlToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        htmlToolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(48, 52, 60)));

        JLabel lblSelectArticle = new JLabel("Bài viết:");
        lblSelectArticle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        htmlToolbar.add(lblSelectArticle);
        cbArticles.setPreferredSize(new Dimension(380, 28));
        htmlToolbar.add(cbArticles);

        btnCopyCurrentHtml.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.COPY_CLIPBOARD, 16));
        btnCopyCurrentHtml.setText("Copy HTML bài này");
        styleToolButton(btnCopyCurrentHtml, new Color(45, 68, 98), Color.WHITE);
        htmlToolbar.add(btnCopyCurrentHtml);

        btnOpenOutputDir.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.FOLDER_OPEN, 16));
        btnOpenOutputDir.setText("Thư mục output_html");
        styleFlatLinkButton(btnOpenOutputDir);
        htmlToolbar.add(btnOpenOutputDir);

        JPanel htmlHeader = new JPanel(new BorderLayout());
        htmlHeader.add(htmlToolbar, BorderLayout.NORTH);
        lblFileSavedInfo.setFont(IntelliJThemeHelper.JETBRAINS_MONO_PLAIN.deriveFont(11f));
        lblFileSavedInfo.setForeground(new Color(120, 130, 140));
        lblFileSavedInfo.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        htmlHeader.add(lblFileSavedInfo, BorderLayout.SOUTH);
        htmlTab.add(htmlHeader, BorderLayout.NORTH);

        txtHtmlContent.setEditable(false);
        txtHtmlContent.setLineWrap(true);
        txtHtmlContent.setWrapStyleWord(true);
        txtHtmlContent.setFont(IntelliJThemeHelper.JETBRAINS_MONO_PLAIN);
        txtHtmlContent.setBackground(new Color(30, 31, 34));
        txtHtmlContent.setForeground(new Color(220, 220, 220));
        JScrollPane scrollHtml = new JScrollPane(txtHtmlContent);
        scrollHtml.setBorder(BorderFactory.createEmptyBorder());
        htmlTab.add(scrollHtml, BorderLayout.CENTER);

        tabbedPane.addTab("  Code: Cleaned Template HTML  ", htmlTab);

        // Ghép Cấu hình và Tabs vào SplitPane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cfg, tabbedPane);
        splitPane.setDividerSize(4);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        root.add(splitPane, BorderLayout.CENTER);

        // 4. THANH TRẠNG THÁI STATUS BAR PHÍA DƯỚI (IntelliJ Status Bar)
        JPanel statusBar = new JPanel(new BorderLayout(12, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(48, 52, 60)),
                BorderFactory.createEmptyBorder(4, 14, 4, 14)
        ));
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(180, 185, 195));

        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 18));
        progressBar.setString("0 / 0 bước");
        progressBar.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        statusBar.add(lblStatus, BorderLayout.CENTER);
        statusBar.add(progressBar, BorderLayout.EAST);
        root.add(statusBar, BorderLayout.SOUTH);
    }

    private static void styleToolButton(JButton btn, Color bg, Color fg) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    }

    private static void styleFlatLinkButton(JButton btn) {
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
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
        btnToggleTheme.addActionListener(e -> toggleTheme());

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
        btnCalendar.setEnabled(!running);
        spSyncWait.setEnabled(!running);
        cbLanguage.setEnabled(!running);
        cbChannel.setEnabled(!running);
        cbPasteMode.setEnabled(!running);
        if (!running) {
            btnPause.setText("Tạm dừng");
            btnPause.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.PAUSE_AMBER, 16));
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
                String pimcoreUrl = selectors.get("pimcore.loginUrl", "");
                String appScriptUrl = selectors.get("appscript.url", "");
                if (!pimcoreUrl.isBlank()) {
                    urls.add(pimcoreUrl.trim());
                }
                if (!appScriptUrl.isBlank()) {
                    urls.add(appScriptUrl.trim());
                }
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
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}.*") && !date.matches("\\d{1,2}/\\d{1,2}/\\d{4}.*")) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Thời gian \"" + date + "\" không theo định dạng yyyy-MM-dd (ví dụ: "
                            + LocalDate.now().format(DatePickerPopup.FORMATTER) + "). Vẫn tiếp tục?",
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
                getLanguage(),
                getChannel(),
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
        btnPause.setIcon(IntelliJThemeHelper.createVectorIcon(
                nowPaused ? IntelliJThemeHelper.VectorType.RUN_GREEN : IntelliJThemeHelper.VectorType.PAUSE_AMBER, 16));
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

    private void initDatePicker() {
        btnCalendar.setIcon(IntelliJThemeHelper.createVectorIcon(IntelliJThemeHelper.VectorType.CHEVRON_DOWN, 13));
        btnCalendar.setFocusable(false);
        btnCalendar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCalendar.setToolTipText("Mở bảng chọn ngày tháng (Calendar)");
        btnCalendar.putClientProperty("JButton.buttonType", "borderless");
        btnCalendar.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        txtDate.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, btnCalendar);

        datePickerPopup = new DatePickerPopup(txtDate);
        btnCalendar.addActionListener(e -> datePickerPopup.showPopup());
        txtDate.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    datePickerPopup.showPopup();
                }
            }
        });
    }

    private String getLanguage() {
        Object item = cbLanguage.getEditor().getItem();
        if (item != null && !item.toString().trim().isEmpty()) {
            return item.toString().trim();
        }
        Object sel = cbLanguage.getSelectedItem();
        return sel != null ? sel.toString().trim() : "Vietnamese";
    }

    private void setLanguage(String lang) {
        if (lang != null && !lang.trim().isEmpty()) {
            cbLanguage.setSelectedItem(lang.trim());
            cbLanguage.getEditor().setItem(lang.trim());
        }
    }

    private String getChannel() {
        Object item = cbChannel.getEditor().getItem();
        if (item != null && !item.toString().trim().isEmpty()) {
            return item.toString().trim();
        }
        Object sel = cbChannel.getSelectedItem();
        return sel != null ? sel.toString().trim() : "Web";
    }

    private void setChannel(String channel) {
        if (channel != null && !channel.trim().isEmpty()) {
            cbChannel.setSelectedItem(channel.trim());
            cbChannel.getEditor().setItem(channel.trim());
        }
    }

    private void loadPreferences() {
        txtDocUrls.setText(prefs.get("docUrls", ""));
        chkSaveHtmlToDoc.setSelected(prefs.getBoolean("saveHtmlToDoc", true));
        chkPasteToAppScript.setSelected(prefs.getBoolean("pasteToAppScript", true));
        chkPauseAfterArticle.setSelected(prefs.getBoolean("pauseAfterArticle", true));
        txtFolder.setText(prefs.get("folder", "2025"));
        String savedDate = prefs.get("date", "2025-12-28");
        if (savedDate.contains("/")) {
            try {
                savedDate = LocalDate.parse(savedDate, DateTimeFormatter.ofPattern("dd/MM/yyyy")).format(DatePickerPopup.FORMATTER);
            } catch (Exception ignored) {
                savedDate = "2025-12-28";
            }
        }
        txtDate.setText(savedDate);
        spSyncWait.setValue(prefs.getInt("syncWait", 25));
        setLanguage(prefs.get("language", "Vietnamese"));
        setChannel(prefs.get("channel", "Web"));
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
        prefs.put("language", getLanguage());
        prefs.put("channel", getChannel());
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
            Preferences p = Preferences.userNodeForPackage(MainUI.class);
            boolean isDark = p.getBoolean("appThemeIsDark", true);
            setupTheme(isDark);
            new MainUI().setVisible(true);
        });
    }

    private void updateThemeButtonUI() {
        boolean dark = isDarkTheme();
        btnToggleTheme.setIcon(IntelliJThemeHelper.createVectorIcon(
                dark ? IntelliJThemeHelper.VectorType.THEME_SUN : IntelliJThemeHelper.VectorType.THEME_MOON, 15));
        btnToggleTheme.setText(dark ? "Giao diện sáng" : "Giao diện tối");
        btnToggleTheme.setToolTipText(dark ? "Chuyển sang giao diện IntelliJ Light" : "Chuyển sang giao diện IntelliJ Darcula");
    }

    private boolean isDarkTheme() {
        return prefs.getBoolean("appThemeIsDark", true);
    }

    private void toggleTheme() {
        boolean nextIsDark = !isDarkTheme();
        prefs.putBoolean("appThemeIsDark", nextIsDark);
        setupTheme(nextIsDark);

        // Cập nhật lại màu sắc trình soạn thảo logArea và txtHtmlContent theo theme
        if (nextIsDark) {
            logArea.setBackground(new Color(30, 31, 34));
            logArea.setForeground(new Color(187, 187, 187));
            txtHtmlContent.setBackground(new Color(30, 31, 34));
            txtHtmlContent.setForeground(new Color(220, 220, 220));
        } else {
            logArea.setBackground(new Color(248, 249, 250));
            logArea.setForeground(new Color(33, 37, 41));
            txtHtmlContent.setBackground(new Color(255, 255, 255));
            txtHtmlContent.setForeground(new Color(33, 37, 41));
        }

        SwingUtilities.updateComponentTreeUI(this);
        updateThemeButtonUI();
        onLog("Đã đổi sang " + (nextIsDark ? "Giao diện tối (IntelliJ Darcula)" : "Giao diện sáng (IntelliJ Light)"));
    }

    private static void setupTheme(boolean isDark) {
        try {
            if (isDark) {
                com.formdev.flatlaf.FlatDarculaLaf.setup();
            } else {
                com.formdev.flatlaf.FlatIntelliJLaf.setup();
            }
            UIManager.put("Component.arrowType", "chevron");
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.tabHeight", 30);
            UIManager.put("Button.arc", 6);
            UIManager.put("Component.arc", 6);
            UIManager.put("ProgressBar.arc", 6);
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
    }
}
