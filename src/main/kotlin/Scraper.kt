package org.example

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Scraper(private val semester: String) {

    private val client = OkHttpClient()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun fetch(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: ""
        response.close()
        return html
    }

    fun parseCourseIds(programHtml: String): Set<String> {
        val document = Jsoup.parse(programHtml)
        // a.what[href*=course.html] matches real courses only, not module headings
        val courseLinks = document.select("a.what[href*=course.html]")

        // dont remove same-named courses, they are different (e.g. os vo, os ku)
        val ids = mutableSetOf<String>()
        for (link in courseLinks) {
            val href = link.attr("href")
            val id = href.substringAfter("lv=").substringBefore("&")
            ids.add(id)
        }
        return ids
    }

    fun parseCourseEvents(courseHtml: String): List<Timeslot> {
        val document = Jsoup.parse(courseHtml)

        val classEvents = document.select("ul.classes.events.list li.event.line")
        val examEvents = document.select("ul.usse-id-exams.events li")
        val courseName = document.select("h1.title span.what").text()

        val timeslots = mutableListOf<Timeslot>()
        for (event in classEvents + examEvents) {
            val date = event.select(".date").text()   // 02.03.
            val time = event.select(".time").text()   // 18:30 - 21:30
            val room = event.select(".room").first()?.text() ?: ""

            try {
                val startTime = time.substringBefore("-").trim()
                val dateTrimmed = date.trim().removeSuffix(".")

                // class dates have no year, exam dates already do
                val fullDate = if (dateTrimmed.split(".").size < 3) {
                    "$dateTrimmed.${semester.take(4)}"
                } else {
                    dateTrimmed
                }

                val start = LocalDateTime.parse("$fullDate $startTime", dateFormatter)
                timeslots.add(Timeslot(start, courseName, room))
            } catch (e: Exception) {
                // unreadable date, skip this event
            }
        }
        return timeslots
    }
}