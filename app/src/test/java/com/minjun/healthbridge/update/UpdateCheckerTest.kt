package com.minjun.healthbridge.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val json = """
        {"tag_name":"v0.5.0","name":"헬스 브릿지 0.5.0","body":"- 업데이트 기능",
         "assets":[
           {"name":"notes.txt","browser_download_url":"https://github.com/mixiboo/HealthBridge/releases/download/v0.5.0/notes.txt","size":10},
           {"name":"healthbridge-v0.5.0.apk","browser_download_url":"https://github.com/mixiboo/HealthBridge/releases/download/v0.5.0/healthbridge-v0.5.0.apk","size":12345678}
         ]}
    """.trimIndent()

    @Test fun parses_latest_release_apk() {
        val r = UpdateChecker.parseRelease(json)!!
        assertEquals("0.5.0", r.version)
        assertEquals("https://github.com/mixiboo/HealthBridge/releases/download/v0.5.0/healthbridge-v0.5.0.apk", r.apkUrl)
        assertEquals(12345678L, r.sizeBytes)
        assertEquals("- 업데이트 기능", r.notes)
    }

    @Test fun release_without_apk_is_ignored() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v0.6.0","assets":[]}"""))
        assertNull(UpdateChecker.parseRelease("not json"))
    }

    @Test fun apk_must_come_from_our_repo() {
        val evil = json.replace("https://github.com/mixiboo/HealthBridge/releases/download/v0.5.0/healthbridge", "https://evil.example/healthbridge")
        assertNull(UpdateChecker.parseRelease(evil))
    }

    @Test fun version_comparison() {
        assertTrue(UpdateChecker.isNewer("0.5.0", "0.4.0"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.3"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.4.0", "0.4.0"))
        assertFalse(UpdateChecker.isNewer("0.3.9", "0.4.0"))
        assertFalse(UpdateChecker.isNewer("garbage", "0.4.0"))
    }
}
