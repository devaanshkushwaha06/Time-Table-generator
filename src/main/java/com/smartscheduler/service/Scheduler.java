package com.smartscheduler.service;

import com.smartscheduler.model.Task;
import com.smartscheduler.model.Timetable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduling abstraction. Keeping the algorithm behind an interface lets us
 * swap implementations (greedy / weighted / ML-based) without touching the UI.
 */
public interface Scheduler {

    /**
     * Build a non-overlapping timetable for the given tasks within the
     * availability window on {@code day}.
     *
     * @param userId        owner of the tasks
     * @param tasks         pending tasks to consider
     * @param day           date to plan for
     * @param availStart    earliest start time (inclusive)
     * @param availEnd      latest end time (exclusive)
     * @return ordered list of timetable entries (may include break blocks)
     */
    List<Timetable> generate(int userId,
                             List<Task> tasks,
                             LocalDate day,
                             LocalTime availStart,
                             LocalTime availEnd);
}
