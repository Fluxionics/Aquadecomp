param(
    [string]$DexSource = "testdata\mini.apk",
    [string]$OutFile = "testdata\fixtures\mini.aab"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Bytes([string]$s) { return [Text.Encoding]::UTF8.GetBytes($s) }

function LenDelimited([byte[]]$payload) {
    $len = $payload.Length
    if ($len -gt 127) { throw "payload demasiado larga: $len" }
    return ,([byte[]]@([byte]$len) + $payload)
}

$pkg = Bytes("com.example.mini")
$namePackage = Bytes("package")

$attr = [byte[]]@(0x12) + (LenDelimited $namePackage) + [byte[]]@(0x1A) + (LenDelimited $pkg)

$manifestName = Bytes("manifest")
$element = [byte[]]@(0x1A) + (LenDelimited $manifestName) + [byte[]]@(0x22) + (LenDelimited $attr)

$node = [byte[]]@(0x0A) + (LenDelimited $element)

$src = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $DexSource))
$dexEntry = $src.GetEntry("classes.dex")
if (-not $dexEntry) { throw "no classes.dex en $DexSource" }
$ms = New-Object System.IO.MemoryStream
$s = $dexEntry.Open(); $s.CopyTo($ms); $s.Dispose()
$dex = $ms.ToArray(); $src.Dispose()

$parent = Split-Path -Parent $OutFile
if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
if (Test-Path $OutFile) { Remove-Item $OutFile -Force }
$zip = [System.IO.Compression.ZipFile]::Open($OutFile, 'Create')

function Add-Entry($zip, [string]$name, [byte[]]$content) {
    $e = $zip.CreateEntry($name)
    $st = $e.Open(); $st.Write($content, 0, $content.Length); $st.Dispose()
}

Add-Entry $zip "BundleConfig.pb" ([byte[]]@())
Add-Entry $zip "base/manifest/AndroidManifest.xml" $node
Add-Entry $zip "base/dex/classes.dex" $dex
$zip.Dispose()

Get-Item $OutFile | Select-Object Name, Length
