# Uni Vienna Course Scraper

A command-line tool that reads the University of Vienna course catalog
([u:find](https://ufind.univie.ac.at)) for a given study program and prints the
**next 10 upcoming events** (lectures and exams) across the whole curriculum,
sorted by date.

## What it does

You give it a study-program id, and it:

1. Reads the program's course list page and pulls out every course id.
2. Reads all the course pages **at the same time (in parallel)** and pulls out
   their event dates, times, and rooms.
3. Puts everything into one list, keeps only events after the current time,
   sorts them, and prints the soonest 10.

## Requirements

- JDK 21 or newer
- Internet access (it reads live pages from u:find)

The libraries (OkHttp, JSoup, Kotlin coroutines) are downloaded automatically by
Gradle.

## Running

```bash
./gradlew run --args="<programId> [semester]"
```

- `programId` - the `path` id of a study program on u:find
  (for example `259334` for Bachelor Computer Science). **Required.**
- `semester` - optional, for example `2025W` or `2026S`. If you leave it out, the
  tool works out the **current semester** from today's date.

Examples:

```bash
# Bachelor Computer Science, current semester (worked out automatically)
./gradlew run --args="259334"

# Bachelor Computer Science, a specific semester
./gradlew run --args="259334 2026S"
```

### Finding a program id

Open a program on the u:find course directory (`vvz.html`). The address of the
program page contains `path=<id>` - that id is what the tool needs.

## Example output

```
Next 10 events:
Date & Time             Course                                            Room
Tue 07.07.2026 08:00    Programming 1                                     PC-Unterrichtsraum 2, ...
Wed 08.07.2026 08:00    Programming 2                                     PC-Unterrichtsraum 2, ...
Wed 02.09.2026 09:45    Algorithms and Data Structures 1                  Auditorium Maximum ...
Mon 21.09.2026 13:15    Software Engineering 1                            Hoersaal 1, Waehringer Strasse 29 1.UG
...
```

If the semester you pick is in the past, all its events get filtered out and the
list is empty - this is expected, since a finished semester has no upcoming
events.

## How it works

The catalog has three page levels; the tool uses two of them:

- **Program page** (`vvz_sub.html?path=...`) - lists all courses. Real course
  links look like `<a class="what" href="course.html?lv=...">`; module headings
  look like `<a class="link" href="vvz_sub.html?path=...">`. The selector
  `a.what[href*=course.html]` picks only the real courses and skips the module
  headings.
- **Course page** (`course.html?lv=...`) - has the event rows. Class events are
  in `ul.classes.events.list li.event`; exam dates are in a separate
  `ul.usse-id-exams.events` list. The tool reads both.

Each event's date and time text is turned into a `LocalDateTime`, which makes
filtering ("after now") and sorting easy.

## Design decisions

- **Semester defaults to the current one.** If you do not pass a semester, the
  tool works it out from today's date using the University of Vienna calendar
  (March-September -> summer `...S`, October-February -> winter `...W`, with the
  year adjusted for January and February). Passing a semester overrides this.

- **Filtering uses the real current time** (`isAfter(now)`), so only genuinely
  upcoming events are shown.

- **All groups are read, then the list is trimmed for display.** A course can be
  split into several groups, each with its own schedule. The tool reads events
  from every group. In the final list, events that share the same time and course
  are shown once, since a student only attends one group and repeated rows do not
  help. One commented line in the code switches this to show every group's session
  separately.

- **Exams are included** together with the regular classes, since they are also
  scheduled events with a date, time, and place.

- **The same course id is only kept once, but events are all kept.** The same
  course can appear many times on a program page, so course ids go into a `Set`
  (which drops repeats). Events go into a `List`, since two events that look the
  same but come from different groups are really separate.

- **The year comes from the semester.** Class-event dates on u:find have no year
  (`02.03.`), so the year is taken from the semester (for example `2026S` ->
  `2026`). Exam dates already have the year, so they are used as they are.

- **It handles failures without crashing.** The first fetch (the program page) is
  wrapped so that, if it fails, the tool prints a message and stops cleanly -
  nothing can be done without the course list. Each course fetch is also wrapped,
  so one network timeout or one broken date only skips that one item instead of
  stopping the whole run.

## Known limitations / possible improvements

- **Multi-room events** show all the room names the site returns, so an event
  booked in several rooms has a long room string. Picking just one main room would
  be an easy improvement.
- 
## Concurrency

About 60 course pages are read per program. Doing this one after another is slow,
because each request spends most of its time waiting on the network. The tool
starts all the fetches as coroutines and runs the blocking HTTP calls on
`Dispatchers.IO`, so the waiting overlaps. This brought a full run down from about
1m40s to under 20s.

## Tech stack

- **Kotlin** (JVM)
- **OkHttp** - HTTP requests
- **JSoup** - HTML parsing
- **Kotlin Coroutines** - parallel fetching
- **Gradle** - build and run