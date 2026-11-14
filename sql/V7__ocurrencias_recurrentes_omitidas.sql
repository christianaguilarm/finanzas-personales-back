-- Ejecutar manualmente antes del despliegue; producción usa ddl-auto=validate.
-- Meses en que el usuario omitió una ocurrencia de una transacción recurrente en la proyección.
CREATE SEQUENCE IF NOT EXISTS ocurrencia_recurrente_omitida_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ocurrencia_recurrente_omitida (
    id BIGINT NOT NULL DEFAULT nextval('ocurrencia_recurrente_omitida_id_seq'),
    user_id BIGINT NOT NULL,
    clave_recurrente VARCHAR(64) NOT NULL,
    periodo VARCHAR(7) NOT NULL,
    CONSTRAINT pk_ocurrencia_recurrente_omitida PRIMARY KEY (id),
    CONSTRAINT uk_omision_recurrente_usuario_clave_periodo UNIQUE (user_id, clave_recurrente, periodo),
    CONSTRAINT fk_omision_recurrente_usuario FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX IF NOT EXISTS ix_omision_recurrente_usuario_periodo
    ON ocurrencia_recurrente_omitida (user_id, periodo);

ALTER SEQUENCE ocurrencia_recurrente_omitida_id_seq OWNED BY ocurrencia_recurrente_omitida.id;
