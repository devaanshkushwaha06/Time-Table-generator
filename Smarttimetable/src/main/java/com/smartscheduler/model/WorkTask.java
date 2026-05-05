package com.smartscheduler.model;

public class WorkTask extends Task {
    public WorkTask() { }

    @Override public TaskType getTaskType() { return TaskType.WORK; }

    /** Deeper focus: longer continuous blocks. */
    @Override public int suggestedBlockMinutes() { return 90; }
}
