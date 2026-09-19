package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

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

    /**
     * Por defecto devuelve solo los estados activos: es lo que alimenta el desplegable de la
     * cita, y un estado retirado no debe poder elegirse.
     *
     * Se retiraron "Cancelada por paciente" y "Cancelada por clínica" al unificarlas en ANULADA.
     * No se borran del catálogo — hay citas e historial apuntando a ellas —, así que la única
     * forma de que dejen de ofrecerse es no listarlas.
     *
     * `incluirInactivos=true` las trae igual, para la pantalla de Configuraciones, que administra
     * el catálogo y tiene que poder verlas.
     */
    @GetMapping
    public List<CatEstadoCita> getAll(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return incluirInactivos ? service.findAll() : service.findActivos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CatEstadoCita> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    @PostMapping
    public ResponseEntity<CatEstadoCita> create(@RequestBody CatEstadoCita estado) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(estado));
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<CatEstadoCita> update(@PathVariable Long id, @RequestBody CatEstadoCita estado) {
        return service.update(id, estado).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
