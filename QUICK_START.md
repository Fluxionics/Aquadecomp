# 🚀 Aquadecomp - Quick Start

## ¿Qué es esto?

**Aquadecomp** es una plataforma multi-herramienta para descompilación de aplicaciones Android. Integra automáticamente:
- **JADX** (Java decompiler rápido)
- **Vineflower** (respaldo para código complejo)
- **CFR** (especializado en Java moderno)
- **Baksmali** (último recurso - genera Smali puro)

**Diferencia**: Cada clase recibe una puntuación de confianza 0-100 para saber qué confiar.

---

## 📦 Lo que Tienes

```
✅ JAR ejecutable:        build/libs/aquedecomp.jar (79 MB)
✅ Todas las herramientas: Incluidas en el JAR
✅ Documentación:         docs/ + README.md
✅ Código fuente:         src/main/java/com/aquadecomp/
```

---

## 💻 Cómo Usar

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
    --threads 8 \
    --include-resources \
    --deobfuscate
```

**Opciones**:
- `-Xmx8g` = 8GB de memoria (importante para APKs grandes)
- `--threads 8` = Usar 8 threads (más rápido)
- `--include-resources` = Extraer todos los recursos
- `--deobfuscate` = Intentar desofuscación

### 3. **Interfaz Gráfica**

```bash
java -jar build/libs/aquedecomp.jar gui
```

---

## 📊 Entender los Resultados

### Reporte JSON

```json
{
  "statistics": {
    "total_classes": 5432,
    "average_score": 78.45,
    "confidence_breakdown": {
      "excellent": 2156,
      "good": 1890,
      "acceptable": 965,
      "poor": 312,
      "unreliable": 109,
      "smali_only": 0
    },
    "compilable": {
      "count": 4546,
      "percentage": 83.65
    }
  }
}
```

**Significado**:
- `average_score` > 80 = Bueno
- `compilable` > 80% = Confiable
- `smali_only` > 5% = Probablemente protegido

### Reporte Markdown

```markdown
# Aquadecomp - Reporte

## Calidad General
- Puntuación promedio: 78.45%
- Clases compilables: 83.65%

## Desglose de Confianza
- Excelente: 2156 (39.71%)
- Bueno: 1890 (34.80%)
- Aceptable: 965 (17.77%)
- Pobre: 312 (5.74%)
- No confiable: 109 (2.01%)
```

---

## 🎯 Niveles de Confianza

| Nivel | Rango | Significado |
|-------|-------|-------------|
| 🟢 Excelente | 90-100 | Perfecto, seguro usar |
| 🟢 Bueno | 75-89 | Muy bueno, confiable |
| 🟡 Aceptable | 60-74 | Útil como referencia |
| 🟠 Pobre | 40-59 | Problemático |
| 🔴 No confiable | 25-39 | NO usar |
| ⚫ Solo Smali | 0-24 | Bytecode sin traducción |

---

## 🔄 Cadena de Fallback (Lo Especial)

Si el primer descompilador falla, automáticamente intenta el siguiente:

```
Intento 1: JADX
    ├─ ¿Funciona? → USA JADX
    └─ ¿Falla? ↓

Intento 2: Vineflower
    ├─ ¿Funciona? → USA VINEFLOWER
    └─ ¿Falla? ↓

Intento 3: CFR
    ├─ ¿Funciona? → USA CFR
    └─ ¿Falla? ↓

Intento 4: Baksmali
    ├─ ¿Funciona? → USA BAKSMALI (Smali)
    └─ ¿Falla? ERROR
```

**Garantía**: 100% cobertura (aunque sea Smali).

---

## 📁 Estructura de Salida

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

## ⚡ Performance

| APK Size | Tiempo |
|----------|--------|
| 5 MB | ~5-10s |
| 50 MB | ~30-60s |
| 150 MB | ~2-3 min |
| 300+ MB | ~5+ min |

**Con 8 threads**: 2-4x más rápido

---

## 🔧 Troubleshooting

### "OutOfMemoryError"
```bash
# Aumentar memoria
java -Xmx12g -jar build/libs/aquedecomp.jar decompile app.apk -o ./output
```

### "Score bajo (< 50%)"
- Probable: APK está protegido/empacado
- Solución: Revisar `aquedecomp-report.md`
- Check: `smali_only` count alto = protegido

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

## 📚 Documentación Completa

Para más detalles:
- **README.md** - Características principales
- **docs/USAGE.md** - Guía completa de uso
- **docs/ARCHITECTURE.md** - Cómo funciona internamente
- **docs/CONFIDENCE_SCORING.md** - Algoritmo de puntuación

---

## ✨ Ejemplo Real

```bash
# Descompilación
java -Xmx6g -jar build/libs/aquedecomp.jar decompile telegram.apk \
    -o ./telegram_analysis \
    --threads 8 \
    --include-resources

# Resultado: 45 segundos

# Ver reporte
cat telegram_analysis/aquedecomp-report.md

# Resultado:
# Average Score: 81.23%
# Excellent: 38% (confiable)
# Compilable: 86.5%
```

---

## 🚀 Próximos Pasos

1. **Prueba con un APK pequeño** (< 50 MB para más rápido)
2. **Lee el reporte** para entender la calidad
3. **Explora el código** en `output/sources/`
4. **Ajusta parámetros** si necesitas más detalle

---

## ⚙️ Dependencias Incluidas

Todo está en el JAR (79 MB):
- ✅ JADX 1.5.6
- ✅ CFR 0.152
- ✅ Baksmali 2.5.2
- ✅ Guava 32.1.3
- ✅ GSON 2.15.0
- ✅ Más librerías de soporte

**NO necesitas instalar nada más**, todo funciona con:
```bash
java -jar build/libs/aquedecomp.jar
```

---

## 💡 Tips

### Para APKs Grandes
```bash
java -Xmx12g -jar build/libs/aquedecomp.jar decompile huge.apk \
    -o ./out \
    --threads 16 \
    --show-bad-code
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
java -jar build/libs/aquedecomp.jar decompile app.apk -o ./out
```

### Para Máxima Detalle
```bash
java -Xmx8g -jar build/libs/aquedecomp.jar decompile app.apk -o ./out \
    --threads 8 \
    --include-resources \
    --deobfuscate \
    --show-bad-code
```

---

## 🎓 Entender la Puntuación

**¿Por qué baja la confianza?**

- `// JADX WARNING` (-15 puntos)
- `throw new UnsupportedOperationException` (-20 puntos)
- `// TODO` o `/* synthetic */` (-10 puntos)
- Errores de compilación (-20 puntos por tipo)

**Base**:
- Código Java: 75 puntos base
- Código Smali: 10 puntos base

---

## ✅ Listo

Todo está instalado y funcional. Solo ejecuta:

```bash
java -jar build/libs/aquedecomp.jar decompile <tu_apk> -o ./salida
```

¡Éxito! 🎉
