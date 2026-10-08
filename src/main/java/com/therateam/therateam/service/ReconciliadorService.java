package com.therateam.therateam.service;

import com.therateam.therateam.dto.ReconciliacionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Fase 2: compara lo que dice el LIBRO con lo que hay guardado, y canta las diferencias.
 *
 * Las tres cifras que el sistema mantiene a mano —el saldo del paciente, lo cobrado de una cita
 * y lo cobrado de un paquete— se pueden deducir del libro sumando sus lineas. Si las dos cuentas
 * no dan lo mismo, hay un camino que movio dinero sin dejar su asiento. Eso es exactamente lo
 * que buscamos: no un error de calculo, sino un camino que se nos paso.
 *
 * NO CORRIGE NADA. Mientras el libro no sea la fuente, lo guardado manda: corregir aqui seria
 * pisar el dato bueno con el derivado de un libro que todavia puede estar incompleto. Esto avisa;
 * decidir es de una persona.
 *
 * Arranca en cero porque la migracion de apertura absorbio el arrastre historico. Si empieza a
 * aparecer algo, es nuevo — y por eso merece la pena mirarlo.
 */
@Service
@RequiredArgsConstructor
public class ReconciliadorService {

    private final JdbcTemplate jdbc;

    /** Saldo del paciente = lo que el libro le genero menos lo que le consumio. */
    private static final String PACIENTES = """
        WITH proy AS (
          SELECT a.paciente_id,
                 SUM(CASE WHEN l.concepto = 'CREDITO_GENERA' THEN l.importe
                          WHEN l.concepto = 'CREDITO_USA'    THEN -l.importe ELSE 0 END) AS libro
            FROM asientos a JOIN asiento_lineas l ON l.asiento_id = a.id
           GROUP BY 1)
        SELECT pa.id, COALESCE(pa.nombre,'') || ' ' || COALESCE(pa.apellido,'') AS quien,
               COALESCE(pa.saldo_a_favor,0) AS guardado, COALESCE(pr.libro,0) AS segun_el_libro
          FROM pacientes pa LEFT JOIN proy pr ON pr.paciente_id = pa.id
         WHERE abs(COALESCE(pa.saldo_a_favor,0) - COALESCE(pr.libro,0)) > 0.005
        """;

    /** Cobrado de una cita = deuda cubierta menos deuda liberada, de las lineas que la nombran. */
    private static final String CITAS = """
        WITH proy AS (
          SELECT l.cita_id,
                 SUM(CASE WHEN l.concepto = 'DEUDA_CUBRE'  THEN l.importe
                          WHEN l.concepto = 'DEUDA_LIBERA' THEN -l.importe ELSE 0 END) AS libro
            FROM asiento_lineas l WHERE l.cita_id IS NOT NULL GROUP BY 1)
        SELECT c.id, COALESCE(pa.nombre,'') || ' ' || COALESCE(pa.apellido,'') AS quien,
               COALESCE(c.monto_pagado,0) AS guardado, COALESCE(pr.libro,0) AS segun_el_libro
          FROM citas c
          JOIN pacientes pa ON pa.id = c.paciente_id
          LEFT JOIN proy pr ON pr.cita_id = c.id
         WHERE c.eliminado = false
           AND abs(COALESCE(c.monto_pagado,0) - COALESCE(pr.libro,0)) > 0.005
        """;

    private static final String PAQUETES = """
        WITH proy AS (
          SELECT l.tratamiento_id,
                 SUM(CASE WHEN l.concepto = 'DEUDA_CUBRE'  THEN l.importe
                          WHEN l.concepto = 'DEUDA_LIBERA' THEN -l.importe ELSE 0 END) AS libro
            FROM asiento_lineas l WHERE l.tratamiento_id IS NOT NULL GROUP BY 1)
        SELECT t.id, COALESCE(t.nombre,'') AS quien,
               COALESCE(t.total_cobrado,0) AS guardado, COALESCE(pr.libro,0) AS segun_el_libro
          FROM tratamientos t LEFT JOIN proy pr ON pr.tratamiento_id = t.id
         WHERE abs(COALESCE(t.total_cobrado,0) - COALESCE(pr.libro,0)) > 0.005
        """;

    /**
     * Pagos sin asiento. Es la senal mas directa de un camino sin cubrir.
     *
     * Las devoluciones quedan fuera: el suyo es el reverso del cobro original y cuelga de ese
     * otro pago, asi que aqui saldrian como huecos sin serlo.
     */
    private static final String PAGOS_SIN_ASIENTO = """
        SELECT p.id, COALESCE(pa.nombre,'') || ' ' || COALESCE(pa.apellido,'') AS quien,
               COALESCE(p.monto_recibido,0) AS guardado, 0::numeric AS segun_el_libro
          FROM pagos p JOIN pacientes pa ON pa.id = p.paciente_id
         WHERE NOT COALESCE(p.es_devolucion, false)
           AND NOT EXISTS (SELECT 1 FROM asientos a WHERE a.pago_id = p.id)
        """;

    @Transactional(readOnly = true)
    public ReconciliacionDTO revisar() {
        List<ReconciliacionDTO.Diferencia> dif = new ArrayList<>();
        recoger(PACIENTES, "PACIENTE", "saldo a favor", dif);
        recoger(CITAS,     "CITA",     "cobrado",       dif);
        recoger(PAQUETES,  "PAQUETE",  "cobrado",       dif);
        recoger(PAGOS_SIN_ASIENTO, "PAGO SIN ASIENTO", "no quedó anotado en el libro", dif);

        BigDecimal izq = unaCifra("""
            SELECT COALESCE(sum(importe),0) FROM asiento_lineas
             WHERE concepto IN ('CAJA_ENTRA','CREDITO_USA','CONSTANCIA','DEUDA_LIBERA')""");
        BigDecimal der = unaCifra("""
            SELECT COALESCE(sum(importe),0) FROM asiento_lineas
             WHERE concepto IN ('DEUDA_CUBRE','CREDITO_GENERA','CAJA_SALE','CONSTANCIA_LIBERA')""");

        return new ReconciliacionDTO(dif.isEmpty(), izq, der, dif);
    }

    private BigDecimal unaCifra(String sql) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class);
        return v != null ? v : BigDecimal.ZERO;
    }

    private void recoger(String sql, String tipo, String campo, List<ReconciliacionDTO.Diferencia> destino) {
        jdbc.query(sql, rs -> {
            BigDecimal guardado = rs.getBigDecimal("guardado");
            BigDecimal libro    = rs.getBigDecimal("segun_el_libro");
            destino.add(new ReconciliacionDTO.Diferencia(tipo, rs.getLong("id"),
                    rs.getString("quien") == null ? "" : rs.getString("quien").trim(),
                    campo, guardado, libro, guardado.subtract(libro)));
        });
    }
}
