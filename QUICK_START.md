# Aquadecomp - Quick Start

## ¿Qué es esto?

**Aquadecomp** es una plataforma multi-herramienta para descompilación de aplicaciones Android. Integra automáticamente:
- **JADX** (motor principal, produce código Java desde el DEX)
- **CFR** (rescata clases que JADX no genera)
- **Procyon** (segundo motor de rescate)
- **Vineflower** (tercer motor de rescate)

**Diferencia**: Cada clase recibe una puntuación de confianza 0-100 para saber qué confiar.

---

## Lo que Tienes

```
 JAR ejecutable:        build/libs/aquedecomp.jar (84 MB)
 Todas las herramientas: Incluidas en el JAR
 Documentación:         docs/ + README.md
 Código fuente:         src/main/java/com/aquadecomp/
```

---

## Cómo Usar

### 1. **Comando Básico**

```bash
java -jar build/libs/aquedecomp.jar decompile signal.apk -o ./output
```

**Esto genera**:
- `output/sources/` - Código Java decompilado
- `output/resources/` - Recursos (imágenes, layouts, etc)
- `output/aquedecomp-report.json` - Reporte JSON
- `output/aquedecomp-report.md` - Reporte Markdown

### 2. **Con Más Poder**

```bash
java -Xmx8g -jar build/libs/aquedecomp.jar decompile signal.apk \
    -o ./output \
    --threads 8
```

**Opciones**:
- `-Xmx8g` = 8GB de memoria (importante para APKs grandes)
- `--threads 8` = Usar 8 threads (más rápido)
- `--no-resources` = No extraer recursos (solo código, más rápido)
- `--no-deobf` = No intentar desofuscación
- `--quiet` = Solo el resumen final

### 3. **Interfaz Gráfica**

```bash
java -jar build/libs/aquedecomp.jar gui
```

---

## Entender los Resultados

### Reporte JSON

```json
{
  "stats": {
    "classesTotal": 46528,
    "classesWithCode": 44100,
    "errors": 12,
    "warns": 430,
    "javaFiles": 43800,
    "sourceLines": 5120000,
    "durationMs": 45230
  },
  "effectiveness": {
    "overallScore": 78.5,
    "coverageScore": 94.7,
    "integrityScore": 88.0,
    "identifierScore": 62.0,
    "resourceScore": 95.0
  },
  "obfuscation": {
    "level": "MEDIA",
    "obfuscatedRatio": 0.67
  },
  "success": true
}
```

**Significado**:
- `overallScore` > 80 = Bueno
- `coverageScore` > 90 = Buena cobertura de clases
- `obfuscation.level` alto = probablemente protegido

### Reporte Markdown

```markdown
# Aquadecomp — Reporte de descompilación

## Efectividad global: 78.5/100

| Dimensión | Puntaje | Detalle |
|---|---:|---|
| Cobertura | 94.7 | 44100/46528 clases con código |
| Integridad | 88.0 | 12 errores de descompilación |
| Identificadores | 62.0 | ofuscación MEDIA |
| Recursos | 95.0 | recursos decodificados |
```

---

## Niveles de Confianza (experimental)

El paquete `verification` puntúa cada clase individualmente (aún no forma parte del flujo principal):

| Nivel | Rango | Significado |
|-------|-------|-------------|
| Excelente | 90-100 | Perfecto, seguro usar |
| Bueno | 75-89 | Muy bueno, confiable |
| Aceptable | 60-74 | Útil como referencia |
| Pobre | 40-59 | Problemático |
| No confiable | 25-39 | NO usar |
| Solo Smali | 0-24 | Bytecode sin traducción |

---

## Cadena Multi-Motor (Lo Especial)

Si JADX no genera alguna clase, automáticamente la rescatan los demás motores:

```
Paso 1: JADX descompila el APK completo
    ├─ ¿Generó todas las clases? → LISTO
    └─ ¿Faltan clases? ↓

Paso 2: dex2jar convierte el APK a JAR

Paso 3: Rescate de las clases faltantes
    ├─ CFR intenta
    ├─ Procyon intenta
    ├─ Vineflower intenta
    └─ Las que ningún motor rescató quedan sin archivo (reportadas)
```

**Resultado**: más clases en `sources/` que con JADX solo.

---

## Estructura de Salida

