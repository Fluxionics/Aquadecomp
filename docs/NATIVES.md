# Análisis de bibliotecas nativas

El comando `natives` analiza los archivos `.so` dentro de un paquete Android. El análisis ELF es propio (parser escrito en Java, sin dependencias externas); el desensamblado con Ghidra es opcional y solo se ejecuta si Ghidra está instalado.

## Uso

```bash
java -jar aquedecomp.jar natives <apk> [--so TEXTO] [--decompile] [--ghidra DIR] [--out DIR] [--limit N]
```

| Opción | Default | Descripción |
|--------|---------|-------------|
| `--so` | sin filtro | Muestra solo bibliotecas cuyo nombre contenga el texto |
| `--decompile` | apagado | Solicita desensamblado con Ghidra headless |
| `--ghidra` | `GHIDRA_INSTALL_DIR` o PATH | Directorio de instalación de Ghidra |
| `--out` | temporal | Directorio para la salida de Ghidra |
| `--limit` | 10 | Máximo de exports impresos por biblioteca |

También está disponible por HTTP: `POST /api/natives` (ver [API.md](API.md)).

## Análisis ELF

Para cada `.so` se leen:

- **Cabecera**: 32/64 bits, little/big endian, `e_machine` y su arquitectura (x86, arm, x86_64, aarch64, ...) y ABI (`arm64-v8a`, `armeabi-v7a`, ...)
- **Secciones**: tabla completa (`.text`, `.dynsym`, `.dynamic`, ...)
- **Símbolos**: exports (símbolos globales definidos), imports (referencias externas) y total
- **Dinámica**: `DT_NEEDED` (bibliotecas de las que depende) y `DT_SONAME`
- **Estado**: si conserva `.symtab` o está *stripped*

Un `.so` corrupto o que no es ELF no detiene el análisis: la biblioteca se lista con su error y el resto se procesa igual.

Ejemplo de salida:

```
lib/arm64-v8a/libsignal_jni.so
  ABI:         arm64-v8a (64 bits, little endian)
  Arquitectura: aarch64  e_machine=183
  Estado:      sin symtab (stripped)
  Secciones:   27
  Símbolos:    791 (exports 660, imports 131)
  Necesita:    libz.so, libdl.so, libm.so, libc.so
  Primeros exports (5/660):
    - Java_org_signal_libsignal_internal_Native_SignalMessage_1Deserialize
    ...
```

Los exports con prefijo `Java_` son puntos de entrada JNI: son las funciones que el código Java de la app puede llamar.

## Ghidra (opcional)

Ghidra **no es requerido**. Con `--decompile`, Aquadecomp busca `support/analyzeHeadless` en este orden:

1. El directorio dado con `--ghidra`
2. La variable de entorno `GHIDRA_INSTALL_DIR`
3. Los directorios del PATH

Si Ghidra está disponible, se ejecuta en modo headless con el script `DecompileFunctions.java` (incluido en el JAR), que descompila hasta 50 funciones a un archivo `.c` en el directorio de salida. El tiempo límite es de 10 minutos.

Si Ghidra **no** está disponible, el comando lo dice con claridad:

```
Ghidra no está disponible: no se encontró analyzeHeadless. Solo se realizó
análisis ELF (símbolos, secciones y dependencias). Instala Ghidra y define
GHIDRA_INSTALL_DIR o indica --ghidra <directorio>.
```

En ese caso el análisis ELF se entrega completo y el comando termina con código 1 cuando se pidió `--decompile`, para que un script lo detecte.

## Códigos de salida

| Código | Condición |
|--------|-----------|
| 0 | Análisis completado (o no se pidió Ghidra) |
| 1 | Error inesperado, o se pidió `--decompile` y Ghidra no está disponible |

## Límites

- El parser ELF cubre ELF32/64 en little y big endian con secciones, símbolos y dinámica; no desensambla código máquina
- La ruta de Ghidra se ejecuta sobre la primera biblioteca ELF que coincida con el filtro (o la primera de la lista); no se analizan todas en serie
- El script de Ghidra limita la salida a 50 funciones para mantener tiempos razonables
