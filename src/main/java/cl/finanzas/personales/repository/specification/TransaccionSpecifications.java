package cl.finanzas.personales.repository.specification;

import cl.finanzas.personales.dto.TransaccionFiltroQuery;
import cl.finanzas.personales.model.Transaccion;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TransaccionSpecifications {

    private TransaccionSpecifications() {
    }

    public static Specification<Transaccion> conFiltros(Long userId, TransaccionFiltroQuery filtros) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (filtros.getPeriodoFacturacion() != null) {
                predicates.add(cb.equal(root.get("periodoFacturacion"), filtros.getPeriodoFacturacion()));
            }

            if (filtros.getFecha() != null) {
                predicates.add(cb.equal(root.get("fecha"), filtros.getFecha()));
            }

            if (filtros.getFechaDesde() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fecha"), filtros.getFechaDesde()));
            }

            if (filtros.getFechaHasta() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fecha"), filtros.getFechaHasta()));
            }

            if (filtros.getCategoriaId() != null) {
                predicates.add(cb.equal(root.get("categoria").get("id"), filtros.getCategoriaId()));
            }

            if (filtros.getSubcategoriaId() != null) {
                predicates.add(cb.equal(root.get("subcategoria").get("id"), filtros.getSubcategoriaId()));
            }

            if (filtros.getCuentaId() != null) {
                predicates.add(cb.equal(root.get("cuenta").get("id"), filtros.getCuentaId()));
            }

            if (filtros.getComercioId() != null) {
                predicates.add(cb.equal(root.get("comercio").get("id"), filtros.getComercioId()));
            }

            if (filtros.getTipo() != null) {
                predicates.add(cb.equal(root.get("tipo"), filtros.getTipo()));
            }

            if (filtros.getMedio() != null) {
                predicates.add(cb.equal(root.get("medio"), filtros.getMedio()));
            }

            if (filtros.getEsRecurrente() != null) {
                predicates.add(cb.equal(root.get("esRecurrente"), filtros.getEsRecurrente()));
            }

            if (filtros.getMontoMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("monto"), filtros.getMontoMin()));
            }

            if (filtros.getMontoMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("monto"), filtros.getMontoMax()));
            }

            if (filtros.getTagId() != null) {
                predicates.add(cb.equal(root.join("tags").get("id"), filtros.getTagId()));
                if (query != null) {
                    query.distinct(true);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}


