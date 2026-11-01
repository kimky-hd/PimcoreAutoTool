package com.automation;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.InputStream;

/**
 * Trợ thủ vẽ vector icon và nạp font JetBrains Mono chuẩn giao diện IntelliJ IDEA.
 */
public final class IntelliJThemeHelper {

    public static Font JETBRAINS_MONO_PLAIN;
    public static Font JETBRAINS_MONO_BOLD;

    static {
        loadFonts();
    }

    private IntelliJThemeHelper() {
    }

    private static void loadFonts() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            InputStream isRegular = IntelliJThemeHelper.class.getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf");
            if (isRegular != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, isRegular);
                ge.registerFont(f);
                JETBRAINS_MONO_PLAIN = f.deriveFont(12.5f);
            }
            InputStream isBold = IntelliJThemeHelper.class.getResourceAsStream("/fonts/JetBrainsMono-Bold.ttf");
            if (isBold != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, isBold);
                ge.registerFont(f);
                JETBRAINS_MONO_BOLD = f.deriveFont(12.5f);
            }
        } catch (Exception ignored) {
        }
        if (JETBRAINS_MONO_PLAIN == null) {
            JETBRAINS_MONO_PLAIN = new Font("Consolas", Font.PLAIN, 13);
        }
        if (JETBRAINS_MONO_BOLD == null) {
            JETBRAINS_MONO_BOLD = new Font("Consolas", Font.BOLD, 13);
        }
    }

    public enum VectorType {
        RUN_GREEN,      // Tam giác xanh Run (IntelliJ)
        CHROME_BLUE,    // Vòng tròn/bánh xe Chrome xanh
        STOP_RED,       // Hình vuông đỏ Stop
        PAUSE_AMBER,    // Hai gạch dọc Pause
        TEST_LAB,       // Bình thí nghiệm Test
        SETTINGS_FILE,  // Biểu tượng file properties
        RELOAD_ARROW,   // Mũi tên xoay vòng
        CLEAR_BIN,      // Thùng rác / chổi quét log
        COPY_CLIPBOARD, // Hai trang giấy chồng nhau
        FOLDER_OPEN,    // Thư mục mở
        THEME_SUN,      // Mặt trời (Theme sáng)
        THEME_MOON,     // Mặt trăng (Theme tối)
        CHEVRON_DOWN,   // Mũi tên trỏ xuống dropdown
        CALENDAR        // Biểu tượng lịch chọn ngày
    }

    public static Icon createVectorIcon(VectorType type, int size) {
        return new VectorIcon(type, size);
    }

    private static class VectorIcon implements Icon {
        private final VectorType type;
        private final int size;

        public VectorIcon(VectorType type, int size) {
            this.type = type;
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int s = size;
            float pad = s * 0.15f;
            float w = s - pad * 2;
            float h = s - pad * 2;

            switch (type) {
                case RUN_GREEN -> {
                    // Tam giác xanh lá tươi IntelliJ (#59A869 / #369B4B)
                    g2.setColor(new Color(89, 168, 105));
                    int[] px = new int[]{Math.round(x + pad + 2), Math.round(x + s - pad), Math.round(x + pad + 2)};
                    int[] py = new int[]{Math.round(y + pad), Math.round(y + s / 2f), Math.round(y + s - pad)};
                    g2.fillPolygon(px, py, 3);
                }
                case CHROME_BLUE -> {
                    // Biểu tượng tròn công nghệ Chrome / Globe xanh (#3592C4)
                    g2.setColor(new Color(53, 146, 196));
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.drawOval(Math.round(x + pad), Math.round(y + pad), Math.round(w), Math.round(h));
                    g2.fillOval(Math.round(x + s / 2f - 2), Math.round(y + s / 2f - 2), 4, 4);
                    g2.drawLine(Math.round(x + pad + 1), Math.round(y + s / 2f), Math.round(x + s - pad - 1), Math.round(y + s / 2f));
                }
                case STOP_RED -> {
                    // Khối vuông đỏ Stop (#DB5860)
                    g2.setColor(new Color(219, 88, 96));
                    int boxSize = Math.round(s * 0.58f);
                    int offset = (s - boxSize) / 2;
                    g2.fillRoundRect(x + offset, y + offset, boxSize, boxSize, 3, 3);
                }
                case PAUSE_AMBER -> {
                    // Hai vạch pause vàng hổ phách (#E5A548)
                    g2.setColor(new Color(229, 165, 72));
                    int barW = Math.max(2, Math.round(s * 0.16f));
                    int barH = Math.round(s * 0.62f);
                    int topY = Math.round(y + (s - barH) / 2f);
                    int leftX = Math.round(x + s * 0.28f);
                    int rightX = Math.round(x + s * 0.58f);
                    g2.fillRoundRect(leftX, topY, barW, barH, 2, 2);
                    g2.fillRoundRect(rightX, topY, barW, barH, 2, 2);
                }
                case TEST_LAB -> {
                    // Bình thí nghiệm beaker (#AFB1B3 / #9AA7B0)
                    g2.setColor(new Color(154, 167, 176));
                    g2.setStroke(new BasicStroke(1.5f));
                    int mx = Math.round(x + s / 2f);
                    int top = Math.round(y + pad);
                    int bot = Math.round(y + s - pad);
                    g2.drawLine(mx - 3, top, mx + 3, top); // miệng bình
                    g2.drawLine(mx - 2, top, mx - 2, top + 4); // cổ trái
                    g2.drawLine(mx + 2, top, mx + 2, top + 4); // cổ phải
                    g2.drawLine(mx - 2, top + 4, Math.round(x + pad), bot); // đáy trái
                    g2.drawLine(mx + 2, top + 4, Math.round(x + s - pad), bot); // đáy phải
                    g2.drawLine(Math.round(x + pad), bot, Math.round(x + s - pad), bot); // đáy ngang
                }
                case SETTINGS_FILE -> {
                    // Trang tài liệu cấu hình
                    g2.setColor(new Color(175, 177, 179));
                    g2.setStroke(new BasicStroke(1.4f));
                    int rx = Math.round(x + pad + 1);
                    int ry = Math.round(y + pad);
                    int rw = Math.round(w - 2);
                    int rh = Math.round(h);
                    g2.drawRoundRect(rx, ry, rw, rh, 2, 2);
                    g2.drawLine(rx + 3, ry + 4, rx + rw - 3, ry + 4);
                    g2.drawLine(rx + 3, ry + 7, rx + rw - 3, ry + 7);
                    g2.drawLine(rx + 3, ry + 10, rx + rw - 5, ry + 10);
                }
                case RELOAD_ARROW -> {
                    // Vòng tròn mũi tên refresh
                    g2.setColor(new Color(175, 177, 179));
                    g2.setStroke(new BasicStroke(1.6f));
                    int arcSize = Math.round(w);
                    g2.drawArc(Math.round(x + pad), Math.round(y + pad), arcSize, arcSize, 45, 270);
                    // Mũi tên nhỏ ở góc
                    int ax = Math.round(x + pad + arcSize * 0.85f);
                    int ay = Math.round(y + pad + arcSize * 0.2f);
                    g2.drawLine(ax - 3, ay, ax, ay);
                    g2.drawLine(ax, ay - 3, ax, ay);
                }
                case CLEAR_BIN -> {
                    // Nét chổi hoặc biểu tượng dọn sạch
                    g2.setColor(new Color(175, 177, 179));
                    g2.setStroke(new BasicStroke(1.6f));
                    g2.drawLine(Math.round(x + pad + 2), Math.round(y + pad + 2), Math.round(x + s - pad - 2), Math.round(y + s - pad - 2));
                    g2.drawLine(Math.round(x + s - pad - 2), Math.round(y + pad + 2), Math.round(x + pad + 2), Math.round(y + s - pad - 2));
                }
                case COPY_CLIPBOARD -> {
                    // Hai hình tài liệu xếp chồng
                    g2.setColor(new Color(89, 168, 105));
                    g2.setStroke(new BasicStroke(1.3f));
                    g2.drawRoundRect(Math.round(x + pad), Math.round(y + pad), Math.round(w * 0.7f), Math.round(h * 0.75f), 2, 2);
                    g2.setColor(new Color(187, 187, 187));
                    g2.drawRoundRect(Math.round(x + pad + w * 0.25f), Math.round(y + pad + h * 0.25f), Math.round(w * 0.7f), Math.round(h * 0.75f), 2, 2);
                }
                case FOLDER_OPEN -> {
                    // Thư mục mở
                    g2.setColor(new Color(229, 165, 72));
                    g2.setStroke(new BasicStroke(1.4f));
                    int fx = Math.round(x + pad);
                    int fy = Math.round(y + pad + 2);
                    int fw = Math.round(w);
                    int fh = Math.round(h - 3);
                    g2.drawRoundRect(fx, fy, fw, fh, 2, 2);
                    g2.drawLine(fx, fy + 3, fx + fw, fy + 3);
                }
                case THEME_SUN -> {
                    // Biểu tượng mặt trời (chuyển sang theme sáng)
                    g2.setColor(new Color(235, 175, 50));
                    g2.setStroke(new BasicStroke(1.5f));
                    int cx = Math.round(x + s / 2f);
                    int cy = Math.round(y + s / 2f);
                    int r = Math.round(s * 0.22f);
                    g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                    // Các tia nắng
                    int rayLen = Math.round(s * 0.38f);
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4.0;
                        int x1 = cx + (int) Math.round((r + 2) * Math.cos(angle));
                        int y1 = cy + (int) Math.round((r + 2) * Math.sin(angle));
                        int x2 = cx + (int) Math.round(rayLen * Math.cos(angle));
                        int y2 = cy + (int) Math.round(rayLen * Math.sin(angle));
                        g2.drawLine(x1, y1, x2, y2);
                    }
                }
                case THEME_MOON -> {
                    // Biểu tượng mặt trăng khuyết (chuyển sang theme tối)
                    g2.setColor(new Color(180, 195, 220));
                    g2.setStroke(new BasicStroke(1.5f));
                    int mx = Math.round(x + pad + 1);
                    int my = Math.round(y + pad + 1);
                    int mw = Math.round(w - 2);
                    g2.drawArc(mx, my, mw, mw, 80, 240);
                    g2.drawArc(mx + Math.round(mw * 0.25f), my + Math.round(mw * 0.1f), Math.round(mw * 0.7f), Math.round(mw * 0.8f), 80, 240);
                }
                case CHEVRON_DOWN -> {
                    // Mũi tên trỏ xuống dropdown
                    g2.setColor(new Color(150, 155, 160));
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = Math.round(x + s / 2f);
                    int cy = Math.round(y + s / 2f);
                    g2.drawLine(cx - 3, cy - 2, cx, cy + 2);
                    g2.drawLine(cx, cy + 2, cx + 3, cy - 2);
                }
                case CALENDAR -> {
                    // Biểu tượng lịch nhỏ
                    g2.setColor(new Color(130, 160, 210));
                    g2.setStroke(new BasicStroke(1.3f));
                    int rx = Math.round(x + pad);
                    int ry = Math.round(y + pad + 1);
                    int rw = Math.round(w);
                    int rh = Math.round(h - 2);
                    g2.drawRoundRect(rx, ry, rw, rh, 2, 2);
                    g2.drawLine(rx, ry + 4, rx + rw, ry + 4);
                    // Ghim treo lịch
                    g2.drawLine(rx + 3, ry - 1, rx + 3, ry + 1);
                    g2.drawLine(rx + rw - 3, ry - 1, rx + rw - 3, ry + 1);
                }
            }
            g2.dispose();
        }
    }
}

