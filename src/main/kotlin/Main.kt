package org.example

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Timeslot(
    val start: LocalDateTime,
    val courseName: String,
    val room: String
)


fun main(args: Array<String>) {
    if(args.isEmpty()){
        println("No bachelor program id is provided!")
        return
    }
    val programId = args[0]
    //val course_url = "https://ufind.univie.ac.at/en/course.html?lv=051020&semester=2021S"
    val bachelor_program_url = "https://ufind.univie.ac.at/en/vvz_sub.html?path=$programId&semester=2021S"

    val httpFetcher = OkHttpClient()
    val request = Request.Builder().url(bachelor_program_url).build()

    val call = httpFetcher.newCall(request)
    val response = call.execute()

    val html = response.body?.string() ?: ""

//    println("status: ${response.code}")
//    println("html length: ${html.length}")
//    println("first 2000 chars")
//    println(html.take(2000))
    response.close()



    val document = Jsoup.parse(html)
    val courseLinks = document.select("a.what[href*=course.html]")

    //<a class="what" href="course.html?lv=051010&semester=2021S">Programming 1</a>
    //val href = link.attr("href") >>>> href = "course.html?lv=051010&semester=2021S"
    //.text() reads visble test between <a> and </a> ; val name = link.text() >> name = Programming 1


    println("Found ${courseLinks.size} courses")
    val seenCourseIds = mutableSetOf<String>()

// dont remove same name since os vo, os ku;
    for (link in courseLinks) {
        val href = link.attr("href")
        val id = href.substringAfter("lv=").substringBefore("&")
        val name = link.text()
//        println("$name : $href")
        seenCourseIds.add(id) // does not add if already in there
    }

//    println("Found ${courseLinks.size} UNIQUE courses")
    val allCoursesExams = mutableSetOf<String>()

    val allEvents = runBlocking{
        val tasks = seenCourseIds.map { id ->
            async {
            //acynv will lauch 60 coroutines but they are handled by A thread(def for runBlocking)
                withContext(Dispatchers.IO)
                {
                    val courseUrl = "https://ufind.univie.ac.at/en/course.html?lv=$id&semester=2021S"
                    try {
                        val request = Request.Builder().url(courseUrl).build()

                        val call = httpFetcher.newCall(request)
                        val response = call.execute()//call.execute() is blocking call
                        val html = response.body?.string() ?: ""
                        response.close()
                        val document = Jsoup.parse(html)

                        val courseExams = document.select("ul.usse-id-exams.events li")
                        val courseLectures = document.select("ul.classes.events.list li.event.line")
                        val courseName = document.select("h1.title span.what").text()

                        val allEvents = courseLectures + courseExams
                        //val eventStrings = mutableListOf<String>()
                        val timeslots = mutableListOf<Timeslot>()

                        for (event in allEvents){
                            val date = event.select(".date").text()// 02.03.
                            val time = event.select(".time").text()// 18:30 - 21:30
                            val room = event.select(".room").text()
                            //eventStrings.add(courseName + " | " + date + " | " + time + " | " + room)
                        try {
                            val startTime = time.substringBefore("-").trim()// 18:30
                            val dateTrimmed = date.trim().removeSuffix(".")

                            val dateParts = dateTrimmed.split(".")
                            //"28.06.2021" to ["28", "06", "2021"]
                            val fullDate = if (dateParts.size < 3) {
                                "$dateTrimmed.2021"   // 2parts, only day,month , add year
                            } else {
                                dateTrimmed
                            }

                            val timeAndDateString = "$fullDate $startTime"
                            // "02.03.2021 18:30"

                            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                            val start = LocalDateTime.parse(timeAndDateString, formatter)//throws error when the string doesnt match the patttern

                            timeslots.add(Timeslot(start, courseName, room))
                        }
                        catch (e: Exception) {
                            println("cant parse date: '$date' time: '$time'")
                        }

                        }

                        //println("Found ${courseExams.size + courseLectures.size} events(lectures+exams) for the course: $courseName")
                        timeslots
                    }
                    catch (e: Exception){
                        println("Skipped course $id: ${e.message}")
                        emptyList<Timeslot>()
                    }
                }
            }
        }
        tasks.awaitAll().flatten()
    }
    println("Total events collected: ${allEvents.size}")

//    for (event in allEvents.take(20)) {
//        println(event)
//    }

    val now = LocalDateTime.now()

    val futureEvents = mutableListOf<Timeslot>()
    for (event in allEvents) {
        //if (event.start.isAfter(now)){
        if (event.start.isAfter(LocalDateTime.of(2020, 1, 1, 0, 0))) {
            futureEvents.add(event)
        }
    }
    futureEvents.sortBy { it.start }

    val outputFormatter = DateTimeFormatter.ofPattern("EEE dd.MM.yyyy HH:mm")

    println("Next 10 events:")
    println("Date & Time".padEnd(24) + "Course".padEnd(50) + "Room")
    var count = 0
    for (event in futureEvents) {
        if (count >= 10) break
        val prettyDate = event.start.format(outputFormatter)
        println(prettyDate.padEnd(24) + event.courseName.padEnd(50) + event.room)
        count++
    }

}