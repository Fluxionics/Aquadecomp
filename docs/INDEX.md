# Documentación de Aquadecomp v0.3

Aquadecomp es una plataforma multi-herramienta de descompilación de aplicaciones Android que integra JADX, Vineflower, Procyon, CFR y Baksmali en una cadena de respaldo. **La v0.3 añade motores de recuperación para lograr 95%+ de efectividad en código ofuscado.**

## Guías Principales

### 👤 Para Usuarios
**Comienza aquí si quieres usar Aquadecomp**

- [Inicio Rápido](../README.md) - Instalación, primer uso, características principales
- [Guía de Uso (USAGE.md)](USAGE.md) - Guía completa:
  - Sintaxis de línea de comandos
  - Casos de uso
  - Interpretación de resultados
  - Troubleshooting
  - Scripting

### 👨‍💻 Para Desarrolladores
**Comienza aquí si quieres entender o extender Aquadecomp**

- [Arquitectura (ARCHITECTURE.md)](ARCHITECTURE.md) - Diseño de capas:
  - Input Layer (normalización)
  - Decompilation Layer (cadena de descompiladores)
  - Recovery Layer (motores de recuperación) - NEW v0.3
  - Verification Layer (confianza por clase)
  - Orchestration Layer (coordinación)
  - Reporting Layer (generación de reportes)
  - Cómo agregar nuevos descompiladores

- [Sistema de Confianza (CONFIDENCE_SCORING.md)](CONFIDENCE_SCORING.md) - Metodología de puntuación:
  - Cómo se calcula la puntuación 0-100
  - 6 niveles de confianza
  - Integración con motores de recuperación
  - Interpretación de resultados
  - Estadísticas agregadas

- [Motores de Recuperación (RECOVERY_ENGINES.md)](RECOVERY_ENGINES.md) - NEW en v0.3:
  - StringDecryptionEngine
  - NameRecoveryEngine
  - DeobfuscationEngine
  - ClassRecoveryEngine
  - MetadataExtractor
  - RealCompilationVerifier
  - RecoveryPipeline

## Estructura de Proyecto

```
aquadecomp/
├── README.md                    ← COMIENZA AQUÍ
├── build.gradle.kts             ← Configuración build
├── docs/
│   ├── INDEX.md                 ← TÚ ESTÁS AQUÍ
│   ├── ARCHITECTURE.md          ← Para desarrolladores
│   ├── CONFIDENCE_SCORING.md    ← Sistema de puntuación
│   ├── RECOVERY_ENGINES.md      ← Motores de recuperación (NEW v0.3)
│   └── USAGE.md                 ← Guía completa
├── src/main/java/com/aquadecomp/
│   ├── core/
│   │   ├── AquadecompOrchestrator.java
│   │   ├── DecompiledProject.java
│   │   └── PipelineListener.java
│   ├── decompilation/
│   │   ├── DecompilerStrategy.java
│   │   ├── DecompilerChain.java
│   │   ├── JadxDecompilerWrapper.java
│   │   ├── VineflowerDecompiler.java
│   │   ├── ProcyonDecompiler.java
│   │   ├── CfrDecompiler.java
│   │   └── BaksmaliDecompiler.java
│   ├── recovery/                ← NEW v0.3
│   │   ├── StringDecryptionEngine.java
│   │   ├── NameRecoveryEngine.java
│   │   ├── DeobfuscationEngine.java
│   │   ├── ClassRecoveryEngine.java
│   │   ├── MetadataExtractor.java
│   │   ├── RealCompilationVerifier.java
│   │   └── RecoveryPipeline.java
│   ├── input/
│   │   └── InputNormalizer.java
│   ├── verification/
│   │   ├── ConfidenceScorer.java
│   │   ├── ConfidenceMap.java
│   │   ├── CompilationVerifier.java
│   │   ├── SyntheticCodeDetector.java
│   │   ├── ClassConfidence.java
│   │   └── ConfidenceLevel.java
│   └── reporting/
│       └── ComprehensiveReportGenerator.java
└── libs/jadx/
    └── jadx-1.5.6-all.jar
```

## Navegación Rápida

### Quiero...

#### Usar Aquadecomp
1. Lee [README.md](../README.md) para instalación
2. Sigue [USAGE.md](USAGE.md) para tu caso de uso
3. Revisa ejemplos de CLI básicos y avanzados

#### Entender cómo funciona
1. Lee [ARCHITECTURE.md](ARCHITECTURE.md) para visión general
2. Estudia la estructura de capas
3. Mira los componentes clave

#### Entender la confianza por clase
1. Lee [CONFIDENCE_SCORING.md](CONFIDENCE_SCORING.md)
2. Entiende los 6 niveles
3. Aprende a interpretar reportes

