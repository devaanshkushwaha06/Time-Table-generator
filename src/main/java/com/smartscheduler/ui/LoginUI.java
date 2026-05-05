package com.smartscheduler.ui;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import com.smartscheduler.model.User;
import com.smartscheduler.service.AuthService;

/**
 * Login + register screen. On a successful authentication, opens
 * {@link TimetableUI} for the authenticated user and disposes itself.
 */
public class LoginUI extends JFrame {

    private static final Color BG = new Color(244, 241, 236);
    private static final Color CARD = new Color(255, 255, 255);
    private static final Color ACCENT = new Color(24, 122, 92);
    private static final Color ACCENT_DARK = new Color(18, 92, 70);
    private static final Color TEXT_DARK = new Color(30, 38, 52);
    private static final Color BORDER = new Color(222, 214, 202);

    private final AuthService auth = new AuthService();

    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel statusLabel = new JLabel(" ");

    public LoginUI() {
        super("Smart Daily Timetable - Sign in");
        buildUi();
        setSize(420, 280);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(20, 24, 20, 24));
        root.setBackground(BG);

        JLabel title = new JLabel("Smart Daily Timetable", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(TEXT_DARK);
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0;
        g.gridy = 0;
        form.add(label("Username:"), g);
        g.gridx = 1;
        form.add(usernameField, g);

        g.gridx = 0;
        g.gridy = 1;
        form.add(label("Password:"), g);
        g.gridx = 1;
        form.add(passwordField, g);

        statusLabel.setForeground(new Color(180, 60, 60));
        g.gridx = 0;
        g.gridy = 2;
        g.gridwidth = 2;
        form.add(statusLabel, g);

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(14, 14, 14, 14)));
        card.add(form, BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);

        styleField(usernameField);
        styleField(passwordField);

        JButton loginBtn = primaryButton("Sign in");
        JButton registerBtn = secondaryButton("Register");

        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> doRegister());

        getRootPane().setDefaultButton(loginBtn);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(registerBtn);
        actions.add(loginBtn);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void doLogin() {
        statusLabel.setText(" ");
        try {
            User u = auth.login(usernameField.getText(),
                    new String(passwordField.getPassword()));
            new MenuUI(u).setVisible(true);
            dispose();
        } catch (AuthService.AuthException ex) {
            statusLabel.setText(ex.getMessage());
        }
    }

    private void doRegister() {
        statusLabel.setText(" ");
        try {
            User u = auth.register(usernameField.getText(),
                    new String(passwordField.getPassword()));
            statusLabel.setForeground(new Color(40, 130, 70));
            statusLabel.setText("Account created. You can sign in now, " + u.getUsername() + ".");
        } catch (AuthService.AuthException ex) {
            statusLabel.setForeground(new Color(180, 60, 60));
            statusLabel.setText(ex.getMessage());
        }
    }

    private static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(24, 102, 173));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setRolloverEnabled(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        installHover(b, new Color(24, 102, 173), new Color(18, 86, 150));
        installPressColor(b, new Color(16, 70, 128));
        return b;
    }

    private static JButton secondaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(24, 102, 173));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setRolloverEnabled(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        installHover(b, new Color(24, 102, 173), new Color(18, 86, 150));
        installPressColor(b, new Color(16, 70, 128));
        return b;
    }

    private static void installHover(JButton button, Color base, Color hover) {
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(base);
            }
        });
    }

    private static void installPressColor(JButton button, Color pressed) {
        button.getModel().addChangeListener(e -> {
            if (button.getModel().isPressed()) {
                button.setBackground(pressed);
            }
        });
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_DARK);
        return l;
    }

    private static void styleField(JTextField field) {
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
    }
}
