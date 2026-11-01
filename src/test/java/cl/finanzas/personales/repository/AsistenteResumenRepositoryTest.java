package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.model.Transaccion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
class AsistenteResumenRepositoryTest {
    @Autowired EntityManager em;
    @Autowired TransaccionRepository repository;

    @Test
    void agregaTodoElMesSinTruncarPaginasNiMezclarUsuarios() {
        var propia = cuenta("propio@example.com");
        var ajena = cuenta("ajeno@example.com");
        for (int i = 0; i < 35; i++) movimiento(propia, "2026-11-01", "1000");
        movimiento(propia, "2026-10-31", "999999");
        movimiento(propia, "2026-12-01", "999999");
        movimiento(ajena, "2026-11-01", "999999");
        em.flush();
        var filas = repository.resumenParaAsistente(propia.getUser().getId(),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1));
        assertEquals(1, filas.size());
        assertEquals(35L, filas.get(0).getCantidad());
        assertEquals(0, new BigDecimal("35000").compareTo(filas.get(0).getTotal()));
        assertEquals("Sin categoría", filas.get(0).getCategoria());
        assertEquals(MedioPago.CREDITO, filas.get(0).getMedio());
    }

    private Cuenta cuenta(String email) {
        var user = new AppUser();
        user.setEmail(email);
        em.persist(user);
        var cuenta = new Cuenta();
        cuenta.setUser(user);
        cuenta.setNombre("Cuenta");
        cuenta.setTipo("CREDITO");
        em.persist(cuenta);
        return cuenta;
    }

    private void movimiento(Cuenta cuenta, String fecha, String monto) {
        var t = new Transaccion();
        t.setUser(cuenta.getUser());
        t.setCuenta(cuenta);
        t.setTipo(TipoTransaccion.EGRESO);
        t.setMedio(MedioPago.CREDITO);
        t.setFecha(LocalDate.parse(fecha));
        t.setMonto(new BigDecimal(monto));
        em.persist(t);
    }
}
