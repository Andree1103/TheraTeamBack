package com.therateam.therateam.service;

import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.HcPlantillaRepository;
import com.therateam.therateam.repository.HistoriaClinicaRepository;
import com.therateam.therateam.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * La ficha clinica de un paciente. Los valores se guardan en un JSONB con la forma
 * {claveDelCampo: valor}, y la plantilla es la que dice que claves son validas y de que tipo.
 *
 * Al guardar se valida contra la plantilla: tipos, opciones de las listas y campos requeridos.
 * Lo que ya estaba guardado bajo una clave que la plantilla ya no define NO se borra — si
 * alguien quita un campo de la plantilla, el dato historico del paciente sigue ahi.
 */
@Service
@RequiredArgsConstructor
public class HistoriaClinicaService {

    private final HistoriaClinicaRepository repository;
    private final HcPlantillaRepository plantillaRepository;
    private final PacienteRepository pacienteRepository;

    public List<HistoriaClinica> findByPaciente(Long pacienteId) {
        return repository.findByPaciente_IdOrderByIdAsc(pacienteId);
    }

    public Optional<HistoriaClinica> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Crea o actualiza la ficha del paciente para esa plantilla (upsert): la pantalla no tiene
     * que saber si es la primera vez que se llena.
     */
    @Transactional
    public HistoriaClinica guardar(Long pacienteId, Long plantillaId, Map<String, Object> datos) {
        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado: " + pacienteId));
        HcPlantilla plantilla = plantillaRepository.findById(plantillaId)
                .orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada: " + plantillaId));

        HistoriaClinica historia = repository.findByPaciente_IdAndPlantilla_Id(pacienteId, plantillaId)
                .orElseGet(() -> {
                    HistoriaClinica nueva = new HistoriaClinica();
                    nueva.setPaciente(paciente);
                    nueva.setPlantilla(plantilla);
                    return nueva;
                });

        Map<String, Object> previos = historia.getDatos() != null ? historia.getDatos() : Map.of();
        historia.setDatos(validarYNormalizar(plantilla, datos, previos));
        return repository.save(historia);
    }

    // ── Validacion contra la plantilla ───────────────────────────────────────

    private Map<String, Object> validarYNormalizar(HcPlantilla plantilla,
                                                    Map<String, Object> entrantes,
                                                    Map<String, Object> previos) {
        Map<String, Object> entrada = entrantes != null ? entrantes : Map.of();
        Map<String, Object> salida = new LinkedHashMap<>();
        Set<String> clavesDeLaPlantilla = new HashSet<>();

        for (HcSeccion s : plantilla.getSecciones()) {
            for (HcCampo c : s.getCampos()) {
                clavesDeLaPlantilla.add(c.getClave());
                Object valor = entrada.get(c.getClave());
                Object normalizado = normalizar(c, valor);

                if (esVacio(normalizado)) {
                    if (Boolean.TRUE.equals(c.getRequerido())) {
                        throw new IllegalArgumentException("Falta completar \"" + c.getEtiqueta() + "\".");
                    }
                    continue; // un campo vacio no se guarda: no ensucia el JSON
                }
                salida.put(c.getClave(), normalizado);
            }
        }

        // Datos de campos que la plantilla ya no define: se conservan tal cual, no se pierden.
        previos.forEach((k, v) -> {
            if (!clavesDeLaPlantilla.contains(k) && !salida.containsKey(k)) salida.put(k, v);
        });
        return salida;
    }

    private Object normalizar(HcCampo campo, Object valor) {
        if (esVacio(valor)) return null;
        String tipo = campo.getTipo();
        String texto = valor instanceof String s ? s.trim() : String.valueOf(valor);

        switch (tipo) {
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
