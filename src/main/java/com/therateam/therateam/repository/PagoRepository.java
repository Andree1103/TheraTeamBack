package com.therateam.therateam.repository;
import com.therateam.therateam.dto.PagoDTO;
import com.therateam.therateam.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByTratamientoId(Long tratamientoId);
    List<Pago> findByPacienteId(Long pacienteId);

    /**
     * Proyección liviana para listados: evita la cadena EAGER completa de Pago.tratamiento
     * (Terapeuta->Usuario/TipoTerapeuta/Area/especialidades, TipoTerapia->Area, etc.)
     */
    @Query("""
        SELECT new com.therateam.therateam.dto.PagoDTO(
            pg.id,
            t.id, t.nombre,
            p.id, p.nombre, p.apellido,
            m.id, m.nombre,
            pg.montoRecibido, pg.montoAplicado, pg.saldoGenerado, pg.saldoPrevio,
            pg.referencia, pg.notas, pg.fechaPago, pg.createdAt
        )
        FROM Pago pg
        LEFT JOIN pg.tratamiento t
        LEFT JOIN pg.paciente p
        LEFT JOIN pg.metodo m
        ORDER BY pg.fechaPago DESC
        """)
    List<PagoDTO> findAllProjected();

    @Query("""
        SELECT new com.therateam.therateam.dto.PagoDTO(
            pg.id,
            t.id, t.nombre,
            p.id, p.nombre, p.apellido,
            m.id, m.nombre,
            pg.montoRecibido, pg.montoAplicado, pg.saldoGenerado, pg.saldoPrevio,
            pg.referencia, pg.notas, pg.fechaPago, pg.createdAt
        )
        FROM Pago pg
        LEFT JOIN pg.tratamiento t
        LEFT JOIN pg.paciente p
        LEFT JOIN pg.metodo m
        WHERE p.id = :pacienteId
        ORDER BY pg.fechaPago DESC
        """)
    List<PagoDTO> findByPacienteIdProjected(@Param("pacienteId") Long pacienteId);

    @Query("""
        SELECT new com.therateam.therateam.dto.PagoDTO(
            pg.id,
            t.id, t.nombre,
            p.id, p.nombre, p.apellido,
            m.id, m.nombre,
            pg.montoRecibido, pg.montoAplicado, pg.saldoGenerado, pg.saldoPrevio,
            pg.referencia, pg.notas, pg.fechaPago, pg.createdAt
        )
        FROM Pago pg
        LEFT JOIN pg.tratamiento t
        LEFT JOIN pg.paciente p
        LEFT JOIN pg.metodo m
        WHERE t.id = :tratamientoId
        ORDER BY pg.fechaPago DESC
        """)
    List<PagoDTO> findByTratamientoIdProjected(@Param("tratamientoId") Long tratamientoId);
}
