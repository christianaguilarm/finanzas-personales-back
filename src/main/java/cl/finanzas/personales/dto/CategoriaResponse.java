package cl.finanzas.personales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CategoriaResponse {
    private Long id;
    private String nombre;
    private String tipo;
    private String icono;
    private String color;

    public CategoriaResponse(Long id, String nombre, String tipo, String icono, String color) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.icono = icono;
        this.color = color;
    }
}
