package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CuadreCategoriaResponse;
import cl.finanzas.personales.dto.CuadreGastosResponse;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.mapper.TransaccionMapper;
import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.model.Comercio;
import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.model.Transaccion;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.ComercioRepository;
import cl.finanzas.personales.repository.CuentaRepository;
import cl.finanzas.personales.repository.TagRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransaccionServiceCuadreTest {

    @Mock
    private TransaccionRepository transaccionRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private ComercioRepository comercioRepository;
    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private TransaccionMapper mapper;
    @Mock
    private Clock clock;

    @InjectMocks
    private TransaccionService transaccionService;

    private static final Long USER_ID = 1L;
    private static final Long CUENTA_ID = 10L;
    private static final YearMonth PERIODO = YearMonth.of(2026, 9);

    private Categoria arriendo;
    private Categoria pan;
    private Categoria salud;
    private Comercio falabella;
    private Comercio jumbo;
    private Cuenta tarjetaFalabella;

    @BeforeEach
    void setUp() {
        arriendo = categoria(1L, "Arriendo", null);
        pan = categoria(2L, "Pan", arriendo);
        salud = categoria(3L, "Salud", null);
        falabella = comercio(20L, "Falabella");
        jumbo = comercio(21L, "Jumbo");
        tarjetaFalabella = cuenta(CUENTA_ID, "Tarjeta Falabella");
    }

    private CuadreGastosResponse cuadrar(List<Transaccion> transacciones) {
        return cuadrar(transacciones, false);
    }

    private CuadreGastosResponse cuadrar(List<Transaccion> transacciones, boolean incluyeCuotaCero) {
        prepararConsulta(transacciones);
        return transaccionService.obtenerCuadreGastos(USER_ID, CUENTA_ID, PERIODO, incluyeCuotaCero);
    }

    private void prepararConsulta(List<Transaccion> transacciones) {
        when(cuentaRepository.findByUserIdAndActivoIsTrue(USER_ID)).thenReturn(List.of(tarjetaFalabella));
        when(transaccionRepository.findAll(ArgumentMatchers.<Specification<Transaccion>>any()))
                .thenReturn(transacciones);
        // El detalle se arma desde la entidad para poder verificar el orden en que llega al usuario.
        if (!transacciones.isEmpty()) {
            when(mapper.toResponse(ArgumentMatchers.any(Transaccion.class)))
                    .thenAnswer(invocacion -> respuestaDe(invocacion.getArgument(0)));
        }
    }

    @Test
    @DisplayName("Devuelve el total y la cantidad de transacciones de la cuenta en el periodo")
    void entregaTotalYCantidad() {
        Transaccion arriendoSeptiembre = base(1L, tarjetaFalabella, arriendo, falabella,
                new BigDecimal("340056.00"), "Pago Arriendo", LocalDate.of(2026, 9, 5));
        Transaccion compraCuota = cuota(2L, tarjetaFalabella, salud, jumbo,
                new BigDecimal("25000.00"), PERIODO, 3, 6, LocalDate.of(2026, 9, 12));
        Transaccion gastoPanaderia = base(3L, tarjetaFalabella, pan, falabella,
                new BigDecimal("9344.00"), "Panadería", LocalDate.of(2026, 9, 20));

        CuadreGastosResponse respuesta = cuadrar(List.of(arriendoSeptiembre, compraCuota, gastoPanaderia));

        assertThat(respuesta.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(respuesta.cuentaNombre()).isEqualTo("Tarjeta Falabella");
        assertThat(respuesta.periodo()).isEqualTo(PERIODO);
        assertThat(respuesta.total()).isEqualByComparingTo("374400.00");
        assertThat(respuesta.cantidadTransacciones()).isEqualTo(3);
    }

    @Test
    @DisplayName("Una cuenta de otro usuario o inactiva responde 404")
    void rechazaCuentaAjenaOInactiva() {
        // La cuenta del usuario no viene en la lista de cuentas activas: es de otro usuario o está inactiva.
        when(cuentaRepository.findByUserIdAndActivoIsTrue(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> transaccionService.obtenerCuadreGastos(USER_ID, 99L, PERIODO, false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("La cuenta no existe, no pertenece al usuario o está inactiva");
    }

    @Test
    @DisplayName("Sin incluir la cuota cero se descarta la fila que abre un plan en cuotas")
    void excluyeLaFilaDeCuotaCero() {
        Transaccion aperturaPlan = cuota(1L, tarjetaFalabella, salud, falabella,
                new BigDecimal("230033.00"), PERIODO, 0, 12, LocalDate.of(2026, 9, 2));
        Transaccion cuotaFacturada = cuota(2L, tarjetaFalabella, salud, falabella,
                new BigDecimal("19170.00"), PERIODO, 1, 12, LocalDate.of(2026, 9, 9));
        Transaccion gastoComun = base(3L, tarjetaFalabella, salud, jumbo,
                new BigDecimal("5000.00"), "Farmacia", LocalDate.of(2026, 9, 18));

        CuadreGastosResponse respuesta = cuadrar(List.of(aperturaPlan, cuotaFacturada, gastoComun), false);

        assertThat(respuesta.incluyeCuotaCero()).isFalse();
        assertThat(respuesta.cantidadTransacciones()).isEqualTo(2);
        assertThat(respuesta.transacciones()).extracting(TransaccionResponse::id).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("Con true el total incluye la cuota cero y con false la excluye")
    void cambiaElTotalSegunIncluyaLaCuotaCero() {
        Transaccion aperturaPlan = cuota(1L, tarjetaFalabella, salud, falabella,
                new BigDecimal("230033.00"), PERIODO, 0, 12, LocalDate.of(2026, 9, 2));
        Transaccion cuotaFacturada = cuota(2L, tarjetaFalabella, salud, falabella,
                new BigDecimal("19170.00"), PERIODO, 1, 12, LocalDate.of(2026, 9, 9));

        // Una sola consulta mockeada alcanza para las dos variantes: cambia el parámetro, no los datos.
        prepararConsulta(List.of(aperturaPlan, cuotaFacturada));

        CuadreGastosResponse sinCuotaCero = transaccionService.obtenerCuadreGastos(USER_ID, CUENTA_ID, PERIODO, false);
        CuadreGastosResponse conCuotaCero = transaccionService.obtenerCuadreGastos(USER_ID, CUENTA_ID, PERIODO, true);


        assertThat(sinCuotaCero.total()).isEqualByComparingTo("19170.00");
        assertThat(conCuotaCero.total()).isEqualByComparingTo("249203.00");
        assertThat(conCuotaCero.incluyeCuotaCero()).isTrue();
        assertThat(conCuotaCero.cantidadTransacciones()).isEqualTo(2);
    }

    @Test
    @DisplayName("Las compras en cuotas suman en el total y no se restan")
    void cuentaLasComprasEnCuotasDentroDelTotal() {
        Transaccion cuotaFonica = cuota(1L, tarjetaFalabella, salud, jumbo,
                new BigDecimal("5000.00"), PERIODO, 2, 3, LocalDate.of(2026, 9, 10));
        Transaccion cuotaNotebook = cuota(2L, tarjetaFalabella, salud, falabella,
                new BigDecimal("40000.00"), PERIODO, 1, 12, LocalDate.of(2026, 9, 11));
        // Una compra en una sola cuota no es un plan en cuotas.
        Transaccion cuotaUnica = cuota(3L, tarjetaFalabella, salud, falabella,
                new BigDecimal("9900.00"), PERIODO, 1, 1, LocalDate.of(2026, 9, 12));
        Transaccion gastoComun = base(4L, tarjetaFalabella, salud, jumbo,
                new BigDecimal("60000.00"), "Gasto común", LocalDate.of(2026, 9, 15));

        CuadreGastosResponse respuesta = cuadrar(List.of(cuotaFonica, cuotaNotebook, cuotaUnica, gastoComun));

        assertThat(respuesta.cantidadComprasEnCuotas()).isEqualTo(2);
        assertThat(respuesta.totalComprasEnCuotas()).isEqualByComparingTo("45000.00");
        // El plan en cuotas sigue siendo parte del gasto real: 45.000 de cuotas + 9.900 + 60.000.
        assertThat(respuesta.total()).isEqualByComparingTo("114900.00");
    }

    @Test
    @DisplayName("Las categorías hijas se agrupan dentro de su categoría padre")
    void agrupaCategoriasHijasEnElPadre() {
        Transaccion gastoArriendo = base(1L, tarjetaFalabella, arriendo, falabella,
                new BigDecimal("340056.00"), "Pago Arriendo", LocalDate.of(2026, 9, 5));
        Transaccion panaderia = base(2L, tarjetaFalabella, pan, falabella,
                new BigDecimal("9344.00"), "Panadería", LocalDate.of(2026, 9, 20));

        CuadreGastosResponse respuesta = cuadrar(List.of(gastoArriendo, panaderia));

        assertThat(respuesta.porCategoria()).hasSize(1);
        CuadreCategoriaResponse linea = respuesta.porCategoria().get(0);
        assertThat(linea.id()).isEqualTo(1L);
        assertThat(linea.nombre()).isEqualTo("Arriendo");
        assertThat(linea.total()).isEqualByComparingTo("349400.00");
        assertThat(linea.cantidad()).isEqualTo(2);
    }

    @Test
    @DisplayName("Una transacción sin categoría ni comercio va a Sin categoría y Sin comercio")
    void agrupaLoQueNoTieneCategoriaNiComercio() {
        Transaccion sinNada = base(1L, tarjetaFalabella, null, null,
                new BigDecimal("1234.00"), "Cargo sin clasificar", LocalDate.of(2026, 9, 8));

        CuadreGastosResponse respuesta = cuadrar(List.of(sinNada));

        assertThat(respuesta.porCategoria()).hasSize(1);
        assertThat(respuesta.porCategoria().get(0).id()).isNull();
        assertThat(respuesta.porCategoria().get(0).nombre()).isEqualTo("Sin categoría");
        assertThat(respuesta.porCategoria().get(0).total()).isEqualByComparingTo("1234.00");

        assertThat(respuesta.porComercio()).hasSize(1);
        assertThat(respuesta.porComercio().get(0).id()).isNull();
        assertThat(respuesta.porComercio().get(0).nombre()).isEqualTo("Sin comercio");
        assertThat(respuesta.porComercio().get(0).cantidad()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una cuenta sin transacciones en el periodo devuelve totales en cero y listas vacías")
    void devuelveCuadreVacio() {
        CuadreGastosResponse respuesta = cuadrar(List.of());

        assertThat(respuesta.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(respuesta.total()).isEqualByComparingTo("0");
        assertThat(respuesta.cantidadTransacciones()).isZero();
        assertThat(respuesta.cantidadComprasEnCuotas()).isZero();
        assertThat(respuesta.totalComprasEnCuotas()).isEqualByComparingTo("0");
        assertThat(respuesta.porCategoria()).isEmpty();
        assertThat(respuesta.porComercio()).isEmpty();
        assertThat(respuesta.transacciones()).isEmpty();
    }

    @Test
    @DisplayName("El detalle viene ordenado por fecha ascendente")
    void ordenaElDetallePorFecha() {
        Transaccion ultima = base(3L, tarjetaFalabella, salud, jumbo,
                new BigDecimal("5000.00"), "Farmacia", LocalDate.of(2026, 9, 28));
        Transaccion primera = base(1L, tarjetaFalabella, arriendo, falabella,
                new BigDecimal("340056.00"), "Pago Arriendo", LocalDate.of(2026, 9, 3));
        Transaccion media = base(2L, tarjetaFalabella, salud, falabella,
                new BigDecimal("12000.00"), "Supermercado", LocalDate.of(2026, 9, 17));

        CuadreGastosResponse respuesta = cuadrar(List.of(ultima, media, primera));

        assertThat(respuesta.transacciones()).extracting(TransaccionResponse::id)
                .containsExactly(1L, 2L, 3L);
        assertThat(respuesta.transacciones()).extracting(TransaccionResponse::fecha)
                .containsExactly(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 28));
    }

    @Test
    @DisplayName("A igual fecha el detalle se ordena por id y el desglose de mayor a menor total")
    void ordenaElDesgloseDeMayorAMenor() {
        // Dos compras del mismo día: la más antigua en id va primero.
        Transaccion jumboGrande = base(1L, tarjetaFalabella, salud, jumbo,
                new BigDecimal("70000.00"), "Jumbo", LocalDate.of(2026, 9, 15));
        Transaccion falabellaChico = base(2L, tarjetaFalabella, arriendo, falabella,
                new BigDecimal("9000.00"), "Falabella", LocalDate.of(2026, 9, 15));
        Transaccion falabellaGrande = base(3L, tarjetaFalabella, salud, falabella,
                new BigDecimal("30000.00"), "Falabella", LocalDate.of(2026, 9, 22));

        CuadreGastosResponse respuesta = cuadrar(List.of(jumboGrande, falabellaChico, falabellaGrande));

        assertThat(respuesta.transacciones()).extracting(TransaccionResponse::id)
                .containsExactly(1L, 2L, 3L);

        assertThat(respuesta.porComercio()).extracting(CuadreCategoriaResponse::nombre)
                .containsExactly("Jumbo", "Falabella");
        assertThat(respuesta.porComercio()).extracting(CuadreCategoriaResponse::total)
                .containsExactly(new BigDecimal("70000.00"), new BigDecimal("39000.00"));
        assertThat(respuesta.porComercio().get(1).cantidad()).isEqualTo(2);

        assertThat(respuesta.porCategoria()).extracting(CuadreCategoriaResponse::nombre)
                .containsExactly("Salud", "Arriendo");
        assertThat(respuesta.porCategoria()).extracting(CuadreCategoriaResponse::total)
                .containsExactly(new BigDecimal("100000.00"), new BigDecimal("9000.00"));
    }

    private Categoria categoria(Long id, String nombre, Categoria parent) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setParent(parent);
        return categoria;
    }

    private Comercio comercio(Long id, String nombre) {
        Comercio comercio = new Comercio();
        comercio.setId(id);
        comercio.setNombre(nombre);
        return comercio;
    }

    private Cuenta cuenta(Long id, String nombre) {
        Cuenta cuenta = new Cuenta();
        cuenta.setId(id);
        cuenta.setNombre(nombre);
        return cuenta;
    }

    private Transaccion cuota(Long id, Cuenta cuenta, Categoria categoria, Comercio comercio,
                              BigDecimal monto, YearMonth periodo, int cuotaActual, int totalCuotas,
                              LocalDate fecha) {
        Transaccion transaccion = base(id, cuenta, categoria, comercio, monto, "Compra en cuotas", fecha);
        transaccion.setPeriodoFacturacion(periodo);
        transaccion.setCuotaActual(cuotaActual);
        transaccion.setTotalCuotas(totalCuotas);
        return transaccion;
    }

    private Transaccion base(Long id, Cuenta cuenta, Categoria categoria, Comercio comercio,
                             BigDecimal monto, String descripcion, LocalDate fecha) {
        Transaccion transaccion = new Transaccion();
        transaccion.setId(id);
        transaccion.setTipo(TipoTransaccion.EGRESO);
        transaccion.setCuenta(cuenta);
        transaccion.setCategoria(categoria);
        transaccion.setComercio(comercio);
        transaccion.setMonto(monto);
        transaccion.setDescripcion(descripcion);
        transaccion.setMedio(MedioPago.CREDITO);
        transaccion.setPeriodoFacturacion(PERIODO);
        transaccion.setFecha(fecha);
        return transaccion;
    }

    /** Reconstruye el DTO del detalle solo con lo que las aserciones necesitan. */
    private TransaccionResponse respuestaDe(Transaccion transaccion) {
        return new TransaccionResponse(
                transaccion.getId(),
                transaccion.getTipo(),
                transaccion.getFecha(),
                transaccion.getPeriodoFacturacion(),
                transaccion.getMonto(),
                transaccion.getMedio(),
                transaccion.getDescripcion(),
                transaccion.isEsRecurrente(),
                transaccion.getTotalCuotas(),
                transaccion.getCuotaActual(),
                transaccion.getCreadoEn(),
                transaccion.getCuenta().getId(),
                transaccion.getCuenta().getNombre(),
                transaccion.getCategoria() != null ? transaccion.getCategoria().getId() : null,
                transaccion.getCategoria() != null ? transaccion.getCategoria().getNombre() : null,
                transaccion.getCategoria() != null && transaccion.getCategoria().getParent() != null,
                null,
                null,
                null,
                null,
                transaccion.getComercio() != null ? transaccion.getComercio().getId() : null,
                transaccion.getComercio() != null ? transaccion.getComercio().getNombre() : null,
                null
        );
    }
}
