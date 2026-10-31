package com.automation;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Giao diện chính của tool (Java Swing).
 */
public class MainUI extends JFrame implements FlowListener {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG_CHARS = 400_000;

    // ---- Cấu hình ----
    private final JTextField txtSheetUrl = new JTextField(45);
    private final JTextField txtFolder = new JTextField("2025", 10);
    private final JTextField txtColumn = new JTextField("B", 4);
    private final JSpinner spStartRow = new JSpinner(new SpinnerNumberModel(2, 1, 1_000_000, 1));
    private final JTextField txtDate = new JTextField("28/12/2025", 10);
    private final JSpinner spSyncWait = new JSpinner(new SpinnerNumberModel(25, 1, 3600, 1));
    private final JSpinner spCount = new JSpinner(new SpinnerNumberModel(1, 1, 100_000, 1));
    private final JTextField txtLanguage = new JTextField("Vietnamese", 10);
    private final JTextField txtChannel = new JTextField("Web", 10);
    private final JComboBox<PasteMode> cbPasteMode = new JComboBox<>(PasteMode.values());

    // ---- Nút điều khiển ----
    private final JButton btnChrome = new JButton("1. Mở Chrome Debug");
    private final JButton btnStart = new JButton("2. Bắt đầu chạy (Start)");
    private final JButton btnPause = new JButton("Tạm dừng");
    private final JButton btnStop = new JButton("Dừng khẩn cấp (Stop)");
    private final JButton btnOpenSelectors = new JButton("Mở selectors.properties");
    private final JButton btnReloadSelectors = new JButton("Nạp lại selectors");
    private final JButton btnClearLog = new JButton("Xoá log");

    // ---- Log & trạng thái ----
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
        super("Pimcore Auto Tool - RPA 27 bước (Selenium 4 + Chrome Debug 9222)");
        this.selectors = new Selectors();
        this.logFile = Paths.get(System.getProperty("user.dir"), "logs",
                "automation_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".log");
        loadPreferences();
        buildLayout();
        bindActions();
        setRunningState(false);
        updateSelectorSource();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        setMinimumSize(new Dimension(980, 720));
        setSize(1100, 800);
        setLocationRelativeTo(null);

        onLog("Tool sẵn sàng. Quy trình: (1) Mở Chrome Debug -> đăng nhập & mở sẵn các tab Google Sheet, Pimcore, "
                + "Web App Script -> (2) Bắt đầu chạy.");
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
        cfg.setBorder(BorderFactory.createTitledBorder("Cấu hình"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addLabel(cfg, c, 0, row, "Đường dẫn URL Google Sheet:");
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 5;
        c.weightx = 1;
        cfg.add(txtSheetUrl, c);
        c.gridwidth = 1;
        c.weightx = 0;

        row++;
        addLabel(cfg, c, 0, row, "Thư mục Pimcore:");
        addField(cfg, c, 1, row, txtFolder);
        addLabel(cfg, c, 2, row, "Cột chứa link Doc:");
        addField(cfg, c, 3, row, txtColumn);
        addLabel(cfg, c, 4, row, "Dòng bắt đầu:");
        addField(cfg, c, 5, row, spStartRow);

        row++;
        addLabel(cfg, c, 0, row, "Thời gian hiển thị:");
        addField(cfg, c, 1, row, txtDate);
        addLabel(cfg, c, 2, row, "Thời gian chờ Sync (giây):");
        addField(cfg, c, 3, row, spSyncWait);
        addLabel(cfg, c, 4, row, "Số lượng bài chạy lặp:");
        addField(cfg, c, 5, row, spCount);

        row++;
        addLabel(cfg, c, 0, row, "Ngôn ngữ hiển thị:");
        addField(cfg, c, 1, row, txtLanguage);
        addLabel(cfg, c, 2, row, "Access Channel:");
        addField(cfg, c, 3, row, txtChannel);
        addLabel(cfg, c, 4, row, "Phương thức nhập:");
        addField(cfg, c, 5, row, cbPasteMode);

        row++;
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
        btnStart.setFont(bold);
        btnStop.setFont(bold);
        btnStart.setForeground(new Color(0, 110, 0));
        btnStop.setForeground(new Color(180, 0, 0));
        buttons.add(btnChrome);
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

        // ---- Log ----
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(250, 250, 247));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Log chi tiết (thời gian thực)"));
        root.add(scroll, BorderLayout.CENTER);

        // ---- Thanh trạng thái ----
        JPanel south = new JPanel(new BorderLayout(8, 4));
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(320, 22));
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
        panel.add(new JLabel(text), c);
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
        btnStart.addActionListener(e -> startFlow());
        btnPause.addActionListener(e -> togglePause());
        btnStop.addActionListener(e -> stopFlow());
        btnOpenSelectors.addActionListener(e -> openSelectorsFile());
        btnReloadSelectors.addActionListener(e -> reloadSelectors());
        btnClearLog.addActionListener(e -> logArea.setText(""));
    }

