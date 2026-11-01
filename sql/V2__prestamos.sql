-- Ejecutar antes de desplegar el backend: producción usa ddl-auto=validate.
\set ON_ERROR_STOP on
BEGIN;
ALTER TABLE categoria ADD COLUMN IF NOT EXISTS codigo_sistema varchar(30);
ALTER TABLE categoria ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
CREATE UNIQUE INDEX IF NOT EXISTS ux_categoria_usuario_codigo_sistema
    ON categoria (user_id, codigo_sistema) WHERE codigo_sistema IS NOT NULL;

ALTER TABLE transaccion ADD COLUMN IF NOT EXISTS prestamo_persona varchar(120);
ALTER TABLE transaccion ADD COLUMN IF NOT EXISTS prestamo_fecha_devolucion date;
ALTER TABLE transaccion ADD COLUMN IF NOT EXISTS prestamo_origen_id bigint;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_transaccion_prestamo_origen') THEN
        ALTER TABLE transaccion ADD CONSTRAINT fk_transaccion_prestamo_origen
            FOREIGN KEY (prestamo_origen_id) REFERENCES transaccion(id);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_transaccion_prestamo_origen ON transaccion(prestamo_origen_id);
COMMIT;
