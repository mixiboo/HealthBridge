package com.minjun.healthbridge.importer

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import com.minjun.healthbridge.apple.AppleExportReader
import com.minjun.healthbridge.hc.RecordWriter
import com.minjun.healthbridge.hc.WriteResult
import com.minjun.healthbridge.model.ImportOptions
import com.minjun.healthbridge.model.OccupiedDays
import com.minjun.healthbridge.testutil.TestZips
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.time.LocalDate

class ImportPipelineAllDataTest {
    private val zip = TestZips.zip("apple_health_export/export.xml" to TestZips.resource("export_more.xml"))
    private val reader = AppleExportReader({ zip.inputStream() }) { KXmlParser() }

    private class Collect : RecordWriter {
        val all = ArrayList<Record>()
        override suspend fun write(records: List<Record>) = WriteResult(records.size, 0).also { all += records }
    }

    private suspend fun run(o: ImportOptions, occupied: OccupiedDays? = null): List<Record> {
        val w = Collect()
        ImportPipeline(reader, w, existingIds = { emptySet() }, occupiedDays = { occupied }).run(o) {}
        return w.all
    }

    @Test fun every_kind_is_converted() = runTest {
        val r = run(ImportOptions.ALL)
        assertEquals(97.0, r.filterIsInstance<OxygenSaturationRecord>().single().percentage.value, 1e-6)
        assertEquals(14.5, r.filterIsInstance<RespiratoryRateRecord>().single().rate, 1e-6)
        assertEquals(45.2, r.filterIsInstance<Vo2MaxRecord>().single().vo2MillilitersPerMinuteKilogram, 1e-6)
        assertEquals(36.6, r.filterIsInstance<BodyTemperatureRecord>().single().temperature.inCelsius, 1e-6)
        val bp = r.filterIsInstance<BloodPressureRecord>().single() // 짝이 없는 수축기 121은 버림
        assertEquals(118.0, bp.systolic.inMillimetersOfMercury, 1e-6)
        assertEquals(76.0, bp.diastolic.inMillimetersOfMercury, 1e-6)
        assertEquals(95.0, r.filterIsInstance<BloodGlucoseRecord>().single().level.inMilligramsPerDeciliter, 1e-6)
        assertEquals(1.75, r.filterIsInstance<HeightRecord>().single().height.inMeters, 1e-6)
        assertEquals(18.0, r.filterIsInstance<BodyFatRecord>().single().percentage.value, 1e-6)
        assertEquals(56.1, r.filterIsInstance<LeanBodyMassRecord>().single().mass.inKilograms, 1e-6)
        assertEquals(250.0, r.filterIsInstance<HydrationRecord>().single().volume.inMilliliters, 1e-6)
        assertEquals(3.0, r.filterIsInstance<FloorsClimbedRecord>().single().floors, 1e-6)
        assertEquals(2, r.filterIsInstance<StepsRecord>().size)
        assertEquals(2, r.filterIsInstance<SleepSessionRecord>().size)
    }

    @Test fun distance_and_energy_inside_workouts_are_not_doubled() = runTest {
        val r = run(ImportOptions.ALL)
        // 운동(19:00~19:30) 안의 0.9km·55kcal는 빼고, 운동 밖 0.8km·40kcal만 + 운동 자체 300kcal
        val dist = r.filterIsInstance<DistanceRecord>()
        assertEquals(listOf(800.0), dist.map { it.distance.inMeters })
        val kcal = r.filterIsInstance<ActiveCaloriesBurnedRecord>().map { it.energy.inKilocalories }.sorted()
        assertEquals(listOf(40.0, 300.0), kcal)
    }

    @Test fun galaxy_days_are_skipped() = runTest {
        val mar1 = LocalDate.of(2025, 3, 1)
        val r = run(ImportOptions.ALL, OccupiedDays(stepDays = setOf(mar1), watchDays = setOf(mar1)))
        assertEquals(listOf(700L), r.filterIsInstance<StepsRecord>().map { it.count }) // 3/1 걸음 빠짐
        assertTrue(r.filterIsInstance<DistanceRecord>().isEmpty())
        val hr = r.filterIsInstance<HeartRateRecord>().flatMap { it.samples }.map { it.beatsPerMinute }
        assertEquals(listOf(74L), hr) // 3/1 하루 심박 빠짐
        assertEquals(1, r.filterIsInstance<SleepSessionRecord>().size) // 3/1에 깬 잠 빠짐
        assertTrue(r.filterIsInstance<OxygenSaturationRecord>().isEmpty())
        // 체온·혈압·몸 기록은 날짜와 상관없이 그대로
        assertEquals(1, r.filterIsInstance<BloodPressureRecord>().size)
        assertEquals(1, r.filterIsInstance<HeightRecord>().size)
    }

    @Test fun overlap_avoidance_can_be_turned_off() = runTest {
        val mar1 = LocalDate.of(2025, 3, 1)
        val r = run(ImportOptions.ALL.copy(avoidGalaxyOverlap = false), OccupiedDays(setOf(mar1), setOf(mar1)))
        assertEquals(2, r.filterIsInstance<StepsRecord>().size)
    }

    @Test fun categories_are_gated() = runTest {
        val r = run(ImportOptions(sleep = true))
        assertEquals(2, r.filterIsInstance<SleepSessionRecord>().size)
        assertTrue(r.filterIsInstance<StepsRecord>().isEmpty())
        assertTrue(r.filterIsInstance<OxygenSaturationRecord>().isEmpty())
        assertTrue(r.filterIsInstance<HydrationRecord>().isEmpty())
    }
}
