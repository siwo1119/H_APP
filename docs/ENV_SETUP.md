# Pleos Connect 개발 환경 구축 런북

> **이 파일을 Claude CLI에 읽히고 "이 런북대로 환경 구축해줘"라고 하면 됩니다.**
>
> 대상: Pleos Connect Emulator v2.1.0 (Android Automotive / API 34)
> 검증 환경: Windows 11, x86_64, 2026-11-12 기준으로 실제 구축·검증 완료
> 공식 문서: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator

---

## 0. 에이전트에게 (AGENT INSTRUCTIONS)

이 문서는 **처음부터 끝까지 순서대로 실행**하는 런북입니다. 아래 원칙을 지켜주세요.

- 각 단계의 **"검증"** 블록을 반드시 실행하고, 기대 출력과 다르면 멈추고 보고할 것
- **§8 함정(Pitfalls)** 을 먼저 읽을 것. 여기 적힌 4가지는 전부 실제로 한 번씩 당한 것들이라, 모르면 반드시 재현됨
- UAC(관리자 권한) 프롬프트가 뜨는 단계가 **2곳**(Android Studio, Go) 있음. 해당 단계 직전에 사용자에게 "화면에 UAC 창이 뜨면 예를 눌러달라"고 명시적으로 알릴 것. winget이 출력 없이 멈춰 있으면 `Get-Process consent` 로 UAC 대기 여부를 확인할 것
- 다운로드가 큼(시스템 이미지 4GB). 장시간 명령은 백그라운드로 돌리고 폴링하지 말 것
- Windows 기준으로 작성됨. macOS/Linux는 §9 참고

**소요 시간**: 다운로드 속도에 따라 30~60분
**필요 디스크**: 최소 30GB 여유 (§1 참고)

---

## 1. 사전 점검 (PREFLIGHT)

```powershell
# CPU / RAM / 가상화
Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,VirtualizationFirmwareEnabled
Get-CimInstance Win32_ComputerSystem | Select-Object TotalPhysicalMemory,HypervisorPresent

# 디스크 여유
Get-PSDrive -PSProvider FileSystem | Select-Object Name,@{n='FreeGB';e={[math]::Round($_.Free/1GB,1)}}

# 이미 설치된 것 확인
winget --version
Test-Path 'C:\Program Files\Android\Android Studio'
```

**요구사항**

| 항목 | 요구 | 비고 |
|---|---|---|
| 가상화 | `VirtualizationFirmwareEnabled = True`, `HypervisorPresent = True` | WHPX 가속에 필요 |
| RAM | 16GB 이상 (AVD에 8GB 할당) | |
| 디스크 | **30GB** 여유 | Studio 3.5GB + SDK 5GB + 시스템 이미지 19GB + AVD |
| GPU | 공식 최소: RTX 3060 Laptop 6GB / RX 6600M 8GB<br>권장: RTX 4060/5060 8GB / RX 7700S 8GB | |

**디스크가 부족하면** — §7 디스크 확보 참고. 특히 Docker를 쓴다면 거기에 수십 GB가 잠겨 있을 가능성이 높음.

---

## 2. Android Studio 설치

> ⚠️ **UAC 프롬프트 발생.** 사용자에게 미리 알릴 것.

```powershell
winget install --id Google.AndroidStudio --exact --silent `
  --accept-package-agreements --accept-source-agreements
```

winget은 다운로드(약 1.5GB) 후 UAC를 띄우고 **승인할 때까지 조용히 대기**합니다. 출력이 없다고 실패로 판단하지 말 것.

**검증**
```powershell
Test-Path 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'   # True
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -version  # openjdk 21+
```

> ⚠️ **정정 (실제 구축 중 확인).** "번들 JBR 을 쓰면 별도 JDK 설치 불필요"는 **이 프로젝트에서는 틀립니다.**
> 현재 Android Studio 번들 JBR 은 **OpenJDK 25** 라 AGP 8.7 / Gradle 8.11 과 호환되지 않습니다.
> **JDK 17 을 따로 설치해야** 앱이 빌드됩니다.
>
> ```powershell
> winget install --id EclipseAdoptium.Temurin.17.JDK --exact --silent `
>   --accept-package-agreements --accept-source-agreements
> # → C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot
> ```
>
> 빌드할 때는 `$env:JAVA_HOME` 을 이 JDK 17 로 지정합니다.
> SDK 관리(`sdkmanager`)·에뮬레이터 실행에는 번들 JBR 로도 충분합니다.

