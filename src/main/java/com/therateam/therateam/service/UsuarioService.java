package com.therateam.therateam.service;

import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.SedeRepository;
import com.therateam.therateam.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final SedeRepository sedeRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Usuario> findAll() {
        return repository.findAll();
    }

    public Page<Usuario> findAllPaged(Pageable pageable, String nombre, String correo, Long rolId, Boolean activo) {
        return repository.buscarPaged(blankToNull(nombre), blankToNull(correo), rolId, activo, pageable);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public List<Usuario> findLibres() {
        return repository.findUsuariosLibres();
    }

    public Optional<Usuario> findById(Long id) {
        return repository.findById(id);
    }

    /** `usuario.passwordHash` llega en texto plano desde el cliente — se encripta aquí antes de guardar. */
    public Usuario save(Usuario usuario) {
        if (usuario.getPasswordHash() != null && !usuario.getPasswordHash().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(usuario.getPasswordHash()));
        }
        if (usuario.getActivo() == null) usuario.setActivo(true);
        if (usuario.getCitasSoloPropias() == null) usuario.setCitasSoloPropias(false);
        if (usuario.getCitasPuedeCrear() == null) usuario.setCitasPuedeCrear(true);
        if (usuario.getSede() == null) {
            sedeRepository.findFirstByActivoTrueOrderByIdAsc().ifPresent(usuario::setSede);
        }
        return repository.save(usuario);
    }

    /** Si no se envía una contraseña nueva, se conserva el hash existente (no se sobreescribe con null). */
    public Optional<Usuario> update(Long id, Usuario data) {
        return repository.findById(id).map(existing -> {
            existing.setNombre(data.getNombre());
            existing.setApellido(data.getApellido());
            existing.setEmail(data.getEmail());
            if (data.getPasswordHash() != null && !data.getPasswordHash().isBlank()) {
                existing.setPasswordHash(passwordEncoder.encode(data.getPasswordHash()));
            }
            existing.setRol(data.getRol());
            existing.setActivo(data.getActivo());
            existing.setCitasSoloPropias(Boolean.TRUE.equals(data.getCitasSoloPropias()));
            existing.setCitasPuedeCrear(!Boolean.FALSE.equals(data.getCitasPuedeCrear()));
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
