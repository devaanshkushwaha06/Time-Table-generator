# Smart Daily Task Timetable Generator

A Java Swing + MySQL desktop application that takes the user's daily tasks
and generates an optimized, conflict-free timetable based on priority,
deadlines, durations, and the user's availability window.

---

## 1. Tech stack

| Layer        | Choice                              |
|--------------|-------------------------------------|
| Language     | Java 11+                            |
| GUI          | Java Swing                          |
| Database     | MySQL 8.x                           |
| Connectivity | JDBC (`mysql-connector-j`)          |
| Architecture | MVC (model / service / ui)          |
| PDF export   | Built-in writer (no external libs)  |

---

## 2. Project structure

```
Smarttimetable/
├── pom.xml                       Maven build (optional)
├── run.sh / run.bat              Manual build+run scripts (no Maven)
├── schema.sql                    Database schema
├── lib/                          Drop mysql-connector-j-*.jar here
└── src/main/
    ├── java/com/smartscheduler/
    │   ├── Main.java                       Entry point
    │   ├── db/DatabaseConnection.java      JDBC + properties loader
    │   ├── model/
    │   │   ├── User.java
    │   │   ├── Task.java                   Abstract base (encapsulation)
    │   │   ├── StudyTask.java              Inheritance + polymorphism
    │   │   ├── WorkTask.java
    │   │   ├── PersonalTask.java
    │   │   ├── Priority.java
    │   │   ├── TaskType.java
    │   │   ├── TaskStatus.java
    │   │   └── Timetable.java
    │   ├── service/
    │   │   ├── AuthService.java            Login / register, hashed pwd
    │   │   ├── TaskService.java            JDBC CRUD + filters
    │   │   ├── Scheduler.java              Interface (abstraction)
    │   │   └── SchedulerService.java       Greedy implementation
    │   ├── ui/
    │   │   ├── LoginUI.java
    │   │   └── TimetableUI.java            JTable, color coding
    │   └── util/PdfExporter.java           PDF export
    └── resources/db.properties
```

---

## 3. SQL schema

Run once before starting the app:

```bash
mysql -u root -p < schema.sql
```

This creates `smart_scheduler` with three tables — `users`, `tasks`,
`timetable` (full DDL is in `schema.sql`).

---

## 4. Configuration

Edit `src/main/resources/db.properties` to point at your MySQL instance:

```properties
db.url=jdbc:mysql://localhost:3306/smart_scheduler?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
db.user=root
db.password=root

availability.start=16:00
availability.end=22:00
schedule.block.maxMinutes=50
schedule.break.minutes=10
```

---

## 5. How to run

### Option A — Maven

```bash
mvn clean package
java -cp target/smart-timetable.jar:target/lib/* com.smartscheduler.Main
# Windows:  java -cp "target\smart-timetable.jar;target\lib\*" com.smartscheduler.Main
```

### Option B — No Maven

1. Download `mysql-connector-j-8.x.x.jar` from
   https://dev.mysql.com/downloads/connector/j/ and put it in `./lib/`.
2. Run:
   ```bash
   ./run.sh        # macOS / Linux
   run.bat         # Windows
   ```

The login window opens. Click **Register** the first time to create an
account, then **Sign in**.

---

## 6. Step-by-step usage

1. **Sign in / Register.**
2. In the **Tasks** tab, click *Add Task* and fill in title, deadline,
   priority, duration, and type (Study / Work / Personal). Repeat for
   every task you need scheduled.
3. Use the **Filters** to narrow by Priority, Type, or Date.
4. Switch to the **Timetable** tab. Pick a date and an availability
   window (defaults come from `db.properties`).
5. Click **Generate Timetable**. The smart scheduler:
   - sorts pending tasks by *(priority, deadline)*,
   - lays them out in non-overlapping blocks within the availability
     window,
   - splits long tasks at `suggestedBlockMinutes()` (50 for STUDY, 90
     for WORK, 30 for PERSONAL),
   - inserts an automatic break after each long block,
   - aborts with **"Task conflict detected"** if anything overlaps.
6. Click **Regenerate** to wipe the saved schedule for that date and
   rebuild it (e.g. after editing tasks).
7. Click **Export PDF** to dump the schedule to a PDF.
8. Mark tasks complete with **Mark Completed**; the progress bar at the
   bottom of the Tasks tab updates automatically.

---

## 7. OOP principles

| Principle      | Where it lives                                                         |
|----------------|------------------------------------------------------------------------|
| Encapsulation  | All `model.*` classes — private fields, validating setters             |
| Inheritance    | `Task` → `StudyTask`, `WorkTask`, `PersonalTask`                       |
| Polymorphism   | `suggestedBlockMinutes()` overrides drive block sizing per task type   |
| Abstraction    | `Scheduler` interface hides scheduling complexity from the UI          |
| Interfaces     | `Scheduler` (swappable algorithm)                                      |

---

## 8. Algorithm summary

`SchedulerService.generate(...)`:

1. Compute `urgencyScore = priorityWeight*1000 + minutesToDeadline`.
2. Stable-sort tasks ascending on `(priority weight, deadline,
   urgencyScore)`.
3. Walk a `cursor` through the availability window. For each task, lay
   blocks of `min(remaining, suggestedBlockMinutes, scheduleBlockMax)`
   until the duration is satisfied or the window ends.
