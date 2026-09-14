package com.therateam.therateam.service;

import com.therateam.therateam.model.HcCampo;
import com.therateam.therateam.model.HcPlantilla;
import com.therateam.therateam.model.HcSeccion;
import com.therateam.therateam.repository.HcPlantillaRepository;
import com.therateam.therateam.repository.HistoriaClinicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Plantillas de historia clinica: definen QUE campos tiene la ficha de cada area.
 *
 * El arbol (plantilla -> secciones -> campos) se guarda completo en cada PUT gracias al
 * cascade de las entidades; aca se valida y se reconstruyen las relaciones inversas, que el
 * JSON que llega del front no trae.
 */
@Service
@RequiredArgsConstructor
public class HcPlantillaService {

    private final HcPlantillaRepository repository;
    private final HistoriaClinicaRepository historiaRepository;

    public List<HcPlantilla> findAll() {
        return repository.findAll(org.springframework.data.domain.Sort.by("orden", "id"));
    }

    public List<HcPlantilla> findActivas() {
        return repository.findByActivoTrueOrderByOrdenAscIdAsc();
    }

    public Optional<HcPlantilla> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public HcPlantilla crear(HcPlantilla data) {
        validar(data);
        enlazar(data);
        return repository.save(data);
    }

    /**
     * Reemplaza la plantilla entera. Las secciones y campos que no vengan en el cuerpo se
     * borran (orphanRemoval): el front siempre manda el arbol completo.
     */
    @Transactional
    public Optional<HcPlantilla> actualizar(Long id, HcPlantilla data) {
        return repository.findById(id).map(existente -> {
            validar(data);
            existente.setNombre(data.getNombre());
            existente.setArea(data.getArea());
            existente.setDescripcion(data.getDescripcion());
            existente.setActivo(data.getActivo() == null || data.getActivo());
            existente.setOrden(data.getOrden() != null ? data.getOrden() : 0);

            existente.getSecciones().clear();
            if (data.getSecciones() != null) {
                data.getSecciones().forEach(existente.getSecciones()::add);
            }
            enlazar(existente);
            return repository.save(existente);
        });
    }

    /**
     * Una plantilla con historias cargadas NO se borra: se desactiva. Borrarla dejaria las
     * fichas de los pacientes apuntando a la nada.
     */
    @Transactional
    public String eliminarODesactivar(Long id) {
        HcPlantilla p = repository.findById(id).orElse(null);
        if (p == null) return "NO_EXISTE";
        boolean enUso = historiaRepository.findAll().stream()
                .anyMatch(h -> h.getPlantilla() != null && id.equals(h.getPlantilla().getId()));
        if (enUso) {
            p.setActivo(false);
            repository.save(p);
            return "DESACTIVADA";
        }
        repository.delete(p);
        return "ELIMINADA";
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    /** El JSON del front no trae las referencias al padre: se reconstruyen antes de persistir. */
    private void enlazar(HcPlantilla p) {
        if (p.getSecciones() == null) { p.setSecciones(new ArrayList<>()); return; }
        int ordenSeccion = 0;
        for (HcSeccion s : p.getSecciones()) {
            s.setPlantilla(p);
            if (s.getOrden() == null) s.setOrden(ordenSeccion);
            ordenSeccion++;
            if (s.getCampos() == null) { s.setCampos(new ArrayList<>()); continue; }
            int ordenCampo = 0;
            for (HcCampo c : s.getCampos()) {
                c.setSeccion(s);
                if (c.getOrden() == null) c.setOrden(ordenCampo);
                if (c.getRequerido() == null) c.setRequerido(false);
                ordenCampo++;
            }
        }
    }

    private void validar(HcPlantilla p) {
        if (p.getNombre() == null || p.getNombre().isBlank()) {
            throw new IllegalArgumentException("La plantilla necesita un nombre.");
        }
        if (p.getSecciones() == null) return;

        // Las claves identifican el valor dentro del JSON de la historia: deben ser unicas en
        // TODA la plantilla, no solo dentro de su seccion, o un campo pisaria a otro.
        Set<String> claves = new HashSet<>();
        for (HcSeccion s : p.getSecciones()) {
            if (s.getNombre() == null || s.getNombre().isBlank()) {
                throw new IllegalArgumentException("Cada sección necesita un nombre.");
            }
            if (s.getCampos() == null) continue;
            for (HcCampo c : s.getCampos()) {
                if (c.getClave() == null || c.getClave().isBlank()) {
                    throw new IllegalArgumentException("El campo \"" + c.getEtiqueta() + "\" necesita una clave.");
                }
                String clave = c.getClave().trim().toLowerCase(Locale.ROOT);
                if (!clave.matches("[a-z0-9_]{1,60}")) {
                    throw new IllegalArgumentException(
                            "La clave \"" + c.getClave() + "\" solo admite minúsculas, números y guion bajo.");
                }
                c.setClave(clave);
                if (!claves.add(clave)) {
                    throw new IllegalArgumentException("La clave \"" + clave + "\" está repetida en la plantilla.");
                }
                if (c.getEtiqueta() == null || c.getEtiqueta().isBlank()) {
                    throw new IllegalArgumentException("El campo \"" + clave + "\" necesita una etiqueta.");
                }
                if (c.getTipo() == null || !HcCampo.TIPOS.contains(c.getTipo())) {
                    throw new IllegalArgumentException("Tipo de campo no válido: " + c.getTipo());
                }
                boolean esLista = "SELECT".equals(c.getTipo()) || "MULTISELECT".equals(c.getTipo());
                if (esLista && (c.getOpciones() == null || c.getOpciones().length == 0)) {
                    throw new IllegalArgumentException(
                            "El campo \"" + c.getEtiqueta() + "\" es de lista: necesita al menos una opción.");
                }
            }
        }
    }
}
