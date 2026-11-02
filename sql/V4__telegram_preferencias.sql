-- Migración MANUAL: ejecutar después de V3__telegram.sql y antes del despliegue.
-- Producción conserva ddl-auto=validate. Null significa que no se eligió una preferencia.
ALTER TABLE telegram_conexion ADD COLUMN IF NOT EXISTS cuenta_predeterminada_id BIGINT;
ALTER TABLE telegram_conexion ADD COLUMN IF NOT EXISTS categoria_predeterminada_id BIGINT;
