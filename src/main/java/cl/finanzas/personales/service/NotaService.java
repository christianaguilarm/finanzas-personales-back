package cl.finanzas.personales.service;

import cl.finanzas.personales.dto.NotaRequest;
import cl.finanzas.personales.dto.NotaResponse;
import cl.finanzas.personales.model.Nota;
import cl.finanzas.personales.repository.AppUserRepository;
import cl.finanzas.personales.repository.NotaRepository;
import cl.finanzas.personales.repository.NotaAvisoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotaService {
    public static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private final NotaRepository repository;
    private final NotaAvisoRepository avisos;
    private final AppUserRepository usuarios;
    private final Clock clock;

    public LocalDate hoy() { return LocalDate.now(clock.withZone(ZONA)); }

    @Transactional(readOnly = true)
    public List<NotaResponse> listar(Long userId) {
        return repository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Nota::isResuelta)
                        .thenComparing(Nota::getFechaLimite, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Nota::getId))
                .map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public List<NotaResponse> proximas(Long userId) {
        return repository.findByUserIdAndResueltaFalseAndFechaLimiteLessThanEqual(userId, hoy().plusDays(30))
                .stream().sorted(Comparator.comparing(Nota::getFechaLimite).thenComparing(Nota::getId))
                .limit(5).map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public Optional<NotaResponse> porSolicitud(Long userId, UUID solicitudId) {
        return solicitudId == null ? Optional.empty() : repository.findByUserIdAndSolicitudId(userId, solicitudId).map(this::respuesta);
    }

    @Transactional
    public NotaResponse crear(Long userId, NotaRequest request) {
        return crear(userId, request, null);
    }

    @Transactional
    public NotaResponse crear(Long userId, NotaRequest request, UUID solicitudId) {
        validar(request);
        if (solicitudId != null) {
            var anterior = repository.findByUserIdAndSolicitudId(userId, solicitudId);
            if (anterior.isPresent()) return respuesta(anterior.get());
        }
        var nota = new Nota();
        nota.setUser(usuarios.getReferenceById(userId));
        nota.setTexto(request.texto().strip());
        nota.setFechaLimite(request.fechaLimite());
        nota.setResuelta(request.resuelta());
        nota.setSolicitudId(solicitudId);
        return respuesta(repository.saveAndFlush(nota));
    }

    @Transactional
    public NotaResponse actualizar(Long userId, Long id, NotaRequest request) {
        validar(request);
        var nota = propia(userId, id);
        nota.setTexto(request.texto().strip());
        nota.setFechaLimite(request.fechaLimite());
        nota.setResuelta(request.resuelta());
        return respuesta(nota);
    }

    @Transactional
    public void eliminar(Long userId, Long id) {
        var nota = propia(userId, id);
        avisos.eliminarPorNota(id);
        repository.delete(nota);
    }

    private Nota propia(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota no encontrada"));
    }

    private void validar(NotaRequest request) {
        if (request.texto() == null || request.texto().isBlank() || request.texto().length() > 1000
                || (request.fechaLimite() != null && (request.fechaLimite().getYear() < 1900 || request.fechaLimite().getYear() > 2200))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Texto o fecha de nota inválidos");
        }
    }

    private NotaResponse respuesta(Nota nota) {
        return new NotaResponse(nota.getId(), nota.getTexto(), nota.getFechaLimite(), nota.isResuelta(),
                nota.getFechaLimite() == null ? null : ChronoUnit.DAYS.between(hoy(), nota.getFechaLimite()));
    }
}
