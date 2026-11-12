# 다른 PC에서 이어서 작업하기

## 요약

| 옮길 것 | 방법 | 소요 |
|---|---|---|
| **코드·문서** | `git clone` | 1분 |
| **빌드 도구** (JDK 17, Android SDK) | winget + sdkmanager | 15분 |
| **에뮬레이터** (Pleos 시스템 이미지 19GB) | `docs/ENV_SETUP.md` 따라가기 | 30~60분 |
| **LLM API 키** (선택) | 직접 입력 | 1분 |

코드는 전부 GitHub에 있습니다. **진짜 작업은 환경 구축**이고, 그 절차는 `docs/ENV_SETUP.md`에
정정 사항까지 반영해 두었습니다.

---

## 1. 코드 받기

```powershell
cd $env:USERPROFILE
git clone https://github.com/siwo1119/H_APP.git
cd H_APP
```

> 경로에 **한글이나 공백이 없는 곳**에 두세요. OneDrive 동기화 폴더도 피하는 편이 좋습니다
> (Gradle 빌드 산출물이 계속 동기화돼 느려집니다). `C:\Users\<USER>\H_APP` 를 권장합니다.

---

## 2. `local.properties` 만들기

이 파일은 git에 올라가지 않습니다 (PC마다 경로가 다르고, API 키가 들어갈 자리라서).
저장소 루트에 직접 만드세요.

```properties
sdk.dir=C:/Users/<사용자명>/AppData/Local/Android/Sdk

# LLM 설명 (선택). 비워 두면 오프라인 템플릿으로 전 기능이 동작합니다.
# llm.apiKey=
# llm.model=claude-opus-5
```

> 역슬래시(`\`)를 쓰려면 `C\:\\Users\\...` 처럼 이스케이프해야 합니다.
> **슬래시(`/`)를 쓰는 편이 안전합니다.**

---

## 3. 환경 구축

`docs/ENV_SETUP.md` 를 그대로 따라가면 됩니다. Claude Code 에 이렇게 말하면 자동으로 해줍니다.

```
docs/ENV_SETUP.md 읽고 환경 구축해줘
```

**UAC 창이 2번 뜹니다** (Android Studio, JDK 17). 화면에 뜨면 "예"를 눌러주세요.

### 꼭 기억할 것

| 함정 | 내용 |
|---|---|
| **JDK 17 필수** | Android Studio 번들 JBR 은 OpenJDK 25 라 AGP 8.7 과 비호환. 빌드가 안 됩니다 |
| **라이선스 승인** | PowerShell 파이프 방식은 PS 5.1 에서 동작 안 함. Git Bash `yes \| sdkmanager --licenses` |
| **AVD 디바이스 프로필** | `--device Pleos_Connect_v2_0` 를 빠뜨리면 1080x600 으로 만들어져 UI 가 겹칩니다 |
| **콜드 부팅 필수** | quick boot 으로 바꾸면 VHAL 을 쓸 수 없습니다 |

---

## 4. 빌드 · 실행

```powershell
cd $env:USERPROFILE\H_APP

# JDK 17 지정 (버전 번호는 설치된 것에 맞추세요)
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot'

.\gradlew.bat testDebugUnitTest    # 18건 통과하면 코드가 정상
.\gradlew.bat installDebug
adb shell am start -n com.cardoctor.advisor/.MainActivity
```

에뮬레이터 실행은 `setup\launch_avd.ps1` 을 홈 디렉터리에 복사해 쓰거나, 저장소에서 바로 실행합니다.

```powershell
copy setup\sdk_env.ps1   $env:USERPROFILE\
copy setup\launch_avd.ps1 $env:USERPROFILE\
cd $env:USERPROFILE
.\launch_avd.ps1
```

---

## 5. 동작 확인 (5분)

```powershell
# 1) 에뮬레이터 부팅 확인
adb shell wm size            # Physical size: 2560x1440
adb shell getprop ro.build.display.id   # RELEASE.release_connect_v2.1.0 test-keys

# 2) Virtual Console 열기 — 브라우저로 한 번 열어야 쓰기가 활성화됩니다
adb forward tcp:8088 tcp:8088
start http://127.0.0.1:8088/

# 3) 알림 권한 (앱은 user 10 으로 실행됩니다)
adb shell pm grant --user 10 com.cardoctor.advisor android.permission.POST_NOTIFICATIONS

# 4) 고장 주입 → 앱이 반응하는지
curl -X PUT "http://127.0.0.1:8088/vehicle/telltales/simulator?type=AFS&state=On&color=Yellow"
```

앱 홈에 "적응형 전조등 이상"이 뜨고, `[낮]` / `[밤]` 토글로 등급이 2↔3단계로 바뀌면 정상입니다.

---

## 6. Claude Code 로 이어서 작업하기

**대화 내용은 PC 간에 옮겨지지 않습니다.** 대신 이 저장소의 문서가 그 역할을 합니다.
새 PC에서 Claude Code 를 열고 저장소 루트에서 시작하면 `CLAUDE.md` 를 자동으로 읽습니다.

맥락이 필요하면 이렇게 말하면 됩니다.

```
README.md 와 docs/ 아래 문서들 읽고 현재 상태 파악해줘
```

| 문서 | 들어 있는 것 |
|---|---|
| `CLAUDE.md` | 프로젝트 구조·규칙·주의사항 (Claude 가 자동으로 읽음) |
| `README.md` | 앱 개요, 판단 규칙, 진단 항목, 확장 기능 |
| `docs/ENV_SETUP.md` | 환경 구축 런북 + 정정 사항 |
| `docs/SDK_API_NOTES.md` | **SDK 실측 기록** — API·권한·가용 속성·Virtual Console HTTP API |
| `docs/DEMO_SCRIPT.md` | 시연 대본 + 복붙용 명령 |
| `docs/PLAN_DELTA.md` | 기획안 → 구현 변경 이력과 근거 |

---

## 7. 저장소에 없는 것

| 항목 | 어디 있나 | 필요한가 |
|---|---|---|
| 기획안 PDF 3건 | 바탕화면 `차량용 앱 개발` 폴더 | 발표 준비에 필요하면 USB·클라우드로 |
| 발표 PPT · 개요 PDF | 같은 폴더 | 발표용 |
| 검증 스크린샷 | 이미 PDF·PPT 안에 포함됨 | 불필요 |
| LLM API 키 | 없음 (처음부터 넣지 않음) | 선택 |

> 기획 문서를 저장소에 넣지 않은 이유 — 내부 자료 반출 제한 가능성 때문입니다
> (기획안 리스크 표의 "대회·회사 보안 규정" 항목). 공개 저장소이므로 판단해서 넣으세요.

---

## 8. 양쪽 PC를 오가며 작업할 때

```powershell
# 작업 시작 전
git pull

# 작업 끝나고
git add -A
git commit -m "..."
git push
```

`local.properties` 는 각 PC 에 따로 있고 git 이 추적하지 않으므로 충돌하지 않습니다.
