package cl.finanzas.personales.dto;

import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.TipoTransaccion;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Getter
@Setter
public class TransaccionFiltroQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaDesde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaHasta;

    @DateTimeFormat(pattern = "yyyy-MM")
    private YearMonth periodoFacturacion;

    private Long categoriaId;
    private Long subcategoriaId;
    private Long cuentaId;
    private Long comercioId;
    private Long tagId;
    private TipoTransaccion tipo;
    private MedioPago medio;
    private Boolean esRecurrente;
    private BigDecimal montoMin;
    private BigDecimal montoMax;
}

