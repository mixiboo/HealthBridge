package com.minjun.healthbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {
    @Test fun workout_labels() {
        assertEquals("달리기", Labels.workout("HKWorkoutActivityTypeRunning"))
        assertEquals("근력 운동", Labels.workout("HKWorkoutActivityTypeTraditionalStrengthTraining"))
        assertEquals("기타 (SomethingNew)", Labels.workout("HKWorkoutActivityTypeSomethingNew"))
    }

    @Test fun record_labels() {
        assertEquals("심박", Labels.record("HeartRateRecord"))
        assertEquals("총 칼로리", Labels.record("TotalCaloriesBurnedRecord"))
        assertEquals("FooRecord", Labels.record("FooRecord"))
    }

    @Test fun taglines_are_short_and_many() {
        assertTrue(Labels.taglines.size >= 5)
        assertTrue(Labels.taglines.all { it.length <= 32 })
    }

    @Test fun file_size_text() {
        assertEquals("412 MB", Labels.size(412_000_000))
        assertEquals("1.5 GB", Labels.size(1_500_000_000))
        assertEquals("820 KB", Labels.size(820_000))
    }
}
