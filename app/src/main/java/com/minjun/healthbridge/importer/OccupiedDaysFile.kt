package com.minjun.healthbridge.importer

import com.minjun.healthbridge.model.OccupiedDays
import java.io.File
import java.time.LocalDate

/** 화면(포그라운드)에서 읽은 '갤럭시워치 날'을 백그라운드 작업으로 넘기는 파일. 줄마다 "S 2025-03-01" / "W 2025-03-01" */
object OccupiedDaysFile {
    fun write(file: File, d: OccupiedDays) {
        file.writeText((d.stepDays.map { "S $it" } + d.watchDays.map { "W $it" }).joinToString("\n"))
    }

    fun read(file: File): OccupiedDays? = runCatching {
        val steps = HashSet<LocalDate>(); val watch = HashSet<LocalDate>()
        file.readLines().filter { it.isNotBlank() }.forEach { line ->
            val (kind, day) = line.split(' ', limit = 2)
            when (kind) { "S" -> steps += LocalDate.parse(day); "W" -> watch += LocalDate.parse(day); else -> error("bad line") }
        }
        OccupiedDays(steps, watch)
    }.getOrNull()
}
