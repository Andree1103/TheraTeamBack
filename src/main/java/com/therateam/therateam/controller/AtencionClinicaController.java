package com.therateam.therateam.controller;

import com.therateam.therateam.dto.AtencionClinicaRequest;
import com.therateam.therateam.model.AtencionClinica;
import com.therateam.therateam.service.AtencionClinicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/atencion-clinica", "/api/atenciones"})
@RequiredArgsConstructor
public class AtencionClinicaController {

    private final AtencionClinicaService service;

    @GetMapping
    public List<AtencionClinica> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<AtencionClinica> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cita/{citaId}")
    public ResponseEntity<AtencionClinica> getByCita(@PathVariable Long citaId) {
        return service.findByCita(citaId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Registra la atención de una cita:
     * guarda atencion_clinica + métricas, actualiza sesion y tratamiento.
     */
    @PostMapping
    public ResponseEntity<AtencionClinica> registrar(@RequestBody AtencionClinicaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AtencionClinica> update(@PathVariable Long id, @RequestBody AtencionClinica atencion) {
        return service.update(id, atencion).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
