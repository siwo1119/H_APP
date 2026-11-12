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
