package com.automation;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Bảng chọn ngày tháng (Calendar Date Picker Popup) phong cách IntelliJ IDEA.
 * Cho phép xem theo lịch tháng, nhảy tháng/năm, bấm chọn ngày nhanh và định dạng chuẩn yyyy-MM-dd.
 */
public class DatePickerPopup extends JPopupMenu {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JTextField targetField;
    private YearMonth currentMonth;
    private LocalDate selectedDate;

    private final JComboBox<String> cbMonth;
    private final JSpinner spYear;
    private final JPanel daysGridPanel;
    private final JButton[] dayButtons = new JButton[42];

    private boolean updatingControls = false;

    private static final String[] MONTH_NAMES = {
            "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
            "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
    };

    private static final String[] WEEK_DAYS = { "T2", "T3", "T4", "T5", "T6", "T7", "CN" };

    public DatePickerPopup(JTextField targetField) {
        this.targetField = targetField;
        this.selectedDate = parseDate(targetField.getText());
        this.currentMonth = YearMonth.from(selectedDate);

        setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(110, 115, 125, 180), 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JPanel mainPanel = new JPanel(new BorderLayout(0, 6));
        mainPanel.setOpaque(false);

        // 1. Header: Điều hướng Tháng / Năm
        JPanel headerPanel = new JPanel(new BorderLayout(4, 0));
        headerPanel.setOpaque(false);

        JButton btnPrev = new JButton("◀");
        btnPrev.setMargin(new Insets(2, 6, 2, 6));
        btnPrev.setFocusable(false);
        btnPrev.setToolTipText("Tháng trước");
        btnPrev.addActionListener(e -> changeMonth(-1));

        JButton btnNext = new JButton("▶");
        btnNext.setMargin(new Insets(2, 6, 2, 6));
        btnNext.setFocusable(false);
        btnNext.setToolTipText("Tháng sau");
        btnNext.addActionListener(e -> changeMonth(1));

        JPanel navCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        navCenter.setOpaque(false);

        cbMonth = new JComboBox<>(MONTH_NAMES);
        cbMonth.setFocusable(false);
        cbMonth.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cbMonth.addActionListener(e -> {
            if (!updatingControls) {
                int month = cbMonth.getSelectedIndex() + 1;
                currentMonth = YearMonth.of(currentMonth.getYear(), month);
                rebuildCalendarGrid();
            }
        });

        spYear = new JSpinner(new SpinnerNumberModel(currentMonth.getYear(), 1970, 2099, 1));
        spYear.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JSpinner.NumberEditor ne = new JSpinner.NumberEditor(spYear, "#");
        spYear.setEditor(ne);
        spYear.addChangeListener(e -> {
            if (!updatingControls) {
                int year = (Integer) spYear.getValue();
                currentMonth = YearMonth.of(year, currentMonth.getMonthValue());
                rebuildCalendarGrid();
            }
        });

        navCenter.add(cbMonth);
        navCenter.add(spYear);

        headerPanel.add(btnPrev, BorderLayout.WEST);
        headerPanel.add(navCenter, BorderLayout.CENTER);
        headerPanel.add(btnNext, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Calendar: Thứ trong tuần + Lưới ngày
        JPanel calPanel = new JPanel(new BorderLayout(0, 4));
        calPanel.setOpaque(false);

        JPanel weekHeader = new JPanel(new GridLayout(1, 7, 2, 2));
        weekHeader.setOpaque(false);
        for (int i = 0; i < 7; i++) {
            JLabel lbl = new JLabel(WEEK_DAYS[i], SwingConstants.CENTER);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            if (i >= 5) {
                lbl.setForeground(new Color(235, 110, 110)); // Cuối tuần màu nổi hơn
            } else {
                lbl.setForeground(new Color(140, 145, 155));
            }
            weekHeader.add(lbl);
        }
        calPanel.add(weekHeader, BorderLayout.NORTH);

        daysGridPanel = new JPanel(new GridLayout(6, 7, 2, 2));
        daysGridPanel.setOpaque(false);
        for (int i = 0; i < 42; i++) {
            JButton btn = new JButton();
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            btn.setMargin(new Insets(2, 2, 2, 2));
            btn.setPreferredSize(new Dimension(32, 28));
            btn.setFocusable(false);
            dayButtons[i] = btn;
            daysGridPanel.add(btn);
        }
        calPanel.add(daysGridPanel, BorderLayout.CENTER);
        mainPanel.add(calPanel, BorderLayout.CENTER);

        // 3. Footer: Nút chọn "Hôm nay", "Đóng"
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        footerPanel.setOpaque(false);

        JButton btnToday = new JButton("Hôm nay");
        btnToday.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnToday.setMargin(new Insets(2, 8, 2, 8));
        btnToday.addActionListener(e -> selectDate(LocalDate.now()));

        JButton btnClose = new JButton("Đóng");
        btnClose.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClose.setMargin(new Insets(2, 8, 2, 8));
        btnClose.addActionListener(e -> setVisible(false));

        footerPanel.add(btnToday);
        footerPanel.add(btnClose);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    public void showPopup() {
        this.selectedDate = parseDate(targetField.getText());
        this.currentMonth = YearMonth.from(selectedDate);
        syncControlsWithCurrentMonth();
        rebuildCalendarGrid();
        show(targetField, 0, targetField.getHeight() + 2);
    }

    private void changeMonth(int delta) {
        currentMonth = currentMonth.plusMonths(delta);
        syncControlsWithCurrentMonth();
        rebuildCalendarGrid();
    }

    private void syncControlsWithCurrentMonth() {
        updatingControls = true;
        try {
            cbMonth.setSelectedIndex(currentMonth.getMonthValue() - 1);
            spYear.setValue(currentMonth.getYear());
        } finally {
            updatingControls = false;
        }
    }

    private void rebuildCalendarGrid() {
        LocalDate firstOfMonth = currentMonth.atDay(1);
        int dayOfWeekVal = firstOfMonth.getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday
        int startOffset = dayOfWeekVal - 1; // 0 for Monday, 6 for Sunday

        int daysInMonth = currentMonth.lengthOfMonth();
        YearMonth prevMonth = currentMonth.minusMonths(1);
        int daysInPrevMonth = prevMonth.lengthOfMonth();

        LocalDate today = LocalDate.now();

        for (int i = 0; i < 42; i++) {
            JButton btn = dayButtons[i];
            for (var al : btn.getActionListeners()) {
                btn.removeActionListener(al);
            }

            if (i < startOffset) {
                // Ngày thuộc tháng trước
                int dayNum = daysInPrevMonth - (startOffset - 1 - i);
                LocalDate d = prevMonth.atDay(dayNum);
                btn.setText(String.valueOf(dayNum));
                btn.setEnabled(true);
                btn.setForeground(new Color(128, 128, 128, 120));
                btn.setBackground(null);
                btn.addActionListener(e -> selectDate(d));
            } else if (i < startOffset + daysInMonth) {
                // Ngày thuộc tháng hiện tại
                int dayNum = i - startOffset + 1;
                LocalDate d = currentMonth.atDay(dayNum);
                btn.setText(String.valueOf(dayNum));
                btn.setEnabled(true);

                boolean isSelected = d.equals(selectedDate);
                boolean isToday = d.equals(today);

                if (isSelected) {
                    btn.setBackground(new Color(53, 116, 240));
                    btn.setForeground(Color.WHITE);
                    btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
                } else if (isToday) {
                    btn.setBackground(new Color(53, 116, 240, 40));
                    btn.setForeground(new Color(78, 142, 247));
                    btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
                } else {
                    btn.setBackground(null);
                    btn.setForeground(null);
                    btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                }

                btn.addActionListener(e -> selectDate(d));
            } else {
                // Ngày thuộc tháng sau
                int dayNum = i - (startOffset + daysInMonth) + 1;
                LocalDate d = currentMonth.plusMonths(1).atDay(dayNum);
                btn.setText(String.valueOf(dayNum));
                btn.setEnabled(true);
                btn.setForeground(new Color(128, 128, 128, 120));
                btn.setBackground(null);
                btn.addActionListener(e -> selectDate(d));
            }
        }
        daysGridPanel.revalidate();
        daysGridPanel.repaint();
    }

    private void selectDate(LocalDate date) {
        this.selectedDate = date;
        targetField.setText(date.format(FORMATTER));
        setVisible(false);
    }

    public static LocalDate parseDate(String text) {
        if (text != null) {
            String trimmed = text.trim();
            // Thử định dạng yyyy-MM-dd
            try {
                return LocalDate.parse(trimmed, FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
            // Thử định dạng dd/MM/yyyy (tương thích ngược)
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (DateTimeParseException ignored) {
            }
        }
        return LocalDate.now();
    }
}

