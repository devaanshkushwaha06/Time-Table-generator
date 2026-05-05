package com.smartscheduler.service;

import com.smartscheduler.db.DatabaseConnection;
import com.smartscheduler.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Greedy / priority-based scheduler.
 *
 * Algorithm:
 *   1. Sort pending tasks by urgency = priority weight + (minutes to deadline).
 *      High priority + near deadline floats to the top.
 *   2. Walk through the availability window.
 *      For each task, lay down blocks of at most {@code suggestedBlockMinutes()}
 *      until its required duration is met, inserting a configurable break
 *      after each long block.
 *   3. Stop when either tasks run out or the day's window is exhausted.
 *
 * Because we always advance a single cursor through the day, the produced
 * schedule is overlap-free by construction. We additionally validate against
 * any pre-existing manually-saved blocks for that day and skip those slots.
 */
public class SchedulerService implements Scheduler {

    private final int blockMaxMinutes;
    private final int breakMinutes;

    public SchedulerService() {
        this.blockMaxMinutes = Integer.parseInt(
                DatabaseConnection.prop("schedule.block.maxMinutes", "50"));
        this.breakMinutes = Integer.parseInt(
                DatabaseConnection.prop("schedule.break.minutes", "10"));
    }

    public SchedulerService(int blockMaxMinutes, int breakMinutes) {
        this.blockMaxMinutes = blockMaxMinutes;
        this.breakMinutes = breakMinutes;
    }

    @Override
    public List<Timetable> generate(int userId, List<Task> tasks,
                                    LocalDate day,
                                    LocalTime availStart, LocalTime availEnd) {

        if (tasks == null || tasks.isEmpty()) return new ArrayList<>();

        LocalDateTime cursor = LocalDateTime.of(day, availStart);
        LocalDateTime windowEnd = LocalDateTime.of(day, availEnd);
        LocalDateTime now = LocalDateTime.now();

        // Sort by composite urgency: priority first, then closest deadline.
        List<Task> queue = new ArrayList<>(tasks);
        queue.sort(Comparator
                .comparingInt((Task t) -> t.getPriority().weight())
                .thenComparing(Task::getDeadline,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingDouble(t -> t.urgencyScore(now)));

        List<Timetable> out = new ArrayList<>();
        int sinceLastBreak = 0;

        for (Task task : queue) {
            int remaining = task.getDurationMinutes();

            while (remaining > 0 && cursor.isBefore(windowEnd)) {
                int blockSize = Math.min(remaining,
                        Math.min(task.suggestedBlockMinutes(), blockMaxMinutes));
                LocalDateTime blockEnd = cursor.plusMinutes(blockSize);
                if (blockEnd.isAfter(windowEnd)) {
                    blockEnd = windowEnd;
                    blockSize = (int) java.time.Duration.between(cursor, blockEnd).toMinutes();
                    if (blockSize <= 0) break;
                }

                Timetable entry = new Timetable(userId, task.getTaskId(), cursor, blockEnd);
                entry.setTaskTitle(task.getTitle());
                entry.setTaskPriority(task.getPriority());
                entry.setTaskStatus(task.getStatus());
                entry.setTaskType(task.getTaskType());
                out.add(entry);

                cursor = blockEnd;
                remaining -= blockSize;
                sinceLastBreak += blockSize;

                if (sinceLastBreak >= blockMaxMinutes && remaining > 0
                        && cursor.plusMinutes(breakMinutes).isBefore(windowEnd)) {
                    Timetable br = new Timetable(userId, -1, cursor,
                            cursor.plusMinutes(breakMinutes));
                    br.setTaskTitle("Break");
                    br.setBreakBlock(true);
                    out.add(br);
                    cursor = cursor.plusMinutes(breakMinutes);
                    sinceLastBreak = 0;
                }
            }
            if (!cursor.isBefore(windowEnd)) break;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Persistence + conflict checks
    // ------------------------------------------------------------------

    /**
     * Persists the given schedule for a single day. The previous schedule
     * for that day is wiped first (this is the "Regenerate timetable" path).
     */
    public void save(int userId, LocalDate day, List<Timetable> entries) throws SQLException {
        try (Connection c = DatabaseConnection.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement clear = c.prepareStatement(
                    "DELETE FROM timetable WHERE user_id=? AND DATE(start_time)=?")) {
                clear.setInt(1, userId);
                clear.setDate(2, Date.valueOf(day));
                clear.executeUpdate();
            }
            String ins = "INSERT INTO timetable(user_id, task_id, start_time, end_time) " +
                         "VALUES (?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(ins)) {
                for (Timetable t : entries) {
                    if (t.isBreakBlock()) continue;       // breaks are display-only
                    ps.setInt(1, t.getUserId());
                    ps.setInt(2, t.getTaskId());
                    ps.setTimestamp(3, Timestamp.valueOf(t.getStartTime()));
                    ps.setTimestamp(4, Timestamp.valueOf(t.getEndTime()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        }
    }

    /** Loads the saved schedule for a single day, joining task details. */
    public List<Timetable> loadForDay(int userId, LocalDate day) throws SQLException {
        String sql =
            "SELECT tt.schedule_id, tt.user_id, tt.task_id, tt.start_time, tt.end_time, " +
            "       t.title, t.priority, t.status, t.task_type " +
            "FROM timetable tt JOIN tasks t ON t.task_id = tt.task_id " +
            "WHERE tt.user_id=? AND DATE(tt.start_time)=? " +
            "ORDER BY tt.start_time ASC";
        List<Timetable> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timetable t = new Timetable();
                    t.setScheduleId(rs.getInt("schedule_id"));
                    t.setUserId(rs.getInt("user_id"));
                    t.setTaskId(rs.getInt("task_id"));
                    t.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
                    t.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
                    t.setTaskTitle(rs.getString("title"));
                    t.setTaskPriority(Priority.valueOf(rs.getString("priority")));
                    t.setTaskStatus(TaskStatus.valueOf(rs.getString("status")));
                    t.setTaskType(TaskType.valueOf(rs.getString("task_type")));
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Returns the first overlap pair found, or null if no conflict exists. */
    public static Timetable[] firstConflict(List<Timetable> entries) {
        List<Timetable> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Timetable::getStartTime));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).overlaps(sorted.get(i))) {
                return new Timetable[]{ sorted.get(i - 1), sorted.get(i) };
            }
        }
        return null;
    }
}
