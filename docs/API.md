# API REST local

Aquadecomp incluye un servidor HTTP local para usar el pipeline desde cualquier lenguaje. No tiene dependencias externas: usa el `HttpServer` del JDK.

## Arranque

```bash
java -jar aquedecomp.jar serve [--host HOST] [--port N]
```

| Opción | Default | Descripción |
|--------|---------|-------------|
| `--host` | `127.0.0.1` | Dirección de escucha |
| `--port` | `8080` | Puerto; `0` elige uno libre |

El servidor escucha en 127.0.0.1 por defecto y no tiene autenticación. Si se cambia el host a una dirección de red, el arranque imprime un aviso: no lo expongas sin poner delante un proxy con autenticación.

Detener con Ctrl+C.

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/` | Índice con la versión y los endpoints disponibles |
| GET | `/health` | Estado y versión |
| POST | `/api/inspect` | Estructura del APK y manifiesto |
| POST | `/api/decompile` | Descompilación completa |
| POST | `/api/rebuild` | Recompilación de fuentes descompilados |
| POST | `/api/natives` | Análisis de bibliotecas `.so` |
| POST | `/api/names` | Inferencia de nombres sobre un directorio de fuentes |

Todas las respuestas son JSON con `Content-Type: application/json; charset=utf-8`.

## Ejemplos

### Health

```bash
curl http://127.0.0.1:8080/health
# {"status":"ok","version":"2.0.1"}
```

### Inspección

```bash
curl -X POST http://127.0.0.1:8080/api/inspect \
  -H "Content-Type: application/json" \
  -d '{"apk": "C:/ruta/app.apk"}'
```

Respuesta: `{ "success": true, "structure": {...}, "manifest": {...} }`.

### Descompilación

```bash
curl -X POST http://127.0.0.1:8080/api/decompile \
  -H "Content-Type: application/json" \
  -d '{"apk": "C:/ruta/app.apk", "output": "C:/salida", "mapping": "C:/ruta/mapping.txt"}'
```

| Campo | Tipo | Requerido | Default | Descripción |
|-------|------|-----------|---------|-------------|
| `apk` | string | sí | - | Ruta del APK/AAB/XAPK/APKS |
| `output` | string | no | `aquadecomp-out/<nombre>` | Directorio de salida |
| `mapping` | string | no | - | Ruta del mapping.txt |
| `deobfuscate` | boolean | no | `true` | Renombrado de JADX |
| `includeResources` | boolean | no | `true` | Extraer recursos |
| `threads` | int | no | `0` (auto) | Threads |

La respuesta es el mismo `PipelineResult` del reporte JSON (`success`, `stats`, `effectiveness`, `confidence`, `errorMessage`, ...). Una descompilación que falla responde 200 con `success: false` y `errorMessage`; un error de petición responde 400.

### Recompilación

```bash
curl -X POST http://127.0.0.1:8080/api/rebuild \
  -H "Content-Type: application/json" \
  -d '{"decompileDir": "C:/salida", "original": "C:/ruta/app.apk",
       "output": "C:/salida/app-nuevo.apk", "androidJar": "C:/android.jar"}'
```

| Campo | Tipo | Requerido | Default |
|-------|------|-----------|---------|
| `decompileDir` | string | sí | - |
| `original` | string | no | detectado del reporte |
| `output` | string | no | `<directorio>/reconstruido.apk` |
| `androidJar` | string | no | `~/.aquadecomp/android.jar` |
| `mapping` | string | no | detectado |
| `files` | lista | no | todas las fuentes |
| `wholeApp` | boolean | no | `false` |
| `minApi` | int | no | `21` |

La respuesta es un `RebuildReport` (`success`, `outputApk`, `outputSha256`, `warnings`, ...).

### Nativos

```bash
curl -X POST http://127.0.0.1:8080/api/natives \
  -H "Content-Type: application/json" \
  -d '{"apk": "C:/ruta/app.apk", "so": "libsignal", "decompile": false}'
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `apk` | string (requerido) | Paquete a analizar |
| `so` | string | Filtro por nombre de biblioteca |
| `decompile` | boolean | Solicitar Ghidra (si no está instalado, se indica en la respuesta) |
| `ghidra` | string | Directorio de instalación de Ghidra |
| `out` | string | Directorio de salida de Ghidra |

Respuesta: `{ "libraries": N, "items": [...], "warnings": [...], "ghidra": {...} }`. Ver [NATIVES.md](NATIVES.md).

### Inferencia de nombres

```bash
curl -X POST http://127.0.0.1:8080/api/names \
  -H "Content-Type: application/json" \
  -d '{"dir": "C:/salida/sources", "out": "C:/salida/nombres.json"}'
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `dir` | string (requerido) | Directorio con fuentes `.java` |
| `out` | string | Si se indica, allí se escribe el informe JSON |

Respuesta: un `NameReport` (`sourceDir`, `filesScanned`, `suggestions`, `notes`). Ver [NAME_INFERENCE.md](NAME_INFERENCE.md).

## Errores

| Código | Cuándo |
|--------|--------|
| 400 | Cuerpo ausente, no JSON, campo requerido faltante o tipo inválido |
| 404 | Ruta desconocida |
| 405 | Método HTTP incorrecto (el mensaje indica el esperado) |
| 500 | Error interno inesperado |

El cuerpo de error siempre es `{ "success": false, "error": "<mensaje en español>" }`.
