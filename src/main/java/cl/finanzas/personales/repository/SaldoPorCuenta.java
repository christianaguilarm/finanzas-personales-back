package cl.finanzas.personales.repository;

import java.math.BigDecimal;

/** Fila de {@link TransaccionRepository#saldosAcumuladosHasta}: acumulado de una cuenta. */
public interface SaldoPorCuenta {

    Long getCuentaId();

    BigDecimal getSaldo();
}
