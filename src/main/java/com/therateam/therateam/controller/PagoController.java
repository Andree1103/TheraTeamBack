package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.dto.PagoDTO;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService service;

    /** GET /api/pagos?page=0&size=20&paciente=x&referencia=x&metodoId=1&tienePaquete=true&montoMin=10&montoMax=100&fechaInicio=...&fechaFin=... */
    @GetMapping
    public Page<PagoDTO> getAll(@PageableDefault(size = 20, sort = "fechaPago", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
                                 @RequestParam(required = false) String paciente,
                                 @RequestParam(required = false) String referencia,
                                 @RequestParam(required = false) Long metodoId,
                                 @RequestParam(required = false) Boolean tienePaquete,
                                 @RequestParam(required = false) BigDecimal montoMin,
                                 @RequestParam(required = false) BigDecimal montoMax,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin) {
        return service.findAllPaged(pageable, paciente, referencia, metodoId, tienePaquete, montoMin, montoMax, fechaInicio, fechaFin);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pago> getById(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tratamiento/{tratamientoId}")
    public List<PagoDTO> getByTratamiento(@PathVariable Long tratamientoId) {
        return service.findByTratamiento(tratamientoId);
    }

    @GetMapping("/paciente/{pacienteId}")
    public List<PagoDTO> getByPaciente(@PathVariable Long pacienteId) {
        return service.findByPaciente(pacienteId);
    }

    @PreAuthorize("hasAuthority('MODULO_PAGOS_CREAR')")
    @PostMapping
    public ResponseEntity<Pago> create(@Valid @RequestBody Pago p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(p));
    }

    @PreAuthorize("hasAuthority('MODULO_PAGOS_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
