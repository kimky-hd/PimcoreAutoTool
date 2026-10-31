package com.automation;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý logo và bộ icon đa độ phân giải cho ứng dụng trên Windows và macOS.
 * - Ưu tiên nạp file logo ngoài: "logo.png" hoặc "logo.jpg" đặt cạnh file .jar (khách hàng dễ dàng thay logo riêng).
 * - Nếu không có file ngoài -> nạp logo chuẩn cao cấp đóng gói sẵn trong resources/assets/app_logo.png.
 * - Tự động bo góc mịn (squircle style macOS/iOS) để icon hiển thị chuyên nghiệp nhất.
 */
public final class AppIconHelper {

    private static BufferedImage baseLogoImage;

    private AppIconHelper() {
    }

    /** Lấy ảnh gốc của logo. */
    public static synchronized BufferedImage getBaseLogo() {
        if (baseLogoImage != null) {
            return baseLogoImage;
        }

        // 1. Tìm file logo tuỳ biến ngoài thư mục chạy (cạnh file .jar)
        String[] externalNames = {"logo.png", "logo.jpg", "logo.jpeg", "assets/logo.png", "assets/logo.jpg"};
        for (String name : externalNames) {
            File f = new File(name);
            if (f.exists() && f.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        baseLogoImage = img;
                        return baseLogoImage;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // 2. Tìm logo mặc định trong resources
        String[] resourcePaths = {"/assets/app_logo.png", "/assets/app_logo.jpg"};
        for (String rPath : resourcePaths) {
            try (InputStream is = AppIconHelper.class.getResourceAsStream(rPath)) {
                if (is != null) {
                    BufferedImage img = ImageIO.read(is);
                    if (img != null) {
                        baseLogoImage = img;
                        return baseLogoImage;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Fallback: Tự sinh logo vector gradient hiện đại nếu chưa có ảnh
        baseLogoImage = createFallbackLogo(512);
        return baseLogoImage;
    }

    /** Tạo icon bo góc mềm mại theo kích thước w x h. */
    public static BufferedImage getRoundedIcon(int width, int height, int cornerRadius) {
        BufferedImage src = getBaseLogo();
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Bo góc squircle
            RoundRectangle2D.Float roundRect = new RoundRectangle2D.Float(0, 0, width, height, cornerRadius, cornerRadius);
            g2.setClip(roundRect);

            // Vẽ ảnh gốc scale vừa vặn
            g2.drawImage(src, 0, 0, width, height, null);

            // Viền kim loại nhẹ bên ngoài cho cảm giác sang trọng
            g2.setClip(null);
            g2.setColor(new Color(255, 255, 255, 45));
            g2.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius);
        } finally {
            g2.dispose();
        }
        return output;
    }

    /** Danh sách đa kích thước (16, 24, 32, 48, 64, 128, 256) cho Title bar, Taskbar và Alt+Tab. */
    public static List<Image> getAppIcons() {
        List<Image> icons = new ArrayList<>();
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        for (int s : sizes) {
            int corner = Math.max(4, s / 4);
            icons.add(getRoundedIcon(s, s, corner));
        }
        return icons;
    }

    /** Icon 52x52 đặt ở Header Banner góc trên giao diện chính. */
    public static ImageIcon getHeaderBannerIcon() {
        return new ImageIcon(getRoundedIcon(52, 52, 14));
    }

    /** Áp dụng logo cho JFrame và Dock (trên macOS). */
    public static void applyToFrame(JFrame frame) {
        try {
            List<Image> icons = getAppIcons();
            frame.setIconImages(icons);

            // Hỗ trợ macOS Dock Icon
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(getRoundedIcon(256, 256, 64));
                }
            }
        } catch (Exception ignored) {
            // Không ảnh hưởng tới giao diện nếu môi trường không hỗ trợ Taskbar API
        }
    }

    /** Tạo logo vector dự phòng với dải màu gradient sắc sảo phong cách công nghệ cao. */
    private static BufferedImage createFallbackLogo(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Nền squircle gradient indigo sang cyan
            GradientPaint gp = new GradientPaint(0, 0, new Color(15, 23, 42), size, size, new Color(2, 132, 199));
            g.setPaint(gp);
            int arc = size * 22 / 100;
            g.fillRoundRect(0, 0, size, size, arc, arc);

            // Viền sáng tinh tế
            g.setColor(new Color(255, 255, 255, 50));
            g.drawRoundRect(1, 1, size - 2, size - 2, arc, arc);

            // Biểu tượng chữ "P" cách điệu kết hợp tia chớp tự động hoá
            g.setColor(Color.WHITE);
            int margin = size * 26 / 100;
            int h = size - margin * 2;
            int w = h * 7 / 10;
            int x = (size - w) / 2;
            int y = margin;

            // Cột dọc
            int barW = size * 11 / 100;
            g.fillRoundRect(x, y, barW, h, barW / 2, barW / 2);

            // Vòng tròn đầu chữ P
            int circleD = h * 58 / 100;
            g.fillOval(x + barW / 2, y, circleD, circleD);
            g.setColor(new Color(15, 23, 42));
            int innerD = circleD - barW * 2;
            g.fillOval(x + barW * 3 / 2, y + barW, innerD, innerD);

            // Điểm nhấn tia chớp vàng cyan
            g.setColor(new Color(56, 189, 248));
            int[] px = {x + w, x + w - size / 8, x + w - size / 16, x + w - size / 5};
            int[] py = {y + circleD, y + circleD + size / 8, y + circleD + size / 8, y + h};
            g.fillPolygon(px, py, 4);

        } finally {
            g.dispose();
        }
        return img;
    }
}

