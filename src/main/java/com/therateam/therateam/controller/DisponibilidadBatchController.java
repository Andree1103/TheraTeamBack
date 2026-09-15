package com.therateam.therateam.controller;

import com.therateam.therateam.dto.DisponibilidadDiaDTO;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.repository.TerapeutaRepository;
import com.therateam.therateam.service.DisponibilidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Disponibilidad de VARIOS terapeutas en una sola peticion.
 *
 * El endpoint por terapeuta (/api/terapeutas/{id}/disponibilidad) sigue existiendo para
 * consultas puntuales. Este es para el modal de citas, que necesita la semana de todos a la
 * vez: antes hacia una peticion HTTP por terapeuta, y las repetia cada vez que se tocaba un
 * campo del formulario.
 */
@RestController
@RequestMapping("/api/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadBatchController {

    private final DisponibilidadService service;
    private final TerapeutaRepository terapeutaRepository;

    /**
     * GET /api/disponibilidad/semana?desde=2026-09-14&hasta=2026-09-20[&terapeutaIds=1,2,3]
     * Sin `terapeutaIds` devuelve todos los terapeutas.
     */
    @GetMapping("/semana")
    public Map<Long, List<DisponibilidadDiaDTO>> semana(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) List<Long> terapeutaIds) {

        List<Long> ids = (terapeutaIds != null && !terapeutaIds.isEmpty())
                ? terapeutaIds
                : terapeutaRepository.findAll().stream().map(Terapeuta::getId).toList();

        return service.obtenerDisponibilidadSemanaDeVarios(ids, desde, hasta);
    }
}
