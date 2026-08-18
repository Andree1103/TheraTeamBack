package com.therateam.therateam.service;

import com.therateam.therateam.model.CatRol;
import com.therateam.therateam.model.RolModuloPermiso;
import com.therateam.therateam.repository.CatRolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatRolService {

    private final CatRolRepository repository;

    public List<CatRol> findAll() { return repository.findAll(); }

    public Optional<CatRol> findById(Long id) { return repository.findById(id); }

    public CatRol save(CatRol rol) {
        // El JSON entrante trae permisos sin el back-reference "rol" (es @JsonIgnore) — sin esto,
        // Hibernate insertaría cada fila con rol_id NULL pese al cascade=ALL.
        if (rol.getPermisos() != null) {
            rol.getPermisos().forEach(p -> p.setRol(rol));
        }
        return repository.save(rol);
    }

    public Optional<CatRol> update(Long id, CatRol data) {
        return repository.findById(id).map(existing -> {
            existing.setKey(data.getKey());
            existing.setNombre(data.getNombre());
            existing.setActivo(data.getActivo());
            existing.setPacientesVerTelefono(Boolean.TRUE.equals(data.getPacientesVerTelefono()));
            if (data.getPermisos() != null) {
                // Actualiza en el sitio las filas que siguen (en vez de borrar-y-recrear): con
                // orphanRemoval, un clear()+add() del mismo (rol,modulo) genera un INSERT antes del
                // DELETE dentro del mismo flush y choca con la constraint única.
                Map<Long, RolModuloPermiso> actuales = existing.getPermisos().stream()
                        .collect(Collectors.toMap(p -> p.getModulo().getId(), p -> p));
                Set<Long> moduloIdsNuevos = data.getPermisos().stream()
                        .map(p -> p.getModulo().getId()).collect(Collectors.toSet());

                for (RolModuloPermiso incoming : data.getPermisos()) {
                    RolModuloPermiso row = actuales.get(incoming.getModulo().getId());
                    if (row != null) {
                        row.setCrear(incoming.isCrear());
                        row.setEditar(incoming.isEditar());
                        row.setEliminar(incoming.isEliminar());
                    } else {
                        RolModuloPermiso nuevo = new RolModuloPermiso();
                        nuevo.setRol(existing);
                        nuevo.setModulo(incoming.getModulo());
                        nuevo.setCrear(incoming.isCrear());
                        nuevo.setEditar(incoming.isEditar());
                        nuevo.setEliminar(incoming.isEliminar());
                        existing.getPermisos().add(nuevo);
                    }
                }
                existing.getPermisos().removeIf(p -> !moduloIdsNuevos.contains(p.getModulo().getId()));
            }
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
