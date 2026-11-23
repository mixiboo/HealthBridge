package com.minjun.healthbridge.testutil

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestZips {
    fun resource(name: String): ByteArray =
        TestZips::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes()

    fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos, Charsets.UTF_8).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** 한국어 설정 애플 내보내기 모양의 zip */
    fun koreanExport(): ByteArray = zip(
        "apple_health_export/export_cda.xml" to
            "<?xml version=\"1.0\"?><ClinicalDocument><title>x</title></ClinicalDocument>".toByteArray(),
        "apple_health_export/내보내기.xml" to resource("export_small.xml"),
        "apple_health_export/workout-routes/route_2025-01-02_7.00am.gpx" to resource("route.gpx"),
    )
}
