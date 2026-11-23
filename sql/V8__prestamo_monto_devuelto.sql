-- Ejecutar antes de desplegar el backend: producción usa ddl-auto=validate.
-- Parte del préstamo que salda una devolución pagada en cuotas; el resto del plan son intereses.
-- NULL en devoluciones directas y en las antiguas en cuotas (cuentan cuota × total, tope el préstamo).
ALTER TABLE transaccion ADD COLUMN IF NOT EXISTS prestamo_monto_devuelto numeric(14, 2);
