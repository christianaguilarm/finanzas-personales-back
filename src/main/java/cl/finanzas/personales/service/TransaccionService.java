package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CuadreCategoriaResponse;
import cl.finanzas.personales.dto.CuadreGastosResponse;
import cl.finanzas.personales.dto.ProyeccionCategoriaResponse;
import cl.finanzas.personales.dto.ProyeccionResponse;
import cl.finanzas.personales.dto.ProyeccionTransaccionResponse;
import cl.finanzas.personales.dto.TransaccionFiltroQuery;
import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.mapper.TransaccionMapper;
import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.model.Comercio;
import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.model.Transaccion;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.ComercioRepository;
import cl.finanzas.personales.repository.CuentaRepository;
import cl.finanzas.personales.repository.TagRepository;
import cl.finanzas.personales.repository.TransaccionRepository;
import cl.finanzas.personales.repository.specification.TransaccionSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

@Service
@RequiredArgsConstructor
public class TransaccionService {

    /** Absorbe los redondeos entre cuotas del mismo plan (4.995 vs 4.997, 66.206 vs 66.208). */
    private static final BigDecimal TOLERANCIA_MONTO_PLAN = new BigDecimal("10");

    private final TransaccionRepository transaccionRepository;
    private final CategoriaRepository categoriaRepository;
    private final ComercioRepository comercioRepository;
    private final CuentaRepository cuentaRepository;
    private final TagRepository tagRepository;
    private final TransaccionMapper mapper;
    private final Clock clock;

    /** Alias de columnas de la UI que no coinciden con el nombre del campo. */
    private static final Map<String, String> ORDENES_PERMITIDOS = Map.of(
            "cuotas", "cuotaActual",
            "categoria", "categoria.nombre",
            "cuenta", "cuenta.nombre"
    );

    /** Propiedades por las que el backend admite ordenar, ya resueltas los alias. */
    private static final Set<String> PROPIEDADES_ORDENABLES = Set.of(
            "id", "tipo", "fecha", "periodoFacturacion", "monto", "medio",
            "descripcion", "esRecurrente", "totalCuotas", "cuotaActual", "creadoEn",
            "categoria.nombre", "categoria.id", "cuenta.nombre", "cuenta.id", "comercio.nombre"
    );

    @Transactional
    public TransaccionResponse crearTransaccion(Long userId, TransaccionRequest request) {
        Cuenta cuenta = cuentaRepository.findByUserIdAndActivoIsTrue(userId)
                .stream()
                .filter(c -> c.getId().equals(request.cuentaId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La cuenta no existe, no pertenece al usuario o está inactiva"
                ));

        TipoTransaccion tipoResuelto = resolverTipoTransaccion(request, cuenta);

        if ((request.totalCuotas() == null) != (request.cuotaActual() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Para compras en cuotas debes enviar totalCuotas y cuotaActual juntos"
            );
        }

        if (request.totalCuotas() != null) {
            if (request.medio() != cl.finanzas.personales.model.MedioPago.CREDITO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las cuotas solo aplican para medio CREDITO");
            }

            if (request.cuotaActual() > request.totalCuotas()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "cuotaActual no puede ser mayor que totalCuotas"
                );
            }
        }

        validarReferenciasUsuario(userId, request.categoriaId(), request.subcategoriaId(), request.comercioId());

        Transaccion transaccion = mapper.toEntity(request, tipoResuelto, userId);
        transaccion.setCuenta(cuenta);

