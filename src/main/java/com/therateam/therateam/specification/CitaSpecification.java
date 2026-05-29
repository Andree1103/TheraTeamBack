package com.therateam.therateam.specification;

import com.therateam.therateam.model.Cita;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CitaSpecification {

    public static Specification<Cita> byFiltros(
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            String terapeuta
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filtro por rango de fecha (fecha_inicio está directo en citas)
            if (fechaInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaInicio"), fechaInicio));
            }
            if (fechaFin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaInicio"), fechaFin));
            }

            // Filtro por nombre de terapeuta: citas.terapeuta → terapeutas.usuario → usuarios
            if (terapeuta != null && !terapeuta.isBlank()) {
                Join<Object, Object> terapeutaJ = root.join("terapeuta", JoinType.LEFT);
                Join<Object, Object> usuario    = terapeutaJ.join("usuario", JoinType.LEFT);
                Expression<String> nombreCompleto = cb.lower(
                        cb.concat(cb.concat(usuario.get("nombre"), " "), usuario.get("apellido"))
                );
                predicates.add(cb.like(nombreCompleto, "%" + terapeuta.toLowerCase() + "%"));
            }

            query.orderBy(cb.asc(root.get("fechaInicio")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
