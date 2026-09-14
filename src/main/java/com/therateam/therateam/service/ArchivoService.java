package com.therateam.therateam.service;

import com.therateam.therateam.config.SecurityUtils;
import com.therateam.therateam.model.Archivo;
import com.therateam.therateam.repository.ArchivoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guarda los adjuntos en el disco del servidor y en la base solo el metadato.
 *
 * Reglas que se aplican siempre, vengan de donde vengan:
 *   - lista blanca de tipos (imagenes, PDF y documentos de oficina): nada de ejecutables;
 *   - tope de tamano configurable (app.archivos.max-mb);
 *   - el nombre del archivo en disco lo genera el backend (uuid + extension). El nombre que
 *     subio el usuario se guarda aparte y nunca toca el filesystem: asi no hay colisiones ni
 *     rutas maliciosas del tipo "../../etc/passwd".
 */
@Service
@RequiredArgsConstructor
public class ArchivoService {

    /** mime permitido -> extension con la que se guarda. */
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/heic", "heic"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("text/plain", "txt")
    );

    private final ArchivoRepository repository;

    @Value("${app.archivos.ruta}")
    private String rutaBase;

    @Value("${app.archivos.max-mb}")
    private long maxMb;

    public List<Archivo> listar(String entidadTipo, Long entidadId) {
        return repository.findByEntidadTipoAndEntidadIdOrderByIdAsc(normalizarEntidad(entidadTipo), entidadId);
    }

    public Optional<Archivo> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Archivo guardar(String entidadTipo, Long entidadId, MultipartFile file, String descripcion) {
        String entidad = normalizarEntidad(entidadTipo);
        if (entidadId == null) throw new IllegalArgumentException("Falta indicar a que se adjunta el archivo.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("El archivo esta vacio.");

        long maxBytes = maxMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("El archivo supera el maximo de " + maxMb + " MB.");
        }

        String mime = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        String extension = TIPOS_PERMITIDOS.get(mime);
        if (extension == null) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido (" + (mime.isBlank() ? "desconocido" : mime)
                    + "). Se aceptan imagenes, PDF y documentos de oficina.");
        }

        String nombreGuardado = UUID.randomUUID() + "." + extension;
        try {
            Path destino = carpeta().resolve(nombreGuardado);
            file.transferTo(destino.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo en el servidor.", e);
        }

        Archivo a = new Archivo();
        a.setEntidadTipo(entidad);
        a.setEntidadId(entidadId);
        a.setNombreOriginal(recortar(nombreLimpio(file.getOriginalFilename()), 255));
        a.setNombreGuardado(nombreGuardado);
        a.setMime(mime);
        a.setTamanoBytes(file.getSize());
        a.setDescripcion(recortar(descripcion, 255));
        return repository.save(a);
    }

    /** Bytes del adjunto. Vacio si el metadato existe pero el fichero ya no esta en disco. */
    public Optional<byte[]> contenido(Archivo a) {
        try {
            Path p = carpeta().resolve(a.getNombreGuardado());
            if (!Files.exists(p)) return Optional.empty();
            return Optional.of(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo.", e);
        }
    }

    /**
     * Borra el metadato y el fichero. Si el fichero ya no esta, igual se borra la fila: dejarla
     * apuntando a la nada solo ensucia el listado.
     */
    @Transactional
    public boolean eliminar(Long id) {
        return repository.findById(id).map(a -> {
            try {
                Files.deleteIfExists(carpeta().resolve(a.getNombreGuardado()));
            } catch (IOException ignored) {
                // El metadato se borra igual: el huerfano en disco no debe bloquear la operacion.
            }
            repository.delete(a);
            return true;
        }).orElse(false);
    }

    /** Cuantos adjuntos tiene algo — para mostrar el contador sin traer la lista entera. */
    public long contar(String entidadTipo, Long entidadId) {
        return repository.countByEntidadTipoAndEntidadId(normalizarEntidad(entidadTipo), entidadId);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private Path carpeta() throws IOException {
        Path p = Paths.get(rutaBase).toAbsolutePath().normalize();
        Files.createDirectories(p);
        return p;
    }

    private static String normalizarEntidad(String entidadTipo) {
        String e = entidadTipo == null ? "" : entidadTipo.trim().toUpperCase(Locale.ROOT);
        if (!Archivo.ENTIDADES.contains(e)) {
            throw new IllegalArgumentException("Tipo de entidad no valido: " + entidadTipo);
        }
        return e;
    }

    /** Se queda solo con el nombre, sin ninguna ruta que venga en el multipart. */
    private static String nombreLimpio(String original) {
        if (original == null || original.isBlank()) return "archivo";
        String base = Paths.get(original.replace('\\', '/')).getFileName().toString();
        return base.isBlank() ? "archivo" : base;
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    public long getMaxMb() { return maxMb; }
}
