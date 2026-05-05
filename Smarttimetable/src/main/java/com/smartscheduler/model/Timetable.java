package com.smartscheduler.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** A single scheduled block (one row in the {@code timetable} table). */
public class Timetable {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private int scheduleId;
    private int userId;
    private int taskId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** Transient: title of the linked task, populated by joins for display. */
    private String taskTitle;
    private Priority taskPriority;
    private TaskStatus taskStatus;
    private TaskType taskType;
    private boolean breakBlock;

    public Timetable() { }

    public Timetable(int userId, int taskId, LocalDateTime startTime, LocalDateTime endTime) {
        this.userId = userId;
        this.taskId = taskId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean overlaps(Timetable other) {
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }

    public String slotLabel() {
        return HHMM.format(startTime) + " - " + HHMM.format(endTime);
    }

    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }

    public Priority getTaskPriority() { return taskPriority; }
    public void setTaskPriority(Priority taskPriority) { this.taskPriority = taskPriority; }

    public TaskStatus getTaskStatus() { return taskStatus; }
    public void setTaskStatus(TaskStatus taskStatus) { this.taskStatus = taskStatus; }

    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }

    public boolean isBreakBlock() { return breakBlock; }
    public void setBreakBlock(boolean breakBlock) { this.breakBlock = breakBlock; }
}
