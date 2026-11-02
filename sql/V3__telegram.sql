-- Migración MANUAL. Ejecutar antes del despliegue: producción usa ddl-auto=validate.
-- No requiere Flyway ni modifica movimientos financieros existentes.
CREATE TABLE IF NOT EXISTS telegram_conexion (
    user_id BIGINT NOT NULL PRIMARY KEY,
    telegram_user_id BIGINT UNIQUE,
    chat_id BIGINT,
    telegram_username VARCHAR(64),
    vinculo_id UUID,
    vinculado_en TIMESTAMP(6) WITH TIME ZONE,
    codigo_hash VARCHAR(64) UNIQUE,
    codigo_expira_en TIMESTAMP(6) WITH TIME ZONE,
    historial VARCHAR(50000) NOT NULL DEFAULT '[]',
    historial_actualizado_en TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS telegram_update (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    vinculo_id UUID,
    contenido VARCHAR(20000) NOT NULL,
    recibido_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    procesado BOOLEAN NOT NULL DEFAULT FALSE,
    entregado BOOLEAN NOT NULL DEFAULT FALSE,
    respuestas VARCHAR(64000) NOT NULL DEFAULT '[]',
    enviados INTEGER NOT NULL DEFAULT 0,
    fallos INTEGER NOT NULL DEFAULT 0,
    proximo_intento TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_telegram_update_actor ON telegram_update (actor_id, id);
CREATE INDEX IF NOT EXISTS ix_telegram_update_pendientes ON telegram_update (procesado, entregado, id);

CREATE TABLE IF NOT EXISTS telegram_propuesta (
    id UUID NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    telegram_user_id BIGINT NOT NULL,
    vinculo_id UUID NOT NULL,
    descartada BOOLEAN NOT NULL DEFAULT FALSE
);
