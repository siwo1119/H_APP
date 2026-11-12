# Pleos Vehicle SDK 2.3.2 — 실측 API 노트

> 개발 방향 제언 문서는 "Vehicle SDK 의 개별 메서드 시그니처와 에뮬레이터에서의 실제 속성
> 가용성은 직접 확인하지 않았다"고 명시했습니다. 이 문서는 그 공백을 메운 **실측 기록**입니다.
>
> 방법: `ai.pleos.playground:Vehicle:2.3.2` AAR 을 받아 `classes.jar` 을 `javap` 로 전수 덤프.
> 확인 일자: 2026-11-12 / 에뮬레이터 Pleos Connect v2.1.0 (Vehicle Service 2.3.22, code 74)

---

## 1. 검증 과제 결과 (제언 문서 §7)

| # | 과제 | 결과 | 근거 |
|---|---|---|---|
| 1 | Light 명령 vs 실제 점등을 구분해 읽을 수 있는가 | ✅ **가능** | `getLowBeamLightSwitch()` → `LightSwitch` 와 `getCurrentLowBeamLightState()` → `LightState` 가 **별도 API** |
| 2 | Virtual Console 로 시나리오 재현 | ✅ **실증 완료** | 경고등 주입 → 앱이 감지 → 등급 산정까지 end-to-end 확인. HTTP API 는 §10 |
| 3 | 매핑 표 도메인이 실제로 값을 주는가 | ✅ **확인 완료** | 실기기 logcat 으로 전수 확인 — §9 표. 등화·와이퍼·경고등·기어·주행거리는 동작, 타이어·오일류는 이 이미지에서 미지원 |
| 4 | Safety 에서 경고등 상태를 읽을 수 있는가 | ✅ **가능** | `getTelltalesInfo()` → `Telltales`, `registerTelltales(TelltaleInfoListener)` |
| 5 | 내부 AI 패키지 접근 가능 여부 | 미확인 | 우선순위 낮음. 외부 API + 템플릿 폴백으로 진행 |

**과제 1·4 가 모두 통과**했으므로, 제언 문서가 우려한 "대표 시나리오를 교체해야 할 가능성"은
발생하지 않았습니다. 시나리오 A·B(전조등)를 그대로 유지합니다.

---

## 2. 제언 문서와 달랐던 점

### 2-1. 도메인은 19개가 아니라 **21개**

`Vehicle` 진입점이 노출하는 getter 기준:

```
EvBattery, HVAC, Door, Seat, Window, Display, Odometer, Light, Safety,
CarInfo, DrivingMode, Gear, SteeringWheel, Toll, TurnSignal, Wiper,
Tire, SideMirror, Brake, Fuel          ← 20개
+ Vehicle 자체의 initialize/release
```

제언 문서 목록에 없던 **`Gear`** 와 **`Brake`** 가 별도 도메인으로 존재합니다.

### 2-2. 냉각수 과열은 **대체가 필요 없습니다**

제언 문서는 "냉각수 과열은 SDK 19개 도메인에 대응 항목이 없어 EV battery 로 대체 권장"
이라고 했지만, 실제로는 Telltale 로 직접 읽을 수 있습니다.

```
TelltaleType.ENGINE_TEMPERATURE    — 엔진 과열
TelltaleType.ENGINE_OIL_PRESSURE   — 엔진 오일 압력
TelltaleType.ENGINE                — 엔진 점검
```

기획안의 "냉각수 과열 → 기본 심각도 3" 항목이 **원안 그대로 살아있습니다.**

### 2-3. 차속 API 가 **없습니다** ⭐ 새 제약

제언 문서는 "도로 종류는 차속·ADAS 로 추정"을 전제했지만, 21개 도메인 전수 검색 결과
차량 속도를 주는 API 가 없습니다. 속도 관련 메서드는 두 개뿐입니다.

```
DrivingMode.getCruiseControlTargetSpeed()  — 크루즈 "목표" 속도 (실제 속도 아님)
HVAC.getHvacFanSpeed()                     — 송풍 세기
```

**대응**: `Odometer.getCurrentDistanceDriven()` 델타로 속도를 추정합니다
(`VehicleSignalRepository.estimateSpeed`). 실차에서는 `android.car` 의
`PERF_VEHICLE_SPEED` 로 교체하는 것이 정확합니다.

### 2-4. 권한 이름이 다릅니다

