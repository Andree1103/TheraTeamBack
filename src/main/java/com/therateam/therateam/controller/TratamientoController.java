package com.therateam.therateam.controller;

import com.therateam.therateam.dto.SesionDTO;
import com.therateam.therateam.dto.TratamientoDTO;
import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.service.TratamientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tratamientos")
@RequiredArgsConstructor
public class TratamientoController {

    private final TratamientoService service;

    /** GET /api/tratamientos?page=0&size=20 */
    @GetMapping
    public Page<TratamientoDTO> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return service.findAllPaged(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tratamiento> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/sesiones")
    public ResponseEntity<List<SesionDTO>> getSesiones(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSesionesByTratamiento(id));
    }

    /** GET /api/tratamientos/paciente/{id}?page=0&size=20 */
    @GetMapping("/paciente/{pacienteId}")
    public Page<TratamientoDTO> getByPaciente(@PathVariable Long pacienteId,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return service.findByPacientePaged(pacienteId, pageable);
    }

    /** GET /api/tratamientos/terapeuta/{id}?page=0&size=20 */
    @GetMapping("/terapeuta/{terapeutaId}")
    public Page<TratamientoDTO> getByTerapeuta(@PathVariable Long terapeutaId,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return service.findByTerapeutaPaged(terapeutaId, pageable);
    }

    @PostMapping
    public ResponseEntity<Tratamiento> create(@RequestBody Tratamiento t) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(t));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tratamiento> update(@PathVariable Long id, @RequestBody Tratamiento t) {
        return service.update(id, t).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
