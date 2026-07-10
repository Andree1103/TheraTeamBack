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

    /** GET /api/terapeutas/{id}/disponibilidad?fecha=2026-07-10 */
    @GetMapping
    public DisponibilidadDiaDTO getDia(@PathVariable Long terapeutaId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.obtenerDisponibilidadDia(terapeutaId, fecha);
    }

    /** GET /api/terapeutas/{id}/disponibilidad/semana?desde=2026-07-06&hasta=2026-07-12 */
    @GetMapping("/semana")
    public List<DisponibilidadDiaDTO> getSemana(@PathVariable Long terapeutaId,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.obtenerDisponibilidadSemana(terapeutaId, desde, hasta);
    }
}
