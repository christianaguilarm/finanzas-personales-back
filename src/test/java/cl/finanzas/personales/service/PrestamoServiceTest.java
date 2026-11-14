package cl.finanzas.personales.service;

import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.model.Transaccion;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrestamoServiceTest {
    @Test
    void calculaPendienteConDevolucionesParciales() {
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        TransaccionRepository transacciones = mock(TransaccionRepository.class);
        Categoria categoria = new Categoria();
        categoria.setActivo(true);
        categoria.setCodigoSistema("PRESTAMO_EGRESO");
        categoria.setTipo(TipoTransaccion.EGRESO);
        Transaccion origen = new Transaccion();
        origen.setId(7L);
        origen.setMonto(new BigDecimal("50000"));
        origen.setFecha(LocalDate.of(2026, 10, 1));
        origen.setPrestamoPersona("Ana");
        origen.setPrestamoFechaDevolucion(LocalDate.of(2026, 11, 1));
        Transaccion abono = new Transaccion();
        abono.setMonto(new BigDecimal("12000"));

        when(categorias.findByUserId(1L)).thenReturn(List.of(categoria));
        when(transacciones.findByUser_IdAndCategoria_CodigoSistemaAndTipo(1L, "PRESTAMO_EGRESO", TipoTransaccion.EGRESO))
                .thenReturn(List.of(origen));
        when(transacciones.findByPrestamoOrigen_Id(7L)).thenReturn(List.of(abono));

        var resultado = new PrestamoService(categorias, transacciones).obtenerPrestamos(1L);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).pendiente()).isEqualByComparingTo("38000");
        assertThat(resultado.get(0).devuelto()).isEqualByComparingTo("12000");
    }

    @Test
    void incluyePrestamosRecibidosYExcluyeDevoluciones() {
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        TransaccionRepository transacciones = mock(TransaccionRepository.class);
        Categoria categoria = new Categoria();
        categoria.setActivo(true);
        categoria.setCodigoSistema("PRESTAMO_EGRESO");
        Transaccion recibido = new Transaccion();
        recibido.setId(9L);
        recibido.setTipo(TipoTransaccion.INGRESO);
        recibido.setMonto(new BigDecimal("100000"));
        recibido.setFecha(LocalDate.of(2026, 10, 1));
        recibido.setPrestamoPersona("Juan");
        Transaccion pagoAJuan = new Transaccion();
        pagoAJuan.setId(10L);
        pagoAJuan.setMonto(new BigDecimal("30000"));
        pagoAJuan.setPrestamoOrigen(recibido);
        Transaccion devolucionRecibida = new Transaccion();
        devolucionRecibida.setId(11L);
        devolucionRecibida.setMonto(new BigDecimal("5000"));
        devolucionRecibida.setPrestamoOrigen(new Transaccion());

        when(categorias.findByUserId(1L)).thenReturn(List.of(categoria));
        when(transacciones.findByUser_IdAndCategoria_CodigoSistemaAndTipo(1L, "PRESTAMO_EGRESO", TipoTransaccion.EGRESO))
                .thenReturn(List.of(pagoAJuan));
        when(transacciones.findByUser_IdAndCategoria_CodigoSistemaAndTipo(1L, "PRESTAMO_INGRESO", TipoTransaccion.INGRESO))
                .thenReturn(List.of(recibido, devolucionRecibida));
        when(transacciones.findByPrestamoOrigen_Id(9L)).thenReturn(List.of(pagoAJuan));

        var resultado = new PrestamoService(categorias, transacciones).obtenerPrestamos(1L);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).tipo()).isEqualTo(PrestamoService.RECIBIDO);
        assertThat(resultado.get(0).persona()).isEqualTo("Juan");
        assertThat(resultado.get(0).pendiente()).isEqualByComparingTo("70000");
    }

    @Test
    void noMuestraPrestamosSiLaCategoriaEstaOculta() {
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        TransaccionRepository transacciones = mock(TransaccionRepository.class);
        when(categorias.findByUserId(1L)).thenReturn(List.of());
        assertThat(new PrestamoService(categorias, transacciones).obtenerPrestamos(1L)).isEmpty();
    }
}
