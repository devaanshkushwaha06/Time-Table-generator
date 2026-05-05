# Smart Daily Task Timetable Generator - Project Report & Presentation Guide

This document outlines all the features, technical details, and architectural choices of the Smart Timetable Application. You can use this to easily build your project report or PowerPoint presentation.

## 1. Project Overview
The Smart Daily Task Timetable Generator is a desktop application designed to help users manage their time effectively. It takes the user's daily tasks and generates an optimized, conflict-free timetable based on priority, deadlines, task durations, and the user's custom availability window.

## 2. Key Features

### User Authentication & Security
*   **User Registration & Login:** Secure account creation and authentication system.
*   **Password Hashing:** Passwords are mathematically hashed using SHA-256 with a unique salt, ensuring they are never stored in plain text.

### Task Management
*   **Comprehensive Task Creation:** Add tasks specifying Title, Deadline, Priority (Low/Medium/High), Duration, and Task Type (Study, Work, Personal).
*   **Task Filtering & Searching:** Easily filter the task list by Priority, Task Type, or specific dates.
*   **Progress Tracking:** An automatic `JProgressBar` provides a visual representation of daily task completion percentage.
*   **Task Completion Logic:** Robust capability to mark tasks as complete, including specialized handling to accurately reflect the completion of "past-deadline" entries.

### Smart Scheduling Engine
*   **Intelligent Prioritization:** Calculates a dynamic `urgencyScore` based on a combination of priority weight and minutes remaining until the deadline.
*   **Deterministic Greedy Algorithm:** Tasks are stable-sorted by urgency and laid out in non-overlapping blocks within the defined availability window.
*   **Smart Task Splitting:** Automatically breaks down lengthy tasks into manageable chunks based on the task type (e.g., Study tasks split into 50-minute blocks, Work tasks into 90-minute blocks, Personal tasks into 30-minute blocks).
*   **Automatic Breaks:** Inserts scheduled breaks (e.g., 10 minutes) automatically after each long task block to reduce cognitive load.
*   **Conflict Prevention:** The algorithm actively detects overlaps and aborts with a "Task conflict detected" warning if the user's availability cannot accommodate all tasks, guaranteeing a conflict-free schedule.

### User Interface & Experience (UX/UI)
*   **Streamlined Navigation:** Intuitive and consolidated button navigation seamlessly directs users between the "Tasks" management tab and the "Timetable" visualization tab.
*   **Visual Clarity:** Features increased table typography and enlarged UI component sizes for better readability and accessibility.
*   **Color-Coded Timetable:** Tasks are displayed in rows visually tinted by priority (Red for High, Amber for Medium, Green for Low), along with a distinct shade for break blocks.
*   **Regenerate Functionality:** Allows users to wipe a day's existing schedule and immediately rebuild it after adding or editing tasks.

### Export Capabilities
*   **Built-in PDF Export:** Generates a PDF document of the daily schedule directly from the application, implemented natively without the need for bloated external libraries.

## 3. Technology Stack
*   **Language:** Java 11+
*   **GUI Framework:** Java Swing
*   **Database:** MySQL 8.x
*   **Database Connectivity:** JDBC (`mysql-connector-j`)
*   **Architecture Pattern:** MVC (Model-View-Controller / Model-Service-UI)

## 4. Object-Oriented Programming (OOP) Principles Applied
The project heavily utilizes core OOP concepts to ensure a robust and maintainable codebase:
*   **Encapsulation:** All `model` classes utilize private fields and validating setters to protect data integrity.
*   **Inheritance:** Implemented a base `Task` class extended by specific types: `StudyTask`, `WorkTask`, and `PersonalTask`.
*   **Polymorphism:** Method overrides (like `suggestedBlockMinutes()`) allow different task types to define their own specific behaviors for scheduling.
*   **Abstraction & Interfaces:** A `Scheduler` interface hides the complex scheduling algorithms from the UI components, allowing the underlying algorithm to be swapped or modified easily without breaking the frontend.

## 5. Potential Slides for PowerPoint Presentation (PPT)
*   **Slide 1: Title Slide** (Project Name, Your Name, Course)
*   **Slide 2: Problem Statement** (Difficulty in managing daily tasks efficiently without overlaps)
*   **Slide 3: Proposed Solution** (Smart Timetable Application overview)
*   **Slide 4: Core Features** (Task management, Authentication, PDF Export)
*   **Slide 5: The Smart Scheduling Algorithm** (Urgency score, Greedy approach, Auto-splitting, Auto-breaks)
*   **Slide 6: User Interface Highlights** (Swing GUI, Color-coding, Navigation improvements)
*   **Slide 7: Technology Stack & Architecture** (Java, MySQL, MVC Pattern)
*   **Slide 8: OOP Principles** (How Encapsulation, Inheritance, Polymorphism are used)
*   **Slide 9: Conclusion / Future Enhancements**
