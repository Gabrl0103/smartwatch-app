# Abre Rumbo con un clic: arranca el emulador Wear OS si no está abierto, espera a que encienda,
# compila e instala la última versión de la app (si no se puede compilar, instala el APK que ya
# esté hecho), da los permisos, pone la posición de inicio de la demo y abre la app.
#
# Uso: doble clic en tools\launch_rumbo.bat (o en el acceso directo "Rumbo" del escritorio).
#   -SinCompilar   instala el APK que ya existe, sin pasar por Gradle (más rápido)
#   -Avd <nombre>  otro emulador (por defecto Wear_OS_Large_Round)
param(
    [switch]$SinCompilar,
    [string]$Avd = "Wear_OS_Large_Round"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$watchapp = Join-Path $root "watchapp"
$apk = Join-Path $watchapp "app\build\outputs\apk\debug\app-debug.apk"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$package = "com.rumbo.watchapp"
# Inicio de la ruta de demostración (docs/demo): Plaza de Nariño, Pasto.
$startLng = "-77.278419"
$startLat = "1.214997"

function Write-Step($text) { Write-Host "`n> $text" -ForegroundColor Cyan }

function Stop-WithError($text) {
    Write-Host "`nNo se pudo abrir Rumbo: $text" -ForegroundColor Red
    Read-Host "Presiona Enter para cerrar"
    exit 1
}

if (-not (Test-Path $adb)) { Stop-WithError "no encuentro adb en $adb. Instala Android Studio (SDK Platform-Tools)." }
if (-not (Test-Path $emulator)) { Stop-WithError "no encuentro el emulador en $emulator." }

# 1. Emulador
$running = & $adb devices | Select-String "^emulator-\d+\s+device"
if ($running) {
    Write-Step "El emulador ya está abierto."
} else {
    $avds = & $emulator -list-avds
    # En otro PC el emulador suele llamarse "Wear_OS_Large_Round_API_34": sirve cualquiera de Wear OS.
    if ($avds -notcontains $Avd) {
        $wear = $avds | Where-Object { $_ -match "Wear" } | Select-Object -First 1
        if (-not $wear) { Stop-WithError "no hay un emulador Wear OS. Créalo en Android Studio (Device Manager). Disponibles: $($avds -join ', ')" }
        $Avd = $wear
    }
    Write-Step "Abriendo el emulador $Avd..."
    Start-Process -FilePath $emulator -ArgumentList "-avd", $Avd | Out-Null
}

Write-Step "Esperando a que el reloj encienda..."
& $adb wait-for-device | Out-Null
$deadline = (Get-Date).AddMinutes(4)
while ((& $adb shell getprop sys.boot_completed 2>$null) -notmatch "1") {
    if ((Get-Date) -gt $deadline) { Stop-WithError "el emulador tardó más de 4 minutos en encender." }
    Start-Sleep -Seconds 2
}

# 2. Compilar e instalar
if (-not $SinCompilar) {
    Write-Step "Compilando la última versión (la primera vez tarda unos minutos)..."
    Push-Location $watchapp
    try {
        & .\gradlew.bat -q :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { Write-Host "  No se pudo compilar; instalo el APK que ya está hecho." -ForegroundColor Yellow }
    } finally {
        Pop-Location
    }
}
if (-not (Test-Path $apk)) { Stop-WithError "no hay APK en $apk. Compila una vez la app (gradlew assembleDebug)." }

Write-Step "Instalando Rumbo en el reloj..."
& $adb install -r -t $apk | Out-Null
if ($LASTEXITCODE -ne 0) { Stop-WithError "adb no pudo instalar el APK." }

# 3. Permisos y posición: así se puede pedir una ruta enseguida.
Write-Step "Dando permisos y poniendo la posición de la demo..."
foreach ($permission in "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "RECORD_AUDIO", "POST_NOTIFICATIONS") {
    & $adb shell pm grant $package "android.permission.$permission" 2>$null | Out-Null
}
& $adb shell cmd location set-location-enabled true | Out-Null
& $adb emu geo fix $startLng $startLat | Out-Null

# 4. Abrir la app (con el emulador al frente)
Write-Step "Abriendo Rumbo..."
& $adb shell am force-stop $package | Out-Null
& $adb shell am start -n "$package/.presentation.MainActivity" | Out-Null
Start-Sleep -Seconds 3
# La posición se repite tras abrir: la app toma la primera lectura al arrancar el GPS.
& $adb emu geo fix $startLng $startLat | Out-Null

$window = Get-Process -Name "qemu-system-*" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if ($window) {
    Add-Type -Namespace Rumbo -Name Win -MemberDefinition '[DllImport("user32.dll")] public static extern bool ShowWindow(System.IntPtr h, int c); [DllImport("user32.dll")] public static extern bool SetForegroundWindow(System.IntPtr h);'
    [Rumbo.Win]::ShowWindow($window.MainWindowHandle, 9) | Out-Null
    [Rumbo.Win]::SetForegroundWindow($window.MainWindowHandle) | Out-Null
}

Write-Host "`nRumbo está abierto en el reloj. Esta ventana se cierra sola." -ForegroundColor Green
Start-Sleep -Seconds 3
