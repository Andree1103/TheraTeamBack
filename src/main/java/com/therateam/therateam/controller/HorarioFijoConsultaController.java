package com.therateam.therateam.controller;

import com.therateam.therateam.dto.HorarioFijoResumenDTO;
import com.therateam.therateam.service.PacienteHorarioFijoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Los horarios fijos de toda la clínica, sin pasar por un paciente en concreto.
 *
 * Va aparte de PacienteHorarioFijoController porque aquel cuelga de /api/pacientes/{id} y esta
 * vista es justo la contraria: mirar el cuadro completo para exportarlo o buscar una casilla.
 * Solo lee — el alta y la baja siguen siendo del paciente.
 */
@RestController
@RequestMapping("/api/horarios-fijos")
@RequiredArgsConstructor
public class HorarioFijoConsultaController {

    private final PacienteHorarioFijoService service;

    @GetMapping
    public List<HorarioFijoResumenDTO> todos() {
        return service.todos();
    }
}
