package com.therateam.therateam.service;

import com.therateam.therateam.model.CatMetodoPago;
import com.therateam.therateam.repository.CatMetodoPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatMetodoPagoService {

    private final CatMetodoPagoRepository repository;

    /**
     * Los metodos que se pueden ELEGIR al cobrar. "Devolución" no es uno de ellos.
     *
     * Existe como metodo para que las devoluciones queden nombradas y fuera del arqueo, pero lo
     * pone el sistema al anular, no una persona desde el desplegable: elegirlo a mano grabaria
     * un cobro que dice ser una salida. Nada mas crearlo aparecio en el selector de "Registrar
     * pago", al lado de Efectivo.
     */
    public List<CatMetodoPago> findAll() {
        return repository.findAll().stream()
                .filter(m -> !"DEVOLUCION".equalsIgnoreCase(m.getKey() == null ? "" : m.getKey().trim()))
                .toList();
    }

    public Optional<CatMetodoPago> findById(Long id) { return repository.findById(id); }

    public CatMetodoPago save(CatMetodoPago metodo) {
        // Un metodo nuevo es dinero salvo que se diga lo contrario.
        if (metodo.getCuentaEnCaja() == null) metodo.setCuentaEnCaja(true);
        return repository.save(metodo);
    }

    public Optional<CatMetodoPago> update(Long id, CatMetodoPago data) {
        return repository.findById(id).map(existing -> {
            existing.setKey(data.getKey());
            existing.setNombre(data.getNombre());
            existing.setActivo(data.getActivo());
            // Si no viene (cliente viejo), se conserva lo que ya estaba en vez de asumir que si
            // es dinero: un metodo marcado como "no cuenta en caja" no debe volver al arqueo
            // porque alguien renombro el metodo desde una pantalla que no manda el campo.
            if (data.getCuentaEnCaja() != null) existing.setCuentaEnCaja(data.getCuentaEnCaja());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