---

## 3. SDK command-line tools 설치

Studio의 첫 실행 마법사를 거치지 않고 CLI로만 끝내기 위해 직접 설치합니다.

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null

# 최신 버전 URL은 https://developer.android.com/studio#command-line-tools-only 에서 확인
$url = 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip'
Invoke-WebRequest $url -OutFile "$env:TEMP\cmdline-tools.zip"
Expand-Archive "$env:TEMP\cmdline-tools.zip" "$env:TEMP\clt" -Force
Move-Item "$env:TEMP\clt\cmdline-tools" "$sdk\cmdline-tools\latest" -Force
Remove-Item "$env:TEMP\cmdline-tools.zip","$env:TEMP\clt" -Recurse -Force
```

**디렉터리 구조가 반드시 `$sdk\cmdline-tools\latest\bin\sdkmanager.bat` 여야 합니다.** zip을 그대로 풀면 `cmdline-tools\cmdline-tools\...` 가 되므로 위처럼 `latest`로 이름을 바꿔 옮길 것.

---

## 4. 환경 변수 설정

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$jdk = 'C:\Program Files\Android\Android Studio\jbr'

[Environment]::SetEnvironmentVariable('ANDROID_HOME',     $sdk, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $sdk, 'User')
[Environment]::SetEnvironmentVariable('JAVA_HOME',        $jdk, 'User')

$add = @("$jdk\bin", "$sdk\platform-tools", "$sdk\emulator", "$sdk\cmdline-tools\latest\bin")
$parts = ([Environment]::GetEnvironmentVariable('Path','User') -split ';') | Where-Object { $_ }
foreach ($a in $add) { if ($parts -notcontains $a) { $parts += $a } }
[Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')
```

영구 설정은 **새 터미널부터** 적용됩니다. 현재 세션용으로 아래 헬퍼를 만들어 두고 매번 dot-source 하세요.

`C:\Users\<USER>\sdk_env.ps1`:
```powershell
$env:JAVA_HOME        = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME     = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
```

**검증**
```powershell
. .\sdk_env.ps1
sdkmanager --version      # 19.0 등 숫자 하나만 나오면 정상
```

---

## 5. Pleos SDK 저장소 등록 ⭐ 핵심

> **문서에 없는 함정입니다. §8-1 참고.**
> 공식 문서는 Android Studio GUI의 "SDK Update Sites"에 URL을 추가하라고만 안내합니다.
> CLI로 하려면 `~/.android/repositories.cfg` (Java properties 형식)에 직접 써야 합니다.

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.android" | Out-Null

# x86_64 (Windows / Linux / Intel Mac)
$u = 'https\://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml'
# Apple Silicon Mac 이라면 위 대신:
# $u = 'https\://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml'

