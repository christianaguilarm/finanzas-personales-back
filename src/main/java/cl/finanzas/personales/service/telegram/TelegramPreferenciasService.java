package cl.finanzas.personales.service.telegram;

import cl.finanzas.personales.dto.TelegramPreferencias;
import cl.finanzas.personales.model.Categoria;
import cl.finanzas.personales.model.TelegramConexion;
import cl.finanzas.personales.model.TipoTransaccion;
import cl.finanzas.personales.repository.AppUserRepository;
import cl.finanzas.personales.repository.CategoriaRepository;
import cl.finanzas.personales.repository.CuentaRepository;
import cl.finanzas.personales.repository.TelegramConexionRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TelegramPreferenciasService {
    private final TelegramConexionRepository conexiones;
    private final AppUserRepository usuarios;
    private final CuentaRepository cuentas;
    private final CategoriaRepository categorias;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TelegramPreferencias obtener(Long userId) {
        return conexiones.findById(userId).map(c -> new TelegramPreferencias(
                c.getCuentaPredeterminadaId(), c.getCategoriaPredeterminadaId()))
                .orElseGet(() -> new TelegramPreferencias(null, null));
    }

    @Transactional
    public TelegramPreferencias guardar(Long userId, TelegramPreferencias preferencias) {
        usuarios.bloquearPorId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (preferencias.cuentaId() != null) validarCuenta(userId, preferencias.cuentaId());
        if (preferencias.categoriaId() != null) validarCategoria(userId, preferencias.categoriaId());
        var conexion = conexiones.bloquearPorUsuario(userId).orElseGet(() -> {
            var nueva = new TelegramConexion();
            nueva.setUserId(userId);
            return nueva;
        });
        conexion.setCuentaPredeterminadaId(preferencias.cuentaId());
        conexion.setCategoriaPredeterminadaId(preferencias.categoriaId());
        conexiones.save(conexion);
        return preferencias;
    }

    /** Solo para Telegram. Conserva los valores explícitos y aplica las preferencias antes de validar el borrador. */
    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public ObjectNode completar(Long userId, TelegramPreferencias preferencias, ObjectNode argumentos) {
        var resultado = argumentos.deepCopy();
        if (!resultado.hasNonNull("periodoFacturacion")) {
            resultado.put("periodoFacturacion", periodoActual().toString());
        }
        if (!resultado.hasNonNull("cuentaId") && preferencias.cuentaId() != null) {
            validarCuenta(userId, preferencias.cuentaId());
            resultado.put("cuentaId", preferencias.cuentaId());
        }
        if ("EGRESO".equals(resultado.path("tipo").asText()) && !resultado.hasNonNull("categoriaId")
                && !resultado.hasNonNull("subcategoriaId") && preferencias.categoriaId() != null) {
            var categoria = validarCategoria(userId, preferencias.categoriaId());
            if (categoria.getParent() == null) {
                resultado.put("categoriaId", categoria.getId());
            } else {
                resultado.put("categoriaId", categoria.getParent().getId());
                resultado.put("subcategoriaId", categoria.getId());
            }
        }
        return resultado;
    }

    public YearMonth periodoActual() {
        return YearMonth.now(clock.withZone(ZoneId.of("America/Santiago")));
    }

    private void validarCuenta(Long userId, Long id) {
        var cuenta = cuentas.findById(id).orElseThrow(this::cuentaInvalida);
        if (!cuenta.isActivo() || !userId.equals(cuenta.getUser().getId())) throw cuentaInvalida();
    }

    private Categoria validarCategoria(Long userId, Long id) {
        var categoria = categorias.findById(id).orElseThrow(this::categoriaInvalida);
        if (!categoriaValida(userId, categoria) || (categoria.getParent() != null && !categoriaValida(userId, categoria.getParent()))) {
            throw categoriaInvalida();
        }
        return categoria;
    }

    private boolean categoriaValida(Long userId, Categoria categoria) {
        return categoria.isActivo() && userId.equals(categoria.getUser().getId())
                && categoria.getTipo() == TipoTransaccion.EGRESO && categoria.getCodigoSistema() == null;
    }

    private ResponseStatusException cuentaInvalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La cuenta predeterminada no está disponible. Elige una cuenta propia activa o indica otra en el mensaje");
    }
    private ResponseStatusException categoriaInvalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La categoría predeterminada no está disponible. Elige una categoría propia de gasto; Préstamo requiere su formulario");
    }
}
