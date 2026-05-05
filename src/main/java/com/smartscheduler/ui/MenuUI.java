package com.smartscheduler.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import com.smartscheduler.model.User;

public class MenuUI extends JFrame {

    private static final Color BG = new Color(244, 241, 236);
    private static final Color ACCENT = new Color(24, 102, 173);
    private static final Color ACCENT_DARK = new Color(18, 86, 150);
    private static final Color TEXT_DARK = new Color(30, 38, 52);

    private final User user;

    public MenuUI(User user) {
        super("Smart Daily Timetable - Menu");
        this.user = user;
        buildUi();
        setSize(540, 380);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(16, 20));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(30, 36, 24, 36));

        // --- Header ---
        JPanel header = new JPanel(new GridBagLayout());
        header.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 0, 2, 0);
        g.anchor = GridBagConstraints.CENTER;
        g.gridx = 0;

        JLabel title = new JLabel("Welcome, " + user.getUsername() + "!");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(TEXT_DARK);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        g.gridy = 0;
        header.add(title, g);

        JLabel subtitle = new JLabel("What would you like to do?");
        subtitle.setForeground(new Color(96, 106, 120));
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        g.gridy = 1;
        g.insets = new Insets(4, 0, 12, 0);
        header.add(subtitle, g);

        // --- Main action buttons ---
        JPanel actionsPanel = new JPanel(new GridBagLayout());
        actionsPanel.setOpaque(false);

        GridBagConstraints ac = new GridBagConstraints();
        ac.insets = new Insets(6, 0, 6, 0);
        ac.fill = GridBagConstraints.HORIZONTAL;
        ac.weightx = 1;
        ac.gridx = 0;

        // "Add / Edit Tasks" — big primary action (Goes to Tasks Tab)
        JButton generateBtn = bigActionButton(
                "\u2795  Add / Edit Tasks",
                "Create, edit, delete tasks, track completion status",
                new Color(24, 122, 92), new Color(18, 100, 76));
        generateBtn.addActionListener(e -> {
            new TimetableUI(user, java.time.LocalDate.now(), false, 0).setVisible(true);
            dispose();
        });

        // "Generate / View Timetable" — secondary action (Goes to Timetable Tab)
        JButton editBtn = bigActionButton(
                "\uD83D\uDCC5  Generate / View Timetable",
                "Generate schedule, mark tasks done, export PDF",
                ACCENT, ACCENT_DARK);
        editBtn.addActionListener(e -> {
            new TimetableUI(user, java.time.LocalDate.now(), false, 1).setVisible(true);
            dispose();
        });

        ac.gridy = 0;
        actionsPanel.add(generateBtn, ac);
        ac.gridy = 1;
        actionsPanel.add(editBtn, ac);

        // Logout row
        JPanel logoutPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        logoutPanel.setOpaque(false);
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(160, 55, 55));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setOpaque(true);
        logoutBtn.setContentAreaFilled(true);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        logoutBtn.setBorder(BorderFactory.createEmptyBorder(7, 18, 7, 18));
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> {
            new LoginUI().setVisible(true);
            dispose();
        });
        installHover(logoutBtn, new Color(160, 55, 55), new Color(140, 40, 40));
        logoutPanel.add(logoutBtn);

        // Assemble
        root.add(header, BorderLayout.NORTH);
        root.add(actionsPanel, BorderLayout.CENTER);
        root.add(logoutPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    /**
     * Creates a large, descriptive action button with title and subtitle.
     */
    private static JButton bigActionButton(String title, String subtitle,
                                           Color bg, Color hoverBg) {
        JButton b = new JButton("<html><div style='text-align:left; padding:2px 0'>"
                + "<span style='font-size:13px'>" + title + "</span><br>"
                + "<span style='font-size:10px; color:#E0E0E0'>" + subtitle + "</span>"
                + "</div></html>");
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setRolloverEnabled(false);
        b.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(460, 90));
        installHover(b, bg, hoverBg);
        return b;
    }

    private static void installHover(JButton button, Color base, Color hover) {
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) button.setBackground(hover);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(base);
            }
        });
    }
}
