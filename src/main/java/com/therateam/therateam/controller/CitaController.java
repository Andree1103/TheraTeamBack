package com.therateam.therateam.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.therateam.therateam.config.SecurityUtils;
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

    /** El celular es un dato sensible: por defecto ningún usuario lo ve, salvo que se le active
     *  el permiso puntual desde Seguridad > Usuarios. */
    private CitaDTO redactarTelefono(CitaDTO dto) {
        if (dto != null && !SecurityUtils.puedeVerTelefonoPacientes()) dto.setPacienteTelefono(null);
        return dto;
    }

    /** GET /api/citas?page=0&size=20&sort=fechaInicio,desc */
    @GetMapping
    public Page<CitaDTO> getAll(@PageableDefault(size = 20, sort = "fechaInicio") Pageable pageable,
                                 Authentication auth) {
        return service.findAllPaged(pageable, restriccionTerapeutaId(auth)).map(this::redactarTelefono);
    }

    /**
     * GET /api/citas/filtro?fechaInicio=...&fechaFin=...&terapeuta=Ana&estadoKey=ASISTIDA&paciente=Juan&areaId=1&metodoPagoId=4&page=0&size=50
     * `estadoKey`/`paciente`/`areaId` los usa sobre todo el módulo de Atenciones (citas ASISTIDA), pero sirven para cualquier filtro combinado.
     */
    @GetMapping("/filtro")
    public Page<CitaDTO> getByFiltros(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin,
            @RequestParam(required = false) String terapeuta,
            @RequestParam(required = false) String estadoKey,
            @RequestParam(required = false) String paciente,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long metodoPagoId,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication auth
    ) {
        return service.findByFiltrosPaged(fechaInicio, fechaFin, terapeuta, restriccionTerapeutaId(auth),
                estadoKey, paciente, areaId, metodoPagoId, pageable).map(this::redactarTelefono);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CitaDTO> getById(@PathVariable Long id, Authentication auth) {
        return service.findById(id)
                .filter(dto -> {
                    Long restriccion = restriccionTerapeutaId(auth);
                    return restriccion == null || restriccion.equals(dto.getTerapeutaId());
                })
                .map(this::redactarTelefono)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/citas/paciente/{id} — historial de citas de un paciente */
    @GetMapping("/paciente/{pacienteId}")
    public List<CitaDTO> getByPaciente(@PathVariable Long pacienteId) {
        return service.findByPaciente(pacienteId).stream().map(this::redactarTelefono).toList();
    }

    /** GET /api/citas/lote/{loteMasivoId}/resumen — cuántas citas de un lote de "citas masivas" faltan/se atendieron. */
    @GetMapping("/lote/{loteMasivoId}/resumen")
    public com.therateam.therateam.dto.LoteResumenDTO getResumenLote(@PathVariable String loteMasivoId) {
        return service.resumenLote(loteMasivoId);
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

    /**
     * POST /api/citas/{id}/anular?devolucion=SALDO|DINERO&metodoId=1 — cancela la cita y
     * resuelve el dinero: SALDO (default) lo deja como saldo a favor del paciente; DINERO
     * registra una devolución auditable (el pago original nunca se borra). `metodoId` es
     * opcional — solo aplica a citas de paquete cuando no se puede inferir del historial.
     */
    @PreAuthorize("hasAuthority('MODULO_CITAS_ELIMINAR')")
    @PostMapping("/{id}/anular")
    public ResponseEntity<CitaDTO> anular(@PathVariable Long id,
                                           @RequestParam(defaultValue = "SALDO") String devolucion,
                                           @RequestParam(required = false) Long metodoId) {
        return service.anular(id, devolucion, metodoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/citas/{id}/correccion — corrige una cita YA ATENDIDA (terapeuta, tipo de terapia,
     * precio, método del pago). Solo ADMIN: la edición normal prohíbe estos cambios en una cita
     * atendida y esa regla se mantiene; esto es la excepción para arreglar cargas mal hechas,
     * y queda registrada en el historial de la cita.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/correccion")
    public ResponseEntity<CitaDTO> corregirAtencion(@PathVariable Long id,
                                                     @RequestBody com.therateam.therateam.dto.CorreccionAtencionRequest req) {
        return service.corregirAtencion(id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