@"
## User Sources for Android Repository
count=1
src00=$u
disp00=Pleos Connect System Image
enabled00=true
"@ | Set-Content "$env:USERPROFILE\.android\repositories.cfg" -Encoding ascii
```

형식 규칙: 키는 `count`, `src<NN>`, `disp<NN>`, `enabled<NN>` (2자리 0-based). URL의 `:` 는 properties 형식이라 `\:` 로 이스케이프.

**검증** — 이게 통과해야 다음 단계가 의미 있습니다.
```powershell
sdkmanager --list | Select-String 'pleos'
# 기대 출력:
#   system-images;android-34;pleos_car_emulator;x86_64  | 2 | Pleos Connect v2.1.0 APIs Intel x86_64 Atom System Image
```
아무것도 안 나오면 repositories.cfg 형식이 틀린 것. 다음으로 진행하지 말 것.

---

## 6. SDK 패키지 설치

시스템 이미지가 **다운로드 4.09GB / 전개 후 약 19GB** 입니다. 백그라운드로 돌리세요.

> ⚠️ **정정 (실제 구축 중 확인).** 아래처럼 PowerShell 에서 `y` 를 파이프로 넣는 방식은
> **Windows PowerShell 5.1 에서 동작하지 않습니다.** 멀티라인 문자열이 단일 객체로 전달돼
> 프롬프트당 하나만 소비되고, 나머지 라이선스는 거부된 채 패키지가 전부 스킵됩니다
> (`exit 0` 으로 끝나서 성공처럼 보입니다).
>
> ```powershell
> # ❌ 동작하지 않음
> $y = ("y`n" * 60)
> $y | sdkmanager --licenses
> ```
>
> **Git Bash 로 실행하세요.**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"   # 또는 /c/Users/<USER>/AppData/Local/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat"

yes | "$SDKM" --licenses
#   → 마지막에 "All SDK package licenses accepted" 가 나와야 합니다

yes | "$SDKM" "platform-tools" "emulator" \
              "platforms;android-34" "build-tools;34.0.0" \
              "platforms;android-35" "build-tools;35.0.0" \
              "system-images;android-34;pleos_car_emulator;x86_64"
```

> **android-35 / build-tools 35 도 설치하는 이유**: 공식 샘플 앱(`vehicle-sdk-sample-app`)이 `compileSdk/targetSdk 35`를 씁니다. 34만 있으면 샘플 빌드가 실패합니다. (에뮬레이터 이미지 자체는 API 34)

**검증**
```powershell
sdkmanager --list_installed
# build-tools;34.0.0, build-tools;35.0.0, emulator, platform-tools,
# platforms;android-34, platforms;android-35,
# system-images;android-34;pleos_car_emulator;x86_64  ← 7개 모두 있어야 함
```

---

## 7. AVD 생성 ⭐ 핵심

> **가장 흔한 실수 지점입니다. §8-2 참고.**
> Google 범용 automotive 프로필을 쓰면 **UI가 겹쳐서 깨집니다.**
> 시스템 이미지가 자체 디바이스 정의(`devices.xml`)를 포함하고 있으므로 반드시 그걸 쓸 것.

```powershell
. .\sdk_env.ps1

# 디바이스 프로필이 보이는지 먼저 확인
avdmanager list device | Select-String 'Pleos'
# 기대: id: NN or "Pleos_Connect_v2_0"  /  Name: Pleos Connect v2.1.0  /  OEM : 42dot

$name = 'Pleos_Connect_2.1.0_API34'
"no" | avdmanager create avd --name $name `
  --package "system-images;android-34;pleos_car_emulator;x86_64" `
  --device "Pleos_Connect_v2_0" --force
```

### config.ini 패치

`avdmanager`는 디바이스의 해상도/밀도만 가져오고, 시스템 이미지가 `source.properties`에 넣어둔 `avd_config_ini.*` 권장값은 적용하지 않습니다. 수동으로 적용합니다.

```powershell
$cfg = "$env:USERPROFILE\.android\avd\Pleos_Connect_2.1.0_API34.avd\config.ini"
$map = @{
  'hw.ramSize'               = '8192'      # 이미지 권장값 (문서상 최소 4096)
  'hw.gpu.enabled'           = 'yes'
  'hw.gpu.mode'              = 'host'      # 이미지 권장값. auto 아님
  'fastboot.forceColdBoot'   = 'yes'       # 필수 — quick boot면 VHAL 사용 불가
  'fastboot.forceFastBoot'   = 'no'
  'hw.keyboard'              = 'yes'
  'hw.initialOrientation'    = 'landscape' # 기본값이 portrait로 들어옴
}
$lines = Get-Content $cfg
foreach ($k in $map.Keys) {
  $lines = $lines -replace "^$([regex]::Escape($k))\s*=.*", "$k=$($map[$k])"
}
$lines | Set-Content $cfg -Encoding ascii
```

