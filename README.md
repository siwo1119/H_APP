# 카닥터 AI — 차량 자가진단 AI 정비 어드바이저

Pleos Connect (Android Automotive) IVI 앱. 차량 상태 신호에서 이상을 **추론**하고, 부품 특성과
현재 주행 환경을 함께 따져 **지금 무엇을 해야 하는지**를 4단계로 안내합니다.

> 한 줄 정의 — 차량 도해 위에 고장 부위를 표시하고, 정비 시점(즉시 정차 / 즉시 정비 / 목적지 도착 후 / 주말)을 조언하는 자가진단 앱

---

## 핵심 주장 3가지

| | 내용 |
|---|---|
| **상황 인지형 판단** | 같은 전조등 고장이라도 낮에는 1단계(주말 방문), 밤에는 3단계(즉시 정비)로 판단이 바뀝니다 |
| **DTC 없이 추론** | Vehicle SDK 에는 고장코드 API 가 없습니다. 정상 상태 신호의 **불일치·임계값·편차·추이**에서 이상을 찾아냅니다 |
| **설명 가능한 판단** | 왜 이 등급인지(어떤 환경 요소가 몇 점 반영됐는지)를 화면에 근거로 표시합니다. 등급은 규칙이 정하고 LLM 은 설명만 합니다 |

---

## 빠른 시작

### 사전 조건
- Pleos Connect Emulator v2.1.0 실행 중 (`Pleos_Connect_2.1.0_API34`)
- JDK 17 (AGP 8.7 요구사항 — Android Studio 번들 JBR 25 로는 빌드되지 않습니다)
- Android SDK: platform 34/35, build-tools 34/35, platform-tools

