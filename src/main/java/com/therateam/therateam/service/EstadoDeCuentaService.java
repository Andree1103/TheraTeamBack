package com.therateam.therateam.service;

import com.therateam.therateam.dto.EstadoDeCuentaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma el estado de cuenta de un paciente leyendo el libro.
 *
 * Responde a "¿en qué se fue mi adelanto?" sin que nadie tenga que reconstruirlo: cada línea
 * del libro dice su concepto y a qué cita o paquete fue.
 */
@Service
@RequiredArgsConstructor
public class EstadoDeCuentaService {

    private final JdbcTemplate jdbc;

    private static final String CABECERA = """
        SELECT COALESCE(p.nombre,'') || ' ' || COALESCE(p.apellido,'') AS quien,
               COALESCE(p.saldo_a_favor,0) AS saldo
          FROM pacientes p WHERE p.id = ?
        """;

    /**
     * Por fecha y luego por id: dos asientos del mismo instante —un cobro y su anulación— tienen
     * que salir en el orden en que ocurrieron, y la fecha sola no lo distingue.
     */
    private static final String MOVIMIENTOS = """
        SELECT a.id AS asiento_id, a.tipo, a.fecha, a.nota,
               l.concepto, l.importe, m.nombre AS metodo, l.cita_id, l.tratamiento_id
          FROM asientos a
          JOIN asiento_lineas l ON l.asiento_id = a.id
          LEFT JOIN cat_metodos_pago m ON m.id = l.metodo_id
         WHERE a.paciente_id = ?
         ORDER BY a.fecha, a.id, l.id
        """;

    @Transactional(readOnly = true)
    public EstadoDeCuentaDTO de(Long pacienteId) {
        EstadoDeCuentaDTO dto = new EstadoDeCuentaDTO();
        dto.setPacienteId(pacienteId);
        jdbc.query(CABECERA, rs -> {
            dto.setPaciente(rs.getString("quien").trim());
            dto.setSaldoGuardado(rs.getBigDecimal("saldo"));
        }, pacienteId);

        List<EstadoDeCuentaDTO.Movimiento> movs = new ArrayList<>();
        BigDecimal[] saldo = { BigDecimal.ZERO };

        jdbc.query(MOVIMIENTOS, rs -> {
            long asientoId = rs.getLong("asiento_id");
            EstadoDeCuentaDTO.Movimiento ultimo = movs.isEmpty() ? null : movs.get(movs.size() - 1);
            if (ultimo == null || !ultimo.getAsientoId().equals(asientoId)) {
                Timestamp ts = rs.getTimestamp("fecha");
                ultimo = new EstadoDeCuentaDTO.Movimiento(asientoId, rs.getString("tipo"),
                        ts != null ? ts.toLocalDateTime() : null, rs.getString("nota"),
                        new ArrayList<>(), null);
                movs.add(ultimo);
            }
            String concepto = rs.getString("concepto");
            BigDecimal importe = rs.getBigDecimal("importe");
            Long citaId = (Long) rs.getObject("cita_id");
            Long tratId = (Long) rs.getObject("tratamiento_id");
            String metodo = rs.getString("metodo");

            if ("CREDITO_GENERA".equals(concepto)) saldo[0] = saldo[0].add(importe);
            if ("CREDITO_USA".equals(concepto))    saldo[0] = saldo[0].subtract(importe);

            ultimo.getLineas().add(new EstadoDeCuentaDTO.Linea(concepto, importe, metodo,
                    citaId, tratId, enCristiano(concepto, importe, metodo, citaId, tratId)));
            ultimo.setSaldoDespues(saldo[0]);
        }, pacienteId);

        dto.setMovimientos(movs);
        dto.setSaldoSegunElLibro(saldo[0]);
        return dto;
    }

    /** La misma línea, dicha como se la contarías al paciente en el mostrador. */
    private static String enCristiano(String concepto, BigDecimal importe, String metodo,
                                      Long citaId, Long tratamientoId) {
        String donde = citaId != null ? " de la cita #" + citaId
                     : tratamientoId != null ? " del paquete #" + tratamientoId : "";
        String porDonde = metodo != null && !metodo.isBlank() ? " (" + metodo.trim() + ")" : "";
        return switch (concepto) {
            case "CAJA_ENTRA"        -> "Entregó S/ " + importe + porDonde;
            case "CAJA_SALE"         -> "Se le devolvió S/ " + importe + porDonde;
            case "CREDITO_USA"       -> "Usó S/ " + importe + " de su saldo a favor";
            case "CREDITO_GENERA"    -> "Le quedaron S/ " + importe + " a favor";
            case "DEUDA_CUBRE"       -> "Se cubrió S/ " + importe + donde;
            case "DEUDA_LIBERA"      -> "Volvió a deberse S/ " + importe + donde;
            case "CONSTANCIA"        -> "S/ " + importe + " dados por pagados sin movimiento de caja";
            case "CONSTANCIA_LIBERA" -> "Se retiró la constancia de S/ " + importe;
            default -> concepto + " S/ " + importe;
        };
    }
}