**검증** — 이 값들이 정확히 맞아야 화면이 안 깨집니다.
```powershell
Get-Content $cfg | Select-String 'hw.lcd.width|hw.lcd.height|hw.lcd.density|hw.ramSize|hw.gpu|fastboot.force|hw.initialOrientation'
```
| 키 | 기대값 |
|---|---|
| `hw.lcd.width` | **2560** |
| `hw.lcd.height` | **1440** |
| `hw.lcd.density` | **160** |
| `hw.initialOrientation` | landscape |
| `hw.gpu.mode` | host |
| `hw.ramSize` | 8192 |
| `fastboot.forceColdBoot` | yes |

`1080 x 600 / 120` 이 나오면 §7의 `--device` 를 빠뜨린 것. AVD를 지우고 다시 만들 것.

---

## 8. 실행 스크립트

`C:\Users\<USER>\launch_avd.ps1`:
```powershell
# Pleos Connect emulator launcher
#   - 벤더 지정 설정으로 콜드 부팅 (VHAL에 필수)
#   - Virtual Console 포트 포워딩 자동 설정
. "$PSScriptRoot\sdk_env.ps1"

$avd = 'Pleos_Connect_2.1.0_API34'

if (-not (Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue)) {
    Write-Host "Starting $avd ..."
    Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" `
        -ArgumentList '-avd', $avd, '-no-snapshot-load' `
        -WorkingDirectory "$env:ANDROID_HOME\emulator"
} else {
    Write-Host "Emulator already running."
}

Write-Host "Waiting for boot ..."
do {
    Start-Sleep -Seconds 5
    $booted = (& adb shell getprop sys.boot_completed 2>$null) -replace '\s',''
} until ($booted -eq '1')

& adb forward tcp:8088 tcp:8088 | Out-Null

Write-Host ""
Write-Host "Ready."
Write-Host "  Virtual Console : http://127.0.0.1:8088/"
Write-Host "  adb device      : $((& adb devices | Select-Object -Skip 1 | Select-Object -First 1))"
```

> **`-gpu` 옵션을 커맨드라인에 주지 말 것.** 주면 config.ini의 `hw.gpu.mode=host` 를 덮어씁니다.

---

## 9. 부팅 검증

```powershell
. .\sdk_env.ps1

emulator -accel-check       # "WHPX ... is installed and usable."
emulator -list-avds         # Pleos_Connect_2.1.0_API34

.\launch_avd.ps1            # 콜드 부팅 2~4분 (2회차부터 ~40초)
```

부팅 후:
```powershell
adb shell wm size                               # Physical size: 2560x1440
adb shell wm density                            # Physical density: 160
adb shell getprop ro.build.display.id           # RELEASE.release_connect_v2.1.0 test-keys
adb shell getprop ro.product.device             # crp_avd_x86_64
adb shell getprop ro.build.version.sdk          # 34

# Pleos IVI 앱 구동 확인
adb shell dumpsys activity activities | Select-String 'topResumedActivity'
#   ai.umos.homescreen/.HomeActivity
#   ai.umos.maps.android.navigation.app/.MainActivity
#   ai.umos.drivingview/.DrivingViewActivity

# GPU 하드웨어 가속 확인 (소프트웨어 렌더링이면 여기 SwiftShader가 나옴)
adb shell dumpsys SurfaceFlinger | Select-String 'GLES:'
#   GLES: Google (NVIDIA Corporation), Android Emulator OpenGL ES Translator (NVIDIA GeForce ...)

# 화면 캡처해서 눈으로 확인
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png "$env:USERPROFILE\Desktop\pleos_boot.png"
```

정상 화면: 좌측에 클러스터(속도/기어/3D 차량), 우측에 내비게이션, 하단에 공조 바(열선 시트·스티어링·성에 제거·통풍)가 **겹침 없이** 배치됨.

---

## 10. Virtual Console (v2.1.0 신규)

차량 상태·이벤트를 도메인별(Vehicle / Motion / ADAS / Convenience / Safety / Energy / Climate)로 시뮬레이션하는 웹 콘솔. 60개 이상 항목 제어 가능.

