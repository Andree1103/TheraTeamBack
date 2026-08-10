package com.therateam.therateam.service;

import com.therateam.therateam.model.Configuracion;
import com.therateam.therateam.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository repository;

    /**
     * Claves fijas de "datos del negocio" (sin sede, globales) — la pantalla de Configuración
     * solo permite editar estas, nunca agregar claves nuevas desde el frontend.
     */
    private static final Map<String, String> CLAVES_NEGOCIO = Map.of(
            "nombre_negocio", "Nombre del negocio/clínica",
            "telefono",       "Teléfono de contacto",
            "direccion",      "Dirección del negocio"
    );

    /** Si alguna clave todavía no existe en la tabla, se devuelve vacía en vez de fallar. */
    public Map<String, String> obtenerNegocio() {
        Map<String, String> out = new LinkedHashMap<>();
        CLAVES_NEGOCIO.keySet().forEach(clave ->
                out.put(clave, repository.findBySedeIsNullAndClave(clave).map(Configuracion::getValor).orElse("")));
        return out;
    }

    /** Upsert de las claves de negocio — crea la fila si no existía, la actualiza si ya existía. */
    public Map<String, String> actualizarNegocio(Map<String, String> datos) {
        CLAVES_NEGOCIO.forEach((clave, descripcion) -> {
            if (!datos.containsKey(clave)) return;
            Configuracion c = repository.findBySedeIsNullAndClave(clave).orElseGet(Configuracion::new);
            c.setClave(clave);
            c.setValor(datos.get(clave));
            c.setDescripcion(descripcion);
            repository.save(c);
        });
        return obtenerNegocio();
    }

    public List<Configuracion> findAll() { return repository.findAll(); }

    public Optional<Configuracion> findById(Long id) { return repository.findById(id); }

    public List<Configuracion> findBySede(Long sedeId) {
        return repository.findAll().stream()
                .filter(c -> c.getSede() != null && c.getSede().getId().equals(sedeId))
                .toList();
    }

    public Configuracion save(Configuracion conf) { return repository.save(conf); }

    public Optional<Configuracion> update(Long id, Configuracion data) {
        return repository.findById(id).map(existing -> {
            existing.setSede(data.getSede());
            existing.setClave(data.getClave());
            existing.setValor(data.getValor());
            existing.setDescripcion(data.getDescripcion());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
