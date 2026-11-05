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
- `--no-deobf` = Desactivar el renombrado de nombres de JADX
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
  "inputType": "APK",
  "confidence": {
    "total": 6425,
    "averageScore": 68.0,
    "structurallyValid": 6146
  },
  "recovery": {
    "filesProcessed": 6425,
    "filesModified": 0,
    "javacSampled": 40,
    "javacCompiled": 16
  },
  "success": true
}
```

**Significado**:
- `overallScore` > 80 = Bueno
- `coverageScore` > 90 = Buena cobertura de clases
- `obfuscation.level` alto = probablemente protegido
- `confidence.averageScore` = confianza media por clase
- `recovery` = qué hizo la etapa de recuperación
- `inputType` = formato detectado (APK, AAB, XAPK o APKS)
- `engineByFile` = qué motor generó cada archivo de `sources/`

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

## Niveles de Confianza

El paquete `verification` puntúa cada clase individualmente; la etapa "PUNTUANDO CONFIANZA" la ejecuta en cada descompilación:

| Nivel | Rango | Significado |
|-------|-------|-------------|
| Excelente | 90-100 | Perfecto, seguro usar |
| Bueno | 75-89 | Muy bueno, confiable |
| Aceptable | 60-74 | Útil como referencia |
| Pobre | 40-59 | Problemático |
| No confiable | 0-39 | NO usar |

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

Tiempos totales medidos con el flujo completo (las siete etapas):

| APK Size | Tiempo |
|----------|--------|
| 50 KB | ~89 s |
| 4.6 MB | ~465 s |
| 110 MB | ~1307 s (≈22 min) |

Las apps con decenas de miles de clases pasan la mayor parte de ese tiempo en
descompilación y en la puntuación de confianza. Con `--threads 8` la
descompilación se acelera; el total varía según la máquina.

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
# Descompilación (medida en esta máquina)
java -jar build/libs/aquedecomp.jar decompile mini.apk \
    -o ./mini_analysis

# Resultado: 465 segundos, 6425 archivos .java
# Efectividad global: 91.8/100
# Motor por archivo: JADX 6360, CFR 63, Procyon 2

# Ver reporte
cat mini_analysis/aquedecomp-report.md

# Resumen (sección Confianza por clase):
# Puntaje medio: 68.0/100
# Fiables (excelente + bueno + aceptable): 81.0%
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
- `// TODO` o `/* FIXME */` (-5 puntos)
- `/* missing */` o `/* synthetic */` (-10 puntos)
- Patrones de error de compilación en el texto (-5 a -20)

**Base**:
- 75 puntos para todas las clases

---

## Listo

Todo está instalado y funcional. Solo ejecuta:

```bash
java -jar build/libs/aquedecomp.jar decompile <tu_apk> -o ./salida
```

Eso es todo.