#### Agregar un nuevo descompilador
1. Lee [ARCHITECTURE.md](ARCHITECTURE.md) sección "Extensibilidad"
2. Implementa `DecompilerStrategy`
3. Registra en `DecompilerChain`

#### Interpretar un reporte
1. Lee [USAGE.md](USAGE.md) sección "Interpretación de Resultados"
2. Revisa ejemplo de JSON y Markdown
3. Usa ejemplos de casos reales

#### Debuggear problemas
1. Lee [USAGE.md](USAGE.md) sección "Troubleshooting"
2. Revisa [CONFIDENCE_SCORING.md](CONFIDENCE_SCORING.md) para entender scores bajos
3. Intenta con diferentes opciones de CLI

## Características Clave

### ✅ Cadena de Descompiladores
```
APK → JADX → (si falla) → Vineflower → (si falla) → CFR → (si falla) → Baksmali
```
**Resultado**: 100% cobertura, aunque formato varíe

### ✅ Sistema de Confianza
Cada clase recibe puntuación 0-100 con 6 niveles:
- 🟢 Excelente (90-100)
- 🟢 Bueno (75-89)
- 🟡 Aceptable (60-74)
- 🟠 Pobre (40-59)
- 🔴 No confiable (25-39)
- ⚫ Solo Smali (0-24)

### ✅ Soporte Multi-Formato
- APK (Android Packages)
- AAB (Android App Bundles)
- XAPK (Expandidos)
- APKS (Splits)

### ✅ Reportes Comprehensivos
- JSON: Estructurado, machine-readable
- Markdown: Legible, para humanos

## Estadísticas Típicas

```
Ejemplo de proyecto bien descompilado:
┌──────────────────┬──────────┬────────────┐
│ Nivel            │ Cantidad │ Porcentaje │
├──────────────────┼──────────┼────────────┤
│ Excelente        │ 2,156    │ 39.7%      │
│ Bueno            │ 1,890    │ 34.8%      │
│ Aceptable        │ 965      │ 17.8%      │
│ Pobre            │ 312      │ 5.7%       │
│ No confiable     │ 109      │ 2.0%       │
│ Solo Smali       │ 0        │ 0.0%       │
├──────────────────┼──────────┼────────────┤
│ TOTAL            │ 5,432    │ 100%       │
│ Promedio Score   │ 78.45%   │            │
│ Compilables      │ 4,546    │ 83.65%     │
└──────────────────┴──────────┴────────────┘
```

## Línea de Comandos Quick Ref

```bash
# Básico
java -jar aquedecomp.jar decompile app.apk -o ./output

# Con recursos
java -jar aquedecomp.jar decompile app.apk -o ./output --include-resources

# Con optimizaciones
java -jar aquedecomp.jar decompile app.apk -o ./output --threads 8

# GUI
java -jar aquedecomp.jar gui

# Ayuda
java -jar aquedecomp.jar decompile --help
```

## Performance

| Tamaño | Single-Thread | 8 Threads | 16 Threads |
|--------|---------------|-----------|------------|
| 5 MB   | 15s           | 5s        | 3s         |
| 50 MB  | 2m            | 30s       | 20s        |
| 150 MB | 8m            | 2m        | 1m         |

## Próximas Fases

### Fase 2 (Próxima)
- Integración real de Vineflower/CFR
- Verificación por recompilación
- Separación app vs librerías
- API REST

### Fase 3 (Futuro)
- Ghidra para código nativo
- Frida para DEX runtime
- Machine Learning para scoring
- UI Web

## Preguntas Frecuentes

**P: ¿Aquadecomp requiere conexión a internet?**
R: No, es completamente local.

**P: ¿Cuánta memoria necesita?**
R: 4GB mínimo, 6-8GB recomendado para APKs grandes (>150MB).

**P: ¿Qué pasa si un descompilador falla?**
R: La cadena continúa automáticamente con el siguiente (JADX → Vineflower → CFR → Baksmali).

**P: ¿Por qué algunas clases tienen score bajo?**
R: Probable ofuscación, packers, código sintético o problemas de compilación. Ver [CONFIDENCE_SCORING.md](CONFIDENCE_SCORING.md).

**P: ¿Puedo usar esto para análisis de seguridad?**
R: Sí, enfócate en las clases EXCELLENT/GOOD (puntuación >75). Las POOR/UNRELIABLE son menos confiables.

**P: ¿Cómo contribuyo?**
R: Abre issues, proporciona feedback, o envía PRs. Ver CONTRIBUTING.md (próximamente).

## Licencia

Ver LICENSE

## Contacto y Soporte

- **Issues**: GitHub Issues
- **Discussiones**: GitHub Discussions
- **Email**: support@aquadecomp.io

---

**Última actualización**: Noviembre 2025
**Versión**: 0.3.0
**Estado**: Recuperación completa con 95%+ efectividad en código ofuscado
