package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.UsuarioRequest;
import cl.finanzas.personales.dto.UsuarioResponse;
import cl.finanzas.personales.model.AppUser;
import cl.finanzas.personales.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse crearUsuario(UsuarioRequest request) {
        String emailNormalizado = request.email().trim().toLowerCase();

        if (appUserRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya existe");
        }

        AppUser user = new AppUser();
        user.setEmail(emailNormalizado);
        user.setNombre(request.nombre() != null ? request.nombre().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        AppUser guardado = appUserRepository.save(user);
        return new UsuarioResponse(
                guardado.getId(),
                guardado.getEmail(),
                guardado.getNombre(),
                guardado.getCreadoEn()
        );
    }
}

