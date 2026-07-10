package com.therateam.therateam.service;

import com.therateam.therateam.dto.TerapeutaCompletoRequest;
import com.therateam.therateam.dto.TerapeutaDTO;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TerapeutaService {

    private final TerapeutaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final CatRolRepository catRolRepository;
    private final CatTipoTerapeutaRepository catTipoTerapeutaRepository;
    private final CatAreaRepository catAreaRepository;
    private final CatEspecialidadRepository catEspecialidadRepository;
    private final SedeRepository sedeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<TerapeutaDTO> findAllPaged(Pageable pageable) {
        return repository.findAll(pageable).map(this::toDTO);
    }

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
            existing.setArea(data.getArea());
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
            usuario.setPasswordHash(passwordEncoder.encode(req.getPassword()));
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

        if (req.getAreaId() != null) {
            catAreaRepository.findById(req.getAreaId()).ifPresent(terapeuta::setArea);
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
                        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
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
            if (req.getAreaId() != null) {
                catAreaRepository.findById(req.getAreaId()).ifPresent(existing::setArea);
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

    public TerapeutaDTO toDTO(Terapeuta t) {
        TerapeutaDTO dto = new TerapeutaDTO();
        dto.setId(t.getId());
        dto.setCmp(t.getCmp());
        dto.setTelefono(t.getTelefono());
        dto.setFotoUrl(t.getFotoUrl());
        dto.setHorarioDescripcion(t.getHorarioDescripcion());
        dto.setActivo(t.getActivo());

        if (t.getUsuario() != null) {
            dto.setNombre(t.getUsuario().getNombre());
            dto.setApellido(t.getUsuario().getApellido());
            dto.setEmail(t.getUsuario().getEmail());
        }

        if (t.getTipoTerapeuta() != null) {
            TerapeutaDTO.TipoInfo ti = new TerapeutaDTO.TipoInfo();
            ti.setId(t.getTipoTerapeuta().getId());
            ti.setKey(t.getTipoTerapeuta().getKey());
            ti.setNombre(t.getTipoTerapeuta().getNombre());
            dto.setTipoTerapeuta(ti);
        }

        if (t.getArea() != null) {
            TerapeutaDTO.AreaInfo ai = new TerapeutaDTO.AreaInfo();
            ai.setId(t.getArea().getId());
            ai.setKey(t.getArea().getKey());
            ai.setNombre(t.getArea().getNombre());
            dto.setArea(ai);
        }

        if (t.getEspecialidades() != null) {
            dto.setEspecialidades(t.getEspecialidades().stream().map(e -> {
                TerapeutaDTO.EspecialidadInfo ei = new TerapeutaDTO.EspecialidadInfo();
                ei.setId(e.getId());
                ei.setKey(e.getKey());
                ei.setNombre(e.getNombre());
                return ei;
            }).collect(Collectors.toList()));
        }

        return dto;
    }
}
