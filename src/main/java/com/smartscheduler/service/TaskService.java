package com.smartscheduler.service;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.smartscheduler.db.DatabaseConnection;
import com.smartscheduler.model.Priority;
import com.smartscheduler.model.Task;
import com.smartscheduler.model.TaskStatus;
import com.smartscheduler.model.TaskType;

/**
 * Persistence operations for {@link Task} entities. All queries are scoped to
 * the supplied user id to enforce per-user data isolation.
 */
public class TaskService {

    public int create(Task t) throws SQLException {
        String sql = "INSERT INTO tasks(user_id, title, description, deadline, "
                + "priority, duration, task_type, status) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConnection.get();
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindForWrite(ps, t);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    t.setTaskId(keys.getInt(1));
                    return t.getTaskId();
                }
            }
        }
        throw new SQLException("Failed to retrieve generated task id");
    }

    public void update(Task t) throws SQLException {
        String sql = "UPDATE tasks SET title=?, description=?, deadline=?, priority=?, "
                + "duration=?, task_type=?, status=? WHERE task_id=? AND user_id=?";
        try (Connection c = DatabaseConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.getTitle());
            ps.setString(2, t.getDescription());
            ps.setTimestamp(3, Timestamp.valueOf(t.getDeadline()));
            ps.setString(4, t.getPriority().name());
            ps.setInt(5, t.getDurationMinutes());
            ps.setString(6, t.getTaskType().name());
            ps.setString(7, t.getStatus().name());
            ps.setInt(8, t.getTaskId());
            ps.setInt(9, t.getUserId());
            ps.executeUpdate();
        }
    }

    public void delete(int userId, int taskId) throws SQLException {
        try (Connection c = DatabaseConnection.get();
                PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM tasks WHERE task_id=? AND user_id=?")) {
            ps.setInt(1, taskId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void markCompleted(int userId, int taskId) throws SQLException {
        updateStatus(userId, taskId, TaskStatus.COMPLETED);
    }

    public void updateStatus(int userId, int taskId, TaskStatus status) throws SQLException {
        try (Connection c = DatabaseConnection.get();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE tasks SET status=? WHERE task_id=? AND user_id=?")) {
            ps.setString(1, status.name());
            ps.setInt(2, taskId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    public List<Task> findAll(int userId) throws SQLException {
        return query(userId, null, null, null);
    }

    /**
     * Returns tasks not yet completed (used by the scheduler).
     */
    public List<Task> findPending(int userId) throws SQLException {
        String sql = "SELECT * FROM tasks WHERE user_id=? AND status <> 'COMPLETED' "
                + "ORDER BY deadline ASC";
        try (Connection c = DatabaseConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return readAll(ps);
        }
    }

    /**
     * Filtered view used by the UI search / filter controls.
     */
    public List<Task> query(int userId, Priority priority, TaskType type, LocalDate onDate)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM tasks WHERE user_id=? ");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (priority != null) {
            sql.append("AND priority=? ");
            params.add(priority.name());
        }
        if (type != null) {
            sql.append("AND task_type=? ");
            params.add(type.name());
        }
        if (onDate != null) {
            sql.append("AND DATE(deadline)=? ");
            params.add(Date.valueOf(onDate));
        }
        sql.append("ORDER BY deadline ASC");

        try (Connection c = DatabaseConnection.get(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return readAll(ps);
        }
    }

    /**
     * Computes the user's overall completion percentage (0-100).
     */
    public double completionPercent(int userId) throws SQLException {
        String sql = "SELECT "
                + "  SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END) AS done, "
                + "  COUNT(*) AS total FROM tasks WHERE user_id=?";
        try (Connection c = DatabaseConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    if (total == 0) {
                        return 0.0;
                    }
                    return rs.getInt("done") * 100.0 / total;
                }
            }
        }
        return 0.0;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private static void bindForWrite(PreparedStatement ps, Task t) throws SQLException {
        ps.setInt(1, t.getUserId());
        ps.setString(2, t.getTitle());
        ps.setString(3, t.getDescription());
        ps.setTimestamp(4, Timestamp.valueOf(t.getDeadline()));
        ps.setString(5, t.getPriority().name());
        ps.setInt(6, t.getDurationMinutes());
        ps.setString(7, t.getTaskType().name());
        ps.setString(8, t.getStatus().name());
    }

    private static List<Task> readAll(PreparedStatement ps) throws SQLException {
        List<Task> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(mapRow(rs));
            }
        }
        return out;
    }

    private static Task mapRow(ResultSet rs) throws SQLException {
        TaskType type = TaskType.valueOf(rs.getString("task_type"));
        Task t = Task.forType(type);
        t.setTaskId(rs.getInt("task_id"));
        t.setUserId(rs.getInt("user_id"));
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        Timestamp dl = rs.getTimestamp("deadline");
        t.setDeadline(dl == null ? LocalDateTime.now().plusDays(1) : dl.toLocalDateTime());
        t.setPriority(Priority.valueOf(rs.getString("priority")));
        t.setDurationMinutes(rs.getInt("duration"));
        t.setStatus(TaskStatus.valueOf(rs.getString("status")));
        return t;
    }
}
