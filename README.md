# 헬스 브릿지 ⌚➡️💚

애플워치로 기록한 운동을 갤럭시의 **Samsung Health**에서 볼 수 있게 옮겨주는 안드로이드 앱이에요.
아이폰 건강 앱의 "모든 건강 데이터 내보내기" 파일을 읽어서 **Health Connect**에 넣고, Samsung Health가 그걸 가져가요.

## 옮겨지는 것
| 항목 | Samsung Health 표시 (Galaxy S25+ 실험) |
|---|---|
| 운동 기록 (종류, 시간, 거리) | ✅ |
| 운동 중 심박수 (평균·최대·차트·심박 구간) | ✅ |
| 칼로리 (운동·총 칼로리) | ✅ (v0.3) |
| GPS 경로 | ❌ Samsung Health가 다른 앱 경로를 지도로 안 보여줌 (Health Connect에는 저장됨) |
| 하루 전체 심박, 안정 시 심박, 체중, 걸음 수, 수면 | 선택 항목 (기본 꺼짐) |
| HRV | 옮기지 않음 (애플·삼성 계산 방식이 다름) |

## 사용법
1. 아이폰 **건강** 앱 → 오른쪽 위 프로필 → **모든 건강 데이터 내보내기**
2. 만들어진 `내보내기.zip`을 갤럭시로 보내기 (퀵쉐어, 드라이브 등)
3. 헬스 브릿지에서 **내보내기 zip 고르기** (또는 파일 앱에서 "헬스 브릿지로 열기")
4. 미리보기에서 기간과 항목 고르기 → **옮기기 시작**
5. Health Connect 권한 허용 → 끝나면 **Samsung Health 열기**

- 같은 파일을 다시 넣어도 **새 운동만** 추가돼요 (중복 없음).
- 빠진 심박·칼로리를 채우려면 **"이미 옮긴 운동도 다시 쓰기"**를 켜고 한 번 더 하세요.
- 내보내기 파일 없이 표시만 확인하려면 첫 화면의 **🧪 샘플 운동 넣어보기**를 누르세요.

## 설치 · 업데이트 📲
- [Releases](https://github.com/mixiboo/HealthBridge/releases/latest)에서 `healthbridge-vX.Y.Z.apk`를 받아 설치해요.
- 그다음부터는 앱 첫 화면에 새 버전이 뜨면 **업데이트하기**만 누르면 돼요 (처음 한 번은 "이 출처 허용"이 필요해요).

## 개인정보 🔒
건강 데이터는 **폰 밖으로 보내지 않아요.** 인터넷은 GitHub에서 새 버전을 확인하고 받을 때만 써요 (`update/UpdateChecker.kt` 한 곳).

## 개발
- Kotlin + Jetpack Compose, Health Connect `connect-client 1.1.0`, WorkManager
- 빌드: `./gradlew assembleDebug` / 테스트: `./gradlew testDebugUnitTest`
- 릴리스: `app/build.gradle.kts`의 `versionName`을 올리고 같은 이름의 태그(`v0.5.0`)를 푸시하면 GitHub Actions가 서명된 APK를 릴리스에 올려요. 서명 키는 저장소 Secrets(`HB_KEYSTORE_BASE64`, `HB_KEYSTORE_PASSWORD`, `HB_KEY_ALIAS`, `HB_KEY_PASSWORD`)에 있어요.
- 설계: `docs/2026-11-23-apple-to-samsung-health-design.md`
