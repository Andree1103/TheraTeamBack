package com.therateam.therateam.controller;

import com.therateam.therateam.model.HcPlantilla;
import com.therateam.therateam.service.HcPlantillaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Plantillas de historia clinica.
 *
 * Leerlas basta con poder ver historias (la ficha del paciente necesita saber que campos
 * pintar); modificarlas es configuracion del sistema y va con el modulo Configuraciones.
 */
@RestController
@RequestMapping("/api/hc-plantillas")
@RequiredArgsConstructor
public class HcPlantillaController {

    private final HcPlantillaService service;

    /**
     * GET /api/hc-plantillas?tipo=HISTORIA&todas=true
     * `tipo` filtra por tipo de ficha (HISTORIA o ATENCION); sin el, devuelve las dos.
     * `todas` incluye las desactivadas — lo usa la pantalla que las administra.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PUEDE_VER_HISTORIA') or hasAuthority('MODULO_CONFIGURACIONES')")
    public List<HcPlantilla> listar(@RequestParam(required = false) String tipo,
                                     @RequestParam(defaultValue = "false") boolean todas) {
        if (tipo != null && !tipo.isBlank()) return service.findPorTipo(tipo, todas);
        return todas ? service.findAll() : service.findActivas();
    }

    /**
     * GET /api/hc-plantillas/resolver?tipo=ATENCION&tipoTerapiaId=15
     * La plantilla que corresponde a ese tipo de terapia: la suya si la tiene, si no la
     * generica. Es lo que usa el modal de atencion para saber que campos pintar.
     */
    @GetMapping("/resolver")
    @PreAuthorize("hasAuthority('PUEDE_VER_HISTORIA') or hasAuthority('MODULO_CITAS')")
    public ResponseEntity<HcPlantilla> resolver(@RequestParam String tipo,
                                                 @RequestParam(required = false) Long tipoTerapiaId) {
        return service.resolver(tipo, tipoTerapiaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PUEDE_VER_HISTORIA') or hasAuthority('MODULO_CONFIGURACIONES')")
    public ResponseEntity<HcPlantilla> porId(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_CREAR')")
    public ResponseEntity<HcPlantilla> crear(@RequestBody HcPlantilla data) {
        return ResponseEntity.status(201).body(service.crear(data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_EDITAR')")
    public ResponseEntity<HcPlantilla> actualizar(@PathVariable Long id, @RequestBody HcPlantilla data) {
        return service.actualizar(id, data).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Si la plantilla ya tiene fichas cargadas se DESACTIVA en vez de borrarse: borrarla
     * dejaria las historias de los pacientes apuntando a la nada. La respuesta dice cual paso.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MODULO_CONFIGURACIONES_ELIMINAR')")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable Long id) {
        String resultado = service.eliminarODesactivar(id);
        if ("NO_EXISTE".equals(resultado)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("resultado", resultado));
    }
}
