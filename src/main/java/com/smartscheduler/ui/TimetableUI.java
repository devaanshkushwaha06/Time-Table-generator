package com.smartscheduler.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.smartscheduler.db.DatabaseConnection;
import com.smartscheduler.model.Priority;
import com.smartscheduler.model.Task;
import com.smartscheduler.model.TaskStatus;
import com.smartscheduler.model.TaskType;
import com.smartscheduler.model.Timetable;
import com.smartscheduler.model.User;
import com.smartscheduler.service.ScheduleResult;
import com.smartscheduler.service.SchedulerService;
import com.smartscheduler.service.TaskService;
import com.smartscheduler.util.PdfExporter;

/**
 * Main application window. Two tabs: - Tasks : CRUD on tasks + filters +
 * completion bar. - Timetable: generated schedule (JTable), regenerate / save /
 * export.
 */
public class TimetableUI extends JFrame {

    private static final Color BG = new Color(244, 241, 236);
    private static final Color CARD = new Color(255, 255, 255);
    private static final Color ACCENT = new Color(18, 74, 112);
    private static final Color ACCENT_DARK = new Color(12, 54, 84);
    private static final Color TEXT_DARK = new Color(30, 38, 52);
    private static final Color BORDER = new Color(222, 214, 202);
    private static final Color MUTED = new Color(120, 130, 145);

    private static final int TASK_COL_DONE = 0;
    private static final int TASK_COL_ID = 1;
    private static final int TASK_COL_STATUS = 7;

    private static final int TT_COL_DONE = 0;
    private static final int TT_COL_SLOT = 1;
    private static final int TT_COL_TASK = 2;
    private static final int TT_COL_PRIORITY = 3;
    private static final int TT_COL_STATUS = 4;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_FMT_12 = DateTimeFormatter.ofPattern("hh:mm a");

    private final User user;
    private final TaskService tasks = new TaskService();
    private final SchedulerService sched = new SchedulerService();
    private boolean use24HourFormat;
    private final JTextField scheduleDate;
    private final JComboBox<String> timeFormatChoice;
    private final JLabel rescheduledLabel = new JLabel();
    private final JButton viewTomorrowBtn = new JButton("View Tomorrow's Timetable");

    // Tasks tab
    private final DefaultTableModel taskModel = new DefaultTableModel(
            new String[] { "Done", "ID", "Title", "Type", "Priority", "Deadline", "Duration", "Status" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return c == TASK_COL_DONE;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == TASK_COL_DONE ? Boolean.class : Object.class;
        }
    };
    private final JTable taskTable = new JTable(taskModel);
    private final JComboBox<String> priorityFilter = new JComboBox<>(
            new String[] { "All", "HIGH", "MEDIUM", "LOW" });
    private final JComboBox<String> typeFilter = new JComboBox<>(
            new String[] { "All", "STUDY", "WORK", "PERSONAL", "CUSTOM" });
    private final JTextField dateFilter = new JTextField(10);
    private final JProgressBar completionBar = new JProgressBar(0, 100);

    // Timetable tab
    private final DefaultTableModel ttModel = new DefaultTableModel(
            new String[] { "Done", "Time Slot", "Task", "Priority", "Status" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            if (c != TT_COL_DONE) {
                return false;
            }
            String status = String.valueOf(getValueAt(r, TT_COL_STATUS));
            return !"BREAK".equals(status);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == TT_COL_DONE ? Boolean.class : Object.class;
        }
    };
    private final JTable ttTable = new JTable(ttModel);
    private final JTextField availStart = new JTextField(
            DatabaseConnection.prop("availability.start", "16:00"), 10);
    private final JTextField availEnd = new JTextField(
            DatabaseConnection.prop("availability.end", "22:00"), 10);

    private final int reminderMinutes = Integer.parseInt(
            DatabaseConnection.prop("reminder.minutes", "10"));
    private final Set<String> reminded = new HashSet<>();
    private List<Timetable> currentEntries = List.of();
    private boolean updatingTaskModel;
    private boolean updatingTtModel;
    private Timer reminderTimer;
    private JTabbedPane tabs;

    public TimetableUI(User user) {
        this(user, LocalDate.now(), false, 1);
    }

