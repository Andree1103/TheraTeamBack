package com.therateam.therateam.controller;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.model.Archivo;
import com.therateam.therateam.service.ArchivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adjuntos de una atencion, una historia clinica o un paciente.
 *
 * El permiso depende de a QUE se adjunta, no del endpoint: los de historia clinica son datos
 * de salud y van con su permiso propio; los demas siguen el permiso del modulo Pacientes.
 */
@RestController
@RequestMapping("/api/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private final ArchivoService service;

    /** GET /api/archivos?entidadTipo=ATENCION&entidadId=12 */
    @GetMapping
    public List<Archivo> listar(@RequestParam String entidadTipo, @RequestParam Long entidadId) {
        exigirLectura(entidadTipo);
        return service.listar(entidadTipo, entidadId);
    }

    /** POST /api/archivos (multipart) — sube un archivo y devuelve su metadato. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Archivo> subir(@RequestParam String entidadTipo,
                                          @RequestParam Long entidadId,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(required = false) String descripcion) {
        exigirEscritura(entidadTipo);
        Archivo guardado = service.guardar(entidadTipo, entidadId, file, descripcion);
        return ResponseEntity.status(201).body(guardado);
    }

    /**
     * GET /api/archivos/{id}/contenido — el binario.
     * Las imagenes y los PDF se muestran inline (para la vista previa); el resto se descarga.
     */
    @GetMapping("/{id}/contenido")
    public ResponseEntity<byte[]> contenido(@PathVariable Long id) {
        Archivo a = service.findById(id).orElse(null);
        if (a == null) return ResponseEntity.notFound().build();
        exigirLectura(a.getEntidadTipo());

        byte[] bytes = service.contenido(a).orElse(null);
        if (bytes == null) return ResponseEntity.notFound().build();

        boolean inline = a.getMime() != null
                && (a.getMime().startsWith("image/") || a.getMime().equals("application/pdf"));
        String nombre = URLEncoder.encode(a.getNombreOriginal(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, a.getMime())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + nombre)
                .body(bytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        Archivo a = service.findById(id).orElse(null);
        if (a == null) return ResponseEntity.notFound().build();
        exigirEscritura(a.getEntidadTipo());
        return service.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Limites que el front necesita para avisar ANTES de intentar la subida. */
    @GetMapping("/limites")
    public Map<String, Object> limites() {
        return Map.of("maxMb", service.getMaxMb());
    }

    // ── Permisos ─────────────────────────────────────────────────────────────

    private void exigirLectura(String entidadTipo) {
        if (esHistoria(entidadTipo)) {
            if (!SecurityUtils.puedeVerHistoria()) {
                throw new AccessDeniedException("Tu rol no puede ver la historia clinica.");
            }
        } else if (!SecurityUtils.tieneModulo("PACIENTES")) {
            throw new AccessDeniedException("Tu rol no tiene acceso al modulo de pacientes.");
        }
    }

    private void exigirEscritura(String entidadTipo) {
        if (esHistoria(entidadTipo)) {
            if (!SecurityUtils.puedeEditarHistoria()) {
                throw new AccessDeniedException("Tu rol no puede editar la historia clinica.");
            }
        } else if (!SecurityUtils.tieneAutoridadPublica("MODULO_PACIENTES_EDITAR")) {
            throw new AccessDeniedException("Tu rol no puede adjuntar archivos.");
        }
    }

    private static boolean esHistoria(String entidadTipo) {
        return Archivo.HISTORIA.equalsIgnoreCase(entidadTipo == null ? "" : entidadTipo.trim());
    }
}
