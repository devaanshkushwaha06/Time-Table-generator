package com.smartscheduler.model;

public class CustomTask extends Task {
    public CustomTask() { }

    @Override public TaskType getTaskType() { return TaskType.CUSTOM; }

    /** Default block length for custom tasks. */
    @Override public int suggestedBlockMinutes() { return 45; }
}
