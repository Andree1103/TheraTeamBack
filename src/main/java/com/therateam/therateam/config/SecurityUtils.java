package com.therateam.therateam.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * El subject del JWT es el id del usuario autenticado (ver JwtService.generarToken) — de ahí se
 * lee para poblar los campos de auditoría (idusuario_creacion / idusuario_modificacion) en las
 * entidades de negocio, sin depender de que cada service lo reciba como parámetro.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** true solo si el usuario autenticado tiene el permiso PACIENTES_VER_TELEFONO activado. */
    public static boolean puedeVerTelefonoPacientes() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "PACIENTES_VER_TELEFONO".equals(a.getAuthority()));
    }
}