    public TimetableUI(User user, LocalDate initialDate, boolean use24HourFormat, int selectedTabIndex) {
        super("Smart Daily Timetable - " + user.getUsername());
        this.user = user;
        this.use24HourFormat = use24HourFormat;
        this.scheduleDate = new JTextField(initialDate.toString(), 10);
        this.timeFormatChoice = new JComboBox<>(new String[] { "12 Hour", "24 Hour" });
        this.timeFormatChoice.setSelectedIndex(use24HourFormat ? 1 : 0);
        this.timeFormatChoice.addActionListener(e -> applyTimeFormat(timeFormatChoice.getSelectedIndex() == 1));

        // If starting in 12-hour mode, convert the default 24h values in the time
        // fields
        if (!use24HourFormat) {
            convertTimeField(availStart, true, false);
            convertTimeField(availEnd, true, false);
        }

        this.tabs = new JTabbedPane();
        tabs.addTab("Tasks", buildTasksPanel());
        tabs.addTab("Timetable", buildTimetablePanel());
        tabs.setBackground(BG);
        tabs.setForeground(TEXT_DARK);
        tabs.setSelectedIndex(Math.max(0, Math.min(selectedTabIndex, tabs.getTabCount() - 1)));
        setContentPane(tabs);

        setSize(960, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        ttTable.setDefaultRenderer(Object.class, new TimetableRowRenderer());
        taskTable.setDefaultRenderer(Object.class, new TaskRowRenderer());
        styleTable(taskTable);
        styleTable(ttTable);
        installTableListeners();
        startReminderTimer();

        refreshTasks();
        refreshTimetable();
    }

    private void applyTimeFormat(boolean use24Hour) {
        if (this.use24HourFormat == use24Hour) {
            return;
        }
        boolean wasUsing24 = this.use24HourFormat;
        this.use24HourFormat = use24Hour;
        // Convert the From/To time input fields between formats
        convertTimeField(availStart, wasUsing24, use24Hour);
        convertTimeField(availEnd, wasUsing24, use24Hour);
        refreshTasks();
        // Re-render current entries in-place (no DB reload) to preserve breaks
        if (!currentEntries.isEmpty()) {
            renderTimetable(currentEntries);
        }
    }

    /**
     * Converts a time text field between 24-hour and 12-hour format.
     */
    private void convertTimeField(JTextField field, boolean was24, boolean now24) {
        try {
            if (was24 == now24)
                return;
            LocalTime t = parseTimeField(field);
            if (now24) {
                field.setText(TIME_FMT.format(t));
            } else {
                field.setText(TIME_FMT_12.format(t));
            }
        } catch (DateTimeParseException ex) {
            // If parsing fails, leave the field as-is
        }
    }

    /**
     * Parses the time from a text field, handling both 12h and 24h formats
     * robustly.
     */
    private LocalTime parseTimeField(JTextField field) {
        String text = field.getText().trim().toUpperCase(java.util.Locale.US);
        try {
            // Try 12-hour format first (e.g., "10:00 PM" or "1:00 AM")
            return LocalTime.parse(text, java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US));
        } catch (DateTimeParseException e1) {
            // If that fails, try 24-hour format (e.g., "22:00" or "09:00")
            return LocalTime.parse(text, java.time.format.DateTimeFormatter.ofPattern("H:mm"));
        }
    }

    // ------------------------------------------------------------------
    // Tasks tab
    // ------------------------------------------------------------------
    private JComponent buildTasksPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setBackground(BG);

        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filter.setBackground(BG);
        filter.add(label("Priority:"));
        filter.add(priorityFilter);
        filter.add(label("Type:"));
        filter.add(typeFilter);
        filter.add(label("Date (YYYY-MM-DD):"));
        filter.add(dateFilter);

        JButton apply = new JButton("Apply Filters");
        styleSecondaryButton(apply);
        apply.addActionListener(e -> refreshTasks());
        filter.add(apply);

        JButton clear = new JButton("Clear");
        styleSecondaryButton(clear);
        clear.addActionListener(e -> {
            priorityFilter.setSelectedIndex(0);
            typeFilter.setSelectedIndex(0);
            dateFilter.setText("");
            refreshTasks();
        });
        filter.add(clear);

        panel.add(filter, BorderLayout.NORTH);

