package com.therateam.therateam.controller;

import com.therateam.therateam.model.CatEstadoCita;
import com.therateam.therateam.service.CatEstadoCitaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cat-estados-cita")
@RequiredArgsConstructor
public class CatEstadoCitaController {

    private final CatEstadoCitaService service;

    @GetMapping
    public List<CatEstadoCita> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CatEstadoCita> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CatEstadoCita> create(@RequestBody CatEstadoCita estado) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(estado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatEstadoCita> update(@PathVariable Long id, @RequestBody CatEstadoCita estado) {
        return service.update(id, estado).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
