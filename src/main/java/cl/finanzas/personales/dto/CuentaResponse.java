package cl.finanzas.personales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CuentaResponse {
    private Long id;
    private String nombre;
    private String tipo;
    /** Ancla con la que el usuario arrancó la cuenta. Es lo único de la cuenta que se persiste. */
    private BigDecimal saldoInicial;
    /**
     * Saldo de la cuenta con los movimientos propios incluidos, derivado y nunca guardado:
     * saldoInicial más lo que se movió desde entonces. Se calcula dentro del método
     * transaccional que arma la respuesta.
     */
    private BigDecimal saldoActual;
}