    private void setRunningState(boolean running) {
        btnStart.setEnabled(!running);
        btnPause.setEnabled(running);
        btnStop.setEnabled(running);
        btnReloadSelectors.setEnabled(!running);
        txtSheetUrl.setEnabled(!running);
        txtFolder.setEnabled(!running);
        txtColumn.setEnabled(!running);
        spStartRow.setEnabled(!running);
        txtDate.setEnabled(!running);
        spSyncWait.setEnabled(!running);
        spCount.setEnabled(!running);
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
                String sheet = txtSheetUrl.getText().trim();
                if (!sheet.isEmpty()) {
                    urls.add(sheet);
                }
                ChromeLauncher.launch(chromePath, urls);
                for (int i = 0; i < 40; i++) {
                    Thread.sleep(500);
                    if (ChromeLauncher.isDebugPortOpen()) {
                        onLog("Chrome Debug đã sẵn sàng tại " + ChromeLauncher.DEBUGGER_ADDRESS
                                + ". Hãy đăng nhập và mở các tab: Google Sheet, Pimcore, Web App Script.");
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

    /** Nút "2. Bắt đầu chạy (Start)". */
    private void startFlow() {
        if (flowThread != null && flowThread.isAlive()) {
            return;
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
                txtSheetUrl.getText().trim(),
                folder,
                txtColumn.getText().trim(),
                (Integer) spStartRow.getValue(),
                date,
                (Integer) spSyncWait.getValue(),
                (Integer) spCount.getValue(),
                txtLanguage.getText().trim(),
                txtChannel.getText().trim(),
                (PasteMode) cbPasteMode.getSelectedItem());
        savePreferences();

        int totalSteps = config.articleCount() * AutomationFlow.TOTAL_STEPS;
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
                : "[TIẾP TỤC] Tool chạy tiếp.");
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
        SwingUtilities.invokeLater(() -> lblStatus.setText(status));
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
        for (JSpinner sp : new JSpinner[]{spStartRow, spSyncWait, spCount}) {
            try {
                sp.commitEdit();
            } catch (ParseException ignored) {
                // giữ giá trị hợp lệ gần nhất
            }
        }
    }

    private void loadPreferences() {
        txtSheetUrl.setText(prefs.get("sheetUrl", ""));
        txtFolder.setText(prefs.get("folder", "2025"));
        txtColumn.setText(prefs.get("column", "B"));
        spStartRow.setValue(prefs.getInt("startRow", 2));
        txtDate.setText(prefs.get("date", "28/12/2025"));
        spSyncWait.setValue(prefs.getInt("syncWait", 25));
        spCount.setValue(prefs.getInt("count", 1));
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
        prefs.put("sheetUrl", txtSheetUrl.getText().trim());
        prefs.put("folder", txtFolder.getText().trim());
        prefs.put("column", txtColumn.getText().trim());
        prefs.putInt("startRow", (Integer) spStartRow.getValue());
        prefs.put("date", txtDate.getText().trim());
        prefs.putInt("syncWait", (Integer) spSyncWait.getValue());
        prefs.putInt("count", (Integer) spCount.getValue());
        prefs.put("language", txtLanguage.getText().trim());
        prefs.put("channel", txtChannel.getText().trim());
        PasteMode mode = (PasteMode) cbPasteMode.getSelectedItem();
        prefs.put("pasteMode", mode == null ? PasteMode.CLIPBOARD_THEN_SENDKEYS.name() : mode.name());
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

