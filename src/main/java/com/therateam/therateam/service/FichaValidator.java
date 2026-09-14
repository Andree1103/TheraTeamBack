package com.therateam.therateam.service;

import com.therateam.therateam.model.HcCampo;
import com.therateam.therateam.model.HcPlantilla;
import com.therateam.therateam.model.HcSeccion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Valida los valores de una ficha contra su plantilla y los normaliza antes de guardarlos.
 *
 * Lo usan por igual la historia clinica y la atencion: las dos son fichas configurables con
 * la misma mecanica (plantilla que define los campos + JSONB con los valores), y duplicar
 * estas reglas en dos servicios era pedir que se desincronizaran.
 */
@Component
public class FichaValidator {

    /**
     * @param previos lo que ya estaba guardado. Los valores bajo una clave que la plantilla
     *                dejo de definir se conservan: quitar un campo de la plantilla no debe
     *                borrar el dato historico del paciente.
     */
    public Map<String, Object> validarYNormalizar(HcPlantilla plantilla,
                                                   Map<String, Object> entrantes,
                                                   Map<String, Object> previos) {
        Map<String, Object> entrada = entrantes != null ? entrantes : Map.of();
        Map<String, Object> anteriores = previos != null ? previos : Map.of();
        Map<String, Object> salida = new LinkedHashMap<>();
        Set<String> clavesDeLaPlantilla = new HashSet<>();

        if (plantilla != null && plantilla.getSecciones() != null) {
            for (HcSeccion s : plantilla.getSecciones()) {
                if (s.getCampos() == null) continue;
                for (HcCampo c : s.getCampos()) {
                    clavesDeLaPlantilla.add(c.getClave());
                    Object normalizado = normalizar(c, entrada.get(c.getClave()));

                    if (esVacio(normalizado)) {
                        if (Boolean.TRUE.equals(c.getRequerido())) {
                            throw new IllegalArgumentException("Falta completar \"" + c.getEtiqueta() + "\".");
                        }
                        continue; // un campo vacio no se guarda: no ensucia el JSON
                    }
                    salida.put(c.getClave(), normalizado);
                }
            }
        }

        anteriores.forEach((k, v) -> {
            if (!clavesDeLaPlantilla.contains(k) && !salida.containsKey(k)) salida.put(k, v);
        });
        return salida;
    }

    private Object normalizar(HcCampo campo, Object valor) {
        if (esVacio(valor)) return null;
        String texto = valor instanceof String s ? s.trim() : String.valueOf(valor);

        switch (campo.getTipo()) {
            case "NUMERO" -> {
                try {
                    return new BigDecimal(texto);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("\"" + campo.getEtiqueta() + "\" debe ser un número.");
                }
            }
            case "FECHA" -> {
                try {
                    return LocalDate.parse(texto).toString();
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("\"" + campo.getEtiqueta() + "\" debe ser una fecha válida.");
                }
            }
            case "BOOLEANO" -> {
                if (valor instanceof Boolean b) return b;
                if ("true".equalsIgnoreCase(texto) || "false".equalsIgnoreCase(texto)) {
                    return Boolean.parseBoolean(texto);
                }
                throw new IllegalArgumentException("\"" + campo.getEtiqueta() + "\" debe ser sí o no.");
            }
            case "SELECT" -> {
                exigirOpcionValida(campo, texto);
                return texto;
            }
            case "MULTISELECT" -> {
                List<String> elegidos = new ArrayList<>();
                if (valor instanceof Collection<?> col) {
                    col.forEach(v -> elegidos.add(String.valueOf(v).trim()));
                } else {
                    elegidos.add(texto);
                }
                elegidos.removeIf(String::isBlank);
                elegidos.forEach(v -> exigirOpcionValida(campo, v));
                return elegidos;
            }
            default -> {
                return texto; // TEXTO y TEXTO_LARGO
            }
        }
    }

    private void exigirOpcionValida(HcCampo campo, String valor) {
        String[] opciones = campo.getOpciones();
        if (opciones == null || Arrays.stream(opciones).noneMatch(o -> o.equals(valor))) {
            throw new IllegalArgumentException(
                    "\"" + valor + "\" no es una opción válida para \"" + campo.getEtiqueta() + "\".");
        }
    }

    private static boolean esVacio(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof Collection<?> c) return c.isEmpty();
        return false;
    }
}
