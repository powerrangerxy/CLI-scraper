package org.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: provide a program id (and optionally a semester)")
        return
    }

    val programId = args[0]
    val semester = args.getOrElse(1) { currentSemester() }
    val scraper = Scraper(semester)

    val programUrl = "https://ufind.univie.ac.at/en/vvz_sub.html?path=$programId&semester=$semester"

    // program page is essential, stop cleanly if it fails
    val programHtml = try {
        scraper.fetch(programUrl)
    } catch (e: Exception) {
        println("Failed to fetch the program page.")
        return
    }

    val courseIds = scraper.parseCourseIds(programHtml)
    println("Found ${courseIds.size} courses")

    val allEvents = runBlocking {
        val tasks = courseIds.map { id ->
            async {
                withContext(Dispatchers.IO) {
                    val courseUrl = "https://ufind.univie.ac.at/en/course.html?lv=$id&semester=$semester"
                    try {
                        val courseHtml = scraper.fetch(courseUrl)
                        scraper.parseCourseEvents(courseHtml)
                    } catch (e: Exception) {
                        println("Skipped course $id: ${e.message}")
                        emptyList<Timeslot>()
                    }
                }
            }
        }
        tasks.awaitAll().flatten()
    }

    println("Total events collected: ${allEvents.size}")

    val now = LocalDateTime.now()
    val upcoming = allEvents
        .filter { it.start.isAfter(now) }
        .sortedBy { it.start }
        .distinctBy { it.start to it.courseName }
    // remove the distinctBy line above to show every group's session separately

    val outputFormatter = DateTimeFormatter.ofPattern("EEE dd.MM.yyyy HH:mm")
    println("Next 10 events:")
    println("Date & Time".padEnd(24) + "Course".padEnd(50) + "Room")
    for (event in upcoming.take(10)) {
        val prettyDate = event.start.format(outputFormatter)
        println(prettyDate.padEnd(24) + event.courseName.padEnd(50) + event.room)
    }
}