제언 문서는 `CAR_* / CONTROL_CAR_*` 가 `protectionLevel="normal"` 이라고 했는데,
실제 상수는 **`pleos.car.permission.*`** 네임스페이스입니다.

```kotlin
PleosPermission.CAR_LIGHTS   = "pleos.car.permission.CAR_LIGHTS"
PleosPermission.CAR_SAFETY   = "pleos.car.permission.CAR_SAFETY"
// CONTROL_* 는 쓰기용. 진단 전용 앱이라 선언하지 않음
// PRIVATE_* 네임스페이스도 별도 존재
```

### 2-5. SDK 가 Vehicle Service 최소 버전을 요구합니다

AAR 매니페스트에 명시돼 있습니다.

```xml
<meta-data android:name="ai.pleos.playground.service.vehicle.min_version_name"
           android:value="2.3.13" />   <!-- code 65 -->
```

에뮬레이터 탑재 버전은 **2.3.22 (code 74)** 라 충족합니다.

---

## 3. API 호출 패턴

모든 메서드가 `(onComplete, onFailed)` 콜백 쌍을 받습니다.

```kotlin
val vehicle = Vehicle(context.applicationContext)
vehicle.initialize()

vehicle.light.getCurrentLowBeamLightState(
    { state -> /* LightState */ },
    { e -> /* Exception */ },
)

vehicle.release()
```

영역이 필요한 API 는 `Area` 객체를 vararg 로 만듭니다.

```kotlin
WheelArea(WheelValue.LEFT_FRONT)
SeatArea(SeatValue.ROW_1_LEFT)
DoorArea(DoorValue.ROW_1_LEFT)
WindowArea(WindowValue.FRONT_WINDSHIELD)   // 와이퍼는 WindowArea 를 받는다
```

> **실패는 예외가 아니라 null 로 흡수합니다** (`data/vehicle/VehicleSdkExt.kt`).
> 에뮬레이터와 실차의 가용 속성이 다르므로, 미지원 속성 하나가 전체 스캔을 깨뜨리면 안 됩니다.

---

## 4. 상수 값 (실측)

```
LightState        : OFF ON DAYTIME_RUNNING
LightSwitch       : OFF ON DAYTIME_RUNNING AUTOMATIC
WiperState        : OTHER OFF ON SERVICE
WiperSwitch       : OTHER OFF MIST INTERMITTENT_LEVEL_1..5 CONTINUOUS_LEVEL_1..5 AUTO SERVICE
OilLevelState     : LOW HIGH NORMAL ERROR CRITICALLY_LOW
VehicleGear       : GEAR_UNKNOWN GEAR_NEUTRAL GEAR_REVERSE GEAR_PARK GEAR_DRIVE GEAR_1..9
TelltaleState     : OFF ON BLINK
WheelValue        : UNKNOWN LEFT_FRONT RIGHT_FRONT LEFT_REAR RIGHT_REAR
SeatValue         : ROW_1_LEFT/CENTER/RIGHT, ROW_2_*, ROW_3_*
DoorValue         : ROW_1_LEFT ROW_1_RIGHT ROW_2_LEFT ROW_2_RIGHT ROW_3_LEFT ROW_3_RIGHT HOOD REAR
WindowValue       : FRONT_WINDSHIELD REAR_WINDSHIELD ROW_1_* ROW_2_* ROOF_TOP_1 ROOF_TOP_2
AccessType        : NONE READ WRITE READ_WRITE
```

### 반환 타입 주의

| 메서드 | 반환 |
|---|---|
| `getEvBatteryStateOfCharge` | **Int** (Float 아님) |
| `getFuelPercent` | **Int** |
| `getCurrentDoorPosition` | **Int** |
| `getTirePressure` / `getLowTirePressure` | Float |
| `getCurrentDistanceDriven` / `getRangeRemainingDistance` | Float |
| `getCurrentSteeringWheelAngle` | Float |

---

## 5. Telltale 전체 목록 (60종)

