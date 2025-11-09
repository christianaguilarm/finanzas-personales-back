package cl.finanzas.personales.mapper;

import cl.finanzas.personales.dto.TransaccionRequest;
import cl.finanzas.personales.dto.TransaccionResponse;
import cl.finanzas.personales.model.*;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TransaccionMapper {

    // === DTO → Entity ===
    public Transaccion toEntity(TransaccionRequest dto) {
        if (dto == null) return null;

        Transaccion transaccion = new Transaccion();
        transaccion.setMonto(dto.monto());
        transaccion.setTipo(dto.tipo());
        transaccion.setFecha(dto.fecha());
        transaccion.setDescripcion(dto.descripcion());
        transaccion.setMedio(dto.medio());
        transaccion.setEsRecurrente(dto.esRecurrente());

        // Relaciones
        if (dto.userId() != null) {
            AppUser user = new AppUser();
            user.setId(dto.userId());
            transaccion.setUser(user);
        }

        if (dto.cuentaId() != null) {
            Cuenta cuenta = new Cuenta();
            cuenta.setId(dto.cuentaId());
            transaccion.setCuenta(cuenta);
        }

        if (dto.categoriaId() != null) {
            Categoria categoria = new Categoria();
            categoria.setId(dto.categoriaId());
            transaccion.setCategoria(categoria);
        }

        if (dto.subcategoriaId() != null) {
            Categoria subcategoria = new Categoria();
            subcategoria.setId(dto.subcategoriaId());
            transaccion.setSubcategoria(subcategoria);
        }

        if (dto.comercioId() != null) {
            Comercio comercio = new Comercio();
            comercio.setId(dto.comercioId());
            transaccion.setComercio(comercio);
        }

        // Tags los maneja el service
        transaccion.setTags(null);

        return transaccion;
    }

    // === Entity → DTO ===
    public TransaccionResponse toResponse(Transaccion entity) {
        if (entity == null) return null;

        return new TransaccionResponse(
                entity.getId(),
                entity.getTipo(),
                entity.getFecha(),
                entity.getMonto(),
                entity.getMedio(),
                entity.getDescripcion(),
                entity.isEsRecurrente(),
                entity.getCreadoEn(),
                entity.getCuenta() != null ? entity.getCuenta().getId() : null,
                entity.getCategoria() != null ? entity.getCategoria().getId() : null,
                entity.getSubcategoria() != null ? entity.getSubcategoria().getId() : null,
                entity.getComercio() != null ? entity.getComercio().getId() : null,
                entity.getTags() != null
                        ? entity.getTags().stream().map(Tag::getId).collect(Collectors.toSet())
                        : Set.of()
        );
    }
}
