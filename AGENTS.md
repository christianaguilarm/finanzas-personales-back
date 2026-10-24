# AGENTS.md

Backend Spring Boot / Java 17. Ejecutar Git dentro de este repositorio.

## Verificación

- CI: `mvn --batch-mode --no-transfer-progress verify` con el perfil `ci` y un PostgreSQL temporal aislado, como define `.github/workflows/ci.yml`.
- El wrapper Maven no tiene su configuración completa; usar Maven instalado y JDK 17.
- Producción usa `ddl-auto=validate`: cualquier cambio del esquema requiere una migración explícita antes del despliegue. No cambiar a `update` para resolver un fallo de producción.
- La integración con Heroku espera que los checks de GitHub pasen antes de desplegar `main`.

## Flujo de trabajo y despliegue

- Para cada funcionalidad, corrección o tarea, crear una rama nueva desde `origin/develop` actualizado. El nombre real de la rama de desarrollo es `develop` (aunque el usuario la llame «development»).
- Trabajar en esa rama, validar los cambios con las pruebas y el build pertinentes, y al terminar crear los commits y pushear la rama. Usar mensajes cortos en español y minúsculas, sin prefijos ni tickets.
- Detenerse después del push si el usuario no autorizó desplegar. No mergear a `develop` ni a `main` por iniciativa propia.
- Si el usuario confirma el despliegue, o ya lo autorizó explícitamente en el pedido vigente: crear un PR de la rama de trabajo hacia `develop`, esperar los checks y mergearlo; luego borrar únicamente la rama de trabajo local y remota.
- A continuación crear un PR de `develop` hacia `main`, esperar los checks y mergearlo. Usar merge commits para conservar el historial; resolver conflictos preservando los cambios vigentes y repetir las validaciones afectadas.
- Verificar el despliegue real del commit de `main`: CI verde no equivale a despliegue exitoso. En backend revisar el despliegue de Heroku y `/api/health` y `/api/health/db`; en frontend revisar que el despliegue de producción de Vercel esté listo y corresponda al commit mergeado.
- Si el despliegue falla, investigar y corregir el problema dentro del alcance autorizado. Si falta acceso o hace falta una decisión del usuario, explicar qué está bloqueado y qué evidencia falta. No afirmar que se desplegó sin verificarlo.
- Los dos repositorios son independientes: ramas, commits, PRs y verificaciones separados. No modificar ni borrar `develop` ni `main` como parte de la limpieza.
