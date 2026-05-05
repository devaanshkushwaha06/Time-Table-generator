package com.smartscheduler;

import com.smartscheduler.ui.LoginUI;

import javax.swing.*;

/**
 * Application entry point. Boots the Swing event loop and shows the login
 * screen. The login screen, on success, opens the main TimetableUI.
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* fall back to default L&F */ }
        SwingUtilities.invokeLater(() -> new LoginUI().setVisible(true));
    }
}
