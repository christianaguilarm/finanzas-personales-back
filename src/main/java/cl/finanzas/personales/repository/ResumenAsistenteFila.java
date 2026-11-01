package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.TipoTransaccion;
import java.math.BigDecimal;

public interface ResumenAsistenteFila {
    TipoTransaccion getTipo();
    String getCategoria();
    MedioPago getMedio();
    BigDecimal getTotal();
    Long getCantidad();
}
