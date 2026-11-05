param(
    [string]$SourceApk = "testdata\mini.apk",
    [string]$OutDir = "testdata\fixtures"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $SourceApk)) {
    throw "No se encontró el APK de origen: $SourceApk. Coloca un APK pequeño ahí (p. ej. descargado de APKMirror) o pásalo con -SourceApk."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$apkBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $SourceApk))

function New-Zip([string]$outPath, [hashtable]$entries) {
    if (Test-Path $outPath) { Remove-Item $outPath -Force }
    $zip = [System.IO.Compression.ZipFile]::Open($outPath, 'Create')
    foreach ($name in $entries.Keys) {
        $entry = $zip.CreateEntry($name)
        $stream = $entry.Open()
        $bytes = $entries[$name]
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Dispose()
    }
    $zip.Dispose()
}

$xapkManifest = @'
{"version":2,"xapk_version":2,"splitApks":[{"file":"base.apk","id":"base"}],"split_apks":[{"file":"base.apk","id":"base"}]}
'@
New-Zip (Join-Path $OutDir "mini.xapk") @{
    "base.apk"     = $apkBytes
    "manifest.json" = [Text.Encoding]::UTF8.GetBytes($xapkManifest)
}

New-Zip (Join-Path $OutDir "mini.apks") @{
    "base-master.apk" = $apkBytes
    "toc.pb"          = [byte[]]@(0)
}

powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "make-aab.ps1") `
    -DexSource $SourceApk -OutFile (Join-Path $OutDir "mini.aab")

Get-ChildItem $OutDir -File | Where-Object { $_.Extension -in ".xapk", ".apks", ".aab" } |
    Select-Object Name, @{n = "MB"; e = { [math]::Round($_.Length / 1MB, 1) } }
