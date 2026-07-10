package com.therateam.therateam.controller;

import com.therateam.therateam.model.TerapeutaHorario;
import com.therateam.therateam.service.TerapeutaHorarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/terapeuta-horarios")
@RequiredArgsConstructor
public class TerapeutaHorarioController {

    private final TerapeutaHorarioService service;

    @GetMapping
    public List<TerapeutaHorario> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<TerapeutaHorario> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/terapeuta/{terapeutaId}")
    public List<TerapeutaHorario> getByTerapeuta(@PathVariable Long terapeutaId) {
        return service.findByTerapeuta(terapeutaId);
    }

    @PostMapping
    public ResponseEntity<TerapeutaHorario> create(@RequestBody TerapeutaHorario horario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(horario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TerapeutaHorario> update(@PathVariable Long id, @RequestBody TerapeutaHorario horario) {
        return service.update(id, horario).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