```powershell
adb forward tcp:8088 tcp:8088
Start-Process 'http://127.0.0.1:8088/'
```

`launch_avd.ps1`에 이미 포함돼 있습니다.

**검증**
```powershell
(Invoke-WebRequest 'http://127.0.0.1:8088/' -UseBasicParsing).StatusCode  # 200
```

문제 시 체크리스트: ① 에뮬레이터 실행 중인지 ② v2.1.0 이상 이미지인지 ③ `adb devices` ④ `adb forward` 재실행 ⑤ 8088 포트 충돌 여부.

---

## 11. 폼팩터 테스트 (선택)

다른 화면 크기에서 앱 레이아웃을 검증하는 도구. 필요 없으면 건너뛰어도 환경 구축은 완료입니다.

### Go 설치
> ⚠️ **UAC 프롬프트 발생.**

```powershell
winget install --id GoLang.Go --exact --silent --accept-package-agreements --accept-source-agreements
go version      # go1.22 이상
```

### host-display-bridge 빌드

```powershell
$tools = "$env:USERPROFILE\pleos-tools"
New-Item -ItemType Directory -Force $tools | Out-Null
Invoke-WebRequest 'https://nexus-playground.pleos.ai/repository/raw-releases/host-display-bridge/1.0.0/host-display-bridge.zip' `
  -OutFile "$tools\host-display-bridge.zip"
Expand-Archive "$tools\host-display-bridge.zip" "$tools\host-display-bridge" -Force

cd "$tools\host-display-bridge\host-display-bridge"
go build -o host-display-bridge.exe .
```

> **빌드가 Go 표준 라이브러리에서 실패하면** (`undefined: ValueOf`, `undefined: goexperiment.RuntimeSecret` 등) 이전 Go 버전의 빌드 캐시 충돌입니다. §12-4 참고:
> ```powershell
> go clean -cache
> go build -o host-display-bridge.exe .
> ```

### 실행

```powershell
adb forward tcp:8095 tcp:8095
$avd = (adb emu avd name | Select-Object -First 1).Trim()
.\host-display-bridge.exe -avd $avd
```

에뮬레이터가 디스커버리 파일(`%LOCALAPPDATA%\Temp\avd\running\pid_*.ini`)에 `grpc.port=8554` 와 `grpc.token` 을 이미 게시하므로 **추가 실행 플래그 불필요**. 게시가 안 돼 있을 때만 `emulator -avd <name> -grpc 8554 -grpc-use-token` 로 실행.

**검증**
```powershell
(Invoke-WebRequest 'http://127.0.0.1:8096/health' -UseBasicParsing).Content
#   {"ok":true}
(Invoke-WebRequest 'http://127.0.0.1:8096/api/window-policy/display' -UseBasicParsing).Content
#   {"ok":true,"available":true,"created":false,"display_slot":2,"display_name":"DISPLAY2",...}
```

그 다음 에뮬레이터에서 FormFactorConsole 앱 실행:
```powershell
adb shell am start -n ai.pleos.connectwebconsole/.FormFactorConsoleActivity
```
화면에 `DISCONNECTED / DISPLAY2가 없습니다` 가 보이고, Last Operation에 `{"ok": true, "available": true}` 가 찍히면 전 구간 연동 성공.

종료: `Ctrl+C` → `adb forward --remove tcp:8095`

---

## 12. 앱 프로젝트 설정 (환경 구축과 별개)

Vehicle SDK Maven 저장소는 **공개이며 인증 불필요**합니다.

`settings.gradle(.kts)`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
```

`build.gradle(.kts)`:
```kotlin
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}
```

| 항목 | 값 |
|---|---|
| Vehicle SDK | 2.3.2 (2.0.3도 존재) |
| Vehicle Service | 2.3.22 |
| compileSdk / targetSdk | 35 |
| **minSdk** | **28 이상 필수** — 차량 사이버보안 요건(서명 키 로테이션·라이브러리 지원) |
| Gradle | 8.0 이상 (Gradle Wrapper 사용 권장) |

