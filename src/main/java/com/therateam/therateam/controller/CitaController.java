package com.therateam.therateam.controller;

import com.therateam.therateam.dto.CitaConPacienteRequest;
import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.dto.CitaRapidaRequest;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.service.CitaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService service;

    // Listado completo sin filtros
    @GetMapping
    public List<CitaDTO> getAll() {
        return service.findAll();
    }

    // Listado con filtros: rango de fechas y/o terapeuta
    // GET /api/citas/filtro?fechaInicio=2026-05-18T00:00:00&fechaFin=2026-05-24T23:59:59&terapeuta=Ana
    @GetMapping("/filtro")
    public List<CitaDTO> getByFiltros(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin,
            @RequestParam(required = false) String terapeuta
    ) {
        return service.findByFiltros(fechaInicio, fechaFin, terapeuta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CitaDTO> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Cita> create(@RequestBody Cita cita) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(cita));
    }

    /** Crea cita a partir de paciente_id — maneja tratamiento y sesión internamente */
    @PostMapping("/rapida")
    public ResponseEntity<CitaDTO> createRapida(@RequestBody CitaRapidaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearRapida(req));
    }

    /**
     * Crea cita(s) atómica con paciente embebido.
     * Busca paciente por DNI — si no existe lo crea. Soporta paciente2 opcional (multipaciente).
     * Resuelve terapeuta por nombre y tipoTerapia/estado por key.
     */
    @PostMapping("/con-paciente")
    public ResponseEntity<List<CitaDTO>> createConPaciente(@RequestBody CitaConPacienteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearConPaciente(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cita> update(@PathVariable Long id, @RequestBody Cita cita) {
        return service.update(id, cita)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