```
PARKING_BRAKE, ELECTRONIC_PARKING_BRAKE, SEAT_BELT, DOOR_OPEN, ABS, MDPS, TPMS,
LOW_VOLT_BATTERY, ESC, ESC_OFF, AIRBAG, AFS, MASTER_WARNING, AUTO_HOLD,
AUTO_HIGH_BEAM, HEADLAMP_LOW, HEADLAMP_HIGH, POSITION_LAMP, FRONT_FOG_LAMP,
REAR_FOG_LAMP, ENGINE_OIL_PRESSURE, ENGINE_TEMPERATURE, EMERGENCY_CALL, EV_SYSTEM,
POWER_DOWN, RBS, IMMOBILIZER, ENGINE, LED_HEADLAMP, PREHEAT, PARTICLE_FILTER,
H2_SENSOR, H2_LEAK, PASSENGER_AIRBAG_OFF, PASSENGER_AIRBAG_ON, TURN_SIGNAL_LEFT,
TURN_SIGNAL_RIGHT, ICY_ROAD, OVER_SPEED, WATER_SEPARATOR, SCR, LOW_EV_BATTERY,
HEV_SYSTEM, FCEV_SYSTEM, EREV_SYSTEM, LPI, EV_CHARGING_CONNECTOR, AWD_LOCK,
HEV_MODE, ISG, DBC, REV, XEV_READY, I_PEDAL, LOW_FUEL, LOW_FUEL_FCEV,
EV_BATTERY_CONDITIONING, TRUNK_OPEN, CHILD_PRESENCE_DETECTION_OFF, ...
```

`TelltaleData` 는 `type / state / color / priority / warningCount` 를 가집니다.

이 중 **36종만** 진단 대상으로 다룹니다 (`TelltaleCatalog`). 방향지시등·하이빔 표시등 같은
정보성 표시등은 점등되어도 고장이 아니므로 `INFORMATIONAL` 로 제외했습니다 — 이걸 걸러내지
않으면 깜빡이 켤 때마다 "이상 감지"가 뜹니다.

---

## 6. 에뮬레이터 확인 결과

```
ai.pleos.playground.service.vehicle   2.3.22 (74)   ← SDK 요구 2.3.13(65) 충족
ai.pleos.hardware.vehicle.emulator    14 (34)
ai.pleos.playground.service.adas      4.3.1 (14)
ai.pleos.connectwebconsole            14 (34)       ← Virtual Console
```

---

## 7. 재현 방법

```bash
curl -O https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar
unzip -o Vehicle-2.3.2.aar -d aar
unzip -o aar/classes.jar -d classes
javap -cp classes ai.pleos.playground.vehicle.Vehicle
javap -cp classes ai.pleos.playground.vehicle.api.Safety
```

---

## 8. 권한 함정 ⭐ 실기기에서 밟은 것

`uses-permission` 을 `CAR_*` 만 선언하면 일부 API 가 **조용히 실패**합니다.

```
getTirePressure need Permission
getTelltalesInfo need Permission
```

원인: **요구 권한이 메서드 단위로 다릅니다.** `@RequiresPermission` 어노테이션을 전수 확인한 결과:

| 메서드 | 요구 권한 |
|---|---|
| `Tire.getTirePressure` | **`PRIVATE_CAR_SAFETY`** |
| `Tire.getLowTirePressure` | `CAR_SAFETY` |
| `Safety.getTelltalesInfo` | `CAR_SAFETY` (+ 도메인 접근에 `PRIVATE_CAR_SAFETY` 필요) |
| `Light.*` 읽기 | `CAR_LIGHTS` |
| `Odometer.*` / `Gear.*` / `SteeringWheel.*` | `CAR_DRIVE` |
| `Wiper.*` 읽기 | `CAR_WINDOWS` |
| `EvBattery.*` / `Fuel.*` | `CAR_ENERGY` |
| `Door.*` 읽기 | `CAR_DOORS` |

**대응**: `CAR_*` 와 `PRIVATE_CAR_*` 를 **둘 다** 선언합니다. 둘 다 `prot=normal` 이라
런타임 대화 상자 없이 설치 시 자동 부여됩니다.

```
adb shell dumpsys package permission pleos.car.permission.PRIVATE_CAR_SAFETY
  → prot=normal
```

어노테이션 확인 방법:

```bash
javap -cp classes -v ai.pleos.playground.vehicle.api.Tire \
  | grep -B12 'value="pleos.car.permission'
```

---

## 9. 에뮬레이터 실측 가용 속성 (검증 과제 3 답)

Pleos Connect Emulator v2.1.0 에서 실제로 **값을 주는** 것과 **아닌** 것입니다.
(앱 실행 후 logcat 의 `VehicleSdk` 태그로 확인)

### ✅ 동작함

