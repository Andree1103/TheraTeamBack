package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.dto.CitaConPacienteRequest;
import com.therateam.therateam.dto.CitaDTO;
import com.therateam.therateam.dto.CitaRapidaRequest;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.service.CitaService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService service;

    /**
     * Si el usuario logueado tiene `citasSoloPropias=true` devuelve su terapeutaId (para acotar
     * los listados); si no tiene la restricción, devuelve null (sin acotar). Un usuario restringido
     * que no está vinculado a ningún Terapeuta no debería ver citas de nadie, por eso usa -1L.
     */
    private Long restriccionTerapeutaId(Authentication auth) {
        if (!(auth.getDetails() instanceof Claims claims)) return null;
        if (!Boolean.TRUE.equals(claims.get("citasSoloPropias", Boolean.class))) return null;
        Number terapeutaId = claims.get("terapeutaId", Number.class);
        return terapeutaId != null ? terapeutaId.longValue() : -1L;
    }

    /** GET /api/citas?page=0&size=20&sort=fechaInicio,desc */
    @GetMapping
    public Page<CitaDTO> getAll(@PageableDefault(size = 20, sort = "fechaInicio") Pageable pageable,
                                 Authentication auth) {
        return service.findAllPaged(pageable, restriccionTerapeutaId(auth));
    }

    /** GET /api/citas/filtro?fechaInicio=...&fechaFin=...&terapeuta=Ana&page=0&size=50 */
    @GetMapping("/filtro")
    public Page<CitaDTO> getByFiltros(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin,
            @RequestParam(required = false) String terapeuta,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication auth
    ) {
        return service.findByFiltrosPaged(fechaInicio, fechaFin, terapeuta, restriccionTerapeutaId(auth), pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CitaDTO> getById(@PathVariable Long id, Authentication auth) {
        return service.findById(id)
                .filter(dto -> {
                    Long restriccion = restriccionTerapeutaId(auth);
                    return restriccion == null || restriccion.equals(dto.getTerapeutaId());
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/citas/paciente/{id} — historial de citas de un paciente */
    @GetMapping("/paciente/{pacienteId}")
    public List<CitaDTO> getByPaciente(@PathVariable Long pacienteId) {
        return service.findByPaciente(pacienteId);
    }

    @PreAuthorize("hasAuthority('MODULO_CITAS_CREAR') and hasAuthority('CITAS_PUEDE_CREAR')")
    @PostMapping
    public ResponseEntity<Cita> create(@Valid @RequestBody Cita cita) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(cita));
    }

    /** Crea cita a partir de paciente_id — maneja tratamiento y sesión internamente */
    @PreAuthorize("hasAuthority('MODULO_CITAS_CREAR') and hasAuthority('CITAS_PUEDE_CREAR')")
    @PostMapping("/rapida")
    public ResponseEntity<CitaDTO> createRapida(@RequestBody CitaRapidaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearRapida(req));
    }

    /**
     * Crea cita(s) atómica con paciente embebido.
     * Busca paciente por DNI — si no existe lo crea. Soporta paciente2 opcional (multipaciente).
     * Resuelve terapeuta por nombre y tipoTerapia/estado por key.
     */
    @PreAuthorize("hasAuthority('MODULO_CITAS_CREAR') and hasAuthority('CITAS_PUEDE_CREAR')")
    @PostMapping("/con-paciente")
    public ResponseEntity<List<CitaDTO>> createConPaciente(@RequestBody CitaConPacienteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearConPaciente(req));
    }

    @PreAuthorize("hasAuthority('MODULO_CITAS_EDITAR')")
    @PutMapping("/{id}")
    public ResponseEntity<Cita> update(@PathVariable Long id, @Valid @RequestBody Cita cita) {
        return service.update(id, cita)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PATCH /api/citas/{id}/estado-pago?key=PAGADA — actualiza solo el estado de pago */
    @PreAuthorize("hasAuthority('MODULO_CITAS_EDITAR')")
    @PatchMapping("/{id}/estado-pago")
    public ResponseEntity<CitaDTO> patchEstadoPago(@PathVariable Long id,
                                                    @RequestParam String key) {
        return service.actualizarEstadoPago(id, key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('MODULO_CITAS_ELIMINAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
