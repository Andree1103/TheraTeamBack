package com.therateam.therateam.service;

import com.therateam.therateam.dto.AtencionClinicaRequest;
import com.therateam.therateam.model.*;
import com.therateam.therateam.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AtencionClinicaService {

    private final AtencionClinicaRepository repository;
    private final AtencionMetricaRepository metricaRepository;
    private final CitaRepository citaRepository;
    private final SesionRepository sesionRepository;
    private final TratamientoRepository tratamientoRepository;
    private final CatEstadoSesionRepository catEstadoSesionRepository;
    private final CatEstadoCitaRepository catEstadoCitaRepository;

    public List<AtencionClinica> findAll() { return repository.findAll(); }

    public Optional<AtencionClinica> findById(Long id) { return repository.findById(id); }

    public Optional<AtencionClinica> findByCita(Long citaId) {
        return repository.findByCitaId(citaId);
    }

    /**
     * Registra o actualiza la atención de una cita (upsert por cita_id):
     *   - Si es nueva: guarda atencion + métricas, actualiza sesion→ATENDIDA,
     *     tratamiento.sesionesAtendidas++, cita→ASISTIDA.
     *   - Si ya existe: actualiza atencion + reemplaza métricas.
     *     No vuelve a incrementar sesionesAtendidas.
     */
    @Transactional
    public AtencionClinica registrar(AtencionClinicaRequest req) {
        Cita cita = citaRepository.findById(req.getCitaId())
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada: " + req.getCitaId()));

        boolean esNueva = false;
        AtencionClinica atencion = repository.findByCitaId(req.getCitaId()).orElse(null);

        if (atencion == null) {
            atencion = new AtencionClinica();
            atencion.setCita(cita);
            esNueva = true;
        }

        atencion.setFechaInicioReal(req.getFechaInicioReal());
        atencion.setNotasPost(req.getNotasPost());
        atencion = repository.save(atencion);

        // Reemplazar métricas
        metricaRepository.deleteByAtencionId(atencion.getId());
        if (req.getMetricas() != null) {
            for (AtencionClinicaRequest.MetricaInput mi : req.getMetricas()) {
                AtencionMetrica m = new AtencionMetrica();
                m.setAtencion(atencion);
                m.setMetrica(mi.getMetrica());
                m.setValor(mi.getValor());
                m.setUnidad(mi.getUnidad());
                metricaRepository.save(m);
            }
        }

        // Solo primera vez: actualizar estados y contador
        if (esNueva) {
            Sesion sesion = cita.getSesion();
            if (sesion != null) {
                catEstadoSesionRepository.findByKey("ATENDIDA").ifPresent(est -> {
                    sesion.setEstado(est);
                    sesionRepository.save(sesion);
                });

                Tratamiento tratamiento = sesion.getTratamiento();
                if (tratamiento != null) {
                    int actual = tratamiento.getSesionesAtendidas() != null ? tratamiento.getSesionesAtendidas() : 0;
                    tratamiento.setSesionesAtendidas(actual + 1);
                    tratamientoRepository.save(tratamiento);
                }
            }

            catEstadoCitaRepository.findByKey("ASISTIDA").ifPresent(est -> {
                cita.setEstado(est);
                citaRepository.save(cita);
            });
        }

        return atencion;
    }

    public AtencionClinica save(AtencionClinica atencion) { return repository.save(atencion); }

    public Optional<AtencionClinica> update(Long id, AtencionClinica data) {
        return repository.findById(id).map(existing -> {
            existing.setCita(data.getCita());
            existing.setFechaInicioReal(data.getFechaInicioReal());
            existing.setFechaFinReal(data.getFechaFinReal());
            existing.setDuracionRealMin(data.getDuracionRealMin());
            existing.setNotasPost(data.getNotasPost());
            existing.setArchivosUrl(data.getArchivosUrl());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
