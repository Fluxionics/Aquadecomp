# Plugins de encriptación

El paquete `crypto` añade plugins que intentan descifrar cadenas cuando el código descompilado contiene la clave embebida como literal. Se cargan junto al motor de descifrado normal (`StringDecryptionEngine`) durante la etapa de recuperación de código.

## Compromiso de honestidad

- Un plugin **solo sustituye código cuando el texto descifrado es imprimible** (ASCII 32-126) y la expresión reconocible es del tipo `new String(...)`
- Si se detecta un patrón de cifrado pero no se puede descifrar (clave no embebida, IV ausente, resultado no imprimible), el plugin **no modifica el código** y deja una nota explicándolo
- Las notas aparecen en el log de la recuperación (hasta 3 por corrida) y en el reporte como "Cadenas descifradas por plugins de encriptación"
- No hay inferencia ni adivinanza de claves: si la clave no está en el código, no se descifra

## Plugins incluidos

| ID | Qué hace |
|----|----------|
| `aes-clave-embebida` | AES (ECB/CBC) con clave literal vía `SecretKeySpec("...".getBytes(), ...)`; para CBC exige además el IV literal. Acepta claves de 16, 24 o 32 bytes |
| `rc4-clave-embebida` | RC4/ARCFOUR con clave literal en el mismo código |
| `xor-constante-base64` | XOR con constante cuando el Base64 cifrado y el valor XOR (`^ 0x..` o `xorBytes(... , (byte) 0x..)`) aparecen en la misma expresión |

El cifrado se reconoce por `Cipher.getInstance("AES...")`, `Cipher.getInstance("RC4")` / `"ARCFOUR"` y las llamadas `Base64.decode("...")` con contenido Base64.

## Cómo se integra

`StringDecryptionEngine.attemptDecryption` ejecuta los plugins antes de los pliegues habituales (arreglos de bytes y Base64). Cada plugin devuelve `PluginOutcome`:

- `replacements`: rangos `[start, end)` del fuente con el texto a sustituir
- `notes`: mensajes en español que explican por qué no se descifró algo

Los contadores por plugin quedan en `lastPluginCounts()` y las notas en `lastNotes()`. `RecoveryPipeline` los acumula en el reporte:

- `Cadenas descifradas por plugins de encriptación`: total de sustituciones aplicadas por plugins
- El log de la etapa incluye hasta 3 notas de los plugins

## Cómo escribir un plugin

1. Implementar `com.aquadecomp.crypto.DecryptionPlugin`:

```java
public interface DecryptionPlugin {
    String id();
    String description();
    PluginOutcome apply(String source);
}
```

2. Registrarlo en `META-INF/services/com.aquadecomp.crypto.DecryptionPlugin` con el nombre completo de la clase (Java `ServiceLoader`)

`PluginRegistry.plugins()` combina los tres plugins integrados con los que encuentre en el classpath, deduplicando por `id` (si dos plugins tienen el mismo id, gana el integrado). Un plugin que lanza una excepción se salta sin frenar la recuperación.

## Límites

- Solo se procesa el código fuente descompilado; no se ataca el binario
- Las claves construidas en tiempo de ejecución (concatenaciones, `StringBuilder`, archivos, red) no se descifran
- El texto descifrado con caracteres fuera de ASCII imprimible se descarta: puede ser otro cifrado o una clave equivocada
- Las sustituciones se aplican sobre la expresión `new String(...)` que envuelve el cifrado; el resto de la llamada (por ejemplo `c.doFinal(...)`) queda intacto
