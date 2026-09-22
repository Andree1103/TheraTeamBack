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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            // Los tres de abajo los usa la agenda: su barra de filtros ahora se resuelve aquí en
            // vez de traerse la semana completa y descartar en el navegador lo que no cumple.
            @RequestParam(required = false) String estadoPagoKey,
            @RequestParam(required = false) String tipoTerapiaKey,
            @RequestParam(required = false) List<Long> terapeutaIds,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication auth
    ) {
        return service.findByFiltrosPaged(fechaInicio, fechaFin, terapeuta, restriccionTerapeutaId(auth),
                estadoKey, paciente, areaId, metodoPagoId, estadoPagoKey, tipoTerapiaKey, terapeutaIds,
                conNulosAlFinal(pageable)).map(this::redactarTelefono);
    }

    /**
     * Ordenar por la fecha de registro de la atencion pone los NULL delante, y hay que evitarlo.
     *
     * Atenciones ordena por ac.createdAt (cuando se anoto), que es null en las citas marcadas
     * antes de que existiera el registro de inasistencia. Postgres, en DESC, manda los NULL al
     * principio: la lista abria con esas filas viejas y sin dato, justo encima de lo que se
     * acababa de registrar. Spring no permite pedir NULLS LAST desde el parametro `sort`, asi
     * que se reconstruye aqui.
     */
    private Pageable conNulosAlFinal(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) return pageable;
        List<Sort.Order> ordenes = pageable.getSort().stream()
                .map(o -> o.getProperty().startsWith("ac.") ? o.nullsLast() : o)
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(ordenes));
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
     * POST /api/citas/{id}/anular?devolucion=SALDO|DINERO&metodoId=1&motivo=... — anula la cita y
     * resuelve el dinero: SALDO (default) lo deja como saldo a favor del paciente; DINERO
     * registra una devolución auditable (el pago original nunca se borra). `metodoId` es
     * opcional — solo aplica a citas de paquete cuando no se puede inferir del historial.
     * `motivo` es OBLIGATORIO: es lo que sustituye a los antiguos estados "cancelada por
     * paciente" y "cancelada por clínica". Se declara como no requerido para responder 400 con
     * un mensaje entendible en vez del error genérico de parámetro faltante.
     */
    @PreAuthorize("hasAuthority('MODULO_CITAS_ELIMINAR')")
    @PostMapping("/{id}/anular")
    public ResponseEntity<CitaDTO> anular(@PathVariable Long id,
                                           @RequestParam(defaultValue = "SALDO") String devolucion,
                                           @RequestParam(required = false) Long metodoId,
                                           @RequestParam(required = false) String motivo) {
        return service.anular(id, devolucion, metodoId, motivo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/citas/{id}/reprogramar — deja la cita original como constancia (REPROGRAMADA, con
     * su motivo) y crea la cita nueva, que hereda paciente, terapia, precio y pago, y apunta a la
     * original por reprogramacion_de. Devuelve la cita NUEVA, que es la que sigue viva.
     *
     * Pide el permiso de EDITAR y no el de ELIMINAR (que es el de anular): mover una cita de hora
     * es parte del trabajo diario de quien maneja la agenda, no una operación excepcional.
     */
    @PreAuthorize("hasAuthority('MODULO_CITAS_EDITAR')")
    @PostMapping("/{id}/reprogramar")
    public ResponseEntity<CitaDTO> reprogramar(@PathVariable Long id,
                                                @RequestBody com.therateam.therateam.dto.ReprogramarCitaRequest req) {
        return ResponseEntity.ok(service.reprogramar(id, req));
    }

    /**
     * PUT /api/citas/{id}/correccion — corrige una cita YA ATENDIDA (terapeuta, tipo de terapia,
     * precio, método del pago). La edición normal prohíbe estos cambios en una cita atendida y
     * esa regla se mantiene; esto es la excepción para arreglar cargas mal hechas, y queda
     * registrada en el historial de la cita.
     *
     * Se habilita por ROL desde Seguridad > Roles (antes estaba clavado a ADMIN acá).
     */
    @PreAuthorize("hasAuthority('PUEDE_CORREGIR_ATENCION')")
    @PutMapping("/{id}/correccion")
    public ResponseEntity<CitaDTO> corregirAtencion(@PathVariable Long id,
                                                     @RequestBody com.therateam.therateam.dto.CorreccionAtencionRequest req) {
        return service.corregirAtencion(id, req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
