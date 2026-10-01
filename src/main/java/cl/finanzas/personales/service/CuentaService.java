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

import java.util.List;

@Service
@RequiredArgsConstructor
public class CuentaService {
    private final CuentaRepository cuentaRepository;

    @Transactional
    public CuentaResponse crearCuenta(Long userId, CuentaRequest request) {
        Cuenta cuenta = nuevaCuenta(request, userId);
        return toResponse(cuentaRepository.save(cuenta));
    }

    @Transactional
    public List<CuentaResponse> obtenerCuentasPorUsuario(Long userId) {
        return cuentaRepository.findByUserIdAndActivoIsTrue(userId)
                .stream()
                .map(CuentaService::toResponse)
                .toList();
    }

    @Transactional
    public CuentaResponse actualizarCuenta(Long id, Long userId, CuentaRequest request) {
        Cuenta cuenta = obtenerCuentaDelUsuario(id, userId);

        cuenta.setNombre(request.nombre());
        cuenta.setMoneda(request.moneda());
        cuenta.setTipo(request.tipo());
        cuenta.setSaldoInicial(request.saldoInicial());

        return toResponse(cuentaRepository.save(cuenta));
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

    private static CuentaResponse toResponse(Cuenta cuenta) {
        return new CuentaResponse(
                cuenta.getId(),
                cuenta.getNombre(),
                cuenta.getTipo(),
                cuenta.getSaldoInicial()
        );
    }
}