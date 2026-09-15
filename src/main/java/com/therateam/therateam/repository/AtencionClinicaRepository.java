package com.therateam.therateam.repository;
import com.therateam.therateam.model.AtencionClinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface AtencionClinicaRepository extends JpaRepository<AtencionClinica, Long> {
    // @Query explícito en vez de derivarlo del nombre: la entidad ahora expone un getCitaId()
    // transitorio (para el JSON del front) que Spring Data confundía con un atributo persistido
    // llamado "citaId" al intentar resolver "findByCitaId" por convención.
    @Query("SELECT a FROM AtencionClinica a WHERE a.cita.id = :citaId")
    Optional<AtencionClinica> findByCitaId(@Param("citaId") Long citaId);

    /**
     * Todas las atenciones de un paciente de una sola consulta. El perfil las pedía cita por cita
     * (una petición HTTP por fila, y casi todas respondían 404 porque la cita no tenía atención).
     *
     * El JOIN FETCH es parte del arreglo, no un detalle: cita y metricas son EAGER, así que sin él
     * la única consulta se convertía en una por atención al resolverlas. DISTINCT porque traer la
     * colección de métricas multiplica la fila de la atención.
     */
    @Query("""
        SELECT DISTINCT a FROM AtencionClinica a
        LEFT JOIN FETCH a.cita c
        LEFT JOIN FETCH a.metricas
        WHERE c.paciente.id = :pacienteId
        """)
    List<AtencionClinica> findByPacienteId(@Param("pacienteId") Long pacienteId);
}