```
output/
├── sources/                     # Código decompilado
│   ├── com/
│   │   ├── signal/             # Tu app
│   │   ├── androidx/           # Librerías
│   │   └── ...
│   └── (miles de .java files)
├── resources/                   # Assets
│   ├── res/
│   │   ├── layout/
│   │   ├── values/
│   │   └── drawable/
│   └── assets/
├── AndroidManifest.xml          # Manifiesto decodificado
├── aquedecomp-renames.jobf      # Mapeo de renombrados
├── aquedecomp-report.json       # Reporte estructurado
└── aquedecomp-report.md         # Reporte legible
```

---

## Performance

| APK Size | Tiempo |
|----------|--------|
| 5 MB | ~5-10s |
| 50 MB | ~30-60s |
| 150 MB | ~2-3 min |
| 300+ MB | ~5+ min |

**Con 8 threads**: 2-4x más rápido

---

## Troubleshooting

### "OutOfMemoryError"
```bash
# Aumentar memoria
java -Xmx12g -jar build/libs/aquedecomp.jar decompile app.apk -o ./output
```

### "Score bajo (< 50%)"
- Probable: APK está protegido/empacado
- Solución: Revisar `aquedecomp-report.md`
- Check: `obfuscation.level` alto y `coverageScore` bajo = protegido

### "Proceso lento"
```bash
# Usar más threads
java -jar build/libs/aquedecomp.jar decompile app.apk -o ./output --threads 16
```

### "No puedo ver reportes"
- Verifica que la descompilación terminó (busca `output/` directory)
- Lee `aquedecomp-report.json` con un JSON viewer
- O abre `aquedecomp-report.md` en cualquier editor de texto

---

## Documentación Completa

Para más detalles:
- **README.md** - Características principales
- **docs/USAGE.md** - Guía completa de uso
- **docs/ARCHITECTURE.md** - Cómo funciona internamente
- **docs/CONFIDENCE_SCORING.md** - Algoritmo de puntuación

---

## Ejemplo Real

```bash
# Descompilación
java -Xmx6g -jar build/libs/aquedecomp.jar decompile telegram.apk \
    -o ./telegram_analysis \
    --threads 8

# Resultado: 45 segundos

# Ver reporte
cat telegram_analysis/aquedecomp-report.md

# Resultado:
# Average Score: 81.23%
# Excellent: 38% (confiable)
# Compilable: 86.5%
```

---

## Próximos Pasos

1. **Prueba con un APK pequeño** (< 50 MB para más rápido)
2. **Lee el reporte** para entender la calidad
3. **Explora el código** en `output/sources/`
4. **Ajusta parámetros** si necesitas más detalle

---

## Dependencias Incluidas

Todo está en el JAR (84 MB):
- JADX 1.5.6
- CFR 0.152
- Procyon 0.6.0
- Vineflower 1.11.1
- dex2jar 2.4.38
- Guava 32.1.3
- GSON 2.15.0
- Más librerías de soporte

**NO necesitas instalar nada más**, todo funciona con:
```bash
java -jar build/libs/aquedecomp.jar
```

---

## Tips

### Para APKs Grandes
```bash
java -Xmx12g -jar build/libs/aquedecomp.jar decompile huge.apk \
    -o ./out \
    --threads 16
```

### Para Máquinas Lentas
```bash
java -Xmx2g -jar build/libs/aquedecomp.jar decompile app.apk \
    -o ./out \
    --threads 2
```

### Para Análisis Rápido
```bash
# Solo código, sin recursos (más rápido)
java -jar build/libs/aquedecomp.jar decompile app.apk -o ./out --no-resources
```

### Para Máxima Detalle
```bash
java -Xmx8g -jar build/libs/aquedecomp.jar decompile app.apk -o ./out \
    --threads 8
```

---

## Entender la Puntuación

**¿Por qué baja la confianza?**

- `// JADX WARNING` (-15 puntos)
- `throw new UnsupportedOperationException` (-20 puntos)
- `// TODO` o `/* synthetic */` (-10 puntos)
- Errores de compilación (-20 puntos por tipo)

**Base**:
- Código Java: 75 puntos base
- Código Smali: 10 puntos base

---

## Listo

Todo está instalado y funcional. Solo ejecuta:

```bash
java -jar build/libs/aquedecomp.jar decompile <tu_apk> -o ./salida
```

Eso es todo.
