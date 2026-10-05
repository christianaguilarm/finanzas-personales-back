-- backfill-medio.sql
--
-- Correccion manual de `transaccion.medio` para que el arrastre entre periodos cuadre.
--
-- Contexto: el saldo de una cuenta se deriva como saldo_inicial + movimientos propios
-- (MedioPago.consumeDineroPropio()). Hoy hay compras en cuentas de tarjeta grabadas con
-- medio EFECTIVO, y eso hace que una compra que NO salio de la bolsillo se descuente dos
-- veces del saldo: una como compra "propia" y otra cuando se paga la tarjeta.
--
-- NO lo ejecuta la aplicacion: no hay Flyway ni Liquibase en este proyecto. Correrlo a mano,
-- con backup antes, y revisar los SELECT de diagnostico.
--
-- La columna `medio` es NOT NULL y tiene un CHECK con los seis valores del enum, asi que no
-- hay medios nulos ni inventados: lo que hay son medios validos pero inconsistentes con el
-- tipo de la cuenta. Por eso la correccion deriva de `cuenta.tipo` en vez de inventar un valor.
--
-- Que NO hace: no toca `total_cuotas`/`cuota_actual`, no borra transacciones y no cambia el
-- esquema. Las compras en cuotas quedan como estan (medio CREDITO).
--
-- Idempotente: volver a correrlo no cambia mas filas. Reversible: la tabla de respaldo permite
-- deshacer exactamente lo que se aplico (ver seccion "REVERTIr", al final).

BEGIN;

-- ---------------------------------------------------------------------------
-- Parametro de la correccion debatable.
--
-- En cuentas tipo CREDITO, un medio que consume dinero propio (EFECTIVO, DEBITO,
-- TRANSFERENCIA, OTRO) es casi siempre una compra con tarjeta mal clasificada. Igual puede
-- ser un avance en efectivo, y eso no es lo mismo, asi que va apagado por omision: se deja
-- en false para ver el reporte y en true cuando se confirmo caso por caso.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE IF NOT EXISTS backfill_param (aplicar_medio_propio_en_credito boolean) ON COMMIT DROP;
DELETE FROM backfill_param;
INSERT INTO backfill_param (aplicar_medio_propio_en_credito) VALUES (false);

-- ---------------------------------------------------------------------------
-- 1. Diagnostico: como esta hoy el medio de cada transaccion segun el tipo de cuenta.
-- ---------------------------------------------------------------------------
SELECT c.tipo AS tipo_cuenta,
       t.medio,
       count(*) AS filas,
       COALESCE(sum(t.monto), 0) AS monto,
       count(*) FILTER (WHERE t.total_cuotas IS NOT NULL) AS filas_en_cuotas
FROM transaccion t
JOIN cuenta c ON c.id = t.cuenta_id
GROUP BY c.tipo, t.medio
ORDER BY c.tipo, t.medio;

-- ---------------------------------------------------------------------------
-- 2. Respaldo de los medios que se van a cambiar.
--
-- Guarda el valor anterior por id, una sola vez (ON CONFLICT DO NOTHING), asi que se puede
-- correr antes de aplicar cualquier correccion y deja el estado previo intacto. Es lo que
-- permite revertir.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medio_backfill_respaldo (
    id            bigint PRIMARY KEY,
    medio_anterior character varying(255) NOT NULL,
    corregido_en  timestamp NOT NULL DEFAULT now()
);

-- 2.a) Inconsistencia inequivoca: medio CREDITO en una cuenta que no es de credito.
-- Una cuenta corriente, de ahorro o en efectivo no compra con tarjeta, asi que el medio no
-- puede ser CREDITO. No hay filas de este tipo al dia de hoy; queda por si aparecen.
INSERT INTO medio_backfill_respaldo (id, medio_anterior)
SELECT t.id, t.medio
FROM transaccion t
JOIN cuenta c ON c.id = t.cuenta_id
WHERE t.medio = 'CREDITO'
  AND c.tipo <> 'CREDITO'
ON CONFLICT (id) DO NOTHING;

UPDATE transaccion t
SET medio = c.tipo
FROM cuenta c
WHERE c.id = t.cuenta_id
  AND t.medio = 'CREDITO'
  AND c.tipo <> 'CREDITO'
  AND c.tipo IN ('DEBITO', 'EFECTIVO', 'AHORRO');

