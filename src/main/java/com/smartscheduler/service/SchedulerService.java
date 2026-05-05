package com.smartscheduler.service;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.smartscheduler.db.DatabaseConnection;
import com.smartscheduler.model.Priority;
import com.smartscheduler.model.Task;
import com.smartscheduler.model.TaskStatus;
import com.smartscheduler.model.TaskType;
import com.smartscheduler.model.Timetable;

/**
 * Greedy / priority-based scheduler.
 *
 * Algorithm: 1. Sort pending tasks by urgency = priority weight + (minutes to
 * deadline). High priority + near deadline floats to the top. 2. Walk through
 * the availability window. For each task, lay down blocks of at most
 * {@code suggestedBlockMinutes()} until its required duration is met, inserting
 * a configurable break after each long block. 3. Stop when either tasks run out
 * or the day's window is exhausted.
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

        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime cursor = LocalDateTime.of(day, availStart);
        LocalDateTime windowEnd = LocalDateTime.of(day, availEnd);
        LocalDateTime now = LocalDateTime.now();

        // Calculate available minutes in the day
        long totalAvailableMinutes = ChronoUnit.MINUTES.between(
                LocalDateTime.of(day, availStart),
                LocalDateTime.of(day, availEnd));

        // Sort by composite urgency: priority first, then closest deadline.
        List<Task> queue = new ArrayList<>(tasks);
        queue.sort(Comparator
                .comparingInt((Task t) -> t.getPriority().weight())
                .thenComparing(Task::getDeadline,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingDouble(t -> t.urgencyScore(now)));

        List<Timetable> out = new ArrayList<>();
        List<Integer> delayedTaskIds = new ArrayList<>();

        for (int taskIdx = 0; taskIdx < queue.size(); taskIdx++) {
            Task task = queue.get(taskIdx);
            int remaining = task.getDurationMinutes();
            boolean isDelayed = false;

            while (remaining > 0) {
                // Check if we've exceeded the window
                if (!cursor.isBefore(windowEnd)) {
                    isDelayed = true;
                    break;
                }

                // Calculate available time left in the day
                long timeLeftInDay = ChronoUnit.MINUTES.between(cursor, windowEnd);

                // If not enough time for even a small chunk, move task to next day
                if (timeLeftInDay < Math.min(remaining, blockMaxMinutes)) {
                    isDelayed = true;
                    break;
                }

                // Schedule a block (max 50 minutes)
                int blockSize = Math.min(remaining, blockMaxMinutes);
                LocalDateTime blockEnd = cursor.plusMinutes(blockSize);

                Timetable entry = new Timetable(userId, task.getTaskId(), cursor, blockEnd);
                entry.setTaskTitle(task.getTitle());
                entry.setTaskPriority(task.getPriority());
                entry.setTaskStatus(TaskStatus.PENDING);
                entry.setTaskType(task.getTaskType());
                out.add(entry);

                cursor = blockEnd;
                remaining -= blockSize;

                // If there's more to schedule for this task
                if (remaining > 0) {
                    long timeLeftAfterBlock = ChronoUnit.MINUTES.between(cursor, windowEnd);

                    // Need: break (10 min) + next block (at least remaining min)
                    if (timeLeftAfterBlock < (breakMinutes + Math.min(remaining, blockMaxMinutes))) {
                        isDelayed = true;
                        break;
                    }

                    // Add 10-min break
                    Timetable br = new Timetable(userId, -1, cursor,
                            cursor.plusMinutes(breakMinutes));
                    br.setTaskTitle("Break");
                    br.setBreakBlock(true);
                    out.add(br);
                    cursor = cursor.plusMinutes(breakMinutes);
                } else if (taskIdx < queue.size() - 1) {
                    // Task is done, and there are more tasks. Snap to the next hour.
                    LocalDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS);
                    if (cursor.getMinute() > 0 || cursor.getSecond() > 0) {
                        nextHour = nextHour.plusHours(1);
                    }
                    if (nextHour.isAfter(windowEnd)) {
                        nextHour = windowEnd;
                    }
                    if (nextHour.isAfter(cursor)) {
                        Timetable br = new Timetable(userId, -1, cursor, nextHour);
                        br.setTaskTitle("Break");
                        br.setBreakBlock(true);
                        out.add(br);
                        cursor = nextHour;
                    }
                }
            }

            // Mark as DELAYED if not all chunks were scheduled
            if (isDelayed) {
                delayedTaskIds.add(task.getTaskId());
                // Remove any partial entries for this task from output
                out.removeIf(t -> !t.isBreakBlock() && t.getTaskId() == task.getTaskId());
            }
        }

        // Mark all delayed task entries with DELAYED status
        for (Timetable entry : out) {
            if (!entry.isBreakBlock() && delayedTaskIds.contains(entry.getTaskId())) {
                entry.setTaskStatus(TaskStatus.DELAYED);
            }
        }

        return out;
    }

    /**
     * Like {@link #generate}, but also returns the list of tasks that did not
     * fit in the availability window so the caller can present a resolution UI.
     */
    public ScheduleResult generateWithOverflow(int userId, List<Task> tasks,
            LocalDate day,
            LocalTime availStart, LocalTime availEnd) {

        if (tasks == null || tasks.isEmpty()) {
            return new ScheduleResult(new ArrayList<>(), new ArrayList<>());
        }

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
        List<Task> overflow = new ArrayList<>();

        for (int taskIdx = 0; taskIdx < queue.size(); taskIdx++) {
            Task task = queue.get(taskIdx);
            int remaining = task.getDurationMinutes();
            boolean isDelayed = false;

            while (remaining > 0) {
                if (!cursor.isBefore(windowEnd)) {
                    isDelayed = true;
                    break;
                }
                long timeLeftInDay = ChronoUnit.MINUTES.between(cursor, windowEnd);
                if (timeLeftInDay < Math.min(remaining, blockMaxMinutes)) {
                    isDelayed = true;
                    break;
                }

                int blockSize = Math.min(remaining, blockMaxMinutes);
                LocalDateTime blockEnd = cursor.plusMinutes(blockSize);

                Timetable entry = new Timetable(userId, task.getTaskId(), cursor, blockEnd);
                entry.setTaskTitle(task.getTitle());
                entry.setTaskPriority(task.getPriority());
                entry.setTaskStatus(TaskStatus.PENDING);
                entry.setTaskType(task.getTaskType());
                out.add(entry);

                cursor = blockEnd;
                remaining -= blockSize;

                if (remaining > 0) {
                    long timeLeftAfterBlock = ChronoUnit.MINUTES.between(cursor, windowEnd);
                    if (timeLeftAfterBlock < (breakMinutes + Math.min(remaining, blockMaxMinutes))) {
                        isDelayed = true;
                        break;
                    }
                    Timetable br = new Timetable(userId, -1, cursor,
                            cursor.plusMinutes(breakMinutes));
                    br.setTaskTitle("Break");
                    br.setBreakBlock(true);
                    out.add(br);
                    cursor = cursor.plusMinutes(breakMinutes);
                } else if (taskIdx < queue.size() - 1) {
                    // Task is done, and there are more tasks. Snap to the next hour.
                    LocalDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS);
                    if (cursor.getMinute() > 0 || cursor.getSecond() > 0) {
                        nextHour = nextHour.plusHours(1);
                    }
                    if (nextHour.isAfter(windowEnd)) {
                        nextHour = windowEnd;
                    }
                    if (nextHour.isAfter(cursor)) {
                        Timetable br = new Timetable(userId, -1, cursor, nextHour);
                        br.setTaskTitle("Break");
                        br.setBreakBlock(true);
                        out.add(br);
                        cursor = nextHour;
                    }
                }
            }

            if (isDelayed) {
                overflow.add(task);
                // Remove any partial entries for this task
                out.removeIf(t -> !t.isBreakBlock() && t.getTaskId() == task.getTaskId());
            }
        }

        return new ScheduleResult(out, overflow);
    }

    // ------------------------------------------------------------------
    // Persistence + conflict checks
    // ------------------------------------------------------------------
    /**
     * Persists the given schedule for a single day. The previous schedule for
     * that day is wiped first (this is the "Regenerate timetable" path).
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
            String ins = "INSERT INTO timetable(user_id, task_id, start_time, end_time) "
                    + "VALUES (?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(ins)) {
                for (Timetable t : entries) {
                    if (t.isBreakBlock()) {
                        continue;       // breaks are display-only

                    }
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

    /**
     * Loads the saved schedule for a single day, joining task details.
     */
    public List<Timetable> loadForDay(int userId, LocalDate day) throws SQLException {
        String sql
                = "SELECT tt.schedule_id, tt.user_id, tt.task_id, tt.start_time, tt.end_time, "
                + "       t.title, t.priority, t.status, t.task_type "
                + "FROM timetable tt JOIN tasks t ON t.task_id = tt.task_id "
                + "WHERE tt.user_id=? AND DATE(tt.start_time)=? "
                + "ORDER BY tt.start_time ASC";
        List<Timetable> out = new ArrayList<>();
        try (Connection c = DatabaseConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
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

    /**
     * Returns the total scheduled minutes per task on days strictly before {@code day}.
     * Used to avoid re-scheduling tasks that were already fully placed on earlier days.
     */
    public java.util.Map<Integer, Long> scheduledMinutesBefore(int userId, LocalDate day) throws SQLException {
        String sql = "SELECT task_id, "
                + "SUM(TIMESTAMPDIFF(MINUTE, start_time, end_time)) AS total_min "
                + "FROM timetable "
                + "WHERE user_id=? AND DATE(start_time) < ? "
                + "GROUP BY task_id";
        java.util.Map<Integer, Long> map = new java.util.HashMap<>();
        try (Connection c = DatabaseConnection.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("task_id"), rs.getLong("total_min"));
                }
            }
        }
        return map;
    }

    /**
     * Returns the first overlap pair found, or null if no conflict exists.
     */
    public static Timetable[] firstConflict(List<Timetable> entries) {
        List<Timetable> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Timetable::getStartTime));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).overlaps(sorted.get(i))) {
                return new Timetable[]{sorted.get(i - 1), sorted.get(i)};
            }
        }
        return null;
    }
}