| 항목 | API |
|---|---|
| 로우빔 / 하이빔 / 미등 — 명령·실제 | `getLowBeamLightSwitch` / `getCurrentLowBeamLightState` 등 |
| 와이퍼 — 명령·실제 | `getCurrentWiperSwitch` / `getCurrentWiperState` |
| 경고등 | `getTelltalesInfo` |
| 전원 차단 경고 | `hasPowerDownWarningIndicator` |
| 도어 위치 | `getCurrentDoorPosition` |
| 기어 | `getCurrentGearSelectionState` |
| 누적 주행거리 / 주행 가능 거리 | `getCurrentDistanceDriven` / `getRangeRemainingDistance` |
| EV 충전 상태 | `getEvBatteryStateOfCharge` |
| 조향각 | `getCurrentSteeringWheelAngle` |

### ❌ 이 이미지에서 미지원 (`property ID is not in carPropertyConfig list`)

| 항목 | 속성 |
|---|---|
| 전/후방 안개등 | `FRONT_FOG_LIGHTS_*`, `REAR_FOG_LIGHTS_*` |
| 타이어 공기압 | `TIRE_PRESSURE` |
| 엔진 오일 레벨 | `ENGINE_OIL_LEVEL` |
| 브레이크액 레벨 | `0x21400730` |
| 변속기 오일 레벨 | `0x21400731` |
| 워셔액 경고 | `0x2120071e` |
| 안전벨트 미착용 경고 | `0x25200709` |
| 연료 잔량 | `0x21400be5` |

> 타이어 공기압은 Virtual Console 카탈로그에서도 `"domain":"misc", "visible":false` 로
> 숨겨져 있습니다 — 이 이미지에서는 배선되지 않은 항목입니다.
>
> **코드는 이 차이를 오류로 보지 않습니다.** 미지원 속성은 null 로 흡수되어 해당 진단
> 항목만 빠지고, 나머지는 그대로 동작합니다. 실차에서는 가용 범위가 달라집니다.

---

## 10. Virtual Console HTTP API (문서에 없는 내용)

Virtual Console(`http://127.0.0.1:8088/`)은 UI 뿐 아니라 **HTTP API** 를 제공합니다.
시연 자동화나 통합 테스트에 쓸 수 있습니다.

### ⚠️ 먼저 브라우저로 한 번 열어야 합니다

```
{"error":"emulator_inactive"}   ← 503
```

`http://127.0.0.1:8088/` 를 **브라우저에서 열면** `emulator_active: true` 로 바뀌고 쓰기가 됩니다.
CLI 로만 접근하면 계속 503 입니다.

### 엔드포인트

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/vehicle/catalog` | 전체 기능·도메인·제어 스키마 (약 36KB) |
| GET | `/vehicle/state` | 현재 전체 상태 |
| **PUT** | `/vehicle/telltales/simulator` | 경고등 주입 |
| **PUT** | `/vehicle/tire-pressure` | 타이어 공기압 |
| **PUT** | `/vehicle/lights` | 등화 |
| **PUT** | `/vehicle/gear` | 기어 |
| **PUT** | `/vehicle/controls` | 속도·가속·제동·조향 |

쓰기는 전부 **PUT + 쿼리 파라미터**입니다. 파라미터 이름은 카탈로그의
`control.field` / `control.area_field` / `control.params` 를 봐야 합니다.

```bash
# 경고등 — area 가 아니라 type, color 도 필수
curl -X PUT "http://127.0.0.1:8088/vehicle/telltales/simulator?type=EngineTemperature&state=On&color=Red"

# 상태 확인
curl -s http://127.0.0.1:8088/vehicle/state | grep -o '"EngineTemperature":[^}]*'
```

지원 값: `states = [Off, On, Blink]`, `colors = [White, Green, Blue, Yellow, Red, Gray]`,
`types` 37종.

> `adb shell cmd car_service inject-vhal-event` 는 **user 빌드라 차단**됩니다
> (`SecurityException: requires non-user build`). Virtual Console 이 유일한 주입 경로입니다.

### 덤: 3D 차량 모델

Virtual Console 이 Three.js 로 glTF 차량 모델을 띄웁니다.

```
http://127.0.0.1:8088/glb/perc_Car.glb
http://127.0.0.1:8088/glb/perc_Suv.glb
```

향후 2D 도해를 3D 로 교체할 때 에셋 출처 후보입니다.
(라이선스·반출 가능 여부는 별도 확인 필요)
