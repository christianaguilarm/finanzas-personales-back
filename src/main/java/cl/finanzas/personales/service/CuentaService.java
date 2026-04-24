package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.CuentaRequest;
import cl.finanzas.personales.dto.CuentaResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.model.Cuenta;
import cl.finanzas.personales.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CuentaService {
    private final CuentaRepository cuentaRepository;

    @Transactional
    public CuentaResponse crearCuenta(CuentaRequest request) {
        Cuenta cuenta = new Cuenta();
        cuenta.setNombre(request.nombre());
        cuenta.setMoneda(request.moneda());
        cuenta.setTipo(request.tipo());
        cuenta.setSaldoInicial(request.saldoInicial());
        cuenta.setActivo(true);

        AppUser user = new AppUser();
        user.setId(request.userId());
        cuenta.setUser(user);

        Cuenta guardada = cuentaRepository.save(cuenta);
        return new CuentaResponse(
                guardada.getId(),
                guardada.getNombre(),
                guardada.getTipo()
        );
    }

    public List<CuentaResponse> obtenerCuentasPorUsuario(Long userId) {
        return cuentaRepository.findByUserIdAndActivoIsTrue(userId)
                .stream()
                .map(cat -> new CuentaResponse(
                        cat.getId(),
                        cat.getNombre(),
                        cat.getTipo()
                ))
                .toList();
    }
}
