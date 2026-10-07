package com.therateam.therateam.service;

import com.therateam.therateam.dto.CuadreDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Recalcula el dinero desde los pagos y dice donde no cuadra. Opcionalmente lo corrige.
 *
 * Va en SQL a proposito: recorrer cientos de citas por JPA para sumar sus pagos haria una
 * consulta por fila, y esto tiene que poder correr a diario sin que se note.
 *
 * Que se recalcula:
 *   - monto_pagado de una cita suelta = lo aplicado por sus pagos. Las citas de paquete NO: su
 *     cobro vive repartido en el paquete y no hay un pago por sesion con el que reconstruirlo.
 *   - total_cobrado del paquete = lo aplicado por los pagos del paquete.
 *   - saldo_a_favor del paciente = el saldo_resultante de su ultimo movimiento. El ledger es la
 *     fuente: cada movimiento dice con que saldo quedo, y esa cadena no se puede derivar de los
 *     pagos (hay ajustes manuales, devoluciones y eliminaciones por medio).
 *
 * Las citas anuladas, las inasistencias y las reprogramadas quedan FUERA: ahi monto_pagado se
 * puso a cero a proposito al revertir, y su pago sigue existiendo como historia. Recalcularlas
 * les devolveria el dinero que justamente se les quito.
 */
@Service
@RequiredArgsConstructor
public class CuadreService {

    private final JdbcTemplate jdbc;

    private static final String CITAS = """
        SELECT c.id, COALESCE(p.nombre,'') || ' ' || COALESCE(p.apellido,'') AS quien,
               COALESCE(c.monto_pagado,0) AS guardado, x.calculado
        FROM citas c
        JOIN pacientes p ON p.id = c.paciente_id
        JOIN cat_estados_cita e ON e.id = c.estado_id
        CROSS JOIN LATERAL (
          SELECT COALESCE(sum(CASE WHEN pg.es_devolucion THEN -pg.monto_aplicado ELSE pg.monto_aplicado END),0) AS calculado
          FROM pagos pg WHERE pg.cita_id = c.id AND NOT COALESCE(pg.es_adicional,false)) x
        WHERE c.eliminado = false AND c.sesion_id IS NULL
          AND e.key NOT IN ('ANULADA','NO_ASISTIO','REPROGRAMADA')
          AND abs(COALESCE(c.monto_pagado,0) - x.calculado) > 0.01
        """;

    private static final String PAQUETES = """
        SELECT t.id, COALESCE(t.nombre,'') AS quien, COALESCE(t.total_cobrado,0) AS guardado, x.calculado
        FROM tratamientos t
        CROSS JOIN LATERAL (
          SELECT COALESCE(sum(CASE WHEN pg.es_devolucion THEN -pg.monto_aplicado ELSE pg.monto_aplicado END),0) AS calculado
          FROM pagos pg WHERE pg.tratamiento_id = t.id) x
        WHERE abs(COALESCE(t.total_cobrado,0) - x.calculado) > 0.01
        """;

    private static final String PACIENTES = """
        SELECT p.id, COALESCE(p.nombre,'') || ' ' || COALESCE(p.apellido,'') AS quien,
               COALESCE(p.saldo_a_favor,0) AS guardado, u.saldo_resultante AS calculado
        FROM pacientes p
        JOIN LATERAL (SELECT m.saldo_resultante FROM saldo_movimientos m
                       WHERE m.paciente_id = p.id ORDER BY m.id DESC LIMIT 1) u ON true
        WHERE abs(COALESCE(p.saldo_a_favor,0) - u.saldo_resultante) > 0.01
        """;

    private static final String RECALCULAR_ESTADO_PAGO = """
        UPDATE citas c SET estado_pago_id = (
          SELECT ep.id FROM cat_estados_pago_cita ep WHERE ep.key =
            CASE WHEN COALESCE(c.monto_pagado,0) <= 0 THEN 'SIN_PAGO'
                 WHEN COALESCE(c.monto_pagado,0) >= COALESCE(c.precio,0) THEN 'PAGADA'
                 ELSE 'PARCIAL' END)
        WHERE c.id = ?
        """;

    /** Saldo sin un solo movimiento que lo respalde: se avisa, no se toca. */
    private static final String SALDOS_HUERFANOS = """
        SELECT p.id, COALESCE(p.nombre,'') || ' ' || COALESCE(p.apellido,'') AS quien,
               COALESCE(p.saldo_a_favor,0) AS guardado, 0::numeric AS calculado
        FROM pacientes p
        WHERE COALESCE(p.saldo_a_favor,0) <> 0
          AND NOT EXISTS (SELECT 1 FROM saldo_movimientos m WHERE m.paciente_id = p.id)
        """;

    @Transactional(readOnly = true)
    public CuadreDTO revisar() { return trabajar(false); }

    @Transactional
    public CuadreDTO corregir() { return trabajar(true); }

    private CuadreDTO trabajar(boolean corregir) {
        List<CuadreDTO.Diferencia> dif = new ArrayList<>();
        recoger(CITAS,     "CITA",     "monto_pagado",  true,  null, dif);
        recoger(PAQUETES,  "PAQUETE",  "total_cobrado", true,  null, dif);
        recoger(PACIENTES, "PACIENTE", "saldo_a_favor", true,  null, dif);
        recoger(SALDOS_HUERFANOS, "PACIENTE", "saldo_a_favor", false,
                "Tiene saldo pero ningun movimiento que lo respalde. Revisalo a mano: puede ser un "
              + "ajuste hecho en la base, o un dato perdido.", dif);

        if (corregir) {
            for (CuadreDTO.Diferencia d : dif) {
                if (!d.isCorregible()) continue;
                switch (d.getTipo()) {
                    case "CITA" -> {
                        jdbc.update("UPDATE citas SET monto_pagado = ? WHERE id = ?", d.getCalculado(), d.getId());
                        // El estado de pago se deriva del importe: se recalcula con el, o la cita
                        // quedaria diciendo "Pagada" sobre una cifra que acaba de cambiar.
                        jdbc.update(RECALCULAR_ESTADO_PAGO, d.getId());
                    }
                    case "PAQUETE"  -> jdbc.update("UPDATE tratamientos SET total_cobrado = ? WHERE id = ?", d.getCalculado(), d.getId());
                    case "PACIENTE" -> jdbc.update("UPDATE pacientes SET saldo_a_favor = ? WHERE id = ?", d.getCalculado(), d.getId());
                    default -> { }
                }
            }
        }
        return new CuadreDTO(corregir, dif);
    }

    private void recoger(String sql, String tipo, String campo, boolean corregible, String nota,
                         List<CuadreDTO.Diferencia> destino) {
        jdbc.query(sql, rs -> {
            destino.add(new CuadreDTO.Diferencia(tipo, rs.getLong("id"),
                    rs.getString("quien") == null ? "" : rs.getString("quien").trim(),
                    campo, rs.getBigDecimal("guardado"), rs.getBigDecimal("calculado"),
                    corregible, nota));
        });
    }
}
