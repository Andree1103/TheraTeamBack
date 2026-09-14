package com.therateam.therateam.service;

import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.HcPlantillaRepository;
import com.therateam.therateam.repository.HistoriaClinicaRepository;
import com.therateam.therateam.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    /** Las reglas de validacion son las mismas que para la atencion: viven en un solo sitio. */
    private final FichaValidator validator;

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
        historia.setDatos(validator.validarYNormalizar(plantilla, datos, previos));
        return repository.save(historia);
    }

}
