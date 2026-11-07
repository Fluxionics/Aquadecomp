# Inferencia de nombres

El comando `nombres` propone nombres descriptivos para identificadores que parecen ofuscados en un directorio de fuentes descompilados. Es un informe de sugerencias: **no renombra ningún archivo**.

## Uso

```bash
java -jar aquedecomp.jar nombres <directorio-fuentes> [--out archivo.json] [--limit N]
```

| Opción | Default | Descripción |
|--------|---------|-------------|
| `--out` | sin archivo | Escribe el informe JSON con todas las sugerencias |
| `--limit` | 40 | Máximo de sugerencias impresas en pantalla |

También está disponible por HTTP: `POST /api/names` (ver [API.md](API.md)).

Ejemplo:

```bash
java -jar aquedecomp.jar decompile app.apk -o salida --no-deobf
java -jar aquedecomp.jar nombres salida/sources --out salida/nombres.json
```

## Qué considera ofuscado

Un identificador se considera ofuscado si:

- es una sola letra (`a`, `b`, `f`)
- es una letra con uno a tres dígitos (`f0`, `a12`)
- son dos o más letras con dígitos (`ab12`, `f0a`)

Nombres legítimos cortos (`id`, `texto`, `contador`) no entran en ningún patrón y nunca se sugieren. Solo se analizan clases, campos y métodos declarados: las variables locales no se tocan.

## Reglas de sugerencia

| Señal | Sugiere | Confianza |
|-------|---------|-----------:|
| Clase con `@Override` y firma `void (View)` | `onClick` | 88 |
| Clase con `@Override` y firma `void (Context, BroadcastReceiver)` | `onReceive` | 88 |
| Clase con `@Override` y firma `boolean (Object)` | `equals` | 88 |
| Clase con `@Override` y firma `String ()` | `toString` | 80 |
| Método `void (View)` en clase que hace `setOnClickListener(this)` | `onClick` | 85 |
| Clase que hereda de algo terminado en `Activity` | `Actividad` | 55 |
| Clase que implementa `Runnable` | `TareaFondo` | 65 |
| Clase que implementa `*OnClickListener` | `ManejadorClick` | 60 |
| Campo `int`/`long` que se incrementa (`++`, `+=`) | `contador` | 45 |
| Campo `String` usado con `.length()` | `texto` | 45 |
| Campo `String` | `texto` | 35 |
| Campo de tipo colección (`List`, `Map`, ...) | `elementos` | 40 |
| Campo arreglo (`[]`) | `valores` | 40 |
| Campo `int`/`long` con literal numérico inicial | `valor` | 30 |

La confianza va de 0 a 95 y refuerza la señal detectada, no garantiza que el nombre sea el original. Las firmas de la tabla son las de la plataforma Android/Java; un método con la misma firma pero de otra interfaz puede recibir una sugerencia errónea, por eso la confianza nunca llega a 100.

## Informe JSON

```json
{
  "sourceDir": "C:/salida/sources",
  "filesScanned": 58416,
  "suggestions": [
    {
      "file": "androidx/activity/ComponentActivity.java",
      "kind": "campo",
      "current": "i",
      "suggested": "contador",
      "confidence": 45,
      "signals": ["de tipo int y se incrementa en el código"]
    }
  ],
  "notes": [
    "Heurística basada en reglas fijas, no en aprendizaje automático.",
    "Solo se sugiere para identificadores que parecen ofuscados ...",
    "No se modifica ningún código: revisa cada sugerencia antes de aplicarla.",
    "La confianza refuerza la señal detectada, no garantiza que el nombre sea el original."
  ]
}
```

Las notas son parte del informe: la heurística es de reglas fijas, no de aprendizaje automático, y ninguna sugerencia se aplica automáticamente.

## Rendimiento

El análisis es por archivo con expresiones regulares, sin construir un AST. Sobre una descompilación grande (58.416 archivos) tarda alrededor de 2 minutos y produce del orden de 13.000 sugerencias; la mayoría son campos de bibliotecas ofuscadas por R8 (`f289g`, `i2`), de confianza baja. Usa `--limit` para revisar por partes y `--out` para el informe completo.

## Límites

- No lee el manifiesto ni el dex: solo el texto de los `.java`
- No distingue entre un nombre ofuscado y una variable local usada como contador con un solo nombre corto
- Las sugerencias pueden repetirse entre archivos (por ejemplo `Actividad` en varias clases)
- Un archivo que no se puede leer se omite del conteo sin frenar el resto
