package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.CitaHistorial;
import com.therateam.therateam.service.CitaHistorialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cita-historial")
@RequiredArgsConstructor
public class CitaHistorialController {

    private final CitaHistorialService service;

    @GetMapping
    public List<CitaHistorial> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<CitaHistorial> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cita/{citaId}")
    public List<CitaHistorial> getByCita(@PathVariable Long citaId) {
        return service.findByCita(citaId);
    }

    @PreAuthorize("hasAuthority('MODULO_CITAS_CREAR')")
    @PostMapping
    public ResponseEntity<CitaHistorial> create(@RequestBody CitaHistorial historial) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(historial));
    }
}