        JScrollPane taskScroll = new JScrollPane(taskTable);
        taskScroll.getViewport().setBackground(CARD);
        panel.add(taskScroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setBackground(BG);

        // Task action buttons row
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setBackground(BG);
        JButton addBtn = new JButton("Add Task");
        JButton editBtn = new JButton("Edit Task");
        JButton delBtn = new JButton("Delete");
        stylePrimaryButton(addBtn);
        styleSecondaryButton(editBtn);
        styleSecondaryButton(delBtn);
        addBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 13f));
        editBtn.setFont(editBtn.getFont().deriveFont(Font.BOLD, 13f));
        delBtn.setFont(delBtn.getFont().deriveFont(Font.BOLD, 13f));
        addBtn.addActionListener(e -> openTaskDialog(null));
        editBtn.addActionListener(e -> {
            Task t = selectedTask();
            if (t != null) {
                openTaskDialog(t);
            }
        });
        delBtn.addActionListener(e -> {
            Task t = selectedTask();
            if (t == null) {
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this,
                    "Delete task '" + t.getTitle() + "' ?", "Confirm",
                    JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                try {
                    tasks.delete(user.getUserId(), t.getTaskId());
                    refreshTasks();
                } catch (SQLException ex) {
                    showError(ex);
                }
            }
        });

        JButton backBtn = new JButton("Back to Menu");
        styleSecondaryButton(backBtn);
        backBtn.setFont(backBtn.getFont().deriveFont(Font.BOLD, 13f));
        backBtn.addActionListener(e -> {
            new MenuUI(user).setVisible(true);
            dispose();
        });

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(160, 55, 55));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setOpaque(true);
        logoutBtn.setContentAreaFilled(true);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFont(logoutBtn.getFont().deriveFont(Font.BOLD, 13f));
        logoutBtn.addActionListener(e -> {
            new LoginUI().setVisible(true);
            dispose();
        });

        actions.add(addBtn);
        actions.add(editBtn);
        actions.add(delBtn);
        actions.add(backBtn);
        actions.add(logoutBtn);

        // Big "View Timetable" button — prominent, spans full width
        JButton viewTimetableBtn = new JButton("\uD83D\uDCC5  View Timetable");
        viewTimetableBtn.setBackground(new Color(24, 102, 173));
        viewTimetableBtn.setForeground(Color.WHITE);
        viewTimetableBtn.setFocusPainted(false);
        viewTimetableBtn.setOpaque(true);
        viewTimetableBtn.setContentAreaFilled(true);
        viewTimetableBtn.setBorderPainted(false);
        viewTimetableBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        viewTimetableBtn.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        viewTimetableBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        viewTimetableBtn.addActionListener(e -> {
            if (tabs != null) {
                tabs.setSelectedIndex(1);
            }
        });
        installHover(viewTimetableBtn, new Color(24, 102, 173), new Color(18, 86, 150));

        // Progress bar
        JPanel progress = new JPanel(new BorderLayout(6, 0));
        progress.setBackground(BG);
        JLabel completionLabel = new JLabel("Completion:");
        completionLabel.setForeground(TEXT_DARK);
        completionLabel.setFont(completionLabel.getFont().deriveFont(Font.BOLD, 13f));
        progress.add(completionLabel, BorderLayout.WEST);
        completionBar.setStringPainted(true);
        completionBar.setFont(completionBar.getFont().deriveFont(Font.BOLD, 12f));
        completionBar.setForeground(new Color(73, 148, 113));
        completionBar.setBackground(new Color(234, 229, 221));
        progress.add(completionBar, BorderLayout.CENTER);

        // Stack: actions row, then view timetable button, then progress
        JPanel southInner = new JPanel(new BorderLayout(6, 6));
        southInner.setBackground(BG);
        southInner.add(actions, BorderLayout.NORTH);
        southInner.add(viewTimetableBtn, BorderLayout.CENTER);

        south.add(southInner, BorderLayout.NORTH);
        south.add(progress, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private Task selectedTask() {
        int row = taskTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a task first.");
            return null;
        }
        int taskId = (int) taskModel.getValueAt(row, TASK_COL_ID);
        try {
            return tasks.findAll(user.getUserId()).stream()
                    .filter(t -> t.getTaskId() == taskId)
                    .findFirst().orElse(null);
        } catch (SQLException e) {
            showError(e);
            return null;
        }
    }

    private void refreshTasks() {
        try {
            Priority p = priorityFilter.getSelectedIndex() == 0 ? null
                    : Priority.valueOf((String) priorityFilter.getSelectedItem());
            TaskType ty = typeFilter.getSelectedIndex() == 0 ? null
                    : TaskType.valueOf((String) typeFilter.getSelectedItem());
            LocalDate onDate = null;
            if (!dateFilter.getText().trim().isEmpty()) {
                try {
                    onDate = LocalDate.parse(dateFilter.getText().trim());
                } catch (DateTimeParseException ex) {
                    /* ignore bad input */ }
            }

            List<Task> all = tasks.query(user.getUserId(), p, ty, onDate);
            updatingTaskModel = true;
            taskModel.setRowCount(0);
            for (Task t : all) {
                int totalMin = t.getDurationMinutes();
                String durationStr = (totalMin >= 60)
                        ? (totalMin / 60) + "h " + (totalMin % 60) + "m"
                        : totalMin + "m";
                taskModel.addRow(new Object[] {
                        t.getStatus() == TaskStatus.COMPLETED,
                        t.getTaskId(), t.getTitle(), t.getTaskType(),
                        t.getPriority(), formatDateTime(t.getDeadline()),
                        durationStr, t.getStatus() });
            }
            completionBar.setValue((int) Math.round(tasks.completionPercent(user.getUserId())));
            completionBar.setString(completionBar.getValue() + "% complete");
            updatingTaskModel = false;
        } catch (SQLException e) {
            updatingTaskModel = false;
            showError(e);
        }
    }

    private void openTaskDialog(Task existing) {
        JTextField title = new JTextField(existing == null ? "" : existing.getTitle(), 24);
        JTextArea desc = new JTextArea(existing == null ? "" : existing.getDescription(), 4, 24);
        LocalDateTime baseDeadline = existing == null
                ? LocalDateTime.now().plusDays(1)
                : existing.getDeadline();
        JTextField deadlineDate = new JTextField(DATE_FMT.format(baseDeadline), 10);
        JTextField deadlineTime = new JTextField(TIME_FMT.format(baseDeadline), 6);
        JComboBox<Priority> pri = new JComboBox<>(Priority.values());
        if (existing != null) {
            pri.setSelectedItem(existing.getPriority());
        }
        JComboBox<TaskType> typ = new JComboBox<>(TaskType.values());
        if (existing != null) {
            typ.setSelectedItem(existing.getTaskType());
        }

        // Custom type name field (shown only when CUSTOM is selected)
        JTextField customTypeName = new JTextField(16);
        customTypeName.setVisible(typ.getSelectedItem() == TaskType.CUSTOM);
        JLabel customTypeLabel = new JLabel("Custom Type Name:");
        customTypeLabel.setVisible(typ.getSelectedItem() == TaskType.CUSTOM);
        typ.addActionListener(ev -> {
            boolean isCustom = typ.getSelectedItem() == TaskType.CUSTOM;
            customTypeName.setVisible(isCustom);
            customTypeLabel.setVisible(isCustom);
            // Repack the dialog to adjust layout
            java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(typ);
            if (w != null)
                w.pack();
        });

        // Duration: hours + minutes spinners
        int existingMin = existing == null ? 60 : existing.getDurationMinutes();
        javax.swing.JSpinner durationHours = new javax.swing.JSpinner(
                new javax.swing.SpinnerNumberModel(existingMin / 60, 0, 23, 1));
        javax.swing.JSpinner durationMins = new javax.swing.JSpinner(
                new javax.swing.SpinnerNumberModel(existingMin % 60, 0, 59, 1));
        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        durationPanel.add(durationHours);
        durationPanel.add(new JLabel("hr"));
        durationPanel.add(durationMins);
        durationPanel.add(new JLabel("min"));

        JComboBox<TaskStatus> status = new JComboBox<>(TaskStatus.values());
        if (existing != null) {
            status.setSelectedItem(existing.getStatus());
        }

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        int row = 0;
        addRow(form, g, row++, "Title:", title);
        addRow(form, g, row++, "Description:", new JScrollPane(desc));
        addRow(form, g, row++, "Deadline Date (YYYY-MM-DD):", deadlineDate);
        addRow(form, g, row++, "Deadline Time (HH:MM):", deadlineTime);
        addRow(form, g, row++, "Priority:", pri);
        addRow(form, g, row++, "Type:", typ);
        // Custom type name row (dynamic visibility)
        g.gridx = 0;
        g.gridy = row;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        form.add(customTypeLabel, g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        form.add(customTypeName, g);
        g.fill = GridBagConstraints.NONE;
        row++;
        addRow(form, g, row++, "Duration:", durationPanel);
        addRow(form, g, row++, "Status:", status);

        int ok = JOptionPane.showConfirmDialog(this, form,
                existing == null ? "Add Task" : "Edit Task",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            TaskType type = (TaskType) typ.getSelectedItem();

            // Validate custom type name
            if (type == TaskType.CUSTOM) {
                String cName = customTypeName.getText().trim();
                if (cName.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Please enter a name for the custom task type.",
                            "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            Task t = (existing == null) ? Task.forType(type) : existing;
            t.setUserId(user.getUserId());
            // If custom type, prepend the custom name to the title for identification
            String taskTitle = title.getText().trim();
            if (type == TaskType.CUSTOM) {
                String cName = customTypeName.getText().trim();
                t.setDescription((desc.getText() == null ? "" : desc.getText())
                        + "\n[Custom Type: " + cName + "]");
            } else {
                t.setDescription(desc.getText());
            }
            t.setTitle(taskTitle);
            LocalDate d = LocalDate.parse(deadlineDate.getText().trim(), DATE_FMT);
            LocalTime tm = LocalTime.parse(deadlineTime.getText().trim(), TIME_FMT);
            LocalDateTime deadlineDt = LocalDateTime.of(d, tm);

            // Warn if the deadline is in the past
            boolean forceComplete = false;
            if (deadlineDt.isBefore(LocalDateTime.now())) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "The deadline date '" + d + "' is in the past.\n"
                                + "Do you still want to add this task? (It will be marked as COMPLETED)",
                        "Past Deadline", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
                forceComplete = true;
            }

            t.setDeadline(deadlineDt);
            t.setPriority((Priority) pri.getSelectedItem());
            int totalDuration = ((Integer) durationHours.getValue()) * 60
                    + ((Integer) durationMins.getValue());
            if (totalDuration <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Duration must be at least 1 minute.",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            t.setDurationMinutes(totalDuration);
            t.setStatus(forceComplete ? TaskStatus.COMPLETED : (TaskStatus) status.getSelectedItem());

            if (existing == null) {
                tasks.create(t);
            } else {
                if (t.getTaskType() != type) {
                    // Type changed: rebuild concrete subclass
                    Task copy = Task.forType(type);
                    copy.setTaskId(t.getTaskId());
                    copy.setUserId(t.getUserId());
                    copy.setTitle(t.getTitle());
                    copy.setDescription(t.getDescription());
                    copy.setDeadline(t.getDeadline());
                    copy.setPriority(t.getPriority());
                    copy.setDurationMinutes(t.getDurationMinutes());
                    copy.setStatus(t.getStatus());
                    t = copy;
                }
                tasks.update(t);
            }
            refreshTasks();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private static void addRow(JPanel form, GridBagConstraints g, int row, String label, JComponent c) {
        g.gridx = 0;
        g.gridy = row;
        g.weightx = 0;
        form.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        form.add(c, g);
        g.fill = GridBagConstraints.NONE;
    }

    // ------------------------------------------------------------------
    // Timetable tab
    // ------------------------------------------------------------------
    private JComponent buildTimetablePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setBackground(BG);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);

        // Prominent date title
        JLabel dateDisplayLabel = new JLabel("Timetable for: " + scheduleDate.getText());
        dateDisplayLabel.setFont(new Font("Arial", Font.BOLD, 16));
        dateDisplayLabel.setForeground(new Color(22, 90, 124));
        JPanel dateTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        dateTitle.setBackground(BG);
        dateTitle.add(dateDisplayLabel);

        // Update date display when textfield changes
        scheduleDate.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                dateDisplayLabel.setText("Timetable for: " + scheduleDate.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                dateDisplayLabel.setText("Timetable for: " + scheduleDate.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                dateDisplayLabel.setText("Timetable for: " + scheduleDate.getText());
            }
        });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row1.setBackground(BG);
        row1.add(label("Date:"));
        row1.add(scheduleDate);
        row1.add(label("From:"));
        row1.add(availStart);
        row1.add(label("To:"));
        row1.add(availEnd);
        row1.add(label("Display:"));
        row1.add(timeFormatChoice);

        JButton genBtn = new JButton("Generate Timetable");
        JButton regenBtn = new JButton("Regenerate");
        JButton viewBtn = new JButton("View Saved");
        JButton exportBtn = new JButton("Export PDF");
        stylePrimaryButton(genBtn);
        styleSecondaryButton(regenBtn);
        styleSecondaryButton(viewBtn);
        styleSecondaryButton(exportBtn);

        genBtn.addActionListener(e -> generateAndSave(false));
        regenBtn.addActionListener(e -> generateAndSave(true));
        viewBtn.addActionListener(e -> refreshTimetable());
        exportBtn.addActionListener(e -> exportPdf());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        buttonRow.setBackground(BG);
        buttonRow.add(genBtn);
        buttonRow.add(regenBtn);
        buttonRow.add(viewBtn);
        buttonRow.add(exportBtn);

        JButton backBtn = new JButton("Back to Menu");
        styleSecondaryButton(backBtn);
        backBtn.addActionListener(e -> {
            new MenuUI(user).setVisible(true);
            dispose();
        });

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setBackground(new Color(160, 55, 55));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setOpaque(true);
        logoutBtn.setContentAreaFilled(true);
        logoutBtn.setBorderPainted(false);
        logoutBtn.addActionListener(e -> {
            new LoginUI().setVisible(true);
            dispose();
        });

        buttonRow.add(backBtn);
        buttonRow.add(logoutBtn);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(BG);
        centerPanel.add(row1, BorderLayout.NORTH);

        JPanel rescheduledPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        rescheduledPanel.setBackground(BG);

        rescheduledLabel.setForeground(new Color(200, 80, 0));
        rescheduledLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        rescheduledPanel.add(rescheduledLabel);

        styleSecondaryButton(viewTomorrowBtn);
        // Special styling for smaller button
        viewTomorrowBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        viewTomorrowBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        viewTomorrowBtn.addActionListener(e -> {
            try {
                LocalDate day = LocalDate.parse(scheduleDate.getText().trim()).plusDays(1);
                scheduleDate.setText(DATE_FMT.format(day));
                // Clear stale timetable before generating for the new day
                currentEntries = List.of();
                updatingTtModel = true;
                ttModel.setRowCount(0);
                updatingTtModel = false;
                // Generate fresh for tomorrow
                generateAndSave(false);
            } catch (Exception ex) {
                showError(ex);
            }
        });
        rescheduledPanel.add(viewTomorrowBtn);

        rescheduledPanel.setVisible(false);
        centerPanel.add(rescheduledPanel, BorderLayout.CENTER);
        centerPanel.add(buttonRow, BorderLayout.SOUTH);

        top.add(dateTitle, BorderLayout.NORTH);
        top.add(centerPanel, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);

        JScrollPane ttScroll = new JScrollPane(ttTable);
        ttScroll.getViewport().setBackground(CARD);
        panel.add(ttScroll, BorderLayout.CENTER);
        return panel;
    }

    private void generateAndSave(boolean regenerate) {
        if (rescheduledLabel.getParent() != null) {
            rescheduledLabel.getParent().setVisible(false);
            if (rescheduledLabel.getParent().getParent() != null) {
                rescheduledLabel.getParent().getParent().revalidate();
                rescheduledLabel.getParent().getParent().repaint();
            }
        }
        try {
            LocalDate day = LocalDate.parse(scheduleDate.getText().trim());
            LocalTime from = parseTimeField(availStart);
            LocalTime to = parseTimeField(availEnd);
            if (!from.isBefore(to)) {
                JOptionPane.showMessageDialog(this,
                        "Availability end must be after start.");
                return;
            }

            // Find tasks already fully scheduled on previous days
            java.util.Map<Integer, Long> priorScheduled = sched.scheduledMinutesBefore(user.getUserId(), day);

            List<Task> allPending = tasks.findPending(user.getUserId());
            List<Task> pending = new java.util.ArrayList<>();
            for (Task t : allPending) {
                // Only include tasks with deadline on or after this day
                if (t.getDeadline().toLocalDate().isBefore(day)) {
                    continue;
                }
                // Skip tasks that were already fully scheduled on earlier days
                Long alreadyScheduled = priorScheduled.get(t.getTaskId());
                if (alreadyScheduled != null && alreadyScheduled >= t.getDurationMinutes()) {
                    continue;
                }
                pending.add(t);
            }
            if (pending.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No pending tasks to schedule. Add some first.");
                return;
            }

            // --- Detect overflow: compute total required vs available time ---
            long availableMinutes = java.time.temporal.ChronoUnit.MINUTES.between(
                    LocalDateTime.of(day, from), LocalDateTime.of(day, to));
            int totalTaskMinutes = pending.stream()
                    .mapToInt(Task::getDurationMinutes).sum();
            // Rough estimate of break time (breaks between tasks)
            int breakMinutes = Integer.parseInt(
                    DatabaseConnection.prop("schedule.break.minutes", "10"));
            int estimatedBreaks = Math.max(0, pending.size() - 1);
            long totalNeeded = totalTaskMinutes + (long) estimatedBreaks * breakMinutes;

            // Use generateWithOverflow to get precise overflow info
            ScheduleResult result = sched.generateWithOverflow(
                    user.getUserId(), pending, day, from, to);
            List<Timetable> generated = result.getEntries();
            List<Task> overflowTasks = result.getOverflowTasks();

            // If there are overflow tasks, show the resolution dialog
            if (!overflowTasks.isEmpty()) {
                OverflowAction[] actions = showOverflowDialog(
                        overflowTasks, availableMinutes, totalTaskMinutes, day);
                if (actions == null) {
                    // User cancelled the whole operation
                    return;
                }

                // Process each overflow task according to user's choice
                List<Task> tasksToReschedule = new java.util.ArrayList<>();
                List<Task> tasksToRemove = new java.util.ArrayList<>();

                for (int i = 0; i < overflowTasks.size(); i++) {
                    Task t = overflowTasks.get(i);
                    switch (actions[i]) {
                        case SCHEDULE_TOMORROW:
                            tasksToReschedule.add(t);
                            break;
                        case REMOVE:
                            tasksToRemove.add(t);
                            break;
                        // SKIP: do nothing, leave as-is
                    }
                }

                // Reschedule tasks to tomorrow (update their deadline)
                for (Task t : tasksToReschedule) {
                    LocalDate tomorrow = day.plusDays(1);
                    LocalDateTime newDeadline = LocalDateTime.of(tomorrow,
                            t.getDeadline().toLocalTime());
                    t.setDeadline(newDeadline);
                    tasks.update(t);
                }

                // Remove tasks the user chose to delete
                for (Task t : tasksToRemove) {
                    tasks.delete(user.getUserId(), t.getTaskId());
                }

                // Show summary
                StringBuilder summary = new StringBuilder();
                if (!tasksToReschedule.isEmpty()) {
                    List<String> rNames = new java.util.ArrayList<>();
                    summary.append("Rescheduled to tomorrow:\n");
                    for (Task t : tasksToReschedule) {
                        summary.append("  • ").append(t.getTitle()).append("\n");
                        rNames.add(t.getTitle());
                    }
                    rescheduledLabel.setText("Tasks Rescheduled to Tomorrow: " + String.join(", ", rNames));
                    rescheduledLabel.getParent().setVisible(true);
                    rescheduledLabel.getParent().getParent().revalidate();
                    rescheduledLabel.getParent().getParent().repaint();
                } else {
                    rescheduledLabel.getParent().setVisible(false);
                    if (rescheduledLabel.getParent().getParent() != null) {
                        rescheduledLabel.getParent().getParent().revalidate();
                        rescheduledLabel.getParent().getParent().repaint();
                    }
                }
                if (!tasksToRemove.isEmpty()) {
                    if (summary.length() > 0)
                        summary.append("\n");
                    summary.append("Removed:\n");
                    for (Task t : tasksToRemove) {
                        summary.append("  • ").append(t.getTitle()).append("\n");
                    }
                }
                if (summary.length() > 0) {
                    JOptionPane.showMessageDialog(this, summary.toString(),
                            "Tasks Updated", JOptionPane.INFORMATION_MESSAGE);
                }
            }

            // Check for conflicts in the generated schedule
            Timetable[] conflict = SchedulerService.firstConflict(generated);
            if (conflict != null) {
                JOptionPane.showMessageDialog(this,
                        "Task conflict detected between '" + conflict[0].getTaskTitle()
                                + "' and '" + conflict[1].getTaskTitle() + "'",
                        "Conflict", JOptionPane.WARNING_MESSAGE);
                return;
            }

            sched.save(user.getUserId(), day, generated);
            renderTimetable(generated);
            refreshTasks();
            JOptionPane.showMessageDialog(this,
                    (regenerate ? "Regenerated " : "Generated ")
                            + generated.size() + " blocks for " + day);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Bad date/time format. Use YYYY-MM-DD and HH:MM.",
                    "Input error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    /** Action choices for overflow tasks. */
    private enum OverflowAction {
        SCHEDULE_TOMORROW, REMOVE, SKIP
    }

    /**
     * Shows a dialog listing all tasks that don't fit in today's window.
     * For each task the user picks: Schedule Tomorrow, Remove, or Skip.
     * Returns an array of actions parallel to overflowTasks, or null if cancelled.
     */
    private OverflowAction[] showOverflowDialog(List<Task> overflowTasks,
            long availableMinutes, int totalTaskMinutes, LocalDate day) {

        OverflowAction[] actions = new OverflowAction[overflowTasks.size()];
        // Default to SCHEDULE_TOMORROW
        java.util.Arrays.fill(actions, OverflowAction.SCHEDULE_TOMORROW);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG);

        // Header info
        JLabel header = new JLabel("<html><b>Not enough time!</b><br>"
                + "Available: <b>" + availableMinutes + " min</b> &nbsp;|&nbsp; "
                + "Tasks need: <b>" + totalTaskMinutes + " min</b> (+ breaks)<br><br>"
                + "The following tasks don't fit in today's schedule.<br>"
                + "Choose what to do with each:</html>");
        header.setForeground(TEXT_DARK);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(header, BorderLayout.NORTH);

        // Build a table-like panel for each overflow task
        JPanel taskListPanel = new JPanel(new GridBagLayout());
        taskListPanel.setBackground(CARD);
        taskListPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(8, 8, 8, 8)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Column headers
        gc.gridy = 0;
        gc.gridx = 0;
        gc.weightx = 0.4;
        JLabel hTask = new JLabel("Task");
        hTask.setFont(hTask.getFont().deriveFont(Font.BOLD));
        hTask.setForeground(ACCENT);
        taskListPanel.add(hTask, gc);

        gc.gridx = 1;
        gc.weightx = 0.15;
        JLabel hPri = new JLabel("Priority");
        hPri.setFont(hPri.getFont().deriveFont(Font.BOLD));
        hPri.setForeground(ACCENT);
        taskListPanel.add(hPri, gc);

        gc.gridx = 2;
        gc.weightx = 0.15;
        JLabel hDur = new JLabel("Duration");
        hDur.setFont(hDur.getFont().deriveFont(Font.BOLD));
        hDur.setForeground(ACCENT);
        taskListPanel.add(hDur, gc);

        gc.gridx = 3;
        gc.weightx = 0.3;
        JLabel hAction = new JLabel("Action");
        hAction.setFont(hAction.getFont().deriveFont(Font.BOLD));
        hAction.setForeground(ACCENT);
        taskListPanel.add(hAction, gc);

        // One row per overflow task
        javax.swing.ButtonGroup[] groups = new javax.swing.ButtonGroup[overflowTasks.size()];
        for (int i = 0; i < overflowTasks.size(); i++) {
            Task task = overflowTasks.get(i);
            gc.gridy = i + 1;

            // Task name
            gc.gridx = 0;
            JLabel nameLabel = new JLabel(task.getTitle());
            nameLabel.setForeground(TEXT_DARK);
            taskListPanel.add(nameLabel, gc);

            // Priority (color-coded)
            gc.gridx = 1;
            JLabel priLabel = new JLabel(task.getPriority().name());
            switch (task.getPriority()) {
                case HIGH:
                    priLabel.setForeground(new Color(180, 40, 40));
                    break;
                case MEDIUM:
                    priLabel.setForeground(new Color(180, 130, 20));
                    break;
                case LOW:
                    priLabel.setForeground(new Color(40, 140, 80));
                    break;
            }
            priLabel.setFont(priLabel.getFont().deriveFont(Font.BOLD));
            taskListPanel.add(priLabel, gc);

            // Duration
            gc.gridx = 2;
            JLabel durLabel = new JLabel(task.getDurationMinutes() + " min");
            durLabel.setForeground(MUTED);
            taskListPanel.add(durLabel, gc);

            // Radio buttons for action
            gc.gridx = 3;
            JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            radioPanel.setBackground(CARD);

            javax.swing.JRadioButton tmrw = new javax.swing.JRadioButton("Tomorrow");
            javax.swing.JRadioButton remove = new javax.swing.JRadioButton("Remove");
            tmrw.setSelected(true);
            tmrw.setBackground(CARD);
            remove.setBackground(CARD);
            tmrw.setForeground(TEXT_DARK);
            remove.setForeground(new Color(180, 40, 40));

            groups[i] = new javax.swing.ButtonGroup();
            groups[i].add(tmrw);
            groups[i].add(remove);

            final int idx = i;
            tmrw.addActionListener(e -> actions[idx] = OverflowAction.SCHEDULE_TOMORROW);
            remove.addActionListener(e -> actions[idx] = OverflowAction.REMOVE);

            radioPanel.add(tmrw);
            radioPanel.add(remove);
            taskListPanel.add(radioPanel, gc);
        }

        JScrollPane scroll = new JScrollPane(taskListPanel);
        scroll.setPreferredSize(new java.awt.Dimension(560,
                Math.min(300, 40 + overflowTasks.size() * 36)));
        scroll.getViewport().setBackground(CARD);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Tasks Exceed Available Time",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        return actions;
    }

    private void refreshTimetable() {
        if (rescheduledLabel.getParent() != null) {
            rescheduledLabel.getParent().setVisible(false);
            rescheduledLabel.getParent().getParent().revalidate();
            rescheduledLabel.getParent().getParent().repaint();
        }
        try {
            LocalDate day = LocalDate.parse(scheduleDate.getText().trim());
            renderTimetable(sched.loadForDay(user.getUserId(), day));
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void renderTimetable(List<Timetable> entries) {
        updatingTtModel = true;
        ttModel.setRowCount(0);
        for (Timetable t : entries) {
            ttModel.addRow(new Object[] {
                    !t.isBreakBlock() && t.getTaskStatus() == TaskStatus.COMPLETED,
                    t.slotLabel(use24HourFormat),
                    t.getTaskTitle() == null ? "" : t.getTaskTitle(),
                    t.isBreakBlock() ? "-"
                            : (t.getTaskPriority() == null ? "" : t.getTaskPriority().name()),
                    t.isBreakBlock() ? "BREAK"
                            : (t.getTaskStatus() == null ? "" : t.getTaskStatus().name())
            });
        }
        currentEntries = entries;
        updatingTtModel = false;
        updateReminderTracking();
    }

    private void exportPdf() {
        try {
            LocalDate day = LocalDate.parse(scheduleDate.getText().trim());
            List<Timetable> entries = sched.loadForDay(user.getUserId(), day);
            if (entries.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nothing to export for " + day);
                return;
            }
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("timetable_" + day + ".pdf"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path target = fc.getSelectedFile().toPath();
            PdfExporter.export(target, day, entries, use24HourFormat);
            JOptionPane.showMessageDialog(this, "Exported to " + target.toAbsolutePath());
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern(use24HourFormat ? "HH:mm" : "hh:mm a");
        return DATE_FMT.format(dateTime) + " " + timeFmt.format(dateTime);
    }

    private void showError(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void updateDelayedStatuses(List<Timetable> entries) throws SQLException {
        Set<Integer> delayedTaskIds = new HashSet<>();
        for (Timetable t : entries) {
            if (t.isBreakBlock()) {
                continue;
            }
            if (t.getTaskStatus() == TaskStatus.DELAYED) {
                delayedTaskIds.add(t.getTaskId());
            }
        }
        for (Integer taskId : delayedTaskIds) {
            tasks.updateStatus(user.getUserId(), taskId, TaskStatus.DELAYED);
        }
    }

    private void installTableListeners() {
        taskModel.addTableModelListener(e -> {
            if (updatingTaskModel || e.getColumn() != TASK_COL_DONE || e.getFirstRow() < 0) {
                return;
            }
            int row = e.getFirstRow();
            boolean done = Boolean.TRUE.equals(taskModel.getValueAt(row, TASK_COL_DONE));
            int taskId = (int) taskModel.getValueAt(row, TASK_COL_ID);
            try {
                tasks.updateStatus(user.getUserId(), taskId,
                        done ? TaskStatus.COMPLETED : TaskStatus.PENDING);
                refreshTasks();
                // Update ALL blocks for this task in timetable (task-level toggle)
                updateAllBlocksForTask(taskId, done ? TaskStatus.COMPLETED : TaskStatus.PENDING);
            } catch (SQLException ex) {
                showError(ex);
            }
        });

        ttModel.addTableModelListener(e -> {
            if (updatingTtModel || e.getColumn() != TT_COL_DONE || e.getFirstRow() < 0) {
                return;
            }
            int row = e.getFirstRow();
            if (row >= currentEntries.size()) {
                return;
            }
            Timetable entry = currentEntries.get(row);
            if (entry.isBreakBlock()) {
                return;
            }
            boolean done = Boolean.TRUE.equals(ttModel.getValueAt(row, TT_COL_DONE));
            try {
                // Mark only THIS specific block as completed/pending
                entry.setTaskStatus(done ? TaskStatus.COMPLETED : TaskStatus.PENDING);

                // Check if ALL blocks for this task are now completed
                boolean allBlocksDone = true;
                for (Timetable t : currentEntries) {
                    if (!t.isBreakBlock() && t.getTaskId() == entry.getTaskId()) {
                        if (t.getTaskStatus() != TaskStatus.COMPLETED) {
                            allBlocksDone = false;
                            break;
                        }
                    }
                }

                // Update task-level status in DB only if all blocks are done
                tasks.updateStatus(user.getUserId(), entry.getTaskId(),
                        allBlocksDone ? TaskStatus.COMPLETED : TaskStatus.PENDING);
                refreshTasks();
                renderTimetable(currentEntries);
            } catch (SQLException ex) {
                showError(ex);
            }
        });
    }

    /**
     * Updates ALL blocks for a given task in the timetable (used when toggling
     * from the Tasks tab, which is task-level, not block-level).
     */
    private void updateAllBlocksForTask(int taskId, TaskStatus newStatus) {
        for (Timetable t : currentEntries) {
            if (!t.isBreakBlock() && t.getTaskId() == taskId) {
                t.setTaskStatus(newStatus);
            }
        }
        renderTimetable(currentEntries);
    }

    private void startReminderTimer() {
        reminderTimer = new Timer(60_000, e -> checkReminders());
        reminderTimer.setInitialDelay(5_000);
        reminderTimer.start();
    }

    private void updateReminderTracking() {
        Set<String> allowed = new HashSet<>();
        for (Timetable t : currentEntries) {
            if (t.isBreakBlock()) {
                continue;
            }
            allowed.add(reminderKey(t));
        }
        reminded.retainAll(allowed);
        checkReminders();
    }

    private void checkReminders() {
        if (currentEntries.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Timetable t : currentEntries) {
            if (t.isBreakBlock()) {
                continue;
            }
            if (t.getTaskStatus() == TaskStatus.COMPLETED) {
                continue;
            }
            if (!t.getStartTime().toLocalDate().equals(LocalDate.now())) {
                continue;
            }
            LocalDateTime remindAt = t.getStartTime().minusMinutes(reminderMinutes);
            if (!now.isBefore(remindAt) && now.isBefore(t.getStartTime())) {
                String key = reminderKey(t);
                if (reminded.add(key)) {
                    JOptionPane.showMessageDialog(this,
                            "Upcoming task in " + reminderMinutes + " minutes: "
                                    + t.getTaskTitle() + " (" + t.slotLabel(use24HourFormat) + ")",
                            "Reminder", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }

    private static String reminderKey(Timetable t) {
        return t.getTaskId() + "|" + t.getStartTime();
    }

    private static void stylePrimaryButton(JButton button) {
        button.setBackground(new Color(24, 102, 173));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setRolloverEnabled(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        installHover(button, new Color(24, 102, 173), new Color(18, 86, 150));
        installPressColor(button, new Color(16, 70, 128));
    }

    private static void styleSecondaryButton(JButton button) {
        button.setBackground(new Color(24, 102, 173));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 11));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setRolloverEnabled(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        installHover(button, new Color(24, 102, 173), new Color(18, 86, 150));
        installPressColor(button, new Color(16, 70, 128));
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

    private static void styleTable(JTable table) {
        table.setGridColor(new Color(230, 224, 214));
        table.setSelectionBackground(new Color(210, 226, 234));
        table.setSelectionForeground(TEXT_DARK);
        table.getTableHeader().setBackground(new Color(232, 227, 219));
        table.getTableHeader().setForeground(TEXT_DARK);
        table.getTableHeader().setBorder(BorderFactory.createLineBorder(BORDER));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 15));
        table.setRowHeight(32);
    }

    // ------------------------------------------------------------------
    // Color-coded renderers (BONUS)
    // ------------------------------------------------------------------
    private static class TimetableRowRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            String pri = String.valueOf(table.getValueAt(row, TT_COL_PRIORITY));
            String status = String.valueOf(table.getValueAt(row, TT_COL_STATUS));
            Color bg;
            if ("BREAK".equals(status)) {
                bg = new Color(235, 231, 224);
            } else if ("HIGH".equals(pri)) {
                bg = new Color(255, 219, 219);
            } else if ("MEDIUM".equals(pri)) {
                bg = new Color(255, 236, 196);
            } else if ("LOW".equals(pri)) {
                bg = new Color(210, 240, 224);
            } else {
                bg = Color.WHITE;
            }
            if (selected) {
                bg = bg.darker();
            }
            c.setBackground(bg);
            // Add horizontal padding so text doesn't clip outside the row background
            if (c instanceof javax.swing.JLabel) {
                javax.swing.JLabel lbl = (javax.swing.JLabel) c;
                lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                lbl.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            }
            return c;
        }
    }

    private static class TaskRowRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            String status = String.valueOf(table.getValueAt(row, TASK_COL_STATUS));
            Color bg = "COMPLETED".equals(status) ? new Color(216, 240, 228)
                    : "DELAYED".equals(status) ? new Color(255, 232, 204)
                            : Color.WHITE;
            if (selected) {
                bg = bg.darker();
            }
            c.setBackground(bg);
            // Add horizontal padding for consistent text alignment
            if (c instanceof javax.swing.JLabel) {
                javax.swing.JLabel lbl = (javax.swing.JLabel) c;
                lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                lbl.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            }
            return c;
        }
    }
}
