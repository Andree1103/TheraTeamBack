package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.model.Configuracion;
import com.therateam.therateam.service.ConfiguracionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService service;

    /** Datos del negocio (nombre, teléfono, dirección) — pantalla de solo edición, nunca de alta. */
    @GetMapping("/negocio")
    public Map<String, String> getNegocio() { return service.obtenerNegocio(); }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/negocio")
    public Map<String, String> updateNegocio(@RequestBody Map<String, String> datos) {
        return service.actualizarNegocio(datos);
    }

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

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    @PostMapping
    public ResponseEntity<Configuracion> create(@RequestBody Configuracion conf) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(conf));
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<Configuracion> update(@PathVariable Long id, @RequestBody Configuracion conf) {
        return service.update(id, conf).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
