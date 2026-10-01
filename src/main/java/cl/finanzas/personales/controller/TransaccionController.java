package cl.finanzas.personales.controller;

import cl.finanzas.personales.dto.CuadreGastosResponse;
import cl.finanzas.personales.dto.ProyeccionResponse;
import cl.finanzas.personales.dto.TransaccionFiltroQuery;
import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.service.TransaccionService;
import cl.finanzas.personales.service.UsuarioActualService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/transacciones")
@RequiredArgsConstructor
public class TransaccionController {
    private final TransaccionService transaccionService;
    private final UsuarioActualService usuarioActualService;

    /**
     * El usuario sale del token. Una pagina vacia es un 200 con la lista vacia adentro, no un 204
     * sin body: el front deserializa la respuesta y un null lo rompia.
     */
    @GetMapping
    public ResponseEntity<PagedModel<TransaccionResponse>> obtenerTransaccionesPorUsuario(
            @AuthenticationPrincipal Jwt jwt,
            @ModelAttribute TransaccionFiltroQuery filtros,
            @PageableDefault(sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<TransaccionResponse> transacciones =
                transaccionService.obtenerTransaccionesPorUsuario(usuarioActualService.idActual(jwt), filtros, pageable);

        PagedModel<TransaccionResponse> pagedModel = PagedModel.of(
                transacciones.getContent(),
                new PagedModel.PageMetadata(
                        transacciones.getSize(),
                        transacciones.getNumber(),
                        transacciones.getTotalElements(),
                        transacciones.getTotalPages()
                ),
                WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(TransaccionController.class)
                        .obtenerTransaccionesPorUsuario(null, filtros, pageable))
                        .withSelfRel()
        );

        return ResponseEntity.ok(pagedModel);
    }

    @GetMapping("/proyeccion")
    public ResponseEntity<ProyeccionResponse> obtenerProyeccion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String periodo
    ) {
        YearMonth periodoProyeccion;
        try {
            periodoProyeccion = YearMonth.parse(periodo);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo debe tener el formato yyyy-MM");
        }

        return ResponseEntity.ok(
                transaccionService.obtenerProyeccion(usuarioActualService.idActual(jwt), periodoProyeccion)
        );
    }

    @GetMapping("/cuadre")
    public ResponseEntity<CuadreGastosResponse> obtenerCuadreGastos(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Long cuentaId,
            @RequestParam String periodo,
            @RequestParam(defaultValue = "false") boolean incluyeCuotaCero
    ) {
        YearMonth periodoCuadre;
        try {
            periodoCuadre = YearMonth.parse(periodo);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo debe tener el formato yyyy-MM");
        }

        // Un periodo sin gastos es un resultado válido, nunca un 204.
        return ResponseEntity.ok(transaccionService.obtenerCuadreGastos(
                usuarioActualService.idActual(jwt), cuentaId, periodoCuadre, incluyeCuotaCero
        ));
    }

    @PostMapping
    public ResponseEntity<TransaccionResponse> crearTransaccion(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransaccionRequest request
    ) {
        return ResponseEntity.ok(
                transaccionService.crearTransaccion(usuarioActualService.idActual(jwt), request)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransaccionResponse> editarTransaccion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TransaccionRequest request
    ) {
        return ResponseEntity.ok(
                transaccionService.editarTransaccion(id, usuarioActualService.idActual(jwt), request)
        );
    }

    @PostMapping("/{id}/eliminar")
    public ResponseEntity<Void> eliminarTransaccion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        transaccionService.eliminarTransaccion(id, usuarioActualService.idActual(jwt));
        return ResponseEntity.noContent().build();
    }
}