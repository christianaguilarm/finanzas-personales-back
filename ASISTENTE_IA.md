# Asistente de finanzas

Integración en Spring Boot con un proveedor compatible con Chat Completions y herramientas.
DeepSeek es el proveedor predeterminado. La UI Angular llama exclusivamente a esta API;
la clave del proveedor nunca se entrega al navegador.

## Configuración

Variables del proceso del backend (también funcionan en Heroku Config Vars):

| Variable | Predeterminado | Uso |
| --- | --- | --- |
| `AI_API_KEY` | Vacía | Clave del proveedor. También se acepta `DEEPSEEK_API_KEY`. |
| `AI_BASE_URL` | `https://api.deepseek.com` | Base sin `/chat/completions`. Puede incluir `/v1`. |
| `AI_MODEL` | `deepseek-flash` | Modelo que soporte herramientas. |
| `AI_ENABLED` | `true` | `false` deshabilita nuevas conversaciones. |

Configurar la clave como variable de entorno o secreto del despliegue, nunca en archivos versionados.
Reiniciar el backend después de configurar. Sin clave el endpoint de estado informa
`disponible: false`; el resto de la app sigue funcionando. No hay respuestas financieras simuladas.
El consumo se cobra en la cuenta del proveedor; esta integración no crea una suscripción.

Referencia del protocolo del proveedor: https://api-docs.deepseek.com/

## Alcance de esta versión

- Consultar cuentas y categorías propias, resumen mensual por fecha de transacción y movimientos paginados.
- Consultar proyección y cuadre usando los servicios actuales, con sus reglas de cuotas y recurrencia.
- Preparar hasta tres gastos o ingresos puntuales por mensaje.
- Confirmar cada propuesta desde su tarjeta. Solo entonces se crea la transacción.

Para crear cuotas, recurrentes, comercios o etiquetas, usar el formulario existente.
No incluye edición/eliminación conversacional, WhatsApp, Calendar ni presupuestos por IA.
El resumen mensual incluye compras a crédito y prepago: es un resumen de movimientos
registrados, no el saldo disponible. El catálogo de cuentas contiene el saldo calculado por la app.

## API (bajo el context path `/api`)

Todos los endpoints requieren el bearer token de Auth0; la identidad se resuelve desde el JWT.

- `GET /asistente/estado`: `{disponible, mensaje}`.
- `POST /asistente/chat`: `{mensaje, historial?: [{role: "user"|"assistant", content}]}`.
  Devuelve `{respuesta, propuestas: [{id, expiraEn, transaccion, cuentaNombre, categoriaNombre}]}`.
- `POST /asistente/propuestas/{uuid}/confirmar`: sin datos de transacción en el body.
  Devuelve `{transaccionId, yaConfirmada}`.

Máximo 2.000 caracteres por mensaje y 12 mensajes de historial. El backend limita
el bucle a cuatro rondas, diez llamadas de herramientas y cuatro conversaciones simultáneas
por proceso (una por usuario). Cada llamada al proveedor tiene timeout de conexión de 5 s
y lectura de 25 s. No hay reintentos automáticos que puedan duplicar escrituras.

La conversación se mantiene en memoria de la pestaña mientras dure la sesión, sin localStorage.
La IA recibe los mensajes, el catálogo propio y los resultados de las herramientas consultadas.
La UI muestra este uso de datos antes de enviar. No se envían JWT ni credenciales del usuario al proveedor.

## Persistencia y confirmación

En local, Hibernate crea `propuesta_asistente` con `ddl-auto=update`.
**En producción se usa `ddl-auto=validate`: la tabla debe crearse antes del arranque.**
Ejecutar manualmente `sql/V2__propuesta_asistente.sql` sobre la base de datos de producción,
con el mismo esquema que usa la aplicación. No hay Flyway/Liquibase que ejecute los scripts
de `sql/` automáticamente. El script solo crea la tabla nueva si falta; no borra datos.
Después, reiniciar el backend y verificar `/api/health` y `/api/health/db`.
Mantener `ddl-auto=validate` en producción.

Si el log muestra `Schema-validation: missing table [propuesta_asistente]`, falta ese paso.
Configurar la clave de IA o deshabilitar la IA no resuelve una tabla faltante, porque
Hibernate valida todas las entidades antes de que el backend pueda atender solicitudes.

Las propuestas tienen dueño
y vencen a los 30 minutos. No son transacciones financieras y no alteran saldos.
La confirmación obtiene un bloqueo de fila y crea el movimiento dentro de la misma
transacción de base de datos. Repetir el mismo UUID devuelve el id ya creado, incluso si
el cliente perdió la respuesta anterior. Otro usuario recibe 404; una propuesta vencida, 410.
Las reglas de negocio y pertenencia se revisan de nuevo al guardar.

Las propuestas se conservan para resolver reintentos; el vencimiento impide confirmar
borradores antiguos. La limpieza/retención de estos registros queda para la política de
mantenimiento de la base de datos.

## Verificación

Con JDK 17 y Maven 3.9:

```text
mvn test -Dtest=AsistenteServiceTest,AsistenteIaClientTest,AsistentePropuestaIntegrationTest,AsistenteControllerTest
```

Estas pruebas usan un proveedor HTTP local de prueba y H2 efímero: no consumen tokens ni
modifican Postgres. Cubren autorización, validación, aislamiento de usuarios, errores del
proveedor y confirmaciones concurrentes. Para una prueba real hace falta configurar la clave
y arrancar el backend con el perfil y base de datos habituales.