-- 2.b) Inconsistencia debatable, apagada por omision (ver backfill_param).
-- En cuentas tipo CREDITO, medio que consume dinero propio. Se excluyen las compras en
-- cuotas (total_cuotas) para no tocar los planes: esas ya son CREDITO y deben seguir asi.
-- Las compras en cuotas con medio incoherent (cuota con EFECTIVO) quedan fuera a proposito:
-- se revisan a mano, no de forma automatica.
INSERT INTO medio_backfill_respaldo (id, medio_anterior)
SELECT t.id, t.medio
FROM transaccion t
JOIN cuenta c ON c.id = t.cuenta_id
WHERE (SELECT aplicar_medio_propio_en_credito FROM backfill_param)
  AND c.tipo = 'CREDITO'
  AND t.medio IN ('EFECTIVO', 'DEBITO', 'TRANSFERENCIA', 'OTRO')
  AND t.total_cuotas IS NULL
ON CONFLICT (id) DO NOTHING;

UPDATE transaccion t
SET medio = 'CREDITO'
FROM cuenta c
WHERE c.id = t.cuenta_id
  AND (SELECT aplicar_medio_propio_en_credito FROM backfill_param)
  AND c.tipo = 'CREDITO'
  AND t.medio IN ('EFECTIVO', 'DEBITO', 'TRANSFERENCIA', 'OTRO')
  AND t.total_cuotas IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Que se toco (para revisar antes de confirmar).
-- ---------------------------------------------------------------------------
SELECT r.corregido_en,
       r.medio_anterior,
       t.medio AS medio_actual,
       c.tipo AS tipo_cuenta,
       count(*) AS filas,
       COALESCE(sum(t.monto), 0) AS monto
FROM medio_backfill_respaldo r
JOIN transaccion t ON t.id = r.id
JOIN cuenta c ON c.id = t.cuenta_id
GROUP BY 1, 2, 3, 4
ORDER BY 1, 2, 4;

-- ---------------------------------------------------------------------------
-- 4. Impacto en el arrastre: el saldo acumulado hasta el inicio de un periodo, por cuenta.
-- Es la misma consulta que usa la API, para verificar el efecto antes de confirmar.
-- ---------------------------------------------------------------------------
SELECT c.id AS cuenta_id,
       c.nombre,
       c.saldo_inicial,
       COALESCE(sum(CASE
           WHEN t.tipo = 'INGRESO' THEN t.monto
           WHEN t.tipo = 'EGRESO' THEN -t.monto
           ELSE 0
       END), 0) AS acumulado_hasta_hoy,
       c.saldo_inicial + COALESCE(sum(CASE
           WHEN t.tipo = 'INGRESO' THEN t.monto
           WHEN t.tipo = 'EGRESO' THEN -t.monto
           ELSE 0
       END), 0) AS saldo_derivado
FROM cuenta c
LEFT JOIN transaccion t
       ON t.cuenta_id = c.id
      AND t.fecha < current_date
      AND t.medio IN ('EFECTIVO', 'DEBITO', 'TRANSFERENCIA', 'OTRO')
WHERE c.activo
GROUP BY c.id, c.nombre, c.saldo_inicial
ORDER BY c.id;

-- ROLLBACK;  -- mientras se esta revisando
COMMIT;

-- ===========================================================================
-- REVERTIR
--
-- Restituye los medios guardados en el respaldo. Correr dentro de una transaccion, revisar el
-- SELECT previo y cambiar el ROLLBACK por COMMIT solo si el resultado es el esperado.
--
--   BEGIN;
--   SELECT r.id, r.medio_anterior, t.medio AS medio_actual
--   FROM medio_backfill_respaldo r
--   JOIN transaccion t ON t.id = r.id
--   ORDER BY r.id;
--
--   UPDATE transaccion t
--   SET medio = r.medio_anterior
--   FROM medio_backfill_respaldo r
--   WHERE r.id = t.id;
--
--   ROLLBACK;  -- o COMMIT
--
-- Cuando la correccion quede validada, la tabla de respaldo se puede borrar:
--   DROP TABLE medio_backfill_respaldo;
-- ===========================================================================