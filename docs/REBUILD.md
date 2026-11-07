# Recompilación y mapping.txt

Aquadecomp puede volver a empaquetar un APK con los cambios hechos en el código fuente descompilado (`rebuild`) y aplicar archivos de mapping de ProGuard/R8 (`--mapping`) en ambos sentidos: restaurar nombres al descompilar y volver a ofuscar el dex recompilado.

## Flujo `rebuild`

```bash
java -jar aquedecomp.jar rebuild <directorio-descompilado> -o <salida.apk> [opciones]
```

Etapas:

1. **Validación** - busca `sources/` en el directorio y localiza el APK original (en `aquedecomp-report.json` o con `--original`)
2. **android.jar** - usa `--android-jar`, luego `~/.aquadecomp/android.jar`
3. **Compilación** - `javac` sobre todas las fuentes `.java` de `sources/`
4. **Conversión a dex** - las clases compiladas se convierten con dexlib2
5. **Reemplazo** - el dex nuevo sustituye al original dentro de una copia del ZIP
6. **Firma** - firma v1+v2+v3 con un certificado propio de Aquadecomp

**Opciones**:

| Opción | Tipo | Default | Descripción |
|--------|------|---------|-------------|
| `-o, --out` | PATH | `<directorio>/reconstruido.apk` | APK de salida |
| `--original` | PATH | detectado del reporte | APK original de donde salen recursos y manifiesto |
| `--android-jar` | PATH | `~/.aquadecomp/android.jar` | android.jar para compilar |
| `--mapping` | PATH | detectado (`aquedecomp-mapping.txt`) | Archivo de mapping |
| `--files` | lista | todas | Fuentes a compilar (modo parche) |
| `--whole-app` | flag | apagado | Recompilar la app completa (reemplaza todo el dex) |
| `--min-api` | INT | 21 | API mínima del dex |

**Ejemplo completo**:

```bash
# 1. Descompilar con mapping (restaura nombres originales)
java -jar aquedecomp.jar decompile app.apk -o salida --mapping mapping.txt

# 2. Editar fuentes en salida/sources/

# 3. Recompilar (el dex vuelve a los nombres del APK original)
java -jar aquedecomp.jar rebuild salida -o app-modificado.apk
```

**Avisos que siempre aparecen**:

- Los recursos, el manifiesto y los assets se conservan del APK original: el rebuild solo aplica cambios de código
- El APK se firma con un certificado propio, distinto del del desarrollador; para instalar puede ser necesario desinstalar la app previa o usar `adb install -r` (ver la sección siguiente)

## Firma con certificado propio e instalación

`rebuild` **no** puede firmar con la clave del desarrollador: esa clave no
está en el APK ni debe usarse. Por eso cada rebuild genera un par de claves
RSA nuevo y lo autocertifica con el sujeto:

```
CN=Aquadecomp Rebuild, O=Aquadecomp
```

y firma el resultado con esquemas **v1 + v2 + v3** (los tres, siempre).

Consecuencias prácticas:

- **El APK reconstruido no es actualización del original.** Android compara
  el certificado del que firma la actualización con el del APK instalado.
  Como son distintos, `adb install -r app-reconstruido.apk` falla con
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- **Para instalar hay que desinstalar antes** (`adb uninstall
  <paquete>` o desinstalar desde el dispositivo). Eso **borra los datos de
  la app**: copia lo que necesites antes.
- **Cada rebuild genera un certificado distinto**, así que dos APKs
  reconstruidos tampoco se actualizan el uno sobre el otro.
- **No aplica a apps de tienda ni de sistema** como destino de
  actualización: esas apps solo reciben actualizaciones firmadas por su
  desarrollador original. El APK reconstruido se instala como app nueva
  (con sus datos propios) después de desinstalar la anterior.
- Las apps que verificen la firma en su propio código (algunos bancos,
  juegos y servicios con DRM) pueden rechazar el APK reconstruido aunque
  el sistema lo instale.

Comprobar quién firmó un APK:

```bash
apksigner verify --print-certs app-reconstruido.apk
```

Debe aparecer `CN=Aquadecomp Rebuild, O=Aquadecomp`. Si aparece otro
nombre, el APK no salió de `rebuild` de Aquadecomp.

## mapping.txt

`--mapping` acepta el mapping que generan ProGuard y R8 (`-keepattributes`, `--pg-map-output`). Se aplica en dos direcciones:

- **Descompilar** (`decompile --mapping archivo.txt`): el mapping se aplica al APK antes de descompilar, así las fuentes salen con los nombres originales de clases y miembros. El archivo se copia a `<salida>/aquedecomp-mapping.txt`.
- **Recompilar** (`rebuild`): detecta automáticamente `aquedecomp-mapping.txt` en la descompilación. Primero restaura el classpath con los nombres originales para que `javac` compile, y después renombra el dex recompilado a los nombres del APK original, para que el resultado mantenga la estructura del paquete ofuscado.

El parser es propio: lee las líneas `clase.obfuscada -> clase.original:` y las líneas de miembros `tipo nombre -> nuevoNombre`, con direcciones inversa (descompilar) y directa (renombrar). Si un nombre es ambiguo entre dos clases, se conserva el original en lugar de arriesgar un renombrado incorrecto.

**Formato soportado**:

```
com.example.Foo -> a.b:
    int contador -> a
    1:1:void calcular(int):7:7 -> a
```

**Límites**:

- Solo funciona con mappings de estilo ProGuard/R8; no procesa mappings de otras herramientas
- Los archivos embebidos (`.apk`, `.aab`, `.apks`, `.xapk`) se recorren hasta 3 niveles de profundidad
- Un mapping incompleto deja los nombres que no aparecen en él sin restaurar (no es un error)

## Verificación

La restauración se comprobó de punta a punta con R8 8.7.18 real: un APK pequeño compilado con `javac`, ofuscado con R8 y su `mapping.txt`, descompilado con `--mapping` produjo `com/example/Foo.java` y `com/example/Bar.java` con sus nombres originales; el `rebuild` posterior generó un APK firmado cuyo dex contenía `a.a`/`a.b` (los nombres del paquete ofuscado) con el cambio aplicado. La matriz de criterios está en [VERIFICATION.md](VERIFICATION.md).
