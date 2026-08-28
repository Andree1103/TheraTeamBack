package com.therateam.therateam.service;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.dto.SaldoMovimientoDTO;
import com.therateam.therateam.model.SaldoMovimiento;
import com.therateam.therateam.repository.SaldoMovimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Punto único donde se anota el historial del saldo a favor.
 *
 * Todos los sitios que tocan pacientes.saldo_a_favor pasan por aquí, para que no haya
 * movimientos de saldo sin explicación.
 */
@Service
@RequiredArgsConstructor
public class SaldoMovimientoService {

    private final SaldoMovimientoRepository repository;

    /**
     * @param montoDelta      cuánto cambió el saldo (con signo)
     * @param saldoResultante saldo del paciente ya actualizado
     */
    public void registrar(Paciente paciente, BigDecimal montoDelta, BigDecimal saldoResultante,
                          String motivo, Cita cita, Pago pago) {
        registrar(paciente, montoDelta, saldoResultante, motivo, cita, pago, null);
    }

    /**
     * @param terapeuta quien atendia. Se pasa explicito porque la cita del request suele venir
     *                  como un stub {id} sin relaciones cargadas, y leerle el terapeuta da null.
     */
    public void registrar(Paciente paciente, BigDecimal montoDelta, BigDecimal saldoResultante,
                          String motivo, Cita cita, Pago pago, Terapeuta terapeuta) {
        if (paciente == null || paciente.getId() == null) return;
        // Un delta de cero no dice nada: ensuciaría el historial sin aportar.
        if (montoDelta == null || montoDelta.compareTo(BigDecimal.ZERO) == 0) return;

        SaldoMovimiento m = new SaldoMovimiento();
        m.setPaciente(paciente);
        m.setMonto(montoDelta);
        m.setSaldoResultante(saldoResultante != null ? saldoResultante : BigDecimal.ZERO);
        m.setMotivo(motivo);
        m.setCita(cita);
        m.setPago(pago);
        // El terapeuta no es un dato propio del saldo: se hereda de la cita o del paquete que
        // lo origino. Se prefiere el que llega resuelto; la cita del request no sirve porque
        // suele ser un stub sin relaciones.
        if (terapeuta != null) m.setTerapeuta(terapeuta);
        else if (cita != null && cita.getTerapeuta() != null) m.setTerapeuta(cita.getTerapeuta());
        m.setUsuarioCreacionId(SecurityUtils.currentUserId());
        repository.save(m);
    }

    /** Movimientos de varios pacientes, del mas reciente al mas antiguo. */
    public List<SaldoMovimientoDTO> ultimosDe(List<Long> pacienteIds) {
        return repository.historialDeVarios(pacienteIds);
    }

    public List<SaldoMovimientoDTO> historial(Long pacienteId) {
        return repository.historialDe(pacienteId);
    }
}
