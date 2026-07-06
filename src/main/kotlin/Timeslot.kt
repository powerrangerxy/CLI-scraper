package org.example

import java.time.LocalDate

import java.time.LocalDateTime

data class Timeslot(
    val start: LocalDateTime,
    val courseName: String,
    val room: String
)
