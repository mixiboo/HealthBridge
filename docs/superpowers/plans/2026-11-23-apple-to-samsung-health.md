# 헬스 브릿지 (애플워치 운동 → Samsung Health) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 애플 건강 앱 내보내기 zip을 읽어 애플워치 운동·운동 중 심박·GPS 경로(+선택 항목)를 Health Connect에 써서 Samsung Health에서 보이게 하는 안드로이드 앱.

**Architecture:** 순수 Kotlin 파싱 계층(zip 스트리밍 → XmlPullParser로 2회 순회: 1회차 운동, 2회차 심박/기타 기록)과 Health Connect 쓰기 계층을 분리한다. 파싱 계층은 JVM 단위 테스트로 검증하고, 쓰기 계층은 실기기 확인으로 검증한다. 가져오기는 WorkManager 포그라운드 작업으로 돌리고 Compose UI가 진행 상황을 보여준다.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3), `androidx.health.connect:connect-client:1.1.0`, WorkManager, JUnit4 + `net.sf.kxml:kxml2` (JVM 테스트용 XmlPullParser 구현), Gradle (Kotlin DSL).

**Spec:** `docs/2026-11-23-apple-to-samsung-health-design.md`

## Global Constraints

- 패키지: `com.minjun.healthbridge`, 앱 이름: `헬스 브릿지`
- minSdk 28, targetSdk/compileSdk 35 (Galaxy S25+, Health Connect 시스템 내장)
- **`android.permission.INTERNET` 권한을 넣지 않는다** (건강 데이터가 폰 밖으로 나가면 실패)
- 방향은 애플 → Health Connect 한쪽만. HRV는 옮기지 않는다.
- 내보내기 파일은 메모리에 통째로 올리지 않는다 (수백 MB~1GB 이상)
- 모든 Health Connect 기록에 `clientRecordId`를 붙여 재가져오기 시 중복이 생기지 않게 한다
- 기본 선택: 운동 ✅, 운동 중 심박 ✅, 경로 ✅ / 하루 전체 심박 ⬜, 안정 시 심박·체중 ⬜, 걸음 수·수면 ⬜(중복 경고 문구 표시)
- 기본은 애플워치 기록만. 토글로 전체 출처 포함 가능.
- UI 문구는 한국어

## Review Focus

