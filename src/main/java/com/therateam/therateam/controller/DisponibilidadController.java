package com.therateam.therateam.controller;

import com.therateam.therateam.dto.DisponibilidadDiaDTO;
import com.therateam.therateam.service.DisponibilidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/terapeutas/{terapeutaId}/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadController {

    private final DisponibilidadService service;

    /**
     * GET /api/terapeutas/{id}/disponibilidad?fecha=2026-07-10&excluirCitaId=12&cupo=2
     *
     * `cupo` es cuantos pacientes admite a la vez la cita que se quiere colocar: sin el, un hueco
     * con una cita de un tipo que admite dos se ofrece como libre aunque la cita que se va a
     * poner solo admita uno, y el guardado la rechaza. `excluirCitaId` ignora una cita al medir
     * la ocupacion — al mover una cita, la que se mueve no debe estorbarse a si misma.
     */
    @GetMapping
    public DisponibilidadDiaDTO getDia(@PathVariable Long terapeutaId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                        @RequestParam(required = false) Long excluirCitaId,
                                        @RequestParam(required = false) Integer cupo) {
        return service.obtenerDisponibilidadDia(terapeutaId, fecha, excluirCitaId, cupo);
    }

    /** GET /api/terapeutas/{id}/disponibilidad/semana?desde=2026-07-06&hasta=2026-07-12 */
    @GetMapping("/semana")
    public List<DisponibilidadDiaDTO> getSemana(@PathVariable Long terapeutaId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.obtenerDisponibilidadSemana(terapeutaId, desde, hasta);
    }
}
