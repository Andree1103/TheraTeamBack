package com.therateam.therateam.controller;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.dto.HorarioFijoResumenDTO;
import com.therateam.therateam.service.PacienteHorarioFijoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Los horarios fijos de la clínica sin pasar por un paciente en concreto.
 *
 * Va aparte de PacienteHorarioFijoController porque aquel cuelga de /api/pacientes/{id} y esta
 * vista es justo la contraria: mirar el cuadro completo para exportarlo. Solo lee — el alta y
 * la baja siguen siendo del paciente.
 */
@RestController
@RequestMapping("/api/horarios-fijos")
@RequiredArgsConstructor
public class HorarioFijoConsultaController {

    private final PacienteHorarioFijoService service;

    /**
     * GET /api/horarios-fijos?nombre=x&dni=x&correo=x&sedeId=1&activo=true&creadoDesde=&creadoHasta=
     *
     * Acepta exactamente los mismos filtros que GET /api/pacientes, y no por simetría: el Excel
     * de horarios se baja desde la misma pantalla y tiene que cubrir a los mismos pacientes que
     * el de pacientes. Sin parámetros devuelve el cuadro completo.
     *
     * El recorte se hace aquí y no en el navegador para no mandar la tabla entera y tirar la
     * mayor parte al llegar.
     */
    @GetMapping
    public List<HorarioFijoResumenDTO> buscar(@RequestParam(required = false) String nombre,
                                              @RequestParam(required = false) String dni,
                                              @RequestParam(required = false) String correo,
                                              @RequestParam(required = false) Long sedeId,
                                              @RequestParam(required = false) Boolean activo,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creadoDesde,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creadoHasta,
                                              Authentication auth) {
        return service.buscar(nombre, dni, correo, sedeId, activo,
                SecurityUtils.restriccionTerapeutaId(auth), creadoDesde, creadoHasta);
    }
}
