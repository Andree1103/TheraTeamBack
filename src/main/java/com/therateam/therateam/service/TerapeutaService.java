package com.therateam.therateam.service;

import com.therateam.therateam.dto.TerapeutaCompletoRequest;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TerapeutaService {

    private final TerapeutaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final CatRolRepository catRolRepository;
    private final CatTipoTerapeutaRepository catTipoTerapeutaRepository;
    private final CatEspecialidadRepository catEspecialidadRepository;
    private final SedeRepository sedeRepository;

    public List<Terapeuta> findAll() {
        return repository.findAll();
    }

    public Optional<Terapeuta> findById(Long id) {
        return repository.findById(id);
    }

    public Terapeuta save(Terapeuta terapeuta) {
        return repository.save(terapeuta);
    }

    public Optional<Terapeuta> update(Long id, Terapeuta data) {
        return repository.findById(id).map(existing -> {
            existing.setUsuario(data.getUsuario());
            existing.setTipoTerapeuta(data.getTipoTerapeuta());
            existing.setCmp(data.getCmp());
            existing.setTelefono(data.getTelefono());
            existing.setFotoUrl(data.getFotoUrl());
            existing.setHorarioDescripcion(data.getHorarioDescripcion());
            existing.setActivo(data.getActivo());
            existing.setEspecialidades(data.getEspecialidades());
            return repository.save(existing);
        });
    }

    @Transactional
    public Terapeuta crearCompleto(TerapeutaCompletoRequest req) {
        Usuario usuario;

        if ("existente".equals(req.getModo())) {
            usuario = usuarioRepository.findById(req.getUsuarioId())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + req.getUsuarioId()));
        } else {
            CatRol rolTerapeuta = catRolRepository.findByKey("TERAPEUTA")
                    .orElseGet(() -> catRolRepository.findAll().stream()
                            .filter(r -> r.getNombre() != null && r.getNombre().toUpperCase().contains("TERAPEUTA"))
                            .findFirst()
                            .orElse(null));

            usuario = new Usuario();
            usuario.setNombre(req.getNombre());
            usuario.setApellido(req.getApellido());
            usuario.setEmail(req.getEmail());
            usuario.setPasswordHash(req.getPassword());
            usuario.setRol(rolTerapeuta);
            usuario.setActivo(true);

            if (req.getSedeId() != null) {
                sedeRepository.findById(req.getSedeId()).ifPresent(usuario::setSede);
            }

            usuario = usuarioRepository.save(usuario);
        }

        Terapeuta terapeuta = new Terapeuta();
        terapeuta.setUsuario(usuario);
        terapeuta.setActivo(req.getActivo() != null ? req.getActivo() : true);
        terapeuta.setCmp(req.getCmp());
        terapeuta.setTelefono(req.getTelefono());
        terapeuta.setFotoUrl(req.getFotoUrl());
        terapeuta.setHorarioDescripcion(req.getHorarioDescripcion());

        if (req.getTipoTerapeutaId() != null) {
            catTipoTerapeutaRepository.findById(req.getTipoTerapeutaId())
                    .ifPresent(terapeuta::setTipoTerapeuta);
        }

        if (req.getEspecialidadIds() != null && !req.getEspecialidadIds().isEmpty()) {
            List<CatEspecialidad> especialidades = catEspecialidadRepository.findAllById(req.getEspecialidadIds());
            terapeuta.setEspecialidades(especialidades);
        } else {
            terapeuta.setEspecialidades(new ArrayList<>());
        }

        return repository.save(terapeuta);
    }

    @Transactional
    public Optional<Terapeuta> actualizarCompleto(Long id, TerapeutaCompletoRequest req) {
        return repository.findById(id).map(existing -> {

            if ("existente".equals(req.getModo()) && req.getUsuarioId() != null) {
                usuarioRepository.findById(req.getUsuarioId()).ifPresent(existing::setUsuario);
            } else {
                Usuario u = existing.getUsuario();
                if (u != null) {
                    if (req.getNombre() != null) u.setNombre(req.getNombre());
                    if (req.getApellido() != null) u.setApellido(req.getApellido());
                    if (req.getEmail() != null) u.setEmail(req.getEmail());
                    if (req.getPassword() != null && !req.getPassword().isBlank()) {
                        u.setPasswordHash(req.getPassword());
                    }
                    if (req.getSedeId() != null) {
                        sedeRepository.findById(req.getSedeId()).ifPresent(u::setSede);
                    }
                    usuarioRepository.save(u);
                }
            }

            if (req.getTipoTerapeutaId() != null) {
                catTipoTerapeutaRepository.findById(req.getTipoTerapeutaId())
                        .ifPresent(existing::setTipoTerapeuta);
            }
            if (req.getCmp() != null) existing.setCmp(req.getCmp());
            if (req.getTelefono() != null) existing.setTelefono(req.getTelefono());
            if (req.getFotoUrl() != null) existing.setFotoUrl(req.getFotoUrl());
            if (req.getHorarioDescripcion() != null) existing.setHorarioDescripcion(req.getHorarioDescripcion());
            if (req.getActivo() != null) existing.setActivo(req.getActivo());

            if (req.getEspecialidadIds() != null) {
                List<CatEspecialidad> especialidades = catEspecialidadRepository.findAllById(req.getEspecialidadIds());
                existing.setEspecialidades(especialidades);
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