1. **한국어 설정 내보내기** — zip 이름 `내보내기.zip`, 안쪽 `apple_health_export/내보내기.xml`, sourceName `민준의 Apple Watch` → 파일 이름이 아니라 루트 요소 `<HealthData>`로 XML을 찾고 정상 파싱 (Task 6 테스트)
2. **같은 파일 재가져오기** — 같은 입력이면 모든 기록의 `clientRecordId`가 똑같이 나와 덮어쓰기만 됨 (Task 1, Task 7 테스트)
3. **심박·경로·거리가 없는 운동**(실내 근력 운동 등) — 운동 세션은 쓰이고 빈 기록(0 kcal, 0 m, 샘플 0개)은 만들지 않음 (Task 7 테스트)
4. **애플 내보내기가 아닌 zip / 깨진 zip** — 앱이 죽지 않고 "애플 건강 내보내기 파일이 아니에요" 오류 (Task 6 테스트)
5. **해외에서 기록한 운동(+0900이 아닌 오프셋)** — 원래 오프셋이 `startZoneOffset`에 보존됨 (Task 1, Task 7 테스트)

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/minjun/healthbridge/
  model/Models.kt                 # AppleWorkout, HeartSample, RoutePoint, QuantitySample, SleepSample, ImportOptions
  apple/AppleDates.kt             # "yyyy-MM-dd HH:mm:ss Z" 파싱
  apple/WorkoutTypeMapper.kt      # HKWorkoutActivityType* → ExerciseSessionRecord.EXERCISE_TYPE_*
  apple/HealthXmlParser.kt        # 스트리밍 파서 (운동 회차 / 기록 회차)
  apple/GpxParser.kt              # workout-routes/*.gpx → List<RoutePoint>
  apple/AppleExportReader.kt      # zip 열기, XML 찾기, GPX 엔트리 읽기
  hc/RecordIds.kt                 # clientRecordId 생성
  hc/RecordFactory.kt             # 도메인 → Health Connect Record
  hc/HealthConnectWriter.kt       # 권한, 배치 insert, 결과 집계
  importer/ImportPipeline.kt      # 파서 + 팩토리 + 라이터 조립
  importer/ImportWorker.kt        # WorkManager 포그라운드 작업 + 진행률
  ui/MainActivity.kt, ui/ImportViewModel.kt, ui/screens/*.kt
  spike/SpikeWriter.kt            # Task 0 실험용 (Task 9에서 삭제)
app/src/test/java/com/minjun/healthbridge/... # JVM 테스트
app/src/test/resources/fixtures/  # 작은 export.xml, 내보내기.xml, route.gpx, 테스트 zip 생성 헬퍼
```

---

### Task 0: 프로젝트 뼈대 + Samsung Health 표시 실험 🧪

설계 7-1단계. 결과에 따라 이후 범위를 조정한다.

**Files:**
- Create: 위 Gradle 파일들, `AndroidManifest.xml`, `ui/MainActivity.kt`, `spike/SpikeWriter.kt`

**Interfaces:**
- Produces: 빌드 가능한 앱 모듈, Health Connect 의존성, 매니페스트 권한 선언

- [ ] **Step 1:** Gradle 프로젝트 생성 (Compose, connect-client 1.1.0, WorkManager, 테스트 의존성 kxml2). `./gradlew assembleDebug` 성공 확인.
- [ ] **Step 2:** 매니페스트에 쓰기 권한 선언: `WRITE_EXERCISE`, `WRITE_EXERCISE_ROUTE`, `WRITE_HEART_RATE`, `WRITE_ACTIVE_CALORIES_BURNED`, `WRITE_DISTANCE`, `WRITE_RESTING_HEART_RATE`, `WRITE_WEIGHT`, `WRITE_STEPS`, `WRITE_SLEEP` (모두 `android.permission.health.` 접두사), Health Connect 권한 근거 화면용 `ACTION_SHOW_PERMISSIONS_RATIONALE` 액티비티 alias, `INTERNET` 없음 확인.
- [ ] **Step 3:** `SpikeWriter.writeSample(client: HealthConnectClient)` — 지금 기준 1시간 전 30분 달리기 1개: `ExerciseSessionRecord`(경로 점 30개, 김해 근처 좌표), 같은 시간대 `HeartRateRecord`(1분 간격 30샘플, 120~160), `ActiveCaloriesBurnedRecord` 250 kcal, `DistanceRecord` 5000 m. Device = WATCH / Apple / Apple Watch.
- [ ] **Step 4:** 버튼 하나("테스트 운동 넣기")와 권한 요청이 있는 화면. APK 빌드 후 사용자에게 전달.
- [ ] **Step 5 (사용자 확인):** Samsung Health에서 확인할 것: 운동 목록에 뜨는지 / 칼로리·거리 / 운동 상세의 심박 그래프 / 경로 지도. 결과를 설계 문서 끝에 "실험 결과"로 기록하고, 안 보이는 항목은 Task 7 범위에서 표시만 하고 계속 진행.
- [ ] **Step 6:** Commit `chore: scaffold app and Samsung Health display spike`

### Task 1: 날짜 파싱 + 모델 + clientRecordId

**Files:**
- Create: `model/Models.kt`, `apple/AppleDates.kt`, `hc/RecordIds.kt`
- Test: `apple/AppleDatesTest.kt`, `hc/RecordIdsTest.kt`

**Interfaces:**
- Produces:
  - `data class AppleTime(val instant: Instant, val offset: ZoneOffset)`
  - `object AppleDates { fun parse(s: String): AppleTime }`
  - `data class AppleWorkout(val activityType: String, val start: AppleTime, val end: AppleTime, val sourceName: String, val isFromWatch: Boolean, val activeKcal: Double?, val distanceMeters: Double?, val routePaths: List<String>, val indoor: Boolean?)`
  - `data class HeartSample(val time: AppleTime, val bpm: Long)`
  - `data class RoutePoint(val time: Instant, val lat: Double, val lon: Double, val altitudeM: Double?)`
  - `data class QuantitySample(val type: String, val start: AppleTime, val end: AppleTime, val value: Double, val unit: String, val isFromWatch: Boolean)`
  - `data class SleepSample(val start: AppleTime, val end: AppleTime, val value: String, val isFromWatch: Boolean)`
  - `data class ImportOptions(val workouts: Boolean = true, val workoutHeartRate: Boolean = true, val routes: Boolean = true, val allDayHeartRate: Boolean = false, val restingHrAndWeight: Boolean = false, val stepsAndSleep: Boolean = false, val watchOnly: Boolean = true, val from: Instant? = null, val to: Instant? = null)`
  - `object RecordIds { fun workout(w: AppleWorkout): String; fun forSample(kind: String, start: Instant, end: Instant, source: String): String }` — `"hb-" + kind + "-" + sha256(...)` 앞 24자

- [ ] **Step 1: 실패하는 테스트**
  - `parse_korea_offset`: `"2025-01-02 07:00:00 +0900"` → instant `2025-01-01T22:00:00Z`, offset `+09:00`
  - `parse_keeps_foreign_offset`: `"2025-07-10 18:30:00 -0700"` → offset `-07:00`
  - `workout_id_is_stable`: 같은 `AppleWorkout` 두 번 → 같은 id, `startsWith("hb-workout-")`
  - `workout_id_differs_by_start`: 시작 시간 1초 차이 → 다른 id
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "*AppleDatesTest*" --tests "*RecordIdsTest*"` → FAIL
- [ ] **Step 3:** 구현 (`DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")`, `MessageDigest` SHA-256)
- [ ] **Step 4:** 같은 명령 → PASS
- [ ] **Step 5:** Commit `feat: apple date parsing, models, stable record ids`

### Task 2: 운동 종류 매핑

**Files:**
- Create: `apple/WorkoutTypeMapper.kt`; Test: `apple/WorkoutTypeMapperTest.kt`

**Interfaces:**
- Produces: `object WorkoutTypeMapper { fun toHealthConnect(appleType: String): Int }`

- [ ] **Step 1: 실패하는 테스트** — `HKWorkoutActivityTypeRunning`→`EXERCISE_TYPE_RUNNING`, `...Walking`→`WALKING`, `...Cycling`→`BIKING`, `...Swimming`→`SWIMMING_POOL`, `...TraditionalStrengthTraining`→`STRENGTH_TRAINING`, `...FunctionalStrengthTraining`→`STRENGTH_TRAINING`, `...HighIntensityIntervalTraining`→`HIGH_INTENSITY_INTERVAL_TRAINING`, `...Yoga`→`YOGA`, `...Hiking`→`HIKING`, `...Soccer`→`SOCCER`, `...Basketball`→`BASKETBALL`, `...Badminton`→`BADMINTON`, `...Elliptical`→`ELLIPTICAL`, `...Rowing`→`ROWING_MACHINE`, `...StairClimbing`→`STAIR_CLIMBING`, `HKWorkoutActivityTypeSomethingNew`→`EXERCISE_TYPE_OTHER_WORKOUT`
- [ ] **Step 2:** 테스트 실행 → FAIL
- [ ] **Step 3:** `Map<String, Int>` 표로 구현. 위 목록 외에도 Health Connect에 대응 상수가 있는 애플 종류는 표에 추가 (만드는 사람 판단).
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit `feat: map apple workout types to health connect`

### Task 3: XML 파서 — 운동 회차

**Files:**
- Create: `apple/HealthXmlParser.kt`; Test: `apple/HealthXmlParserWorkoutTest.kt`, `fixtures/export_small.xml`

**Interfaces:**
- Consumes: `AppleDates.parse`, `AppleWorkout`
- Produces: `class HealthXmlParser(private val parserFactory: () -> XmlPullParser) { fun readWorkouts(input: InputStream, watchOnly: Boolean): List<AppleWorkout> }`

픽스처에 넣을 형태(실제 애플 형식):
```xml
<Workout workoutActivityType="HKWorkoutActivityTypeRunning" sourceName="민준의 Apple Watch"
  device="&lt;&lt;HKDevice: 0x0&gt;, name:Apple Watch, manufacturer:Apple Inc.&gt;"
  startDate="2025-01-02 07:00:00 +0900" endDate="2025-01-02 07:30:00 +0900">
  <MetadataEntry key="HKIndoorWorkout" value="0"/>
  <WorkoutStatistics type="HKQuantityTypeIdentifierActiveEnergyBurned" sum="250.5" unit="kcal"/>
  <WorkoutStatistics type="HKQuantityTypeIdentifierDistanceWalkingRunning" sum="5.01" unit="km"/>
  <WorkoutRoute><FileReference path="/workout-routes/route_2025-01-02_7.00am.gpx"/></WorkoutRoute>
</Workout>
```
+ 구버전 형식(`totalDistance="3.2" totalDistanceUnit="mi" totalEnergyBurned="180" totalEnergyBurnedUnit="kcal"` 속성), + 아이폰 출처 운동(`sourceName="민준의 iPhone"`, device에 Watch 없음), + 통계 없는 근력 운동.

- [ ] **Step 1: 실패하는 테스트**
  - `reads_modern_workout`: kcal `250.5`, distance `5010.0` m, routePaths `["/workout-routes/route_2025-01-02_7.00am.gpx"]`, indoor `false`, isFromWatch `true`
  - `reads_legacy_attributes`: distance `3.2 mi` → `5149.9 ± 0.5` m, kcal `180.0`
  - `watch_only_filters_iphone`: watchOnly=true → 아이폰 운동 제외, false → 포함
  - `workout_without_stats`: kcal `null`, distance `null`, routePaths 빈 목록
  - `energy_unit_kj`: `unit="kJ"` sum 1000 → `239.0 ± 0.1` kcal
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현. `Record` 등 다른 요소는 건너뜀(자식까지 skip). Watch 판단: `device`에 `"Watch"` 포함 또는 `sourceName`에 `"Watch"` 포함. 거리 단위 `km|mi|m`, 에너지 `kcal|Cal|kJ` 지원.
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit `feat: stream-parse workouts from apple export xml`

### Task 4: XML 파서 — 기록 회차 (심박·선택 항목)

**Files:**
- Modify: `apple/HealthXmlParser.kt`; Test: `apple/HealthXmlParserRecordTest.kt`, fixture에 `Record` 추가

**Interfaces:**
- Consumes: `ImportOptions`, Task 3의 `List<AppleWorkout>`
- Produces:
  - `interface RecordSink { fun onWorkoutHeart(workout: AppleWorkout, sample: HeartSample); fun onAllDayHeart(sample: HeartSample); fun onQuantity(sample: QuantitySample); fun onSleep(sample: SleepSample) }`
  - `fun HealthXmlParser.readRecords(input: InputStream, options: ImportOptions, workouts: List<AppleWorkout>, sink: RecordSink)`

- [ ] **Step 1: 실패하는 테스트** (sink는 리스트에 모으는 가짜 구현)
  - `heart_inside_workout_goes_to_workout`: 07:10 심박 145 → `onWorkoutHeart` (해당 운동)
  - `heart_outside_workout_ignored_by_default`: 12:00 심박 → 아무 콜백 없음 (allDayHeartRate=false)
  - `heart_outside_workout_all_day`: allDayHeartRate=true → `onAllDayHeart`
  - `hrv_never_emitted`: `HKQuantityTypeIdentifierHeartRateVariabilitySDNN` → 어떤 옵션에서도 콜백 없음
  - `optional_types_gated`: `RestingHeartRate`, `BodyMass`는 restingHrAndWeight=true일 때만, `StepCount`, `HKCategoryTypeIdentifierSleepAnalysis`는 stepsAndSleep=true일 때만
  - `date_range_filter`: from/to 밖의 기록 제외
  - `watch_only_records`: watchOnly=true면 아이폰 출처 심박 제외 (체중은 출처 무관하게 포함)
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현. 운동 구간은 시작 시간 기준 정렬 후 이분 탐색으로 매칭 (기록 수 수백만 개 대비).
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit `feat: stream heart rate and optional records with workout matching`

### Task 5: GPX 경로 파서

**Files:**
- Create: `apple/GpxParser.kt`; Test: `apple/GpxParserTest.kt`, `fixtures/route.gpx`

**Interfaces:**
- Produces: `object GpxParser { fun parse(input: InputStream, parserFactory: () -> XmlPullParser): List<RoutePoint> }`

- [ ] **Step 1: 실패하는 테스트**
  - `parses_trkpt`: `<trkpt lat="35.2285" lon="128.8894"><ele>12.3</ele><time>2025-01-01T22:00:05Z</time></trkpt>` → RoutePoint(lat 35.2285, lon 128.8894, alt 12.3, time `2025-01-01T22:00:05Z`)
  - `skips_points_without_time`
  - `empty_gpx_returns_empty_list`
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit `feat: parse workout route gpx`

### Task 6: zip 읽기 (AppleExportReader)

**Files:**
- Create: `apple/AppleExportReader.kt`; Test: `apple/AppleExportReaderTest.kt` (테스트 안에서 임시 zip 생성)

**Interfaces:**
- Consumes: `HealthXmlParser`, `GpxParser`
- Produces:
  - `class NotAppleExportException : Exception()`
  - `class AppleExportReader(private val open: () -> InputStream, private val parserFactory: () -> XmlPullParser) { fun readWorkouts(watchOnly: Boolean): List<AppleWorkout>; fun readRecords(options: ImportOptions, workouts: List<AppleWorkout>, sink: RecordSink); fun readRoutes(paths: Set<String>): Map<String, List<RoutePoint>> }`
  - 매 호출마다 `open()`으로 새로 열어 `ZipInputStream`으로 순차 스트리밍 (압축 해제 파일을 디스크/메모리에 만들지 않음)

- [ ] **Step 1: 실패하는 테스트**
  - `finds_korean_named_xml`: 엔트리 `apple_health_export/내보내기.xml` + `apple_health_export/export_cda.xml`(루트가 `ClinicalDocument`) → 운동 정상 파싱 (루트 `HealthData`인 엔트리 선택)
  - `finds_english_named_xml`: `apple_health_export/export.xml`
  - `reads_route_by_path`: 경로 `/workout-routes/a.gpx` → 엔트리 `apple_health_export/workout-routes/a.gpx`의 점들
  - `not_apple_zip_throws`: 아무 txt만 든 zip → `NotAppleExportException`
  - `corrupt_zip_throws`: 랜덤 바이트 → `NotAppleExportException`
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현 (zip 엔트리 이름은 UTF-8로 읽기)
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit `feat: read apple health export zip by streaming`

### Task 7: Health Connect 기록 만들기 (RecordFactory)

**Files:**
- Create: `hc/RecordFactory.kt`; Test: `hc/RecordFactoryTest.kt`

**Interfaces:**
- Consumes: Task 1 모델, `RecordIds`, `WorkoutTypeMapper`
- Produces: `object RecordFactory { fun forWorkout(w: AppleWorkout, heart: List<HeartSample>, route: List<RoutePoint>?, options: ImportOptions): List<Record>; fun allDayHeart(samples: List<HeartSample>): List<HeartRateRecord>; fun quantity(s: QuantitySample): Record?; fun sleep(samples: List<SleepSample>): List<SleepSessionRecord> }`
- 모든 기록 metadata: 애플워치면 `Metadata.activelyRecorded(Device(type = Device.TYPE_WATCH, manufacturer = "Apple", model = "Apple Watch"), clientRecordId, 1)`, 아니면 `Metadata.manualEntry(clientRecordId, 1)` 계열

- [ ] **Step 1: 실패하는 테스트**
  - `workout_full`: kcal 250.5, 5010 m, 심박 3개, 경로 3점 → `ExerciseSessionRecord`(type RUNNING, route 3점, startZoneOffset +09:00) + `ActiveCaloriesBurnedRecord`(250.5 kcal) + `DistanceRecord`(5010 m) + `HeartRateRecord`(3샘플), 각 clientRecordId 서로 다르고 모두 `hb-` 접두사
  - `workout_minimal`: 통계·심박·경로 없음 → `ExerciseSessionRecord` 1개만
  - `zero_values_skipped`: kcal 0.0, distance 0.0 → 칼로리/거리 기록 없음
  - `foreign_offset_preserved`: -07:00 운동 → `startZoneOffset == ZoneOffset.of("-07:00")`
  - `same_input_same_ids`: 두 번 호출 → clientRecordId 목록 동일
  - `route_off`: options.routes=false → 세션에 route 없음
  - `route_points_outside_session_dropped`: 세션 시간 밖 점 제거 (Health Connect가 거부하므로)
  - `long_workout_heart_chunked`: 심박 5,000샘플 → 여러 `HeartRateRecord`(각 ≤1,000샘플, 시간 겹침 없음)
  - `sleep_stages`: `HKCategoryValueSleepAnalysisAsleepDeep/REM/Core/Awake` → `STAGE_TYPE_DEEP/REM/LIGHT/AWAKE`, 연속 구간은 한 세션으로
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현 (quantity: RestingHeartRate→`RestingHeartRateRecord`, BodyMass→`WeightRecord`, StepCount→`StepsRecord`, 그 외 `null`)
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit `feat: convert apple data to health connect records`

### Task 8: Health Connect 쓰기 + 가져오기 파이프라인

**Files:**
- Create: `hc/HealthConnectWriter.kt`, `importer/ImportPipeline.kt`; Test: `importer/ImportPipelineTest.kt`

**Interfaces:**
- Consumes: `AppleExportReader`, `RecordFactory`
- Produces:
  - `interface RecordWriter { suspend fun write(records: List<Record>): WriteResult }`
  - `data class WriteResult(val written: Int, val failed: Int)`
  - `class HealthConnectWriter(client: HealthConnectClient) : RecordWriter` — 배치 500개, 실패 배치는 1회 재시도 후 `failed`로 집계
  - `data class ImportPreview(val workoutCount: Int, val firstDate: Instant?, val lastDate: Instant?, val routeCount: Int)`
  - `data class ImportProgress(val phase: String, val done: Int, val total: Int)`
  - `data class ImportReport(val newWorkouts: Int, val alreadyThere: Int, val recordsWritten: Int, val failed: Int)`
  - `class ImportPipeline(reader: AppleExportReader, writer: RecordWriter, existingIds: suspend () -> Set<String>) { fun preview(watchOnly: Boolean): ImportPreview; suspend fun run(options: ImportOptions, onProgress: (ImportProgress) -> Unit): ImportReport }`
  - `existingIds`는 이 앱이 이전에 쓴 `ExerciseSessionRecord`의 clientRecordId (Health Connect `readRecords`, dataOriginFilter = 우리 패키지)

- [ ] **Step 1: 실패하는 테스트** (가짜 writer, Task 6 테스트용 zip 헬퍼 재사용)
  - `run_writes_workouts_with_heart_and_route`: written > 0, newWorkouts == 픽스처 운동 수
  - `second_run_counts_already_there`: existingIds에 첫 실행 id 넣고 재실행 → newWorkouts 0, alreadyThere == 운동 수
  - `progress_reaches_total`: 마지막 progress done == total
  - `writer_failure_counted`: 항상 실패하는 writer → failed > 0, 예외 없이 리포트 반환
- [ ] **Step 2:** → FAIL
- [ ] **Step 3:** 구현. 순서: readWorkouts → 기간/옵션 필터 → readRecords(심박 수집, 선택 항목은 바로 버퍼 후 배치 쓰기) → 필요한 경로만 readRoutes → 운동별 forWorkout → write. 운동 심박은 운동별로 모아서 처리(전체 하루 심박은 스트리밍 배치).
- [ ] **Step 4:** → PASS
- [ ] **Step 5:** Commit `feat: import pipeline and health connect writer`

### Task 9: 화면 + 백그라운드 작업

**Files:**
- Create: `importer/ImportWorker.kt`, `ui/ImportViewModel.kt`, `ui/screens/{PickScreen,PreviewScreen,ProgressScreen,DoneScreen}.kt`
- Modify: `ui/MainActivity.kt`, `AndroidManifest.xml`
- Delete: `spike/SpikeWriter.kt`

**Interfaces:**
- Consumes: `ImportPipeline`, `ImportOptions`, `ImportPreview`, `ImportProgress`, `ImportReport`

- [ ] **Step 1:** 매니페스트: `ACTION_VIEW`/`ACTION_SEND` intent-filter (`application/zip`, `application/x-zip-compressed`, `application/octet-stream`)로 "이 앱으로 열기" 지원, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`. 여전히 `INTERNET` 없음.
- [ ] **Step 2:** `ImportWorker` (CoroutineWorker, `setForeground` 진행률 알림 "운동 옮기는 중 12/132"). 입력: zip URI(영구 권한 `takePersistableUriPermission`), 옵션 직렬화.
- [ ] **Step 3:** 화면 흐름 = 설계 5절 스케치: 파일 선택 → 미리보기(운동 수·기간·경로 수, 기간 선택, 항목 체크박스 기본값은 Global Constraints대로, 걸음·수면 켜면 "갤럭시워치와 같이 차면 두 번 기록될 수 있어요" 경고, "애플워치 기록만" 토글) → Health Connect 권한 요청(선택 항목에 맞는 권한만) → 진행 → 완료(새로 추가 / 이미 있음 / 실패, "Samsung Health 열기" 버튼). `NotAppleExportException` → "애플 건강 내보내기 파일이 아니에요" 안내.
- [ ] **Step 4:** Health Connect 미설치/비활성 시 안내(`HealthConnectClient.getSdkStatus`).
- [ ] **Step 5:** `./gradlew assembleDebug testDebugUnitTest` 모두 성공
- [ ] **Step 6:** Commit `feat: import UI, open-with support, background worker`

### Task 10: 실기기 확인 + 전달

- [ ] **Step 1:** 디버그 APK 빌드, 사용자에게 전달
- [ ] **Step 2 (사용자 확인):** 실제 `내보내기.zip`으로: 미리보기 숫자가 맞는지, 가져오기 완료, Samsung Health에 운동·칼로리·거리·심박·경로 표시, **같은 파일 재가져오기 시 새로 추가 0개**
- [ ] **Step 3:** 문제 수정 후 README(설치·사용법, 한국어) 작성, Commit `docs: usage guide`
