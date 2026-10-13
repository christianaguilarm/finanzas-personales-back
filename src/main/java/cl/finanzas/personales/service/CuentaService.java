package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CuentaRequest;
import cl.finanzas.personales.dto.CuentaResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CuentaService {
    private final CuentaRepository cuentaRepository;
    private final SaldoService saldoService;
    private final Clock clock;

    @Transactional
    public CuentaResponse crearCuenta(Long userId, CuentaRequest request) {
        Cuenta cuenta = nuevaCuenta(request, userId);
        Cuenta guardada = cuentaRepository.save(cuenta);
        return toResponse(guardada, saldoService.saldoActual(userId, guardada, LocalDate.now(clock)));
    }

    @Transactional
    public List<CuentaResponse> obtenerCuentasPorUsuario(Long userId) {
        List<Cuenta> cuentas = cuentaRepository.findByUserIdAndActivoIsTrue(userId);
        Map<Long, BigDecimal> saldos = saldoService.saldosActuales(userId, cuentas, LocalDate.now(clock));

        return cuentas.stream()
                .map(cuenta -> toResponse(cuenta, saldos.get(cuenta.getId())))
                .toList();
    }

    @Transactional
    public CuentaResponse actualizarCuenta(Long id, Long userId, CuentaRequest request) {
        Cuenta cuenta = obtenerCuentaDelUsuario(id, userId);

        cuenta.setNombre(request.nombre());
        cuenta.setMoneda(request.moneda());
        cuenta.setTipo(request.tipo());

        // El saldo inicial solo se toca si viene en el request: es el ancla que el usuario puso
        // al crear la cuenta y de la que dependen todos los saldos derivados. No se recalcula ni
        // se deriva de las transacciones, y menos se pisa con cero.
        if (request.saldoInicial() != null) {
            cuenta.setSaldoInicial(request.saldoInicial());
        }

        Cuenta guardada = cuentaRepository.save(cuenta);
        return toResponse(guardada, saldoService.saldoActual(userId, guardada, LocalDate.now(clock)));
    }

    /**
     * Baja logica (activo = false) y no fisica: las transacciones apuntan a la cuenta con FK, y
     * borrarla las dejaria colgando. El filtro de lectura ya ignora las cuentas inactivas, asi
     * que para la API es indistinguible de un borrado, pero se puede deshacer.
     */
    @Transactional
    public void eliminarCuenta(Long id, Long userId) {
        Cuenta cuenta = obtenerCuentaDelUsuario(id, userId);
        cuenta.setActivo(false);
        cuentaRepository.save(cuenta);
    }

    private Cuenta nuevaCuenta(CuentaRequest request, Long userId) {
        Cuenta cuenta = new Cuenta();
        cuenta.setNombre(request.nombre());
        cuenta.setMoneda(request.moneda());
        cuenta.setTipo(request.tipo());
        cuenta.setSaldoInicial(request.saldoInicial());
        cuenta.setActivo(true);

        AppUser user = new AppUser();
        user.setId(userId);
        cuenta.setUser(user);

        return cuenta;
    }

    private Cuenta obtenerCuentaDelUsuario(Long id, Long userId) {
        Cuenta cuenta = cuentaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuenta no existe"));

        if (!cuenta.isActivo() || !userId.equals(cuenta.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta no pertenece al usuario");
        }

        return cuenta;
    }

    /**
     * El saldo actual se arma acá adentro, con la sesión abierta: viene de la consulta de
     * acumulado, no de la relación perezosa de la cuenta, así que tampoco hay DTOs que se armen
     * fuera de la transacción.
     */
    private static CuentaResponse toResponse(Cuenta cuenta, BigDecimal saldoActual) {
        return new CuentaResponse(
                cuenta.getId(),
                cuenta.getNombre(),
                cuenta.getTipo(),
                cuenta.getSaldoInicial(),
                saldoActual
        );
    }
}
