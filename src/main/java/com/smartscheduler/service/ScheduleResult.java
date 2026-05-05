package com.smartscheduler.service;

import java.util.List;

import com.smartscheduler.model.Task;
import com.smartscheduler.model.Timetable;

/**
 * Holds the output of a schedule generation pass: the timetable entries that
 * fit within the availability window AND the tasks that overflowed (did not fit).
 */
public class ScheduleResult {

    private final List<Timetable> entries;
    private final List<Task> overflowTasks;

    public ScheduleResult(List<Timetable> entries, List<Task> overflowTasks) {
        this.entries = entries;
        this.overflowTasks = overflowTasks;
    }

    /** Scheduled timetable blocks (including breaks). */
    public List<Timetable> getEntries() {
        return entries;
    }

    /** Tasks whose total duration did not fit in the available window. */
    public List<Task> getOverflowTasks() {
        return overflowTasks;
    }

    /** True when every task was fully scheduled. */
    public boolean allFit() {
        return overflowTasks.isEmpty();
    }
}
