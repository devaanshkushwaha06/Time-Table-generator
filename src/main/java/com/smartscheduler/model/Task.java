package com.smartscheduler.model;

import java.time.LocalDateTime;

/**
 * Abstract base class for all tasks. Concrete subclasses ({@link StudyTask},
 * {@link WorkTask}, {@link PersonalTask}) override scheduling-related hooks
 * to demonstrate polymorphism.
 *
 * Encapsulation: all fields are private and exposed only through getters /
 * setters. Validation lives in the setters where it matters.
 */
public abstract class Task {

    private int taskId;
    private int userId;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private Priority priority = Priority.MEDIUM;
    private int durationMinutes;
    private TaskStatus status = TaskStatus.PENDING;

    protected Task() { }

    protected Task(int userId, String title, String description, LocalDateTime deadline,
                   Priority priority, int durationMinutes) {
        this.userId = userId;
        setTitle(title);
        this.description = description;
        this.deadline = deadline;
        this.priority = priority;
        setDurationMinutes(durationMinutes);
    }

    /** Concrete type identifier persisted in the {@code task_type} column. */
    public abstract TaskType getTaskType();

    /**
     * Suggested maximum continuous block (minutes) for this task type. The
     * scheduler splits longer tasks at this boundary and inserts breaks.
     * Subclasses override to customize.
     */
    public int suggestedBlockMinutes() {
        return 50;
    }

    /**
     * Composite urgency score (lower = scheduled earlier).
     * Combines priority weight and remaining time to deadline.
     */
    public double urgencyScore(LocalDateTime now) {
        long minutesToDeadline = Math.max(1,
                java.time.Duration.between(now, deadline == null ? now : deadline).toMinutes());
        return priority.weight() * 1000.0 + minutesToDeadline;
    }

    public int getTaskId() { return taskId; }
    public void setTaskId(int taskId) { this.taskId = taskId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        this.title = title.trim();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) {
        this.priority = (priority == null) ? Priority.MEDIUM : priority;
    }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        this.durationMinutes = durationMinutes;
    }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) {
        this.status = (status == null) ? TaskStatus.PENDING : status;
    }

    @Override public String toString() {
        return getTaskType() + "{id=" + taskId + ", title='" + title +
                "', priority=" + priority + ", duration=" + durationMinutes + "m}";
    }

    /** Factory: build the right concrete subclass from a stored TaskType value. */
    public static Task forType(TaskType type) {
        switch (type) {
            case STUDY:    return new StudyTask();
            case WORK:     return new WorkTask();
            case PERSONAL:
            default:       return new PersonalTask();
        }
    }
}
