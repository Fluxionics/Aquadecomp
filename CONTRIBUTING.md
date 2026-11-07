# Guía de Contribución

Gracias por tu interés en contribuir a Aquadecomp. Este documento
describe cómo preparar el entorno, qué esperamos de cada contribución
y cómo se revisan los cambios.

## Requisitos del Entorno

- JDK 21 o superior
- Android SDK con `ANDROID_HOME` configurado (solo para `rebuild`)
- Gradle (el wrapper incluido, `./gradlew`, se encarga del resto)

Comandos útiles:

```
./gradlew test        # suite completa
./gradlew jar         # genera build/libs/aquedecomp.jar
./gradlew run --args="decompile <apk> -o <salida>"
```

## Reglas del Proyecto

- **Honradez en los resultados.** Ninguna función puede afirmar más de
  lo que verifica. La heurística nunca se describe como aprendizaje
  automático, y ningún módulo afirma descifrar texto salvo que el
  resultado sea imprimible y comprobable. Si una función no está
  disponible (por ejemplo, Ghidra), el mensaje debe decirlo y ofrecer
  lo que sí se puede hacer.
- **Código sin comentarios ni Javadoc.** El código debe explicarse por
  sí mismo; la explicación vive en la documentación de `docs/`.
- **Mensajes de la aplicación en español.** Salidas de CLI, API y GUI
  se escriben en español.
- **Documentación sin emojis y sin texto generado.** Los documentos de
  `docs/` son precisos y directos.
- **Licencia Apache-2.0.** Todo código nuevo debe ser compatible.

## Flujo de Trabajo

1. Abrir una issue antes de empezar cambios relevantes, para acordar
   el alcance.
2. Crear una rama desde `main` con un nombre descriptivo
   (`fix/...`, `feat/...`, `docs/...`).
3. Hacer cambios acotados: una idea por pull request.
4. Asegurar que `./gradlew test` pasa con la suite completa.
5. Abrir el pull request usando la plantilla incluida.

## Pull Requests

El pull request debe indicar:

- Qué problema resuelve o qué funcionalidad agrega
- Cómo se verificó (comandos ejecutados y resultados)
- Si cambia comportamiento observable, qué cambia exactamente

Los cambios que alteran resultados de descompilación, puntuación de
confianza o reportes deben incluir evidencia de una corrida real
(APK o fixture) comparada antes y después.

## Commits

- Mensajes claros en imperativo, en inglés o español coherente con el
  historial del repositorio.
- Un commit no debe mezclar cambios funcionales con reformato.

## Licencia de las Contribuciones

Al contribuir, aceptas que tu aporte se distribuya bajo la licencia
Apache-2.0 del proyecto.
