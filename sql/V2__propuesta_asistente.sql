-- Ejecutar antes de arrancar la version que incorpora el asistente de IA.
-- Produccion usa ddl-auto=validate: Hibernate NO crea esta tabla.
-- Es un script manual; el proyecto no tiene Flyway/Liquibase.
-- Puede ejecutarse nuevamente sin borrar propuestas ni modificar tablas existentes.

CREATE TABLE IF NOT EXISTS propuesta_asistente (
    id UUID NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contenido VARCHAR(12000) NOT NULL,
    expira_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    transaccion_id BIGINT
);