### 빌드 · 설치

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot'
.\gradlew.bat installDebug
adb shell am start -n com.cardoctor.advisor/.MainActivity
```

### LLM 설명 (선택)

키가 없어도 **오프라인 템플릿**으로 전 기능이 동작합니다. AI 생성 설명을 쓰려면
`local.properties` 에 키를 넣으세요 (이 파일은 git 에 올라가지 않습니다).

```properties
llm.apiKey=sk-ant-...
llm.model=claude-opus-5
```

> ⚠️ 배포 시에는 자체 프록시를 두고 앱에서 키를 제거하세요. APK 에 담긴 키는 추출 가능합니다.
> `LlmExplainer(baseUrl = "https://your-proxy/...")` 로 교체하면 됩니다.

---

## 시연 대본 — Virtual Console

심사장에서 **녹화 영상 없이 즉석 재현**하는 것이 이 앱의 가장 강한 데모입니다.

```powershell
adb forward tcp:8088 tcp:8088
start http://127.0.0.1:8088/
```

> ⚠️ Virtual Console 은 **브라우저로 한 번 열어야** 쓰기가 활성화됩니다.
> 열기 전 모든 쓰기는 `{"error":"emulator_inactive"}` (503) 으로 거부됩니다.

| 단계 | Virtual Console 조작 | 앱에서 벌어지는 일 |
|---|---|---|
| 1 | Safety → Telltales → **AFS** `On` | 좌측 전조등 노란색, **2단계 목적지 후 정비** (2점) |
| 2 | 앱 홈에서 **[밤]** 토글 | 같은 고장이 빨간색 + **3단계 즉시 정비** (5점) 로 승격 |
| 3 | 상세 화면 진입 | `기본 +2` / `야간 운행 +3` / `합계 5점` 이 근거로 표시됨 |
| 4 | Telltales → **EngineTemperature** `On` | "냉각 계통 과열" **3단계** — 환경을 아무리 좋게 해도 안 내려감 |
| 5 | Telltales → **TurnSignalLeft** `Blink` | **앱은 변화 없음** — 정보성 표시등은 고장이 아님 |

전체 대본과 복붙용 `curl` 명령은 [`docs/DEMO_SCRIPT.md`](docs/DEMO_SCRIPT.md) 에 있습니다.

> 환경을 수동으로 바꾸면 화면에 **"시연 모드"** 배지가 뜹니다 — 실측값인지 시연값인지
> 보는 사람이 항상 구분할 수 있도록 의도한 설계입니다.

---

## 아키텍처

```
┌─ Pleos Connect IVI (AAOS, Kotlin + Compose) ─────────────────────────┐
│                                                                       │
│  Vehicle SDK 2.3.2 ── 21개 도메인 상태 조회·구독                      │
│         │                                                             │
│         ▼                                                             │
│  ① 신호 정규화    data/vehicle/VehicleSignalRepository                │
│         │          도메인별 응답 → VehicleSnapshot (공통 스키마)      │
│         ▼                                                             │
│  ② 이상 감지      domain/detect/AnomalyDetector                       │
│         │          불일치 / 임계값·편차 / 추이 / 경고등 / 상태 조합   │
│         ▼                                                             │
│  ③ 심각도 엔진    domain/severity/SeverityEngine   ◄── 환경 데이터     │
│         │          기본 심각도(1~3) + Σ환경 가중치(0~3)    │           │
│         ▼                                                  │           │
│  ④ 4단계 권고 ──┬─► 차량 도해 부위 하이라이트              │           │
│                 └─► 정비소 전달용 리포트            data/env/          │
│         │                                     SunCalculator(오프라인) │
│         ▼                                     WeatherClient(Open-Meteo)│
│  ⑤ LLM 설명 ──── 실패 시 ──► 오프라인 템플릿 폴백                     │
└───────────────────────────────────────────────────────────────────────┘
```

**①~④ 는 전부 온디바이스입니다.** 네트워크가 없어도 진단·등급 판정·화면 표시가 동작합니다.
LLM 설명만 뒤따라 채워지는 2단 렌더링 구조입니다.

### 주요 파일

| 경로 | 역할 |
|---|---|
| `domain/detect/AnomalyDetector.kt` | 5가지 추론 패턴. 순수 함수 |
| `domain/detect/TelltaleCatalog.kt` | 경고등 → 진단 항목 매핑 (정보성 표시등 제외) |
| `domain/severity/SeverityEngine.kt` | 등급 결정. **여기만 등급을 정한다** |
| `data/vehicle/VehicleSignalRepository.kt` | Vehicle SDK 래퍼. 미지원 속성은 null 로 흡수 |
| `data/env/SunCalculator.kt` | 일출·일몰 오프라인 계산 (NOAA 약식) |
| `data/llm/LlmExplainer.kt` | 설명 생성. 등급은 건드리지 않음 |
| `data/llm/ExplanationTemplates.kt` | 오프라인 폴백 문구 |
| `ui/car/CarDiagram.kt` | 부위별 색 표시 + 터치. 3D 교체 지점 |
| `platform/DrivingStateMonitor.kt` | AAOS CarUxRestrictions |

---

## 심각도 판정 규칙

```
긴급도 = 기본 심각도(1~3) + Σ 환경 가중치(0~3)
단, 기본 심각도 3(안전 핵심)은 환경과 무관하게 최소 3등급
```

| 긴급도 | 등급 | 권고 |
|---|---|---|
| 6 이상 | 4 즉시 정차 | 안전한 곳에 정차하고 견인·긴급출동을 부르세요 |
| 4~5 | 3 즉시 정비 | 가장 가까운 정비소로 바로 가세요 |
| 2~3 | 2 목적지 후 정비 | 목적지까지는 운행 가능, 오늘 중 점검 권장 |
| 1 | 1 여유 있게 정비 | 주말이나 다음 정기점검 때 방문 |

**환경 가중치** (`SeverityEngine.weightsFor`)

| 조건 | 대상 | 점수 |
|---|---|---|
| 야간 | 등화류 | +3 |
| 비·눈 | 와이퍼 | +3 |
| 비·눈 | 타이어 · 제동 | +2 |
| 안개 | 등화류 | +2 |
| 고속도로 | 타이어 · 제동 · 조향 | +1 |
| 장거리 | 엔진·냉각 · 배터리 | +1 |
| 혹서·혹한 | 엔진·냉각 | +1 |

---

## 진단 항목 (SDK 실측 기준)

Vehicle SDK 2.3.2 의 AAR 을 디컴파일해 실제 메서드 시그니처를 확인한 뒤 확정한 목록입니다.

| 항목 | 판정 방법 | SDK API | 기본 심각도 |
|---|---|---|---|
| 전조등·미등·안개등 미점등 | 명령 ≠ 실제 | `getLowBeamLightSwitch` ↔ `getCurrentLowBeamLightState` | 1 |
| 와이퍼 작동 불량 | 명령 ≠ 실제 | `getCurrentWiperSwitch` ↔ `getCurrentWiperState` | 1 |
| 타이어 공기압 저하 | 임계값 미만 | `getTirePressure` / `getLowTirePressure` | 2 |
| 타이어 공기압 편차 | 4륜 편차 >15% | `getTirePressure` × 4 | 2 |
| 엔진오일 부족 | 레벨 상태 | `getEngineOilLevelState` | 2~3 |
| 브레이크액 부족 | 레벨 상태 | `getBrakeFluidLevelState` | **3** |
| 변속기 오일 부족 | 레벨 상태 | `getTransmissionOilLevelState` | 2~3 |
| 워셔액 부족 | 경고 플래그 | `hasLowWasherFluidWarning` | 1 |
| 경고등 36종 | 점등·깜빡임 | `getTelltalesInfo` | 1~3 |
| 전원 차단 경고 | 경고 플래그 | `hasPowerDownWarningIndicator` | 3 |
| 안전벨트 미착용 주행 | 상태 조합 | `hasSeatBeltNotBuckledWarning` + `getCurrentGearSelectionState` | 2 |
| 도어 열림 주행 | 상태 조합 | `getCurrentDoorPosition` + 기어/속도 | 2 |
| 소모품 교체 주기 | 누적 주행거리 | `getCurrentDistanceDriven` | 1 |

경고등은 **정비 판단에 의미 있는 36종만** 다룹니다. 방향지시등·하이빔 표시등 같은 정보성
표시등은 `TelltaleCatalog.INFORMATIONAL` 로 의도적으로 제외했습니다.

---

## 테스트

```powershell
.\gradlew.bat testDebugUnitTest
```

기획안의 시나리오 A·B·C 가 실제로 그 등급을 내는지 검증합니다. 가중치 표를 고치면
`ScenarioTest` 가 먼저 깨지므로, 규칙 변경이 조용히 시연을 망가뜨리지 않습니다.

---

## 알려진 제약

| 제약 | 내용 | 대응 |
|---|---|---|
| **차속 API 없음** | Vehicle SDK 에 차량 속도 API 가 없습니다 (크루즈 목표 속도만 존재) | Odometer 델타로 추정. 실차에서는 `android.car` `PERF_VEHICLE_SPEED` 로 교체 권장 |
| **3D 미구현** | 부위별 메시가 분리된 차량 glTF 에셋이 없습니다 | 동작하는 2D 탑다운 도해로 구현. 교체 지점은 `ui/car/CarDiagram.kt` 한 파일 |
| **위치 고정** | 에뮬레이터에 GPS 가 없고 SDK 도 위치를 주지 않습니다 | 서울 좌표 고정. 실차에서는 LocationManager 로 교체 (`EnvironmentRepository`) |
| **에뮬 ≠ 실차** | 에뮬레이터 v2.1.0 은 **타이어 공기압·엔진오일·브레이크액·변속기오일·워셔액·안개등·안전벨트 경고를 미지원**합니다 (실측 — `docs/SDK_API_NOTES.md` §9) | 모든 SDK 읽기가 실패를 null 로 흡수 — 미지원 항목만 진단에서 빠지고 나머지는 동작합니다. 실차에서는 가용 범위가 달라집니다 |
| **권한** | `CAR_*` 만 선언하면 `getTirePressure` / `getTelltalesInfo` 가 조용히 실패합니다 | `PRIVATE_CAR_*` 도 함께 선언 (둘 다 `prot=normal`). 메서드별 요구 권한표는 `docs/SDK_API_NOTES.md` §8 |
| **추이 분석 범위** | 시연 시간 안에 의미 있는 배터리 추이를 모으기 어렵습니다 | 결정론적인 주행거리 기반 소모품 주기만 구현 |

---

## 기술 스택

- Kotlin 2.0.21 · Jetpack Compose (BOM 2024.10.01) · Material 3
- Pleos Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`
- minSdk **28** (차량 사이버보안 요건) · compileSdk/targetSdk **35**
- AGP 8.7.3 · Gradle 8.11.1 · **JDK 17**
- Ktor (날씨·LLM) · kotlinx.serialization · DataStore

---

## 안전 고지

본 앱의 안내는 참고용입니다. 등급 판정은 규칙 기반이며 LLM 은 설명만 생성하지만,
**최종 판단은 정비 전문가에게** 받으시기 바랍니다.
