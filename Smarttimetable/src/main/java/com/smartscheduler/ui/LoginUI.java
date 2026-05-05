package com.smartscheduler.ui;

import com.smartscheduler.model.User;
import com.smartscheduler.service.AuthService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Login + register screen. On a successful authentication, opens
 * {@link TimetableUI} for the authenticated user and disposes itself.
 */
public class LoginUI extends JFrame {

    private final AuthService auth = new AuthService();

    private final JTextField     usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel         statusLabel   = new JLabel(" ");

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
        root.setBackground(new Color(245, 247, 251));

        JLabel title = new JLabel("Smart Daily Timetable", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(new Color(31, 64, 104));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 0; form.add(new JLabel("Username:"), g);
        g.gridx = 1; form.add(usernameField, g);

        g.gridx = 0; g.gridy = 1; form.add(new JLabel("Password:"), g);
        g.gridx = 1; form.add(passwordField, g);

        statusLabel.setForeground(new Color(180, 60, 60));
        g.gridx = 0; g.gridy = 2; g.gridwidth = 2;
        form.add(statusLabel, g);

        root.add(form, BorderLayout.CENTER);

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
            new TimetableUI(u).setVisible(true);
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
        b.setBackground(new Color(31, 99, 199));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        return b;
    }

    private static JButton secondaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(Color.WHITE);
        b.setFocusPainted(false);
        return b;
    }
}
