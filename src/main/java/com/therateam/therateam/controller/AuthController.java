package com.therateam.therateam.controller;

import com.therateam.therateam.config.JwtService;
import com.therateam.therateam.dto.ForgotPasswordRequest;
import com.therateam.therateam.dto.LoginRequest;
import com.therateam.therateam.dto.LoginResponse;
import com.therateam.therateam.dto.PermisoDTO;
import com.therateam.therateam.dto.ResetPasswordRequest;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.model.Usuario;
import com.therateam.therateam.repository.TerapeutaRepository;
import com.therateam.therateam.repository.UsuarioRepository;
import com.therateam.therateam.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
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
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final SecureRandom RANDOM = new SecureRandom();

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

    /**
     * Siempre responde 200 con el mismo mensaje genérico, exista o no el correo — así no se puede
     * usar este endpoint para averiguar qué correos están registrados en el sistema.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        usuarioRepository.findByEmail(req.getEmail())
                .filter(u -> !Boolean.FALSE.equals(u.getActivo()))
                .ifPresent(u -> {
                    String token = generarTokenSeguro();
                    u.setResetToken(token);
                    u.setResetTokenExpira(LocalDateTime.now().plusHours(1));
                    usuarioRepository.save(u);

                    String link = frontendUrl + "/restablecer-password?token=" + token;
                    emailService.enviarCorreo(u.getEmail(), "Recupera tu contraseña — Thera Team",
                            "Hola " + u.getNombre() + ",\n\n" +
                            "Recibimos una solicitud para restablecer tu contraseña. Haz clic en el siguiente enlace " +
                            "(válido por 1 hora):\n\n" + link + "\n\n" +
                            "Si tú no lo solicitaste, puedes ignorar este correo.");
                });
        return ResponseEntity.ok(Map.of("mensaje",
                "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        Usuario usuario = usuarioRepository.findByResetToken(req.getToken()).orElse(null);
        if (usuario == null
                || usuario.getResetTokenExpira() == null
                || usuario.getResetTokenExpira().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(400).body(Map.of("error", "El enlace no es válido o ya venció"));
        }

        usuario.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        usuario.setResetToken(null);
        usuario.setResetTokenExpira(null);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente"));
    }

    private String generarTokenSeguro() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
