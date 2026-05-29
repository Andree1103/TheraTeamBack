package com.therateam.therateam.controller;

import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.service.TratamientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tratamientos")
@RequiredArgsConstructor
public class TratamientoController {

    private final TratamientoService service;

    @GetMapping
    public List<Tratamiento> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Tratamiento> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/paciente/{pacienteId}")
    public List<Tratamiento> getByPaciente(@PathVariable Long pacienteId) {
        return service.findByPaciente(pacienteId);
    }

    @GetMapping("/terapeuta/{terapeutaId}")
    public List<Tratamiento> getByTerapeuta(@PathVariable Long terapeutaId) {
        return service.findByTerapeuta(terapeutaId);
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
