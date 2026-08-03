package com.therateam.therateam.controller;

import com.therateam.therateam.config.JwtService;
import com.therateam.therateam.dto.LoginRequest;
import com.therateam.therateam.dto.LoginResponse;
import com.therateam.therateam.dto.PermisoDTO;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.TerapeutaRepository;
import com.therateam.therateam.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final TerapeutaRepository terapeutaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmail(req.getEmail()).orElse(null);

        if (usuario == null
                || Boolean.FALSE.equals(usuario.getActivo())
                || usuario.getPasswordHash() == null
                || !passwordEncoder.matches(req.getPassword(), usuario.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciales inválidas"));
        }

        return ResponseEntity.ok(buildResponse(usuario));
    }

    /** Revalida el token actual y devuelve el usuario/permisos frescos desde BD (para el arranque del SPA). */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        Long id;
        try {
            id = Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return ResponseEntity.status(401).build();
        }
        return usuarioRepository.findById(id)
                .filter(u -> !Boolean.FALSE.equals(u.getActivo()))
                .map(u -> ResponseEntity.ok(buildResponse(u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    private LoginResponse buildResponse(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        var permisosEntidad = usuario.getRol() != null ? usuario.getRol().getPermisos() : List.<com.therateam.therateam.model.RolModuloPermiso>of();
        List<String> modulos = permisosEntidad.stream().map(p -> p.getModulo().getKey()).sorted().toList();
        List<PermisoDTO> permisos = permisosEntidad.stream()
                .map(p -> new PermisoDTO(p.getModulo().getKey(), p.isCrear(), p.isEditar(), p.isEliminar()))
                .toList();
        Long terapeutaId = terapeutaRepository.findByUsuarioId(usuario.getId())
                .map(Terapeuta::getId).orElse(null);
        return new LoginResponse(
                token, usuario.getId(), usuario.getNombre(), usuario.getApellido(), usuario.getEmail(),
                usuario.getRol() != null ? usuario.getRol().getKey() : null, modulos, permisos,
                terapeutaId, Boolean.TRUE.equals(usuario.getCitasSoloPropias()),
                !Boolean.FALSE.equals(usuario.getCitasPuedeCrear())
        );
    }
}
