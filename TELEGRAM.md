# Telegram en Finanzas Personales

El bot reutiliza el agente de IA y las propuestas confirmables del chat web. Solo atiende
conversaciones privadas. No hay token del bot ni claves de IA en Angular.

## Preparación y despliegue

1. Crear el bot con `/newbot` en el BotFather oficial: https://t.me/BotFather.
2. **Antes de desplegar**, ejecutar manualmente `sql/V3__telegram.sql` en la misma base y
   esquema de producción del backend. Se necesitan también las migraciones previas que
   correspondan a la versión desplegada, incluida `sql/V2__propuesta_asistente.sql`.
   Los nombres de estos archivos no implican ejecución automática: no existe Flyway.
   Mantener `ddl-auto=validate` en producción. El script de Telegram puede repetirse y no
   modifica transacciones ni tablas financieras existentes.
3. Configurar las variables del backend en Heroku y reiniciar/desplegar:

| Variable | Valor |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Token secreto entregado por BotFather. |
| `TELEGRAM_BOT_USERNAME` | Usuario exacto del bot, sin `@`, por ejemplo `mis_finanzas_bot`. |
| `TELEGRAM_WEBHOOK_SECRET` | Secreto independiente, aleatorio, de 16 a 256 caracteres `A-Z a-z 0-9 _ -`. |

   Las consultas requieren además la configuración de IA descrita en `ASISTENTE_IA.md`.
   Sin ella todavía es posible vincular/desvincular y usar `/ayuda` y `/nuevo`.
   Con la configuración de Telegram incompleta, su procesador queda inactivo y la UI
   informa que aún no está habilitado.
4. Verificar `/api/health` y `/api/health/db`.
5. Registrar el webhook ejecutando `scripts/configurar-telegram.ps1` desde PowerShell, con
   `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME` y `TELEGRAM_WEBHOOK_SECRET` en el entorno de
   esa terminal (los mismos valores del backend). Agregar en esa terminal:

   ```powershell
   $env:TELEGRAM_WEBHOOK_URL = 'https://TU-BACKEND.herokuapp.com/api/telegram/webhook'
   ./scripts/configurar-telegram.ps1
   ```

   El script consulta `getMe`, registra `message`/`callback_query` con `secret_token` y comprueba
   `getWebhookInfo`. No imprime el token ni el secreto. No borra actualizaciones pendientes.
   `TELEGRAM_WEBHOOK_URL` es una variable del script, no una variable requerida por Spring Boot.
   No se registra ningún webhook automáticamente al iniciar el backend.

## Vinculación desde la app

1. Iniciar sesión con Auth0 y entrar al menú **Conectar Telegram** o al enlace del chat web.
2. Pulsar **Conectar mi Telegram** y después **Abrir Telegram y vincular**.
3. Pulsar **Iniciar** en Telegram. La app comprueba el vínculo automáticamente; también permite
   pulsar **Ya pulsé Iniciar**. El enlace dura diez minutos y solo puede usarse una vez.
4. Para desconectar o cancelar un enlace, usar la misma pantalla. Regenerar un enlace invalida
   el anterior. No compartir enlaces: quien los abra podría vincular su Telegram a esa cuenta.

Cada Telegram puede vincularse a una sola cuenta de la app. La identidad se resuelve por el id
numérico de Telegram, nunca por su nombre, teléfono o correo. Enlaces y desvinculación requieren
el JWT de Auth0; el webhook requiere el secreto enviado por Telegram en
`X-Telegram-Bot-Api-Secret-Token`. Ese único POST es la excepción al JWT.

## Conversación y confirmaciones

- `/start` y `/ayuda` muestran instrucciones; `/nuevo` borra el contexto conversacional.
- Texto de hasta 2.000 caracteres. Grupos, canales y bots se ignoran. No se procesan voz ni archivos.
- Las consultas usan las herramientas existentes del agente. Los resultados se envían como texto
  simple; no se interpreta HTML/Markdown generado por la IA.
- Las propuestas llevan botones **Confirmar y guardar** y **Descartar**. Solo una confirmación
  válida crea el movimiento, reutilizando el bloqueo/idempotencia por UUID del chat web.
- Los botones verifican dueño, usuario de Telegram y generación del vínculo. Desconectar invalida
  los botones antiguos, incluso si se vuelve a conectar el mismo Telegram.
- Se conserva un contexto acotado a diez mensajes en la base del backend. Deja de usarse a los
  30 minutos; `/nuevo` y desconectar lo borran. Esto es independiente de la conversación del chat web.
  Telegram conserva los mensajes según su propia política; los datos necesarios también pasan al
  proveedor de IA. El contexto de Telegram no se guarda en localStorage.

## Webhook, procesamiento y entrega

`POST /api/telegram/webhook` valida el secreto y guarda una bandeja durable por `update_id`.
Devuelve 200 sin esperar a la IA. Un procesador programado en el mismo backend atiende los
eventos en orden por usuario; `FOR UPDATE SKIP LOCKED` evita que dos procesos tomen la misma fila.
Las mutaciones, el contexto y la preparación de respuestas se confirman en una misma transacción.
Un error revierte la operación antes de guardar un aviso. Los mensajes repetidos no reinician eventos.

La entrega a Telegram se registra por separado. Un fallo de envío reintenta la entrega hasta cinco
veces, sin volver a invocar la IA ni confirmar otra transacción. Si Telegram recibió un mensaje
pero se perdió su respuesta HTTP, puede verse una respuesta repetida: no se promete entrega de
mensajes exactamente una vez. Las confirmaciones financieras siguen siendo idempotentes.
Las respuestas financieras pendientes se suprimen si cambió o se eliminó el vínculo.

El backend debe permanecer ejecutándose para atender la bandeja. No hace falta un worker separado.
Los mensajes originales se borran de la bandeja al procesarlos y las respuestas al entregarse.
Los metadatos de eventos y autorizaciones de propuestas se conservan para deduplicación; definir
su retención dentro del mantenimiento de la base de datos. Tras cinco fallos, revisar el log por
id de actualización y la bandeja `telegram_update` (`fallos`, `enviados`, `entregado`). Los logs no
incluyen mensajes, códigos, tokens ni URLs de la API del bot.

## Pruebas

Con JDK 17 y Maven 3.9:

```text
mvn test -Dtest=TelegramIntegrationTest,TelegramSchemaTest,TelegramControllerTest,TelegramApiClientTest
```

Pruebas locales con H2 efímero y servidor HTTP simulado: no contactan a Telegram ni consumen IA.
La prueba del script SQL comprueba su compatibilidad con `Hibernate validate` y su repetición.
Antes de afirmar que producción está lista, aplicar el script allí, configurar el bot/webhook y
verificar un vínculo real, una consulta y una confirmación sin duplicar movimientos.
