package com.smartscheduler.model;

/** Task priority. Lower {@link #weight} means higher importance. */
public enum Priority {
    HIGH(1), MEDIUM(2), LOW(3);

    private final int weight;
    Priority(int w) { this.weight = w; }
    public int weight() { return weight; }
}
