package cl.finanzas.personales.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MedioPagoTest {

    @Test
    @DisplayName("Consumen dinero propio los medios con los que se paga en el momento")
    void losMediosQuePaganEnElActoConsumenDineroPropio() {
        assertThat(MedioPago.EFECTIVO.consumeDineroPropio()).isTrue();
        assertThat(MedioPago.DEBITO.consumeDineroPropio()).isTrue();
        assertThat(MedioPago.TRANSFERENCIA.consumeDineroPropio()).isTrue();
        assertThat(MedioPago.OTRO.consumeDineroPropio()).isTrue();
    }

    @Test
    @DisplayName("No consumen dinero propio el credito ni el prepago: es deuda, no gasto")
    void elCreditoYElPrepagoNoConsumenDineroPropio() {
        assertThat(MedioPago.CREDITO.consumeDineroPropio()).isFalse();
        assertThat(MedioPago.PREPAGO.consumeDineroPropio()).isFalse();
    }

    @Test
    @DisplayName("La lista de medios que mueven el saldo sale de la misma regla")
    void laListaDeMediosSeDerivaDeLaRegla() {
        assertThat(MedioPago.queConsumenDineroPropio())
                .containsExactlyInAnyOrder(MedioPago.EFECTIVO, MedioPago.DEBITO, MedioPago.TRANSFERENCIA, MedioPago.OTRO);
    }
}
