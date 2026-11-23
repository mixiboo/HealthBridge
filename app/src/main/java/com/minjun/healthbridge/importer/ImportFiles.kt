package com.minjun.healthbridge.importer

import java.io.File
import java.io.InputStream

/** 다른 앱에서 공유받은 zip은 임시 권한이라 앱 전용 공간에 한 번 복사해 둔다 (한 번에 하나만 보관) */
object ImportFiles {
    private const val NAME = "shared-import.zip"

    fun copyToPrivate(input: InputStream, dir: File): File {
        dir.mkdirs()
        val tmp = File(dir, "$NAME.part")
        input.use { src -> tmp.outputStream().use { src.copyTo(it, 1 shl 16) } }
        val target = File(dir, NAME)
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "copy failed" }
        return target
    }

    fun clear(dir: File) {
        File(dir, NAME).delete()
        File(dir, "$NAME.part").delete()
    }
}
