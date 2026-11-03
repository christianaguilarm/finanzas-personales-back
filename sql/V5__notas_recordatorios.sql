-- Ejecutar manualmente antes del despliegue; producción usa ddl-auto=validate.
CREATE SEQUENCE IF NOT EXISTS nota_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE IF NOT EXISTS nota (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    texto VARCHAR(1000) NOT NULL,
    fecha_limite DATE,
    resuelta BOOLEAN NOT NULL DEFAULT FALSE,
    solicitud_id UUID,
    CONSTRAINT uk_nota_usuario_solicitud UNIQUE (user_id, solicitud_id)
);
CREATE INDEX IF NOT EXISTS ix_nota_user_fecha ON nota (user_id, resuelta, fecha_limite);
CREATE TABLE IF NOT EXISTS nota_aviso (
    id UUID PRIMARY KEY,
    nota_id BIGINT NOT NULL REFERENCES nota(id) ON DELETE CASCADE,
    fecha_aviso DATE NOT NULL,
    vinculo_id UUID NOT NULL,
    chat_id BIGINT NOT NULL,
    entregado BOOLEAN NOT NULL DEFAULT FALSE,
    fallos INTEGER NOT NULL DEFAULT 0,
    proximo_intento TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_nota_aviso_dia UNIQUE (nota_id, fecha_aviso)
);
CREATE INDEX IF NOT EXISTS ix_nota_aviso_pendiente ON nota_aviso (entregado, proximo_intento);
