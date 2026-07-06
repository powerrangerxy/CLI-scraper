package org.example

import java.time.LocalDate

// March-Sept -> summer (S), Oct-Dec -> winter (W), Jan-Feb -> previous year's winter
fun currentSemester(): String {
    val today = LocalDate.now()
    val month = today.monthValue
    val year = today.year

    val semester: String
    if (month in 3..9) {
        semester = "${year}S"
    } else if (month >= 10) {
        semester = "${year}W"
    } else {
        semester = "${year - 1}W"
    }
    return semester
}