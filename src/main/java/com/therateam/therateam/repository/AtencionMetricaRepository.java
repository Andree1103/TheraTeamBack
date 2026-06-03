package com.therateam.therateam.repository;
import com.therateam.therateam.model.AtencionMetrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface AtencionMetricaRepository extends JpaRepository<AtencionMetrica, Long> {
    List<AtencionMetrica> findByAtencionId(Long atencionId);
    void deleteByAtencionId(Long atencionId);
}