        // Cargar tags si existen
        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            var tags = new HashSet<>(tagRepository.findAllById(request.tagIds()));
            transaccion.setTags(tags);
        }

        Transaccion guardada = transaccionRepository.save(transaccion);
        return mapper.toResponse(guardada);
    }

    private TipoTransaccion resolverTipoTransaccion(TransaccionRequest request, Cuenta cuenta) {
        if (request.tipo() != null) {
            return request.tipo();
        }

        String tipoCuenta = cuenta.getTipo();
        if (tipoCuenta == null || tipoCuenta.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cuenta no tiene un tipo válido para derivar la transacción"
            );
        }

        String tipoNormalizado = tipoCuenta.trim().toUpperCase(Locale.ROOT);

        try {
            return TipoTransaccion.valueOf(tipoNormalizado);
        } catch (IllegalArgumentException ignored) {
            if ("CREDITO".equals(tipoNormalizado)) {
                return TipoTransaccion.EGRESO;
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El tipo de la cuenta no coincide con un TipoTransaccion válido"
            );
        }
    }

    @Transactional
    public TransaccionResponse editarTransaccion(Long transaccionId, Long userId, TransaccionRequest request) {
        Transaccion transaccion = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transacción no encontrada"
                ));

        if (!transaccion.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes permiso para editar esta transacción"
            );
        }

        Cuenta cuenta = cuentaRepository.findByUserIdAndActivoIsTrue(userId)
                .stream()
                .filter(c -> c.getId().equals(request.cuentaId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La cuenta no existe, no pertenece al usuario o está inactiva"
                ));

        TipoTransaccion tipoResuelto = resolverTipoTransaccion(request, cuenta);

        if ((request.totalCuotas() == null) != (request.cuotaActual() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Para compras en cuotas debes enviar totalCuotas y cuotaActual juntos"
            );
        }

        if (request.totalCuotas() != null) {
            if (request.medio() != cl.finanzas.personales.model.MedioPago.CREDITO) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las cuotas solo aplican para medio CREDITO");
            }

            if (request.cuotaActual() > request.totalCuotas()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "cuotaActual no puede ser mayor que totalCuotas"
                );
            }
        }

        validarReferenciasUsuario(userId, request.categoriaId(), request.subcategoriaId(), request.comercioId());

        // Actualizar campos
        transaccion.setCuenta(cuenta);
        transaccion.setTipo(tipoResuelto);
        transaccion.setFecha(request.fecha());
        transaccion.setPeriodoFacturacion(
                request.periodoFacturacion() != null
                        ? request.periodoFacturacion()
                        : java.time.YearMonth.from(request.fecha())
        );
        transaccion.setMonto(request.monto());
        transaccion.setMedio(request.medio());
        transaccion.setDescripcion(request.descripcion());
        transaccion.setEsRecurrente(request.esRecurrente());
        transaccion.setTotalCuotas(request.totalCuotas());
        transaccion.setCuotaActual(request.cuotaActual());

        // Actualizar relaciones opcionales por id (si viene null, limpia la relación)
        transaccion.setCategoria(toCategoriaRef(request.categoriaId()));
        transaccion.setSubcategoria(toCategoriaRef(request.subcategoriaId()));
        transaccion.setComercio(toComercioRef(request.comercioId()));

        // Actualizar tags
        if (request.tagIds() != null) {
            if (request.tagIds().isEmpty()) {
                transaccion.setTags(new HashSet<>());
            } else {
                var tags = new HashSet<>(tagRepository.findAllById(request.tagIds()));
                transaccion.setTags(tags);
            }
        }

        Transaccion actualizada = transaccionRepository.save(transaccion);
        return mapper.toResponse(actualizada);
    }

    @Transactional
    public void eliminarTransaccion(Long transaccionId, Long userId) {
        Transaccion transaccion = transaccionRepository.findById(transaccionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transacción no encontrada"
                ));

        if (!transaccion.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes permiso para eliminar esta transacción"
            );
        }

        transaccion.getTags().clear();
        transaccionRepository.delete(transaccion);
    }

    private void validarReferenciasUsuario(Long userId, Long categoriaId, Long subcategoriaId, Long comercioId) {
        if (categoriaId != null && !categoriaRepository.existsByIdAndUserId(categoriaId, userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoriaId no existe o no pertenece al usuario");
        }

        if (subcategoriaId != null && !categoriaRepository.existsByIdAndUserId(subcategoriaId, userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subcategoriaId no existe o no pertenece al usuario");
        }

        if (comercioId != null && !comercioRepository.existsByIdAndUserId(comercioId, userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "comercioId no existe o no pertenece al usuario");
        }
    }

    private Categoria toCategoriaRef(Long id) {
        if (id == null) {
            return null;
        }
        Categoria categoria = new Categoria();
        categoria.setId(id);
        return categoria;
    }

    private Comercio toComercioRef(Long id) {
        if (id == null) {
            return null;
        }
        Comercio comercio = new Comercio();
        comercio.setId(id);
        return comercio;
    }

    /**
     * Traduce los alias de ordenamiento de la UI a propiedades reales de Transaccion.
     * Spring Data lanza PropertyReferenceException (500) ante una propiedad inexistente,
     * por ejemplo la columna "cuotas", que en realidad ordena por cuotaActual.
     */
    private Pageable traducirOrden(Pageable pageable) {
        List<Order> ordenes = pageable.getSort().stream()
                .map(orden -> {
                    String propiedad = ORDENES_PERMITIDOS.getOrDefault(orden.getProperty(), orden.getProperty());
                    if (!PROPIEDADES_ORDENABLES.contains(propiedad)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "No se puede ordenar por '" + orden.getProperty() + "'"
                        );
                    }
                    return orden.isAscending() ? Order.asc(propiedad) : Order.desc(propiedad);
                })
                .toList();

        return ordenes.isEmpty()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(ordenes));
    }

    @Transactional(readOnly = true)
    public Page<TransaccionResponse> obtenerTransaccionesPorUsuario(Long userId, TransaccionFiltroQuery filtros, Pageable pageable) {
        if (filtros.getFecha() != null && (filtros.getFechaDesde() != null || filtros.getFechaHasta() != null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No puedes usar fecha exacta junto con fechaDesde o fechaHasta"
            );
        }

        if (filtros.getFechaDesde() != null && filtros.getFechaHasta() != null
                && filtros.getFechaDesde().isAfter(filtros.getFechaHasta())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fechaDesde no puede ser mayor que fechaHasta");
        }

        if (filtros.getMontoMin() != null && filtros.getMontoMax() != null
                && filtros.getMontoMin().compareTo(filtros.getMontoMax()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "montoMin no puede ser mayor que montoMax");
        }

        return transaccionRepository.findAll(TransaccionSpecifications.conFiltros(userId, filtros), traducirOrden(pageable))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProyeccionResponse obtenerProyeccion(Long userId, YearMonth periodo) {
        List<Transaccion> recurrentes = deduplicarRecurrentes(
                transaccionRepository.findAll(TransaccionSpecifications.recurrentesParaProyeccion(userId))
                        .stream()
                        .filter(Transaccion::isEsRecurrente)
                        .toList()
        );

        List<Transaccion> cuotasDelMes = deduplicarPlanesEnCuotas(
                transaccionRepository.findAll(TransaccionSpecifications.comprasEnCuotasParaProyeccion(userId))
        ).stream()
                .filter(transaccion -> aplicaCuotaEnPeriodo(transaccion, periodo))
                .toList();

        List<Transaccion> proyectadas = new ArrayList<>(recurrentes);
        proyectadas.addAll(cuotasDelMes);
        proyectadas.addAll(registradasDelPeriodoEnCurso(userId, periodo, proyectadas));

        List<ProyeccionCategoriaResponse> ingresos = agruparPorCategoriaRaiz(
                proyectadas.stream().filter(t -> t.getTipo() == TipoTransaccion.INGRESO).toList()
        );
        List<ProyeccionCategoriaResponse> egresos = agruparPorCategoriaRaiz(
                proyectadas.stream().filter(t -> t.getTipo() == TipoTransaccion.EGRESO).toList()
        );

        BigDecimal ingresoTotal = sumar(ingresos);
        BigDecimal egresoTotal = sumar(egresos);

        return new ProyeccionResponse(
                periodo,
                ingresoTotal,
                egresoTotal,
                ingresoTotal.subtract(egresoTotal),
                ingresos,
                egresos,
                detallarProyeccion(proyectadas, periodo)
        );
    }

    /**
     * En el mes en curso una parte de los gastos ya ocurrió, así que la proyección se completa con
     * lo que el usuario ya registró para ese periodo. Solo se suman las que todavía no estén
     * representadas: cada concepto recurrente y cada plan en cuotas ya se contaron con su fila
     * más reciente, que es justamente la del periodo en curso cuando ya existe, así que agregar
     * esa misma fila otra vez lo duplicaría.
     *
     * Para los periodos futuros no se suma nada: todavía no hay transacciones que registradas.
     */
    private List<Transaccion> registradasDelPeriodoEnCurso(Long userId, YearMonth periodo,
                                                           List<Transaccion> yaProyectadas) {
        if (!periodo.equals(YearMonth.now(clock))) {
            return List.of();
        }

        Set<Long> yaContadas = new HashSet<>();
        for (Transaccion transaccion : yaProyectadas) {
            yaContadas.add(transaccion.getId());
        }

        return transaccionRepository.findAll(TransaccionSpecifications.delPeriodo(userId, periodo))
                .stream()
                .filter(transaccion -> !yaContadas.contains(transaccion.getId()))
                .toList();
    }

    /**
     * Cuadre de los gastos registrados de una cuenta en un periodo. El monto facturado del
     * estado de cuenta no se persiste: el usuario lo compara a mano contra su PDF, así que
     * acá solo se devuelve lo que el sistema tiene registrado, su total y el desglose.
     *
     * No se pagina ni se ordena en base: la consulta trae el periodo completo de la cuenta
     * y el filtrado en memoria es el mismo criterio de cuota cero que usa la vista de presupuesto.
     */
    @Transactional(readOnly = true)
    public CuadreGastosResponse obtenerCuadreGastos(Long userId, Long cuentaId, YearMonth periodo,
                                                    boolean incluyeCuotaCero) {
        Cuenta cuenta = cuentaRepository.findByUserIdAndActivoIsTrue(userId)
                .stream()
                .filter(c -> c.getId().equals(cuentaId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La cuenta no existe, no pertenece al usuario o está inactiva"
                ));

        List<Transaccion> transacciones = transaccionRepository
                .findAll(TransaccionSpecifications.paraCuadre(userId, cuentaId, periodo))
                .stream()
                .filter(transaccion -> incluyeCuotaCero || !esCuotaCero(transaccion))
                .toList();

        BigDecimal total = sumarMontos(transacciones);
        List<Transaccion> comprasEnCuotas = transacciones.stream()
                .filter(TransaccionService::esCompraEnCuotas)
                .toList();

        return new CuadreGastosResponse(
                cuenta.getId(),
                cuenta.getNombre(),
                periodo,
                total,
                transacciones.size(),
                comprasEnCuotas.size(),
                sumarMontos(comprasEnCuotas),
                incluyeCuotaCero,
                desglosarPorCategoria(transacciones),
                desglosarPorComercio(transacciones),
                transacciones.stream()
                        .sorted(Comparator
                                .comparing(Transaccion::getFecha, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(Transaccion::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(mapper::toResponse)
                        .toList()
        );
    }

    /**
     * La fila con cuotaActual en cero es la que abre un plan en cuotas, no un cargo: no suma
     * al estado de cuenta hasta que se factura la primera cuota, así que se descarta salvo
     * que el usuario pida verla explícitamente.
     */
    private static boolean esCuotaCero(Transaccion transaccion) {
        return transaccion.getCuotaActual() != null && transaccion.getCuotaActual() == 0;
    }

    private static boolean esCompraEnCuotas(Transaccion transaccion) {
        return transaccion.getTotalCuotas() != null && transaccion.getTotalCuotas() > 1;
    }

    private BigDecimal sumarMontos(List<Transaccion> transacciones) {
        return transacciones.stream()
                .map(Transaccion::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Agrupado por categoría raíz, como en la proyección, pero sin la línea "Compras en cuotas":
     * en el cuadre el usuario quiere ver la categoría real para poder notar que falta algo.
     */
    private List<CuadreCategoriaResponse> desglosarPorCategoria(List<Transaccion> transacciones) {
        Map<String, AcumuladorLinea> lineas = new LinkedHashMap<>();

        for (Transaccion transaccion : transacciones) {
            Categoria raiz = resolverCategoriaRaiz(transaccion.getCategoria());
            Long id = raiz != null ? raiz.getId() : null;
            String nombre = raiz != null ? raiz.getNombre() : "Sin categoría";

            acumular(lineas, id, nombre, transaccion);
        }

        return cerrarLineas(lineas);
    }

    private List<CuadreCategoriaResponse> desglosarPorComercio(List<Transaccion> transacciones) {
        Map<String, AcumuladorLinea> lineas = new LinkedHashMap<>();

        for (Transaccion transaccion : transacciones) {
            Comercio comercio = transaccion.getComercio();
            Long id = comercio != null ? comercio.getId() : null;
            String nombre = comercio != null ? comercio.getNombre() : "Sin comercio";

            acumular(lineas, id, nombre, transaccion);
        }

        return cerrarLineas(lineas);
    }

    private void acumular(Map<String, AcumuladorLinea> lineas, Long id, String nombre, Transaccion transaccion) {
        String clave = (id == null ? "null" : id.toString()) + "-" + nombre;

        AcumuladorLinea linea = lineas.computeIfAbsent(clave, k -> new AcumuladorLinea(id, nombre));
        linea.total = linea.total.add(transaccion.getMonto());
        linea.cantidad++;
    }

    private List<CuadreCategoriaResponse> cerrarLineas(Map<String, AcumuladorLinea> lineas) {
        return lineas.values()
                .stream()
                .sorted(Comparator.comparing((AcumuladorLinea linea) -> linea.total).reversed())
                .map(linea -> new CuadreCategoriaResponse(linea.id, linea.nombre, linea.total, linea.cantidad))
                .toList();
    }

    /**
     * Un concepto recurrente se identifica por tipo + cuenta + categoría, sin importar la descripción.
     * Si hay varias filas del mismo concepto se conserva la más reciente (mayor id).
     */
    private List<Transaccion> deduplicarRecurrentes(List<Transaccion> recurrentes) {
        Map<String, Transaccion> porConcepto = new HashMap<>();

        for (Transaccion transaccion : recurrentes) {
            String clave = transaccion.getTipo() + "|"
                    + (transaccion.getCuenta() != null ? transaccion.getCuenta().getId() : "null") + "|"
                    + (transaccion.getCategoria() != null ? transaccion.getCategoria().getId() : "null");

            porConcepto.merge(clave, transaccion,
                    (existente, nuevo) -> existente.getId() > nuevo.getId() ? existente : nuevo);
        }

        return new ArrayList<>(porConcepto.values());
    }

    /**
     * Cada compra en cuotas genera una fila por cuota. Solo la fila más avanzada del plan debe
     * proyectar las cuotas que faltan, porque si no un plan facturado hasta la cuota 1 se contaría
     * dos veces en el mismo mes.
     *
     * Un plan no tiene identificador propio, así que se arma con las filas que lo componen:
     * cuenta + comercio + categoría + número de cuotas, y dentro de eso por monto.
     * Ni la fecha ni la descripción participan: las cuotas de un plan se facturan en meses y
     * días distintos (4.995 en agosto y 4.997 en septiembre son la misma cuota avanzando) y la
     * descripción es solo un comentario del usuario, que además el banco va cambiando entre
     * cuotas ("AVANCE EN CUOTAS TE" y luego "AVANCE EN CUOTAS TE TASA INT. 3,15%").
     */
    private List<Transaccion> deduplicarPlanesEnCuotas(List<Transaccion> cuotas) {
        Map<String, List<Transaccion>> porGrupo = new LinkedHashMap<>();

        for (Transaccion transaccion : cuotas) {
            porGrupo.computeIfAbsent(clavePlan(transaccion), clave -> new ArrayList<>()).add(transaccion);
        }

        List<Transaccion> anclas = new ArrayList<>();
        for (List<Transaccion> grupo : porGrupo.values()) {
            for (List<Transaccion> plan : separarPorMonto(grupo)) {
                anclas.add(cuotaMasAvanzada(plan));
            }
        }

        return anclas;
    }

    private String clavePlan(Transaccion transaccion) {
        return idDe(transaccion.getCuenta()) + "|"
                + idDe(transaccion.getComercio()) + "|"
                + idDe(transaccion.getCategoria()) + "|"
                + transaccion.getTotalCuotas();
    }

    private String idDe(Object relacion) {
        if (relacion instanceof Cuenta cuenta) {
            return String.valueOf(cuenta.getId());
        }
        if (relacion instanceof Comercio comercio) {
            return String.valueOf(comercio.getId());
        }
        if (relacion instanceof Categoria categoria) {
            return String.valueOf(categoria.getId());
        }
        return "null";
    }

    /**
     * Dos compras distintas pueden coincidir en cuenta, comercio, categoría y número de cuotas,
     * así que dentro de cada grupo se separan por monto. La tolerancia absorbe los redondeos y
     * el ajuste de tasa entre cuotas del mismo plan (4.995 vs 4.997, 66.206 vs 66.208) sin
     * llegar a fusionar dos compras realmente distintas (66.206 vs 66.800).
     */
    private List<List<Transaccion>> separarPorMonto(List<Transaccion> grupo) {
        List<List<Transaccion>> planes = new ArrayList<>();

        for (Transaccion transaccion : grupo) {
            List<Transaccion> plan = planes.stream()
                    .filter(candidato -> montosEquivalentes(candidato.get(0).getMonto(), transaccion.getMonto()))
                    .findFirst()
                    .orElseGet(() -> {
                        List<Transaccion> nuevoPlan = new ArrayList<>();
                        planes.add(nuevoPlan);
                        return nuevoPlan;
                    });
            plan.add(transaccion);
        }

        return planes;
    }

    private boolean montosEquivalentes(BigDecimal primero, BigDecimal segundo) {
        if (primero == null || segundo == null) {
            return true;
        }

        return primero.subtract(segundo).abs().compareTo(TOLERANCIA_MONTO_PLAN) <= 0;
    }

    /** La fila más avanzada del plan: la de mayor cuota, y a igual cuota la más reciente. */
    private Transaccion cuotaMasAvanzada(List<Transaccion> plan) {
        return plan.stream()
                .max(Comparator
                        .comparingInt(TransaccionService::cuotaDe)
                        .thenComparing(TransaccionService::periodoDe, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Transaccion::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow();
    }

    private static int cuotaDe(Transaccion transaccion) {
        return transaccion.getCuotaActual() == null ? 0 : transaccion.getCuotaActual();
    }

    /** Periodo al que corresponde la fila, que es el que usa el cálculo de cuotas. */
    private static YearMonth periodoDe(Transaccion transaccion) {
        return transaccion.getPeriodoFacturacion() != null
                ? transaccion.getPeriodoFacturacion()
                : YearMonth.from(transaccion.getFecha());
    }

    private boolean aplicaCuotaEnPeriodo(Transaccion transaccion, YearMonth periodo) {
        Integer totalCuotas = transaccion.getTotalCuotas();
        if (totalCuotas == null || totalCuotas <= 1) {
            return false;
        }

        long mesesTranscurridos = mesesTranscurridosDesde(transaccion, periodo);

        // Cada fila es una cuota ya facturada en su periodo, y las cuotas de un plan caen una por mes.
        // Tomando la fila más avanzada del plan, las que faltan son total - cuotaActual,
        // empezando el mes siguiente al de esa fila.
        long cuotasRestantes = totalCuotas - cuotaDe(transaccion);

        return mesesTranscurridos >= 1 && mesesTranscurridos <= cuotasRestantes;
    }

    /** Meses entre el periodo de facturación de la fila y el periodo proyectado. */
    private long mesesTranscurridosDesde(Transaccion transaccion, YearMonth periodo) {
        return ChronoUnit.MONTHS.between(periodoDe(transaccion), periodo);
    }

    private List<ProyeccionCategoriaResponse> agruparPorCategoriaRaiz(List<Transaccion> transacciones) {
        Map<String, AcumuladorCategoria> mapa = new LinkedHashMap<>();

        for (Transaccion transaccion : transacciones) {
            boolean esCompraEnCuotas = transaccion.getTotalCuotas() != null && transaccion.getTotalCuotas() > 1;
            Categoria raiz = esCompraEnCuotas ? null : resolverCategoriaRaiz(transaccion.getCategoria());

            Long categoriaId = raiz != null ? raiz.getId() : null;
            String categoriaNombre = nombreCategoriaProyeccion(transaccion);
            String clave = (categoriaId == null ? "null" : categoriaId.toString()) + "-" + categoriaNombre;

            AcumuladorCategoria acumulador = mapa.computeIfAbsent(clave, k -> new AcumuladorCategoria(categoriaId, categoriaNombre));
            acumulador.total = acumulador.total.add(transaccion.getMonto());
            acumulador.cantidad++;
        }

        return mapa.values()
                .stream()
                .sorted(Comparator.comparing((AcumuladorCategoria a) -> a.total).reversed())
                .map(acumulador -> new ProyeccionCategoriaResponse(
                        acumulador.categoriaId,
                        acumulador.categoriaNombre,
                        acumulador.total,
                        acumulador.cantidad
                ))
                .toList();
    }

    private Categoria resolverCategoriaRaiz(Categoria categoria) {
        if (categoria == null) {
            return null;
        }
        return categoria.getParent() != null ? categoria.getParent() : categoria;
    }

    /**
     * Etiqueta de categoría usada tanto en el agrupado como en el detalle de la proyección.
     * Las compras en cuotas se agrupan siempre bajo "Compras en cuotas", sin importar la categoría real.
     */
    private String nombreCategoriaProyeccion(Transaccion transaccion) {
        if (transaccion.getTotalCuotas() != null && transaccion.getTotalCuotas() > 1) {
            return "Compras en cuotas";
        }
        Categoria raiz = resolverCategoriaRaiz(transaccion.getCategoria());
        return raiz != null ? raiz.getNombre() : "Sin categoría";
    }

    private List<ProyeccionTransaccionResponse> detallarProyeccion(List<Transaccion> proyectadas, YearMonth periodo) {
        return proyectadas.stream()
                .map(transaccion -> new ProyeccionTransaccionResponse(
                        transaccion.getDescripcion(),
                        transaccion.getCuenta() != null ? transaccion.getCuenta().getNombre() : null,
                        nombreCategoriaProyeccion(transaccion),
                        formatearCuota(transaccion, periodo),
                        transaccion.getFecha(),
                        transaccion.getMonto(),
                        transaccion.getTipo()
                ))
                .sorted(Comparator
                        .comparing((ProyeccionTransaccionResponse t) -> t.tipo() == TipoTransaccion.INGRESO ? 0 : 1)
                        .thenComparing(ProyeccionTransaccionResponse::monto, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Muestra la cuota que corresponde al periodo proyectado, no la de la última cuota facturada.
     * Una fila 5/6 facturada en septiembre se muestra 6/6 en octubre y desaparece en noviembre.
     */
    private String formatearCuota(Transaccion transaccion, YearMonth periodo) {
        Integer total = transaccion.getTotalCuotas();
        if (total == null || total <= 1) {
            return null;
        }

        int cuotaProyectada = cuotaDe(transaccion) + (int) mesesTranscurridosDesde(transaccion, periodo);
        return cuotaProyectada + "/" + total;
    }

    private BigDecimal sumar(List<ProyeccionCategoriaResponse> categorias) {
        return categorias.stream()
                .map(ProyeccionCategoriaResponse::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final class AcumuladorCategoria {
        private final Long categoriaId;
        private final String categoriaNombre;
        private BigDecimal total = BigDecimal.ZERO;
        private long cantidad;

        private AcumuladorCategoria(Long categoriaId, String categoriaNombre) {
            this.categoriaId = categoriaId;
            this.categoriaNombre = categoriaNombre;
        }
    }

    /** Línea en construcción de un desglose del cuadre, por categoría o por comercio. */
    private static final class AcumuladorLinea {
        private final Long id;
        private final String nombre;
        private BigDecimal total = BigDecimal.ZERO;
        private long cantidad;

        private AcumuladorLinea(Long id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }
}
