package com.therateam.therateam.controller;

import com.therateam.therateam.model.Pago;
import com.therateam.therateam.service.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService service;

    @GetMapping
    public List<Pago> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Pago> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tratamiento/{tratamientoId}")
    public List<Pago> getByTratamiento(@PathVariable Long tratamientoId) {
        return service.findByTratamiento(tratamientoId);
    }

    @GetMapping("/paciente/{pacienteId}")
    public List<Pago> getByPaciente(@PathVariable Long pacienteId) {
        return service.findByPaciente(pacienteId);
    }

    @PostMapping
    public ResponseEntity<Pago> create(@RequestBody Pago p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(p));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
