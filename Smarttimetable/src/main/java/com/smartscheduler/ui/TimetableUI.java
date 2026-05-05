package com.smartscheduler.ui;

import com.smartscheduler.db.DatabaseConnection;
import com.smartscheduler.model.*;
import com.smartscheduler.service.SchedulerService;
import com.smartscheduler.service.TaskService;
import com.smartscheduler.util.PdfExporter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Main application window. Two tabs:
 *   - Tasks   : CRUD on tasks + filters + completion bar.
 *   - Timetable: generated schedule (JTable), regenerate / save / export.
 */
public class TimetableUI extends JFrame {

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final User           user;
    private final TaskService    tasks    = new TaskService();
    private final SchedulerService sched  = new SchedulerService();

    // Tasks tab
    private final DefaultTableModel taskModel = new DefaultTableModel(
            new String[]{"ID", "Title", "Type", "Priority", "Deadline", "Duration", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable      taskTable    = new JTable(taskModel);
    private final JComboBox<String>     priorityFilter = new JComboBox<>(
            new String[]{"All", "HIGH", "MEDIUM", "LOW"});
    private final JComboBox<String>     typeFilter     = new JComboBox<>(
            new String[]{"All", "STUDY", "WORK", "PERSONAL"});
    private final JTextField  dateFilter   = new JTextField(10);
    private final JProgressBar completionBar = new JProgressBar(0, 100);

    // Timetable tab
    private final DefaultTableModel ttModel = new DefaultTableModel(
            new String[]{"Time Slot", "Task", "Priority", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable     ttTable      = new JTable(ttModel);
    private final JTextField scheduleDate = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField availStart   = new JTextField(
            DatabaseConnection.prop("availability.start", "16:00"), 6);
    private final JTextField availEnd     = new JTextField(
            DatabaseConnection.prop("availability.end",   "22:00"), 6);

    public TimetableUI(User user) {
        super("Smart Daily Timetable - " + user.getUsername());
        this.user = user;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tasks", buildTasksPanel());
        tabs.addTab("Timetable", buildTimetablePanel());
        setContentPane(tabs);

        setSize(960, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        ttTable.setDefaultRenderer(Object.class, new TimetableRowRenderer());
        taskTable.setDefaultRenderer(Object.class, new TaskRowRenderer());

        refreshTasks();
        refreshTimetable();
    }

    // ------------------------------------------------------------------
    // Tasks tab
    // ------------------------------------------------------------------
    private JComponent buildTasksPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filter.add(new JLabel("Priority:"));
        filter.add(priorityFilter);
        filter.add(new JLabel("Type:"));
        filter.add(typeFilter);
        filter.add(new JLabel("Date (YYYY-MM-DD):"));
        filter.add(dateFilter);

        JButton apply = new JButton("Apply Filters");
        apply.addActionListener(e -> refreshTasks());
        filter.add(apply);

        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> {
            priorityFilter.setSelectedIndex(0);
            typeFilter.setSelectedIndex(0);
            dateFilter.setText("");
            refreshTasks();
        });
        filter.add(clear);

        panel.add(filter, BorderLayout.NORTH);

        panel.add(new JScrollPane(taskTable), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn  = new JButton("Add Task");
        JButton editBtn = new JButton("Edit Task");
        JButton delBtn  = new JButton("Delete");
        JButton doneBtn = new JButton("Mark Completed");
        addBtn .addActionListener(e -> openTaskDialog(null));
        editBtn.addActionListener(e -> {
            Task t = selectedTask();
            if (t != null) openTaskDialog(t);
        });
        delBtn.addActionListener(e -> {
            Task t = selectedTask();
            if (t == null) return;
            int ok = JOptionPane.showConfirmDialog(this,
                    "Delete task '" + t.getTitle() + "' ?", "Confirm",
                    JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                try { tasks.delete(user.getUserId(), t.getTaskId()); refreshTasks(); }
                catch (SQLException ex) { showError(ex); }
            }
        });
        doneBtn.addActionListener(e -> {
            Task t = selectedTask();
            if (t == null) return;
            try { tasks.markCompleted(user.getUserId(), t.getTaskId()); refreshTasks(); }
            catch (SQLException ex) { showError(ex); }
        });
        actions.add(addBtn); actions.add(editBtn); actions.add(delBtn); actions.add(doneBtn);

        JPanel progress = new JPanel(new BorderLayout(6, 0));
        progress.add(new JLabel("Completion:"), BorderLayout.WEST);
        completionBar.setStringPainted(true);
        progress.add(completionBar, BorderLayout.CENTER);

        south.add(actions, BorderLayout.NORTH);
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
        int taskId = (int) taskModel.getValueAt(row, 0);
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
                try { onDate = LocalDate.parse(dateFilter.getText().trim()); }
                catch (DateTimeParseException ex) { /* ignore bad input */ }
            }

            List<Task> all = tasks.query(user.getUserId(), p, ty, onDate);
            taskModel.setRowCount(0);
            for (Task t : all) {
                taskModel.addRow(new Object[]{
                        t.getTaskId(), t.getTitle(), t.getTaskType(),
                        t.getPriority(), DATETIME_FMT.format(t.getDeadline()),
                        t.getDurationMinutes() + " min", t.getStatus()});
            }
            completionBar.setValue((int) Math.round(tasks.completionPercent(user.getUserId())));
            completionBar.setString(completionBar.getValue() + "% complete");
        } catch (SQLException e) {
            showError(e);
        }
    }

    private void openTaskDialog(Task existing) {
        JTextField title = new JTextField(existing == null ? "" : existing.getTitle(), 24);
        JTextArea  desc  = new JTextArea(existing == null ? "" : existing.getDescription(), 4, 24);
        JTextField deadline = new JTextField(existing == null
                ? DATETIME_FMT.format(LocalDateTime.now().plusDays(1))
                : DATETIME_FMT.format(existing.getDeadline()), 16);
        JComboBox<Priority> pri = new JComboBox<>(Priority.values());
        if (existing != null) pri.setSelectedItem(existing.getPriority());
        JComboBox<TaskType> typ = new JComboBox<>(TaskType.values());
        if (existing != null) typ.setSelectedItem(existing.getTaskType());
        JTextField duration = new JTextField(existing == null ? "60" :
                String.valueOf(existing.getDurationMinutes()), 6);
        JComboBox<TaskStatus> status = new JComboBox<>(TaskStatus.values());
        if (existing != null) status.setSelectedItem(existing.getStatus());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,4,4,4); g.anchor = GridBagConstraints.WEST;
        int row = 0;
        addRow(form, g, row++, "Title:", title);
        addRow(form, g, row++, "Description:", new JScrollPane(desc));
        addRow(form, g, row++, "Deadline (YYYY-MM-DD HH:MM):", deadline);
        addRow(form, g, row++, "Priority:", pri);
        addRow(form, g, row++, "Type:", typ);
        addRow(form, g, row++, "Duration (minutes):", duration);
        addRow(form, g, row++, "Status:", status);

        int ok = JOptionPane.showConfirmDialog(this, form,
                existing == null ? "Add Task" : "Edit Task",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        try {
            TaskType type = (TaskType) typ.getSelectedItem();
            Task t = (existing == null) ? Task.forType(type) : existing;
            t.setUserId(user.getUserId());
            t.setTitle(title.getText());
            t.setDescription(desc.getText());
            t.setDeadline(LocalDateTime.parse(deadline.getText().trim(), DATETIME_FMT));
            t.setPriority((Priority) pri.getSelectedItem());
            t.setDurationMinutes(Integer.parseInt(duration.getText().trim()));
            t.setStatus((TaskStatus) status.getSelectedItem());

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
        g.gridx = 0; g.gridy = row; g.weightx = 0; form.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; form.add(c, g);
        g.fill = GridBagConstraints.NONE;
    }

    // ------------------------------------------------------------------
    // Timetable tab
    // ------------------------------------------------------------------
    private JComponent buildTimetablePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Date:"));      top.add(scheduleDate);
        top.add(new JLabel("From:"));      top.add(availStart);
        top.add(new JLabel("To:"));        top.add(availEnd);

        JButton genBtn   = new JButton("Generate Timetable");
        JButton regenBtn = new JButton("Regenerate");
        JButton viewBtn  = new JButton("View Saved");
        JButton exportBtn= new JButton("Export PDF");

        genBtn  .addActionListener(e -> generateAndSave(false));
        regenBtn.addActionListener(e -> generateAndSave(true));
        viewBtn .addActionListener(e -> refreshTimetable());
        exportBtn.addActionListener(e -> exportPdf());

        top.add(genBtn); top.add(regenBtn); top.add(viewBtn); top.add(exportBtn);
        panel.add(top, BorderLayout.NORTH);

        ttTable.setRowHeight(26);
        panel.add(new JScrollPane(ttTable), BorderLayout.CENTER);
        return panel;
    }

    private void generateAndSave(boolean regenerate) {
        try {
            LocalDate day = LocalDate.parse(scheduleDate.getText().trim());
            LocalTime from = LocalTime.parse(availStart.getText().trim());
            LocalTime to   = LocalTime.parse(availEnd.getText().trim());
            if (!from.isBefore(to)) {
                JOptionPane.showMessageDialog(this,
                        "Availability end must be after start.");
                return;
            }

            List<Task> pending = tasks.findPending(user.getUserId());
            if (pending.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No pending tasks to schedule. Add some first.");
                return;
            }

            List<Timetable> generated = sched.generate(
                    user.getUserId(), pending, day, from, to);

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
            JOptionPane.showMessageDialog(this,
                    (regenerate ? "Regenerated " : "Generated ") +
                            generated.size() + " blocks for " + day);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Bad date/time format. Use YYYY-MM-DD and HH:MM.",
                    "Input error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void refreshTimetable() {
        try {
            LocalDate day = LocalDate.parse(scheduleDate.getText().trim());
            renderTimetable(sched.loadForDay(user.getUserId(), day));
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void renderTimetable(List<Timetable> entries) {
        ttModel.setRowCount(0);
        for (Timetable t : entries) {
            ttModel.addRow(new Object[]{
                    t.slotLabel(),
                    t.getTaskTitle() == null ? "" : t.getTaskTitle(),
                    t.isBreakBlock() ? "-" :
                        (t.getTaskPriority() == null ? "" : t.getTaskPriority().name()),
                    t.isBreakBlock() ? "BREAK" :
                        (t.getTaskStatus() == null ? "" : t.getTaskStatus().name())
            });
        }
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
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path target = fc.getSelectedFile().toPath();
            PdfExporter.export(target, day, entries);
            JOptionPane.showMessageDialog(this, "Exported to " + target.toAbsolutePath());
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showError(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ------------------------------------------------------------------
    // Color-coded renderers (BONUS)
    // ------------------------------------------------------------------
    private static class TimetableRowRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            String pri = String.valueOf(table.getValueAt(row, 2));
            String status = String.valueOf(table.getValueAt(row, 3));
            Color bg;
            if ("BREAK".equals(status))      bg = new Color(230, 230, 230);
            else if ("HIGH".equals(pri))     bg = new Color(255, 220, 220);
            else if ("MEDIUM".equals(pri))   bg = new Color(255, 240, 200);
            else if ("LOW".equals(pri))      bg = new Color(220, 240, 220);
            else                             bg = Color.WHITE;
            if (selected) bg = bg.darker();
            c.setBackground(bg);
            return c;
        }
    }

    private static class TaskRowRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            String status = String.valueOf(table.getValueAt(row, 6));
            Color bg = "COMPLETED".equals(status) ? new Color(225, 245, 225) : Color.WHITE;
            if (selected) bg = bg.darker();
            c.setBackground(bg);
            return c;
        }
    }
}
