package cl.finanzas.personales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CuentaResponse {
    private Long id;
    private String nombre;
    private String tipo;
}
