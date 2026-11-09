param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$Paquete,
    [string]$Dispositivo,
    [string]$Capturas = "capturas",
    [switch]$Desinstalar
)

$ErrorActionPreference = "Stop"

function Find-Adb {
    $enPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($enPath) { return $enPath.Source }
    $candidatos = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
    )
    foreach ($c in $candidatos) {
        if (Test-Path $c) { return $c }
    }
    throw "adb no esta en el PATH. Instala platform-tools de Android."
}

function Invoke-Adb([string[]]$Argumentos) {
    $salida = & $adb @Argumentos 2>&1
    return ($salida | Out-String).Trim()
}

$adb = Find-Adb
if (-not (Test-Path $Apk)) { throw "No se encontro el APK: $Apk" }

if ($Dispositivo) {
    $conexion = Invoke-Adb @("connect", $Dispositivo)
    if ($conexion -match "failed|cannot") {
        throw "No se pudo conectar a $Dispositivo. Si es la primera vez, parea antes con: adb pair $Dispositivo"
    }
}

$lista = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $lista) {
    throw "No hay dispositivos conectados. Conecta el equipo con depuracion USB activada o pasa -Dispositivo ip:puerto."
}
$dispositivoId = ($lista[0].Line -split "`t")[0]
if ($lista.Count -gt 1) {
    Write-Host "Hay varios dispositivos; se usara el primero: $dispositivoId"
}
Write-Host "Dispositivo: $dispositivoId"

Write-Host "Instalando $Apk ..."
$instalacion = Invoke-Adb @("-s", $dispositivoId, "install", "-r", $Apk)
if ($instalacion -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
    if (-not $Desinstalar) {
        throw "La app $Paquete esta instalada con una firma distinta y hay que desinstalarla primero (se pierden sus datos locales). Vuelve a ejecutar con -Desinstalar para continuar."
    }
    Write-Host "Firma distinta: se desinstala $Paquete y se vuelve a instalar."
    Invoke-Adb @("-s", $dispositivoId, "uninstall", $Paquete) | Out-Null
    $instalacion = Invoke-Adb @("-s", $dispositivoId, "install", $Apk)
}
if ($instalacion -match "Failure|failed") {
    throw "Fallo la instalacion: $instalacion"
}
Write-Host "Instalacion correcta."

$resuelta = Invoke-Adb @("-s", $dispositivoId, "shell", "cmd", "package", "resolve-activity", "--brief", $Paquete)
$componente = ($resuelta -split "`n" | Where-Object { $_ -match "/" -and $_ -notmatch "priority" } | Select-Object -First 1)
if (-not $componente) {
    throw "No se pudo resolver la activity principal de $Paquete"
}
Write-Host "Lanzando $componente ..."
Invoke-Adb @("-s", $dispositivoId, "shell", "am", "start", "-n", $componente.Trim()) | Out-Null
Start-Sleep -Seconds 6

$proceso = Invoke-Adb @("-s", $dispositivoId, "shell", "pidof", $Paquete)
$logcat = Invoke-Adb @("-s", $dispositivoId, "logcat", "-d", "-t", "500")
$fatal = ($logcat -split "`n" | Where-Object { $_ -match "FATAL EXCEPTION" } | Select-Object -First 3)

New-Item -ItemType Directory -Force -Path $Capturas | Out-Null
$fichero = Join-Path $Capturas ("rebuild-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".png")
Invoke-Adb @("-s", $dispositivoId, "shell", "screencap", "-p", "/sdcard/aquadecomp-verificacion.png") | Out-Null
Invoke-Adb @("-s", $dispositivoId, "pull", "/sdcard/aquadecomp-verificacion.png", $fichero) | Out-Null
Invoke-Adb @("-s", $dispositivoId, "shell", "rm", "/sdcard/aquadecomp-verificacion.png") | Out-Null

$corre = -not [string]::IsNullOrWhiteSpace($proceso)
$sinCrash = $fatal.Count -eq 0 -or -not ($fatal -match $Paquete)

Write-Host ""
Write-Host "Proceso vivo: $proceso"
if ($fatal.Count -gt 0) {
    Write-Host "Lineas FATAL en logcat:"
    $fatal | ForEach-Object { Write-Host "  $_" }
}
if (Test-Path $fichero) {
    Write-Host "Captura: $fichero"
}

if ($corre -and $sinCrash) {
    Write-Host "RESULTADO: la app $Paquete arranco y sigue en ejecucion sin excepciones fatales."
} else {
    throw "RESULTADO: la app $Paquete no quedo en ejecucion limpia. Revisa el logcat anterior."
}

if ($Desinstalar) {
    Invoke-Adb @("-s", $dispositivoId, "uninstall", $Paquete) | Out-Null
    Write-Host "App desinstalada del dispositivo."
}