버전 확인:
```powershell
(Invoke-WebRequest 'https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/maven-metadata.xml' -UseBasicParsing).Content
```

---

## 13. 함정 모음 (PITFALLS) ⭐ 먼저 읽을 것

실제로 구축하면서 하나씩 다 밟은 것들입니다.

### 13-1. sdkmanager는 `sites-settings.xml` 을 읽지 않는다
커스텀 SDK 업데이트 사이트를 `~/.android/sites-settings.xml` 에 XML로 쓰면 **Android Studio GUI는 읽지만 sdkmanager CLI는 무시**합니다. CLI용은 `~/.android/repositories.cfg` (Java properties)입니다. → §5

> 근거: `com.android.repository.impl.sources.LocalSourceProvider` 가 properties 형식으로 `count` / `src<NN>` / `disp<NN>` / `enabled<NN>` 를 읽습니다.

### 13-2. Google 범용 automotive 프로필을 쓰면 UI가 깨진다 ⭐ 가장 치명적
`automotive_1080p_landscape` = **1080x600 / 120dpi / portrait**.
Pleos Connect UI는 **2560x1440 / 160dpi / landscape** 기준 레이아웃이라, 클러스터·IVI·공조 바가 전부 1080x600에 압축되며 **서로 겹쳐 보입니다.**

