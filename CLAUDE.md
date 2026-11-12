# 카닥터 AI — Claude Code 작업 지침

Pleos Connect (Android Automotive) IVI 앱. 차량 상태 신호에서 이상을 추론하고,
부품 특성과 주행 환경을 함께 따져 정비 시점을 4단계로 안내한다.

**처음 보는 세션이라면** `README.md` → `docs/SDK_API_NOTES.md` 순으로 읽으면 맥락이 잡힌다.
환경 구축이 안 된 PC라면 `docs/HANDOFF.md` 부터.

---

## 빌드

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot'
.\gradlew.bat testDebugUnitTest    # 18건
.\gradlew.bat installDebug
adb shell am start -n com.cardoctor.advisor/.MainActivity
```

**JDK 17 이어야 한다.** Android Studio 번들 JBR 은 OpenJDK 25 라 AGP 8.7 과 호환되지 않는다.
`JAVA_HOME` 을 JBR 로 둔 채 빌드하면 실패한다.

Gradle 을 PowerShell 에서 `cmd /c "cd ... && gradlew.bat"` 로 부르면 작업 디렉터리가
전달되지 않는 경우가 있다. Git Bash 에서 `./gradlew` 로 부르는 쪽이 안정적이다.

---

## 설계 원칙 — 깨뜨리지 말 것

**등급은 규칙이 정하고, LLM 은 설명만 만든다.**
`domain/severity/SeverityEngine` 이 긴급도와 4단계 권고를 확정한 뒤 `data/llm/LlmExplainer`
에 넘긴다. LLM 이 등급을 바꿀 수 있는 경로를 만들지 말 것. 안전 조언이라 타협 대상이 아니다.

**①~④ 는 온디바이스다.**
신호 수집 → 이상 감지 → 등급 판정 → 화면 반영까지 네트워크 없이 동작해야 한다.
LLM 설명과 날씨만 외부 의존이며, 둘 다 실패해도 진단은 그대로 돌아간다 (2단 렌더링).

**SDK 읽기 실패는 예외가 아니라 null 이다.**
`data/vehicle/VehicleSdkExt.kt` 의 `sdkCall` 이 전부 흡수한다. 에뮬레이터와 실차의 가용
속성이 다르므로, 미지원 속성 하나가 전체 스캔을 중단시키면 안 된다.

**순수 함수는 순수하게 유지한다.**
`AnomalyDetector`, `SeverityEngine`, `ServiceReportBuilder` 는 안드로이드 의존이 없다.
단위 테스트가 여기에 걸려 있으니 Context 를 끌어들이지 말 것.

---

## 구조

```
domain/detect/AnomalyDetector.kt     5가지 추론 패턴 (순수)
domain/detect/TelltaleCatalog.kt     경고등 36종 → 진단 항목 매핑
domain/severity/SeverityEngine.kt    등급 결정 — 여기만 등급을 정한다 (순수)
data/vehicle/VehicleSignalRepository Vehicle SDK 래퍼
data/env/                            일출·일몰(오프라인) + 날씨(Open-Meteo)
data/llm/                            설명 생성 + 오프라인 템플릿 폴백
data/report/ServiceReport.kt         정비소 리포트 (확장 B)
platform/AlertManager.kt             단계적 알림 (확장 A)
platform/DrivingStateMonitor.kt      AAOS CarUxRestrictions
ui/car/CarDiagram.kt                 차량 도해 — 3D 교체 지점
```

---

## 플랫폼 제약 (반복해서 밟기 쉬운 것들)

| 제약 | 내용 |
|---|---|
| **DTC API 없음** | Vehicle SDK 21개 도메인이 전부 상태 조회·구독. 고장코드가 없으니 추론해야 한다 |
| **차속 API 없음** | 크루즈 *목표* 속도만 있다. Odometer 델타로 추정 중 |
| **권한** | `CAR_*` 만 선언하면 일부 API 가 "need Permission" 으로 조용히 실패. `PRIVATE_CAR_*` 도 선언해야 한다 |
| **앱은 user 10** | `adb shell pm grant` 에 `--user 10` 을 빼면 권한이 안 먹는다 |
| **Virtual Console** | 브라우저로 한 번 열어야 쓰기가 활성화된다. 안 열면 전부 503 |
| **VHAL 주입 불가** | `cmd car_service inject-vhal-event` 는 user 빌드라 차단. Virtual Console 이 유일한 경로 |
| **에뮬 ≠ 실차** | 타이어 공기압·오일류·안개등·안전벨트 경고는 이 이미지에 없다 (`SDK_API_NOTES.md` §9) |

---

## 고장 상황 만들기

```bash
VC=http://127.0.0.1:8088
# 먼저 브라우저로 $VC 를 한 번 열어야 한다

curl -X PUT "$VC/vehicle/telltales/simulator?type=AFS&state=On&color=Yellow"
#   파라미터는 area 가 아니라 type 이고, color 가 필수다
```

전체 목록은 `docs/DEMO_SCRIPT.md` 부록.

---

## 변경 시 주의

- 환경 가중치표(`SeverityEngine.weightsFor`)를 고치면 `ScenarioTest` 가 먼저 깨진다.
  시나리오 A·B·C 가 의도대로 동작하는지 보장하는 장치이므로, 테스트를 고치기 전에
  규칙 변경이 맞는지 다시 생각할 것.
- `TelltaleCatalog.INFORMATIONAL` 에서 항목을 빼면 방향지시등 같은 표시등이 고장으로 잡힌다.
- `local.properties` 는 절대 커밋하지 말 것 (API 키 자리).