4. After every block long enough to cross the break threshold, insert a
   `breakMinutes` block. Breaks are display-only (not persisted).
5. Validate the produced list with `firstConflict(...)`; refuse to save
   if any overlap is detected.

This is a deterministic greedy approach — fast, easy to reason about,
and overlap-free by construction.

---

## 9. Bonus features included

- **Color-coded timetable** — rows tinted by priority (red / amber /
  green) with a distinct shade for break blocks.
- **Regenerate timetable** — wipes the day's saved schedule and rebuilds
  it in one click.
- **Progress tracking** — `JProgressBar` shows completion percentage.
- **Filter & search** — by priority, task type, or specific date.
- **PDF export** — built without external libraries.

---

## 10. Running in Visual Studio Code

You can develop and run the project entirely from VS Code. Two paths:
**without Maven** (simplest, no extra tools) and **with Maven** (cleaner
classpath management).

### 10.1 Prerequisites

1. **Java JDK 11 or newer** — confirm with `java -version` and
   `javac -version`.
2. **MySQL server** running locally (or anywhere reachable from your
   machine), with the schema loaded:
   ```bash
   mysql -u root -p < schema.sql
   ```
3. **VS Code extensions** — install these from the Extensions panel
   (`Ctrl+Shift+X`):
   - *Extension Pack for Java* (Microsoft) — bundles Language Support,
     Debugger, Test Runner, Project Manager, Maven for Java, etc.
   - *(Optional)* *MySQL* (Weijan Chen) for browsing the DB inside VS Code.

### 10.2 Open the project

1. Unzip `Smarttimetable.zip`.
2. In VS Code: **File → Open Folder…** and pick the `Smarttimetable`
   folder. Accept the prompt to trust the workspace.
3. Wait a few seconds for the Java language server to index the
   project. The "Java Projects" view in the sidebar should list
   `smart-timetable`.

### 10.3 Edit DB credentials

Open `src/main/resources/db.properties` and update `db.user` /
`db.password` / `db.url` to match your MySQL instance.

### 10.4 Path A — Run without Maven

The simplest path; works even on a clean machine.

1. Download `mysql-connector-j-8.x.x.jar` from
   https://dev.mysql.com/downloads/connector/j/ and copy it into the
   `lib/` folder inside the project.
2. Open `src/main/java/com/smartscheduler/Main.java`.
3. Just above the `main` method you will see a small **▶ Run | Debug**
   code-lens link injected by the Java extension. Click **Run**.
   - First run: VS Code asks where to put `.classpath`. Accept the
     defaults.
   - The Java extension auto-adds JARs in `lib/` to the classpath, so
     the MySQL driver is picked up.
4. The login window appears. Register a user, sign in, and you're in.

If the Run code-lens does not appear:
- Open the Command Palette (`Ctrl+Shift+P`) → **Java: Configure Java
  Runtime** to confirm a JDK is detected.
- Or use `Run → Start Debugging` (`F5`) with `Main.java` open.

### 10.5 Path B — Run with Maven

1. Ensure Maven is installed (`mvn -version`). The "Maven for Java"
   extension also exposes a **Maven** view in the sidebar where you
   can run lifecycle goals with one click.
2. In the Maven view, expand **smart-timetable → Lifecycle**, right-click
   **package**, and choose *Run Maven Command*. This produces:
   - `target/smart-timetable.jar`
   - `target/lib/mysql-connector-j-*.jar`
3. Run from the integrated terminal (`Ctrl+`` `):
   ```bash
   # Linux / macOS
   java -cp "target/smart-timetable.jar:target/lib/*" com.smartscheduler.Main
   # Windows (PowerShell or cmd)
   java -cp "target\smart-timetable.jar;target\lib\*" com.smartscheduler.Main
   ```
4. Or open `Main.java` and click **Run** from the code-lens — once the
   Maven build has populated the classpath, the Java extension uses it
   automatically.

### 10.6 Debugging

1. Open any source file and click in the gutter to set a breakpoint
   (red dot).
2. Press `F5` (or **Run → Start Debugging**). VS Code launches the app
   with the debugger attached. Use the *Variables*, *Call Stack*, and
   *Debug Console* panels in the sidebar.
3. To customize the launch (JVM args, working directory, etc.) create a
   `.vscode/launch.json` with `Run → Add Configuration… → Java`.

### 10.7 Common issues

| Symptom                                         | Fix                                                                                          |
|-------------------------------------------------|----------------------------------------------------------------------------------------------|
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | The MySQL JAR is missing — drop `mysql-connector-j-*.jar` into `lib/` and reload the window. |
| `Access denied for user 'root'@'localhost'`     | Wrong `db.user` / `db.password` in `db.properties`.                                          |
| `Unknown database 'smart_scheduler'`            | You skipped step 10.1.2 — run `mysql -u root -p < schema.sql`.                               |
| Run code-lens missing                           | Reload window (`Ctrl+Shift+P → Developer: Reload Window`) or pick a JDK with `Java: Configure Java Runtime`. |
| HiDPI / blurry Swing UI on Windows              | Right-click `java.exe` → Properties → Compatibility → *Override high DPI scaling: Application*. |

---

## 11. Notes on security

Passwords are stored as `salt:sha256(salt+password)`. This is
significantly better than plain text, but for a real product you should
swap in BCrypt or Argon2.
