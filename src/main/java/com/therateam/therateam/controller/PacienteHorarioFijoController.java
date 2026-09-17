package com.therateam.therateam.controller;

import com.therateam.therateam.dto.HorarioFijoRequest;
import com.therateam.therateam.model.PacienteHorarioFijo;
import com.therateam.therateam.service.PacienteHorarioFijoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Horario habitual del paciente: "viene los lunes a las 9 con Carla, terapia KIDS".
 *
 * Es solo referencia — no reserva el espacio en la agenda ni genera citas. Por eso cuelga de
 * pacientes y no del módulo de citas, y usa sus mismos permisos.
 */
@RestController
@RequestMapping("/api/pacientes/{pacienteId}/horarios-fijos")
@RequiredArgsConstructor
public class PacienteHorarioFijoController {

    private final PacienteHorarioFijoService service;

    @GetMapping
    public List<PacienteHorarioFijo> delPaciente(@PathVariable Long pacienteId) {
        return service.delPaciente(pacienteId);
    }

    /**
     * Reemplaza el horario completo del paciente por el que venga en el cuerpo. Una lista vacía
     * lo deja sin horarios fijos, que es cómo la pantalla los quita.
     */
    @PreAuthorize("hasAuthority('MODULO_PACIENTES_EDITAR')")
    @PutMapping
    public List<PacienteHorarioFijo> reemplazar(@PathVariable Long pacienteId,
                                                @RequestBody List<HorarioFijoRequest> horarios) {
        return service.reemplazar(pacienteId, horarios);
    }
}
