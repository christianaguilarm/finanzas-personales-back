-- =============================================================================
-- V1__auth0_setup.sql
--
-- Migracion manual para la integracion con Auth0. NO es Flyway: aplicala a mano.
--
--   docker exec -i finanzas-postgres psql -U finanzas_user -d finanzas_personales \
--     -v ON_ERROR_STOP=1 -f - < sql/V1__auth0_setup.sql
--
-- (desde la raiz de finanzas-personales-back, en Git Bash / WSL)
--
-- El script es idempotente: puedes correrlo mas de una vez sin romper nada.
-- Verificado contra el esquema real (PostgreSQL 15, Hibernate ddl-auto=update).
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Columna auth0_sub
--
-- Identificador de Auth0, formato 'auth0|<hash>'. Es la UNICA clave por la que
-- el backend resuelve un usuario a partir del JWT: ya no se usa el email.
--
-- Nullable a proposito. ddl-auto=update crea la columna pero NO la puebla, asi
-- que las filas existentes quedan en NULL hasta que se bindingeen a mano.
-- En PostgreSQL un UNIQUE admite multiples NULL, asi que las filas legacy
-- conviven sin conflicto entre si.
-- -----------------------------------------------------------------------------
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS auth0_sub varchar(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_auth0_sub
    ON app_user (auth0_sub);

COMMENT ON COLUMN app_user.auth0_sub IS
    'Identificador de Auth0 (sub del JWT). Clave unica de identidad; el email ya no identifica.';


-- -----------------------------------------------------------------------------
-- 2. Liberar el UNIQUE de email
--
-- ANTES: app_user.email era la clave de identidad, por eso era UNIQUE.
-- AHORA: el email es solo un dato de perfil. Auth0 ya garantiza la unicidad del
--        email dentro del tenant, asi que el UNIQUE local es redundante.
--
-- Sin este DROP, un usuario nuevo que se registre en Auth0 con un email que ya
-- existe en la tabla revienta el INSERT y su login queda roto de forma
-- permanente (no se puede reintentar nunca mas).
--
-- El nombre de la constraint lo autogenero Hibernate y es un hash, no un
-- nombre legible. Este es el nombre real verificado en la base de desarrollo.
-- -----------------------------------------------------------------------------
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS uk1j9d9a06i600gd43uu3km82jw;

-- Reemplazado por un indice NO unico: sirve para buscar, sin imponer unicidad.
CREATE INDEX IF NOT EXISTS ix_app_user_email
    ON app_user (lower(email));


-- -----------------------------------------------------------------------------
-- 3. password_hash -> nullable
--
-- Con Auth0 ya no hay login local, asi que este valor no verifica nada: es un
-- bcrypt aleatorio escrito solo para cumplir el NOT NULL. La columna miente
-- sobre el modelo de seguridad de la app.
--
-- Se relaja el NOT NULL y el servicio de provisioning deja de escribirla.
-- Su valor actual ($2a$10$...) se conserva intacto como historico.
-- -----------------------------------------------------------------------------
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;


-- -----------------------------------------------------------------------------
-- 4. Ampliar nombre a 300
--
-- El claim 'name' de Auth0 puede venir mas largo que 120 caracteres (sobre todo
-- con login social), y varchar(120) revienta el INSERT por truncamiento.
--
-- IMPORTANTE: si la entidad AppUser sigue declarando length = 120, Hibernate
-- con ddl-auto=update REVIERTE este cambio en el siguiente arranque (verificado:
-- se aplico esto, arranco la app y la columna volvio a varchar(120)).
-- La entidad manda. Hay que cambiar length = 300 en model/AppUser.java ANTES o
-- junto con este script, o el cambio se pierde.
-- -----------------------------------------------------------------------------
ALTER TABLE app_user ALTER COLUMN nombre TYPE varchar(300);


-- -----------------------------------------------------------------------------
-- 5. Indices en foreign keys
--
-- PostgreSQL NO crea indices en columnas de foreign key. Sin esto, toda consulta
-- filtrada por user_id es un seq scan.
--
-- Con Auth0 esto pasa en CADA request: primero se resuelve el AppUser por
-- auth0_sub y despues se filtra por user_id.
--
-- El compuesto (user_id, fecha) es el que mas rinde: casi todas las consultas
-- son "transacciones de este usuario en este rango de fechas".
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_transaccion_user_id
    ON transaccion (user_id);

CREATE INDEX IF NOT EXISTS ix_transaccion_user_fecha
    ON transaccion (user_id, fecha);

CREATE INDEX IF NOT EXISTS ix_transaccion_cuenta_id
    ON transaccion (cuenta_id);

CREATE INDEX IF NOT EXISTS ix_transaccion_categoria_id
    ON transaccion (categoria_id);

CREATE INDEX IF NOT EXISTS ix_transaccion_subcategoria_id
    ON transaccion (subcategoria_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_user_id
    ON cuenta (user_id);

CREATE INDEX IF NOT EXISTS ix_categoria_user_id
    ON categoria (user_id);

CREATE INDEX IF NOT EXISTS ix_comecio_user_id
    ON comercio (user_id);

CREATE INDEX IF NOT EXISTS ix_tag_user_id
    ON tag (user_id);

COMMIT;


-- =============================================================================
-- 6. MANUAL: bindear el usuario preexistente  (NO se puede automatizar)
-- =============================================================================
--
-- ESTE ES EL PASO CRITICO Y EL MAS FACIL DE HACER MAL.
--
-- Con la estrategia de vinculacion elegida, el backend SOLO busca por auth0_sub.
-- Si este UPDATE no se ha ejecutado, el primer login de christianaguilarm1@gmail.com
-- creara un AppUser NUEVO y vacio, y sus 5 cuentas y 355 transacciones quedaran
-- en la fila id=1, inaccesibles. No hay error ni aviso: solo silencio.
--
-- Orden obligatorio:
--   1. Copiar el 'sub' del usuario en Auth0
--      User Management > Users > [email] > campo 'sub'  (formato 'auth0|abc123')
--   2. Ejecutar el UPDATE de abajo con ese valor
--   3. SOLO DESPUES, hacer login con esa cuenta
--
-- Repite el paso 1 y 2 para cada tenant: los subs son distintos en dev y prod.
-- El correo NO cambia entre tenants, pero el 'sub' SI.
-- -----------------------------------------------------------------------------
--
-- UPDATE app_user SET auth0_sub = 'auth0|PEGA_AQUI_EL_SUB' WHERE id = 1;
--
-- Verificacion (debe devolver 1 fila con el sub correcto):
--
--   SELECT id, email, auth0_sub FROM app_user ORDER BY id;
--

-- =============================================================================
-- 7. Utilidad: detectar basura acumulada
-- =============================================================================
--
-- Sin verificacion de email, cualquiera que se registre en Auth0 y llame a la
-- API crea una fila. Para una app personal es ruido tolerable, pero esto lista
-- los candidatos para borrar a mano (los que nunca crearon nada).
--
--   SELECT u.id, u.email, u.creado_en
--   FROM app_user u
--   WHERE NOT EXISTS (SELECT 1 FROM cuenta c      WHERE c.user_id = u.id)
--     AND NOT EXISTS (SELECT 1 FROM transaccion t WHERE t.user_id = u.id)
--     AND u.creado_en < now() - interval '30 days';
--
-- Aviso: antes de borrar uno, confirma que su auth0_sub no corresponde a
-- alguien real. Si tiene auth0_sub, es un usuario de verdad, no basura.
-- =============================================================================


-- =============================================================================
-- 8. Comportamiento de ddl-auto=update (verificado, no es teoria)
-- =============================================================================
--
-- Se aplico este script y luego se arranco la app con el perfil local.
-- Resultado observado:
--
--   email UNIQUE        NO se re-creo. El DROP se mantiene entre arranques.
--   auth0_sub           se mantiene, y se queda nullable.
--   password_hash        se mantiene nullable. El NOT NULL NO vuelve.
--   nombre               REVERTIDO a varchar(120) por Hibernate.
--
-- Conclusion: ddl-auto=update solo anade lo que falta, no revierte cambios de
-- nulabilidad ni constraints. La UNICA excepcion es el TIPO de la columna, que
-- Hibernate si re-sincroniza contra lo que declare la entidad.
--
-- Por eso el punto 4 depende de cambiar model/AppUser.java a length = 300.
-- Mientras la entidad diga 120, ese ALTER se pierde en cada arranque.
-- =============================================================================
