package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.ProyeccionCategoriaResponse;
import cl.finanzas.personales.dto.ProyeccionResponse;
import cl.finanzas.personales.dto.ProyeccionTransaccionResponse;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransaccionServiceProyeccionTest {

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

    /** Periodo en curso durante las pruebas: hace determinista la proyección del mes actual. */
    private static final YearMonth PERIODO_EN_CURSO = YearMonth.of(2026, 10);

    private Categoria arriendo;
    private Categoria pan;
    private Categoria mercaderia;
    private Categoria sueldo;
    private Categoria salud;
    private Cuenta cuentaCorriente;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-10-05T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        arriendo = categoria(1L, "Arriendo", null);
        pan = categoria(2L, "Pan", arriendo);
        mercaderia = categoria(3L, "Mercadería", null);
        sueldo = categoria(4L, "Sueldo", null);
        salud = categoria(5L, "Salud", null);
        cuentaCorriente = cuenta(10L, "CC Banco Chile");
    }

    private ProyeccionResponse proyectar(YearMonth periodo, List<Transaccion> recurrentes, List<Transaccion> cuotas) {
        return proyectar(periodo, recurrentes, cuotas, List.of());
    }

    private ProyeccionResponse proyectar(YearMonth periodo, List<Transaccion> recurrentes, List<Transaccion> cuotas,
                                         List<Transaccion> registradasEnElPeriodo) {
        // El servicio consulta los recurrentes, luego las compras en cuotas y, solo si el periodo
        // es el en curso, las transacciones ya registradas en ese periodo.
        when(transaccionRepository.findAll(ArgumentMatchers.<Specification<Transaccion>>any()))
                .thenReturn(recurrentes, cuotas, registradasEnElPeriodo);
        return transaccionService.obtenerProyeccion(USER_ID, periodo);
    }

    @Test
    @DisplayName("Deduplica recurrentes por tipo, cuenta y categoría quedándose con la más reciente")
    void deduplicaRecurrentesPorConcepto() {
        Transaccion antiguo = recurrente(1L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("330966.00"), "Arriendo");
        Transaccion nuevo = recurrente(418L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("340056.00"), "Pago Arriendo");

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 10), List.of(antiguo, nuevo), List.of());

        assertThat(respuesta.egresoTotal()).isEqualByComparingTo("340056.00");
        assertThat(respuesta.egresosPorCategoria()).hasSize(1);
        assertThat(respuesta.egresosPorCategoria().get(0).categoriaNombre()).isEqualTo("Arriendo");
        assertThat(respuesta.egresosPorCategoria().get(0).cantidad()).isEqualTo(1);
    }

    @Test
    @DisplayName("Detalla las transacciones proyectadas con descripción, cuenta, categoría, cuota y fecha")
    void detallaLasTransaccionesProyectadas() {
        Transaccion cuotaPan = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, pan,
                new BigDecimal("13166.00"), YearMonth.of(2026, 9), 2, 6, "Bolso + cubre sillon");
        Transaccion sueldoOctubre = recurrente(2L, TipoTransaccion.INGRESO, cuentaCorriente, sueldo,
                new BigDecimal("1921926.00"), "Sueldo");

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 10),
                List.of(sueldoOctubre), List.of(cuotaPan));

        assertThat(respuesta.transacciones()).hasSize(2);

        ProyeccionTransaccionResponse ingreso = respuesta.transacciones().get(0);
        assertThat(ingreso.tipo()).isEqualTo(TipoTransaccion.INGRESO);
        assertThat(ingreso.descripcion()).isEqualTo("Sueldo");
        assertThat(ingreso.cuenta()).isEqualTo("CC Banco Chile");
        assertThat(ingreso.categoria()).isEqualTo("Sueldo");
        assertThat(ingreso.cuota()).isNull();
        assertThat(ingreso.monto()).isEqualByComparingTo("1921926.00");
        assertThat(ingreso.fecha()).isEqualTo(LocalDate.of(2026, 9, 15));

        ProyeccionTransaccionResponse egreso = respuesta.transacciones().get(1);
        assertThat(egreso.tipo()).isEqualTo(TipoTransaccion.EGRESO);
        assertThat(egreso.descripcion()).isEqualTo("Bolso + cubre sillon");
        assertThat(egreso.cuenta()).isEqualTo("CC Banco Chile");
        assertThat(egreso.categoria()).isEqualTo("Compras en cuotas");
        // La cuota facturada en septiembre fue la 2, así que en octubre se proyecta la 3.
        assertThat(egreso.cuota()).isEqualTo("3/6");
        assertThat(egreso.fecha()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    @DisplayName("El detalle se ordena con ingresos primero y cada tipo de mayor a menor monto")
    void ordenaElDetallePorTipoYMonto() {
        Transaccion gastoArriendo = recurrente(1L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("340056.00"), "Pago Arriendo");
        Transaccion cuotaPan = cuota(2L, TipoTransaccion.EGRESO, cuentaCorriente, pan,
                new BigDecimal("9573.00"), YearMonth.of(2026, 9), 3, 12, "BODEGA ACUENTA PANOTECA");
        Transaccion ingresoSueldo = recurrente(3L, TipoTransaccion.INGRESO, cuentaCorriente, sueldo,
                new BigDecimal("1921926.00"), "Sueldo");
        Transaccion gastoSalud = recurrente(4L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("5000.00"), "Farmacia");

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 10),
                List.of(gastoArriendo, ingresoSueldo, gastoSalud), List.of(cuotaPan));

        assertThat(respuesta.transacciones())
                .extracting(ProyeccionTransaccionResponse::tipo)
                .containsExactly(TipoTransaccion.INGRESO, TipoTransaccion.EGRESO, TipoTransaccion.EGRESO, TipoTransaccion.EGRESO);
        assertThat(respuesta.transacciones().get(1).monto()).isEqualByComparingTo("340056.00");
        assertThat(respuesta.transacciones().get(2).monto()).isEqualByComparingTo("9573.00");
        assertThat(respuesta.transacciones().get(3).monto()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("Recurrentes de cuentas o categorías distintas se projetan por separado")
    void separaConceptosDistintos() {
        Transaccion gastoArriendo = recurrente(1L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("340056.00"), "Pago Arriendo");
        Transaccion gastoArriendoOtro = recurrente(2L, TipoTransaccion.EGRESO, cuenta(11L, "Cuenta Tenpo"), arriendo,
                new BigDecimal("340056.00"), "Pago Arriendo");
        Transaccion ingresoSueldo = recurrente(278L, TipoTransaccion.INGRESO, cuentaCorriente, sueldo,
                new BigDecimal("1921926.00"), "Sueldo");

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2027, 3),
                List.of(gastoArriendo, gastoArriendoOtro, ingresoSueldo), List.of());

        assertThat(respuesta.ingresoTotal()).isEqualByComparingTo("1921926.00");
        assertThat(respuesta.egresoTotal()).isEqualByComparingTo("680112.00");
        assertThat(respuesta.total()).isEqualByComparingTo("1241814.00");
    }

    @Test
    @DisplayName("Una cuota 1/12 sigue proyectándose hasta el mes 12 y luego desaparece")
    void proyectaCuotaHastaCompletarElTotal() {
        Transaccion cuota = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("10000.00"), YearMonth.of(2026, 9), 1, 12);

        ProyeccionResponse dentroDeRango = proyectar(YearMonth.of(2027, 3), List.of(), List.of(cuota));
        assertThat(dentroDeRango.egresoTotal()).isEqualByComparingTo("10000.00");

        ProyeccionResponse ultimoMes = proyectar(YearMonth.of(2027, 8), List.of(), List.of(cuota));
        assertThat(ultimoMes.egresoTotal()).isEqualByComparingTo("10000.00");

        ProyeccionResponse mesPosterior = proyectar(YearMonth.of(2027, 9), List.of(), List.of(cuota));
        assertThat(mesPosterior.egresoTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Una cuota 3/3 no se proyecta en ningún mes futuro")
    void noProyectaCuotaFinalizada() {
        Transaccion cuota = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("5000.00"), YearMonth.of(2026, 9), 3, 3);

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 12), List.of(), List.of(cuota));

        assertThat(respuesta.egresoTotal()).isEqualByComparingTo("0");
        assertThat(respuesta.egresosPorCategoria()).isEmpty();
    }

    @Test
    @DisplayName("Una cuota 0/M empieza al mes siguiente de la compra")
    void cuotaEnCeroEmpiezaElMesSiguiente() {
        Transaccion cuota = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("5000.00"), YearMonth.of(2026, 9), 0, 3);

        ProyeccionResponse mesDeCompra = proyectar(YearMonth.of(2026, 9), List.of(), List.of(cuota));
        assertThat(mesDeCompra.egresoTotal()).isEqualByComparingTo("0");

        ProyeccionResponse primerMes = proyectar(YearMonth.of(2026, 10), List.of(), List.of(cuota));
        assertThat(primerMes.egresoTotal()).isEqualByComparingTo("5000.00");

        ProyeccionResponse ultimoMes = proyectar(YearMonth.of(2026, 12), List.of(), List.of(cuota));
        assertThat(ultimoMes.egresoTotal()).isEqualByComparingTo("5000.00");

        ProyeccionResponse mesPosterior = proyectar(YearMonth.of(2027, 1), List.of(), List.of(cuota));
        assertThat(mesPosterior.egresoTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("La cuota proyectada avanza de número y desaparece al terminar el plan")
    void avanzaElNumeroDeCuotaProyectada() {
        // Última cuota facturada: la 5 de 6, en septiembre.
        Transaccion cuota5 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("7000.00"), YearMonth.of(2026, 9), 5, 6);

        ProyeccionResponse septiembre = proyectar(YearMonth.of(2026, 9), List.of(), List.of(cuota5));
        assertThat(septiembre.egresoTotal()).isEqualByComparingTo("0");

        ProyeccionResponse octubre = proyectar(YearMonth.of(2026, 10), List.of(), List.of(cuota5));
        assertThat(octubre.egresoTotal()).isEqualByComparingTo("7000.00");
        assertThat(octubre.transacciones()).hasSize(1);
        assertThat(octubre.transacciones().get(0).cuota()).isEqualTo("6/6");

        ProyeccionResponse noviembre = proyectar(YearMonth.of(2026, 11), List.of(), List.of(cuota5));
        assertThat(noviembre.egresoTotal()).isEqualByComparingTo("0");
        assertThat(noviembre.transacciones()).isEmpty();
    }

    @Test
    @DisplayName("Cuotas del mismo plan en meses distintos se muestran una sola vez, con la más actual")
    void agrupaCuotasDelMismoPlanEnDistintosMeses() {
        // Mismo plan de 6 cuotas: la 1 se facturó en agosto y la 2 en septiembre, con 2 pesos
        // de diferencia y descripciones distintas porque son comentarios del usuario.
        Transaccion cuota1 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("4995.00"), YearMonth.of(2026, 8), 1, 6, "primera cuota");
        Transaccion cuota2 = cuota(2L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("4997.00"), YearMonth.of(2026, 9), 2, 6, "segunda cuota, con un comentario");

        ProyeccionResponse octubre = proyectar(YearMonth.of(2026, 10), List.of(), List.of(cuota1, cuota2));

        assertThat(octubre.egresoTotal()).isEqualByComparingTo("4997.00");
        assertThat(octubre.egresosPorCategoria()).hasSize(1);
        assertThat(octubre.egresosPorCategoria().get(0).cantidad()).isEqualTo(1);
        assertThat(octubre.transacciones()).hasSize(1);
        assertThat(octubre.transacciones().get(0).cuota()).isEqualTo("3/6");
        // Se muestra la descripción de la cuota más reciente, no la de la primera.
        assertThat(octubre.transacciones().get(0).descripcion()).isEqualTo("segunda cuota, con un comentario");

        // La cuota 3 cae en noviembre, una sola vez.
        ProyeccionResponse noviembre = proyectar(YearMonth.of(2026, 11), List.of(), List.of(cuota1, cuota2));
        assertThat(noviembre.egresoTotal()).isEqualByComparingTo("4997.00");
        assertThat(noviembre.transacciones()).hasSize(1);
        assertThat(noviembre.transacciones().get(0).cuota()).isEqualTo("4/6");
    }

    @Test
    @DisplayName("La proyección del mes en curso suma lo ya registrado en ese periodo, sin duplicar")
    void sumaLoYaRegistradoEnElMesEnCurso() {
        // Cuota 5/6 en septiembre y la 6/6 ya registrada en octubre: es la última del plan.
        Transaccion cuota5 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("7000.00"), YearMonth.of(2026, 9), 5, 6);
        Transaccion cuota6 = cuota(2L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("7000.00"), YearMonth.of(2026, 10), 6, 6);

        // Recurrente de arriendo: septiembre y octubre ya registrados.
        Transaccion arriendoSeptiembre = recurrente(3L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("340056.00"), "Pago Arriendo");
        Transaccion arriendoOctubre = recurrente(4L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("340056.00"), "Pago Arriendo octubre");

        // Gasto puntual de octubre, sin recurrencia ni cuotas.
        Transaccion cena = base(5L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("12000.00"), "Cena");
        cena.setPeriodoFacturacion(PERIODO_EN_CURSO);

        ProyeccionResponse octubre = proyectar(PERIODO_EN_CURSO,
                List.of(arriendoSeptiembre, arriendoOctubre),
                List.of(cuota5, cuota6),
                List.of(cuota6, cena, arriendoOctubre));

        // 340.056 de arriendo + 7.000 de la cuota 6/6 + 12.000 de la cena.
        assertThat(octubre.egresoTotal()).isEqualByComparingTo("359056.00");

        // La cuota 6/6 cuenta una sola vez y con su número real, no proyectada como 7/6.
        assertThat(octubre.egresosPorCategoria()).extracting(ProyeccionCategoriaResponse::cantidad)
                .containsExactlyInAnyOrder(1L, 1L, 1L);
        assertThat(octubre.transacciones()).hasSize(3);
        assertThat(octubre.transacciones())
                .filteredOn(t -> t.categoria().equals("Compras en cuotas"))
                .extracting(ProyeccionTransaccionResponse::cuota)
                .containsExactly("6/6");
        assertThat(octubre.egresosPorCategoria()).extracting(ProyeccionCategoriaResponse::categoriaNombre)
                .containsExactlyInAnyOrder("Arriendo", "Compras en cuotas", "Salud");
    }

    @Test
    @DisplayName("La proyección de un mes futuro no suma transacciones ya registradas")
    void noSumaLoRegistradoEnUnPeriodoQueNoEsElEnCurso() {
        Transaccion cuota1 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("7000.00"), YearMonth.of(2026, 9), 1, 6);
        Transaccion cena = base(2L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("12000.00"), "Cena");
        cena.setPeriodoFacturacion(PERIODO_EN_CURSO);

        ProyeccionResponse noviembre = proyectar(YearMonth.of(2026, 11), List.of(), List.of(cuota1),
                List.of(cena));

        // Solo la cuota 3/6 proyectada desde septiembre; la cena de octubre no cuenta en noviembre.
        assertThat(noviembre.egresoTotal()).isEqualByComparingTo("7000.00");
        assertThat(noviembre.transacciones()).hasSize(1);
        assertThat(noviembre.transacciones().get(0).cuota()).isEqualTo("3/6");
    }

    @Test
    @DisplayName("Las compras en cuotas se agrupan en su propia línea, no en su categoría")
    void agrupaCuotasEnLineaPropia() {
        Transaccion cuota = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("7000.00"), YearMonth.of(2026, 9), 1, 6);
        Transaccion recurrente = recurrente(2L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("340056.00"), "Pago Arriendo");

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 10), List.of(recurrente), List.of(cuota));

        List<ProyeccionCategoriaResponse> egresos = respuesta.egresosPorCategoria();
        assertThat(egresos).extracting(ProyeccionCategoriaResponse::categoriaNombre)
                .containsExactly("Arriendo", "Compras en cuotas");
    }

    @Test
    @DisplayName("Las categorías hijas se agrupan dentro de su categoría padre")
    void agrupaCategoriasHijasEnElPadre() {
        Transaccion enPadre = recurrente(1L, TipoTransaccion.EGRESO, cuentaCorriente, arriendo,
                new BigDecimal("100.00"), "Arriendo");
        Transaccion enHija = recurrente(2L, TipoTransaccion.EGRESO, cuentaCorriente, pan,
                new BigDecimal("50.00"), "Panadería");

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 10), List.of(enPadre, enHija), List.of());

        assertThat(respuesta.egresosPorCategoria()).hasSize(1);
        ProyeccionCategoriaResponse arriendoTotal = respuesta.egresosPorCategoria().get(0);
        assertThat(arriendoTotal.categoriaNombre()).isEqualTo("Arriendo");
        assertThat(arriendoTotal.categoriaPadreId()).isEqualTo(1L);
        assertThat(arriendoTotal.total()).isEqualByComparingTo("150.00");
        assertThat(arriendoTotal.cantidad()).isEqualTo(2);
    }

    @Test
    @DisplayName("Devuelve vacío cuando no hay nada recurrente ni en cuotas")
    void devuelveProyeccionVacia() {
        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 10), List.of(), List.of());

        assertThat(respuesta.periodo()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(respuesta.ingresoTotal()).isEqualByComparingTo("0");
        assertThat(respuesta.egresoTotal()).isEqualByComparingTo("0");
        assertThat(respuesta.total()).isEqualByComparingTo("0");
        assertThat(respuesta.ingresosPorCategoria()).isEmpty();
        assertThat(respuesta.egresosPorCategoria()).isEmpty();
    }

    @Test
    @DisplayName("Las cuotas que faltan de un plan avanzado caen en los meses siguientes")
    void proyectaLasCuotasQueFaltanDeUnPlanAvanzado() {
        // La cuota 2 de 3 se facturó en agosto, así que la cuota 3 va en septiembre.
        Transaccion cuota2 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("7100.00"), YearMonth.of(2026, 8), 2, 3);

        ProyeccionResponse agosto = proyectar(YearMonth.of(2026, 8), List.of(), List.of(cuota2));
        assertThat(agosto.egresoTotal()).isEqualByComparingTo("0");

        ProyeccionResponse septiembre = proyectar(YearMonth.of(2026, 9), List.of(), List.of(cuota2));
        assertThat(septiembre.egresoTotal()).isEqualByComparingTo("7100.00");

        ProyeccionResponse octubre = proyectar(YearMonth.of(2026, 10), List.of(), List.of(cuota2));
        assertThat(octubre.egresoTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Un plan con cuota 0 y cuota 1 proyecta una sola cuota por mes")
    void noProyectaDosVecesElMismoPlan() {
        Transaccion cuota0 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("230033.00"), YearMonth.of(2026, 8), 0, 12);
        Transaccion cuota1 = cuota(2L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("230033.00"), YearMonth.of(2026, 9), 1, 12);

        ProyeccionResponse octubre = proyectar(YearMonth.of(2026, 10), List.of(), List.of(cuota0, cuota1));
        assertThat(octubre.egresoTotal()).isEqualByComparingTo("230033.00");
        assertThat(octubre.egresosPorCategoria().get(0).cantidad()).isEqualTo(1);

        // La cuota 1 sigue siendo la que ancla el plan, por eso la cuota 2 cae en noviembre.
        ProyeccionResponse noviembre = proyectar(YearMonth.of(2026, 11), List.of(), List.of(cuota0, cuota1));
        assertThat(noviembre.egresoTotal()).isEqualByComparingTo("230033.00");

        ProyeccionResponse septiembre = proyectar(YearMonth.of(2026, 9), List.of(), List.of(cuota0, cuota1));
        assertThat(septiembre.egresoTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Las cuotas de un plan facturadas en meses distintos se agrupan aunque cambie la descripción")
    void agrapaCuotasAunqueCambieLaDescripcion() {
        Transaccion cuota10 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("68636.00"), YearMonth.of(2026, 8), 10, 12, "AVANCE EN CUOTAS TE");
        Transaccion cuota11 = cuota(2L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("68636.00"), YearMonth.of(2026, 9), 11, 12, "AVANCE EN CUOTAS TE TASA INT. 3,15%");

        ProyeccionResponse octubre = proyectar(YearMonth.of(2026, 10), List.of(), List.of(cuota10, cuota11));
        assertThat(octubre.egresoTotal()).isEqualByComparingTo("68636.00");
        assertThat(octubre.egresosPorCategoria().get(0).cantidad()).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos compras distintas del mismo día y con distinto monto se proyectan por separado")
    void separaPlanesConDistintoMonto() {
        Transaccion lyon = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("5325.00"), YearMonth.of(2026, 8), 2, 3, "LYON");
        Transaccion monarch = cuota(2L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("4663.00"), YearMonth.of(2026, 8), 2, 3, "MONARCH 1088 PROVIDENCI");

        ProyeccionResponse septiembre = proyectar(YearMonth.of(2026, 9), List.of(), List.of(lyon, monarch));
        assertThat(septiembre.egresoTotal()).isEqualByComparingTo("9988.00");
        assertThat(septiembre.egresosPorCategoria().get(0).cantidad()).isEqualTo(2);
    }

    @Test
    @DisplayName("Los redondeos entre cuotas del mismo plan no lo dividen en dos")
    void toleraRedondeosEnElMontoDelPlan() {
        Transaccion cuota1 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("66208.00"), YearMonth.of(2026, 8), 1, 3, "NEAT GASTO COMUN SAN FELIPE CH");
        Transaccion cuota2 = cuota(2L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("66206.00"), YearMonth.of(2026, 9), 2, 3, "Pago Gasto comun junio");

        ProyeccionResponse octubre = proyectar(YearMonth.of(2026, 10), List.of(), List.of(cuota1, cuota2));
        assertThat(octubre.egresoTotal()).isEqualByComparingTo("66206.00");
    }

    @Test
    @DisplayName("Compras con el mismo comercio y fecha pero en cuentas distintas se proyectan por separado")
    void separaPlanesEnCuentasDistintas() {
        Transaccion enCorriente = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("7000.00"), YearMonth.of(2026, 9), 0, 6);
        Transaccion enTenpo = cuota(2L, TipoTransaccion.EGRESO, cuenta(11L, "Cuenta Tenpo"), salud,
                new BigDecimal("7000.00"), YearMonth.of(2026, 9), 0, 6);

        ProyeccionResponse respuesta = proyectar(YearMonth.of(2026, 10), List.of(), List.of(enCorriente, enTenpo));

        assertThat(respuesta.egresoTotal()).isEqualByComparingTo("14000.00");
        assertThat(respuesta.egresosPorCategoria().get(0).cantidad()).isEqualTo(2);
    }

    @Test
    @DisplayName("Las compras en cuotas sin descripción no se agrupan entre sí")
    void noAgrupaCuotasSinDescripcion() {
        Transaccion sinDescripcion1 = cuota(1L, TipoTransaccion.EGRESO, cuentaCorriente, salud,
                new BigDecimal("9998.00"), YearMonth.of(2026, 8), 2, 6, "");
        Transaccion sinDescripcion2 = cuota(2L, TipoTransaccion.EGRESO, cuenta(11L, "Cuenta Tenpo"), salud,
                new BigDecimal("9998.00"), YearMonth.of(2026, 8), 2, 6, "");

        ProyeccionResponse septiembre = proyectar(YearMonth.of(2026, 9), List.of(), List.of(sinDescripcion1, sinDescripcion2));
        assertThat(septiembre.egresoTotal()).isEqualByComparingTo("19996.00");
    }

    private Categoria categoria(Long id, String nombre, Categoria parent) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setParent(parent);
        return categoria;
    }

    private Cuenta cuenta(Long id, String nombre) {
        Cuenta cuenta = new Cuenta();
        cuenta.setId(id);
        cuenta.setNombre(nombre);
        return cuenta;
    }

    private Transaccion recurrente(Long id, TipoTransaccion tipo, Cuenta cuenta, Categoria categoria,
                                   BigDecimal monto, String descripcion) {
        Transaccion transaccion = base(id, tipo, cuenta, categoria, monto, descripcion);
        transaccion.setEsRecurrente(true);
        return transaccion;
    }

    private Transaccion cuota(Long id, TipoTransaccion tipo, Cuenta cuenta, Categoria categoria,
                              BigDecimal monto, YearMonth periodo, int cuotaActual, int totalCuotas) {
        return cuota(id, tipo, cuenta, categoria, monto, periodo, cuotaActual, totalCuotas, "Compra en cuotas");
    }

    private Transaccion cuota(Long id, TipoTransaccion tipo, Cuenta cuenta, Categoria categoria,
                              BigDecimal monto, YearMonth periodo, int cuotaActual, int totalCuotas, String descripcion) {
        Transaccion transaccion = base(id, tipo, cuenta, categoria, monto, descripcion);
        transaccion.setPeriodoFacturacion(periodo);
        transaccion.setCuotaActual(cuotaActual);
        transaccion.setTotalCuotas(totalCuotas);
        // Cada cuota se factura en su propio mes, con su propia fecha.
        transaccion.setFecha(LocalDate.of(periodo.getYear(), periodo.getMonth(), 15));
        return transaccion;
    }

    private Transaccion base(Long id, TipoTransaccion tipo, Cuenta cuenta, Categoria categoria,
                             BigDecimal monto, String descripcion) {
        Transaccion transaccion = new Transaccion();
        transaccion.setId(id);
        transaccion.setTipo(tipo);
        transaccion.setCuenta(cuenta);
        transaccion.setCategoria(categoria);
        transaccion.setMonto(monto);
        transaccion.setDescripcion(descripcion);
        transaccion.setMedio(MedioPago.CREDITO);
        transaccion.setPeriodoFacturacion(YearMonth.of(2026, 9));
        transaccion.setFecha(LocalDate.of(2026, 9, 15));
        transaccion.setComercio(new Comercio());
        return transaccion;
    }
}
