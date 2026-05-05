package com.smartscheduler.model;

public class PersonalTask extends Task {
    public PersonalTask() { }

    @Override public TaskType getTaskType() { return TaskType.PERSONAL; }

    @Override public int suggestedBlockMinutes() { return 30; }
}
