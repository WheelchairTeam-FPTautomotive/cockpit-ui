# Automotive AVD launcher — host mic notes for THIS machine
#
# BLOCKER (verified): DirectSoundCaptureCreate returns DSERR_NODRIVER (0x88780078).
# Android Emulator on Windows still uses DirectSound for mic input.
# WASAPI/ffmpeg CAN open PD200X/Razer, but the emulator cannot use WASAPI
# (SDK 37.1.11: "Unknown audio driver wasapi").
# Same failure class as https://github.com/intel/haxm/issues/246
#
# DEMO WORKAROUND: tap the mic button in-app (bypasses Vosk wake-word).
# Wake-word needs a host where DirectSound capture enumerates a real device.
#
# Never set QEMU_AUDIO_DRV=wasapi in this shell — it poisons emulator launches.

$ErrorActionPreference = "Stop"
$sdk = "D:\Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$avd = "Automotive_Ultrawide"

Remove-Item Env:QEMU_AUDIO_DRV,Env:QEMU_AUDIO_IN_DRV,Env:QEMU_AUDIO_OUT_DRV -ErrorAction SilentlyContinue

$avdConf = Join-Path $env:USERPROFILE ".android\avd\$avd.avd\AVD.conf"
if (Test-Path $avdConf) {
  (Get-Content $avdConf) -replace 'mic\\available=false','mic\available=true' | Set-Content $avdConf
}

& $adb emu kill 2>$null
Start-Sleep 2
Get-Process qemu-system-x86_64,emulator -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 1

Start-Process $emulator -ArgumentList @("-avd", $avd, "-no-snapshot-load", "-gpu", "auto", "-allow-host-audio")
Write-Host "Started $avd with -allow-host-audio"
Write-Host "If wake-word stays silent: Windows DirectSound capture is NODRIVER on this PC."
Write-Host "Use in-app mic tap for demo. Friend laptops work when DSound enumerates a mic."
