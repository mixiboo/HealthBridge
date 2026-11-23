package com.minjun.healthbridge.apple

import androidx.health.connect.client.records.ExerciseSessionRecord as E
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTypeMapperTest {
    private val expected = mapOf(
        "Running" to E.EXERCISE_TYPE_RUNNING,
        "Walking" to E.EXERCISE_TYPE_WALKING,
        "Cycling" to E.EXERCISE_TYPE_BIKING,
        "Swimming" to E.EXERCISE_TYPE_SWIMMING_POOL,
        "TraditionalStrengthTraining" to E.EXERCISE_TYPE_STRENGTH_TRAINING,
        "FunctionalStrengthTraining" to E.EXERCISE_TYPE_STRENGTH_TRAINING,
        "HighIntensityIntervalTraining" to E.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        "Yoga" to E.EXERCISE_TYPE_YOGA,
        "Hiking" to E.EXERCISE_TYPE_HIKING,
        "Soccer" to E.EXERCISE_TYPE_SOCCER,
        "Basketball" to E.EXERCISE_TYPE_BASKETBALL,
        "Badminton" to E.EXERCISE_TYPE_BADMINTON,
        "Elliptical" to E.EXERCISE_TYPE_ELLIPTICAL,
        "Rowing" to E.EXERCISE_TYPE_ROWING_MACHINE,
        "StairClimbing" to E.EXERCISE_TYPE_STAIR_CLIMBING,
    )

    @Test fun maps_known_types() {
        expected.forEach { (apple, hc) ->
            assertEquals(apple, hc, WorkoutTypeMapper.toHealthConnect("HKWorkoutActivityType$apple"))
        }
    }

    @Test fun unknown_type_is_other_workout() {
        assertEquals(E.EXERCISE_TYPE_OTHER_WORKOUT, WorkoutTypeMapper.toHealthConnect("HKWorkoutActivityTypeSomethingNew"))
    }
}
