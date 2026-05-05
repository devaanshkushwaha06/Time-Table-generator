package com.smartscheduler.model;

public class StudyTask extends Task {
    public StudyTask() { }

    @Override public TaskType getTaskType() { return TaskType.STUDY; }

    /** Pomodoro-friendly: 50 min work blocks. */
    @Override public int suggestedBlockMinutes() { return 50; }
}
