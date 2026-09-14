package com.therateam.therateam.controller;

import com.therateam.therateam.model.HistoriaClinica;
import com.therateam.therateam.service.HistoriaClinicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * La ficha clinica del paciente. Son datos de salud: ver y editar van con permisos propios
 * por rol (Seguridad > Roles), separados del permiso del modulo Pacientes.
 */
@RestController
@RequestMapping("/api/historias-clinicas")
@RequiredArgsConstructor
public class HistoriaClinicaController {

    private final HistoriaClinicaService service;

    /** GET /api/historias-clinicas?pacienteId=12 — todas las fichas de ese paciente. */
    @GetMapping
    @PreAuthorize("hasAuthority('PUEDE_VER_HISTORIA')")
    public List<HistoriaClinica> porPaciente(@RequestParam Long pacienteId) {
        return service.findByPaciente(pacienteId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PUEDE_VER_HISTORIA')")
    public ResponseEntity<HistoriaClinica> porId(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/historias-clinicas/paciente/{pacienteId}/plantilla/{plantillaId}
     * Upsert: crea la ficha si es la primera vez y la actualiza si ya existia, para que la
     * pantalla no tenga que distinguir los dos casos.
     */
    @PutMapping("/paciente/{pacienteId}/plantilla/{plantillaId}")
    @PreAuthorize("hasAuthority('PUEDE_EDITAR_HISTORIA')")
    public HistoriaClinica guardar(@PathVariable Long pacienteId,
                                    @PathVariable Long plantillaId,
                                    @RequestBody Map<String, Object> datos) {
        return service.guardar(pacienteId, plantillaId, datos);
    }
}
