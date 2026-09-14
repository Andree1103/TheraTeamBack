package com.therateam.therateam.repository;

import com.therateam.therateam.model.HcPlantilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HcPlantillaRepository extends JpaRepository<HcPlantilla, Long> {

    List<HcPlantilla> findByActivoTrueOrderByOrdenAscIdAsc();

    List<HcPlantilla> findByTipoAndActivoTrueOrderByOrdenAscIdAsc(String tipo);

    List<HcPlantilla> findByTipoOrderByOrdenAscIdAsc(String tipo);

    /**
     * La plantilla que corresponde a un tipo de terapia: primero la propia de ese tipo y, si no
     * hay, la generica (tipoTerapia nulo). Asi no hace falta crear una plantilla por cada uno de
     * los tipos para que el sistema funcione.
     *
     * El ORDER BY pone las especificas antes que las genericas; el service toma la primera.
     */
    @Query("""
        SELECT p FROM HcPlantilla p
        WHERE p.tipo = :tipo AND p.activo = true
          AND (p.tipoTerapia.id = :tipoTerapiaId OR p.tipoTerapia IS NULL)
        ORDER BY CASE WHEN p.tipoTerapia IS NULL THEN 1 ELSE 0 END, p.orden ASC, p.id ASC
        """)
    List<HcPlantilla> resolverPara(@Param("tipo") String tipo,
                                    @Param("tipoTerapiaId") Long tipoTerapiaId);

    /** Solo las genericas — para cuando la cita no trae tipo de terapia. */
    @Query("""
        SELECT p FROM HcPlantilla p
        WHERE p.tipo = :tipo AND p.activo = true AND p.tipoTerapia IS NULL
        ORDER BY p.orden ASC, p.id ASC
        """)
    List<HcPlantilla> genericas(@Param("tipo") String tipo);
}
