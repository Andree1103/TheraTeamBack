package com.therateam.therateam.controller;

import com.therateam.therateam.model.AtencionMetrica;
import com.therateam.therateam.service.AtencionMetricaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/atencion-metricas")
@RequiredArgsConstructor
public class AtencionMetricaController {

    private final AtencionMetricaService service;

    @GetMapping
    public List<AtencionMetrica> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<AtencionMetrica> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/atencion/{atencionId}")
    public List<AtencionMetrica> getByAtencion(@PathVariable Long atencionId) {
        return service.findByAtencion(atencionId);
    }

    @PostMapping
    public ResponseEntity<AtencionMetrica> create(@RequestBody AtencionMetrica metrica) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(metrica));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AtencionMetrica> update(@PathVariable Long id, @RequestBody AtencionMetrica metrica) {
        return service.update(id, metrica).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