시스템 이미지 안에 벤더가 넣어둔 값을 쓰세요 (문서에는 이 내용이 전혀 없음):
- `system-images/android-34/pleos_car_emulator/x86_64/devices.xml` → 디바이스 `Pleos_Connect_v2_0` (2560x1440, 160dpi, 12.8", landscape)
- 같은 폴더 `source.properties` → `avd_config_ini.hw.gpu.mode=host`, `hw.ramSize=8192`, `hw.keyboard=yes`

Android Studio GUI로 AVD를 만들면 이 값들이 자동 적용되지만, **avdmanager CLI는 `--device Pleos_Connect_v2_0` 를 명시해야** 하고 `avd_config_ini.*` 는 수동 패치가 필요합니다. → §7

### 13-3. Cold boot은 선택이 아니라 필수
Quick boot으로 설정하면 **VHAL을 사용할 수 없습니다.** 공식 문서 명시 사항. 대신 콜드 부팅이라 첫 부팅 2~4분 걸립니다(2회차부터 ~40초). 느리다고 quick boot으로 바꾸지 말 것.

### 13-4. Go 빌드가 표준 라이브러리에서 깨지면 캐시 문제
`%LOCALAPPDATA%\go-build` 에 이전 Go 버전 캐시가 남아 있으면 `undefined: ValueOf`, `undefined: goexperiment.RuntimeSecret` 같은 **Go 자체 소스에서의 에러**가 납니다. GOROOT 문제로 오진하기 쉬움. `go clean -cache` 한 방이면 됩니다. → §11

### 13-5. winget이 조용히 멈춰 있으면 UAC 대기
출력 없이 몇 분째 진행이 없으면 `Get-Process consent` 로 확인. UAC 창이 다른 창 뒤에 가려져 있을 수 있음.

### 13-6. Temp를 통째로 지우지 말 것
디스크 정리 시 `%TEMP%\*` 를 재귀 삭제하면 실행 중인 도구의 작업 파일까지 날아갑니다. `%TEMP%\claude` 등은 제외할 것.

---

## 14. 디스크 공간 확보

30GB가 안 나올 때. **위에서부터 안전한 순서**입니다.

### 14-1. 안전한 캐시 (바로 삭제 가능)
```powershell
python -m pip cache purge
npm cache clean --force
Clear-RecycleBin -Force -Confirm:$false
# Temp는 선별 삭제 (§13-6)
```

### 14-2. Docker — 보통 여기 제일 많이 잠겨 있음
WSL2 vhdx는 내부 데이터를 지워도 **호스트 파일이 줄지 않습니다.** 실제로 비어 있는데 95GB를 차지하고 있을 수 있음.

```powershell
# 1. 먼저 실제 내용 확인 — 0이면 데이터 손실 없이 전량 회수 가능
docker system df

# 2. 내용이 있고 지워도 된다면
docker system prune -a --volumes

# 3. 게스트 파일시스템의 빈 블록을 비움 (핵심 단계, 이것 없으면 compact가 거의 안 먹음)
wsl -d docker-desktop -e sh -c "fstrim -av"

# 4. Docker/WSL 종료
Get-Process 'Docker Desktop','com.docker.backend','com.docker.build' -EA SilentlyContinue | Stop-Process -Force
wsl --shutdown

# 5. vhdx 압축 (관리자 권한 필요 → UAC)
@"
select vdisk file="$env:LOCALAPPDATA\Docker\wsl\disk\docker_data.vhdx"
attach vdisk readonly
compact vdisk
detach vdisk
exit
"@ | Set-Content "$env:TEMP\compact.txt" -Encoding ascii
Start-Process diskpart.exe -ArgumentList '/s',"$env:TEMP\compact.txt" -Verb RunAs -Wait

# 6. 결과 확인
(Get-Item "$env:LOCALAPPDATA\Docker\wsl\disk\docker_data.vhdx").Length / 1GB
```

> 실측: 95.5GB → 2.6GB (데이터 손실 0). **3번 `fstrim` 을 건너뛰면 compact가 거의 효과 없습니다.**

### 14-3. "1년간 미사용 파일 삭제"는 권하지 않음
실측 결과 100MB↑ / 1년↑ 미접근 파일은 5.9GB·23개뿐이었고, 전부 **설치된 프로그램의 내부 파일이거나 프로젝트 자산**이었습니다. 지우면 프로그램이 깨집니다. 효과 대비 위험이 큼. 스캔해서 사용자에게 목록을 보여주고 판단을 맡기세요.

```powershell
$cut = (Get-Date).AddDays(-365)
@('Downloads','Documents','Desktop','Videos','Pictures') | ForEach-Object {
  Get-ChildItem "$env:USERPROFILE\$_" -Recurse -File -Force -EA SilentlyContinue |
    Where-Object { $_.Length -gt 100MB -and $_.LastAccessTime -lt $cut -and $_.LastWriteTime -lt $cut }
} | Sort-Object Length -Descending |
    Select-Object FullName, @{n='GB';e={[math]::Round($_.Length/1GB,2)}}, LastWriteTime
```

---

## 15. macOS / Linux 차이

| 항목 | 차이 |
|---|---|
| 시스템 이미지 URL | Apple Silicon은 `crp-arm64-sys-img.xml`, 그 외는 `crp-sys-img.xml` |
| SDK 기본 경로 | macOS `~/Library/Android/sdk`, Linux `~/Android/Sdk` |
| repositories.cfg | 동일하게 `~/.android/repositories.cfg` |
| 가속 | macOS는 Hypervisor.framework, Linux는 KVM (`emulator -accel-check` 로 확인) |
| 커맨드라인 툴 | `commandlinetools-mac-*.zip` / `commandlinetools-linux-*.zip` |
| 설치 | winget 대신 `brew install --cask android-studio` 또는 공식 tarball |

---

## 16. 최종 산출물

| 경로 | 내용 |
|---|---|
| `C:\Program Files\Android\Android Studio` | IDE + 번들 JBR(JDK) |
| `%LOCALAPPDATA%\Android\Sdk` | SDK (platform 34/35, build-tools 34/35, platform-tools, emulator, Pleos 시스템 이미지) |
| `%USERPROFILE%\.android\repositories.cfg` | Pleos SDK 저장소 등록 |
| `%USERPROFILE%\.android\avd\Pleos_Connect_2.1.0_API34.avd` | AVD |
| `%USERPROFILE%\sdk_env.ps1` | 세션 환경 로더 |
| `%USERPROFILE%\launch_avd.ps1` | 에뮬레이터 실행 + 8088 포워딩 |
| `%USERPROFILE%\pleos-tools\host-display-bridge\` | 폼팩터 테스트 bridge (선택) |

**환경 변수**: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME`, `Path`(4개 추가) — User 레벨, 새 터미널부터 적용

---

## 17. 참고 문서

- [Pleos Connect Emulator 설치 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Pleos Connect Emulator 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
- [Virtual Console 셋업 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Virtual Console 사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)
- [폼 팩터 테스트 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide)
- [Vehicle SDK 연동 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [Vehicle SDK Sample App](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)
- [에뮬레이터 릴리스 노트](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note)

> v2.1.0(2026-10-19)에서 **LLM SDK / CRN / 블루투스 동글 연동은 제공 종료**되었습니다. 관련 설치 단계가 있는 옛 가이드를 따르지 마세요.

---

## 18. 이 런북에 대한 정정 모음 (실제 구축 후 추가)

이 문서는 원래 환경 구축 전에 작성된 것입니다. 실제로 구축하고 앱까지 올린 뒤 확인된
차이를 모았습니다. **§2·§6 본문에도 반영해 두었습니다.**

| # | 원문 | 실제 | 영향 |
|---|---|---|---|
| 1 | `("y\`n" * 60) \| sdkmanager --licenses` 로 라이선스 자동 승인 | **PS 5.1 에서 동작 안 함.** 프롬프트당 1개만 소비 | 패키지가 전부 스킵되는데 `exit 0` 이라 성공처럼 보임. Git Bash `yes \|` 로 대체 |
| 2 | 번들 JBR 로 충분, 별도 JDK 불필요 | 번들 JBR 이 **OpenJDK 25** 라 AGP 8.7 과 비호환 | **JDK 17 별도 설치 필요** |
| 3 | §7 `config.ini` 패치 키 7개 | 시스템 이미지 `source.properties` 가 **2개를 더 권장** | `hw.multi_display_window=no`, `hw.hotplug_multi_display=no` 도 적용 |
| 4 | §9 `adb pull ... %USERPROFILE%\Desktop` | OneDrive 리다이렉트 환경에서는 그 경로가 없음 | `[Environment]::GetFolderPath('Desktop')` 사용 |

### 추가로 알아낸 것

**Virtual Console 은 브라우저로 한 번 열어야 쓰기가 활성화됩니다.**
열기 전에는 모든 제어가 `{"error":"emulator_inactive"}` (503) 으로 거부됩니다.
CLI(`curl`)로만 접근하면 계속 503 입니다. 상태는 `/vehicle/state` 의 `emulator_active` 로 확인합니다.

**`adb shell cmd car_service inject-vhal-event` 는 쓸 수 없습니다.**
production(user) 빌드라 `SecurityException: requires non-user build` 로 막힙니다.
차량 신호를 바꾸는 유일한 경로는 Virtual Console 입니다.

**앱은 user 10 으로 실행됩니다.**
런타임 권한을 adb 로 줄 때 user 를 지정하지 않으면 user 0 에만 부여돼 효과가 없습니다.

```powershell
adb shell pm grant --user 10 com.cardoctor.advisor android.permission.POST_NOTIFICATIONS
```

**이 에뮬레이터 이미지가 미지원하는 차량 속성이 있습니다.**
타이어 공기압·엔진오일·브레이크액·변속기오일·워셔액·안개등·안전벨트 경고는 VHAL 에 없습니다.
전체 목록은 `SDK_API_NOTES.md` §9 참고.

### 시간 단축 팁

가장 오래 걸리는 것은 **시스템 이미지(다운로드 4GB / 전개 19GB)** 입니다.
이미 구축한 PC 가 있다면 아래를 통째로 복사하는 편이 다시 받는 것보다 빠릅니다.

```
%LOCALAPPDATA%\Android\Sdk\system-images\android-34\pleos_car_emulator\
```

단, SDK 경로가 PC 마다 다를 수 있으니 복사 후 `sdkmanager --list_installed` 로 인식되는지
확인하세요. 인식이 안 되면 그냥 `sdkmanager` 로 다시 받는 쪽이 확실합니다.
