package com.therateam.therateam.repository;

import com.therateam.therateam.model.HcPlantilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HcPlantillaRepository extends JpaRepository<HcPlantilla, Long> {
    List<HcPlantilla> findByActivoTrueOrderByOrdenAscIdAsc();

    /** Plantillas de un area concreta mas las genericas (area nula), que sirven para todas. */
    List<HcPlantilla> findByActivoTrueAndAreaIdOrActivoTrueAndAreaIsNullOrderByOrdenAscIdAsc(Long areaId);
}
