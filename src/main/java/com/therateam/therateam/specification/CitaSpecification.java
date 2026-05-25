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

            Join<Object, Object> sesion     = root.join("sesion",    JoinType.LEFT);
            Join<Object, Object> terapeutaJ = sesion.join("terapeuta", JoinType.LEFT);
            Join<Object, Object> usuario    = terapeutaJ.join("usuario", JoinType.LEFT);

            if (fechaInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(sesion.get("fechaInicio"), fechaInicio));
            }
            if (fechaFin != null) {
                predicates.add(cb.lessThanOrEqualTo(sesion.get("fechaInicio"), fechaFin));
            }
            if (terapeuta != null && !terapeuta.isBlank()) {
                Expression<String> nombreCompleto = cb.lower(
                        cb.concat(cb.concat(usuario.get("nombre"), " "), usuario.get("apellido"))
                );
                predicates.add(cb.like(nombreCompleto, "%" + terapeuta.toLowerCase() + "%"));
            }

            query.orderBy(cb.asc(sesion.get("fechaInicio")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
