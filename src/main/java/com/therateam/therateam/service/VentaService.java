package com.therateam.therateam.service;

import com.therateam.therateam.dto.VentaResumenDTO;
import com.therateam.therateam.model.Producto;
import com.therateam.therateam.model.VentaItem;
import com.therateam.therateam.repository.ProductoRepository;
import com.therateam.therateam.repository.VentaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Venta de productos del catálogo. La plata la sigue registrando PagoService como un cobro
 * adicional; acá solo vive lo que ese pago no sabe: qué producto, cuántos, a qué precio, y el
 * descuento de stock.
 */
@Service
@RequiredArgsConstructor
public class VentaService {

    private final ProductoRepository productoRepository;
    private final VentaItemRepository ventaItemRepository;

    /**
     * Convierte lo que llegó del front ({productoId, cantidad}) en líneas listas para guardar,
     * con el nombre y el precio tomados del catálogo — nunca del request. Si el precio viniera
     * del cliente, cualquiera podría venderse una pelota a S/ 1 editando la llamada.
     *
     * Valida el stock acá, ANTES de crear el pago: así una venta sin stock no deja un pago
     * huérfano registrado.
     */
    public List<VentaItem> preparar(List<VentaItem> solicitados) {
        if (solicitados == null || solicitados.isEmpty()) return List.of();

        // Dos líneas del mismo producto se acumulan: si no, cada una validaría el stock por
        // separado y entre las dos podrían llevarse más unidades de las que hay.
        Map<Long, Integer> cantidadPorProducto = new LinkedHashMap<>();
        for (VentaItem it : solicitados) {
            if (it.getProductoId() == null) continue;
            int cantidad = it.getCantidad() != null ? it.getCantidad() : 0;
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad de un producto debe ser mayor a cero.");
            }
            cantidadPorProducto.merge(it.getProductoId(), cantidad, Integer::sum);
        }

        List<VentaItem> preparados = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : cantidadPorProducto.entrySet()) {
            Producto producto = productoRepository.findById(e.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + e.getKey()));
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new IllegalArgumentException("El producto \"" + producto.getNombre() + "\" está desactivado.");
            }
            int cantidad = e.getValue();
            int disponible = producto.getStock() != null ? producto.getStock() : 0;
            if (cantidad > disponible) {
                throw new IllegalArgumentException("Stock insuficiente de \"" + producto.getNombre()
                        + "\": quedan " + disponible + " y se intentan vender " + cantidad
                        + ". Repón el stock desde el catálogo de productos.");
            }

            BigDecimal precio = producto.getPrecio() != null ? producto.getPrecio() : BigDecimal.ZERO;
            VentaItem item = new VentaItem();
            item.setProductoId(producto.getId());
            item.setNombreProducto(producto.getNombre());
            item.setCantidad(cantidad);
            item.setPrecioUnitario(precio);
            item.setSubtotal(precio.multiply(BigDecimal.valueOf(cantidad)));
            preparados.add(item);
        }
        return preparados;
    }

    public BigDecimal total(List<VentaItem> items) {
        return items.stream()
                .map(VentaItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Concepto legible para el pago y para el cierre de caja: "2x Pelota, 1x Theraband". */
    public String describir(List<VentaItem> items) {
        return items.stream()
                .map(i -> i.getCantidad() + "x " + i.getNombreProducto())
                .collect(Collectors.joining(", "));
    }

    /**
     * Guarda las líneas contra el pago ya persistido y descuenta el stock. Se llama DESPUÉS de
     * salvar el Pago: antes, el pagoId no existe todavía.
     */
    @Transactional
    public void confirmar(Long pagoId, List<VentaItem> items) {
        for (VentaItem item : items) {
            item.setPagoId(pagoId);
            ventaItemRepository.save(item);
            productoRepository.findById(item.getProductoId()).ifPresent(producto -> {
                int actual = producto.getStock() != null ? producto.getStock() : 0;
                producto.setStock(Math.max(0, actual - item.getCantidad()));
                productoRepository.save(producto);
            });
        }
    }

    /**
     * Devuelve las unidades al stock cuando el pago de una venta se elimina o se devuelve —
     * el producto volvió físicamente al estante, el contador debe reflejarlo.
     */
    @Transactional
    public void devolverStock(Long pagoId) {
        for (VentaItem item : ventaItemRepository.findByPagoId(pagoId)) {
            productoRepository.findById(item.getProductoId()).ifPresent(producto -> {
                int actual = producto.getStock() != null ? producto.getStock() : 0;
                producto.setStock(actual + (item.getCantidad() != null ? item.getCantidad() : 0));
                productoRepository.save(producto);
            });
        }
    }

    public List<VentaItem> itemsDe(Long pagoId) { return ventaItemRepository.findByPagoId(pagoId); }

    /** Sin fechas devuelve el historico completo: la consulta exige un rango, asi que se abre al maximo. */
    public List<VentaResumenDTO> resumen(LocalDateTime desde, LocalDateTime hasta) {
        return ventaItemRepository.resumenPorProducto(
                desde != null ? desde : LocalDateTime.of(1900, 1, 1, 0, 0),
                hasta != null ? hasta : LocalDateTime.of(2999, 1, 1, 0, 0));
    }
}
