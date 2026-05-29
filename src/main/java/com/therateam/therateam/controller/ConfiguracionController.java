package com.therateam.therateam.controller;

import com.therateam.therateam.model.Configuracion;
import com.therateam.therateam.service.ConfiguracionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService service;

    @GetMapping
    public List<Configuracion> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Configuracion> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sede/{sedeId}")
    public List<Configuracion> getBySede(@PathVariable Long sedeId) {
        return service.findBySede(sedeId);
    }

    @PostMapping
    public ResponseEntity<Configuracion> create(@RequestBody Configuracion conf) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(conf));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Configuracion> update(@PathVariable Long id, @RequestBody Configuracion conf) {
        return service.update(id, conf).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
