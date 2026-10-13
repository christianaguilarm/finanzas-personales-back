package cl.finanzas.personales.model;

import java.util.Arrays;
import java.util.List;

public enum MedioPago {
    EFECTIVO,
    DEBITO,
    CREDITO,
    PREPAGO,
    TRANSFERENCIA,
    OTRO;

    /**
     * Si el movimiento saca dinero del bolsillo del usuario en el momento en que se registra.
     *
     * Es la regla que separa "gasto" de "deuda": una compra con tarjeta de crédito no consume
     * dinero propio (se paga después, y ese pago es el que consume), mientras que el pago de esa
     * tarjeta, que va por débito o transferencia, sí lo consume. Contar ambos haría que la misma
     * compra se descontara dos veces del saldo.
     *
     * PREPAGO sigue el mismo criterio que CREDITO: el dinero salió de la billetera para pagar de
     * antemano, no de la cuenta que se está moviendo.
     */
    public boolean consumeDineroPropio() {
        return switch (this) {
            case EFECTIVO, DEBITO, TRANSFERENCIA, OTRO -> true;
            case CREDITO, PREPAGO -> false;
        };
    }

    /**
     * Los medios que mueven dinero propio, para las consultas de saldo acumulado. Se deriva de
     * {@link #consumeDineroPropio()} y no al revés, para que la regla de Java y la de la base
     * no puedan quedar desalineadas.
     */
    public static List<MedioPago> queConsumenDineroPropio() {
        return Arrays.stream(values())
                .filter(MedioPago::consumeDineroPropio)
                .toList();
    }
}
