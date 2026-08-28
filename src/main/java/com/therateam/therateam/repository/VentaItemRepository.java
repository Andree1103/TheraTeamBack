package com.therateam.therateam.repository;

import com.therateam.therateam.dto.VentaResumenDTO;
import com.therateam.therateam.model.VentaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaItemRepository extends JpaRepository<VentaItem, Long> {

    List<VentaItem> findByPagoId(Long pagoId);

    List<VentaItem> findByPagoIdIn(List<Long> pagoIds);

    /**
     * Cuántas unidades y cuánta plata dejó cada producto en un rango — la pregunta que el
     * cobro adicional con concepto libre no podía responder.
     *
     * Se une contra Pago por la fecha porque el item no la lleva: la fecha de la venta es la
     * del pago. Las devoluciones se excluyen: el pago original ya quedó revertido.
     *
     * El rango es obligatorio: con `:desde IS NULL` Postgres no puede inferir el tipo del
     * parámetro y revienta con "could not determine data type". VentaService pone los topes
     * cuando el caller no manda fechas.
     */
    @Query("""
        SELECT new com.therateam.therateam.dto.VentaResumenDTO(
            vi.productoId,
            MAX(vi.nombreProducto),
            SUM(vi.cantidad),
            SUM(vi.subtotal))
        FROM VentaItem vi, Pago pg
        WHERE pg.id = vi.pagoId
          AND pg.esDevolucion = false
          AND pg.fechaPago >= :desde
          AND pg.fechaPago <  :hasta
        GROUP BY vi.productoId
        ORDER BY SUM(vi.subtotal) DESC
    """)
    List<VentaResumenDTO> resumenPorProducto(@Param("desde") LocalDateTime desde,
                                             @Param("hasta") LocalDateTime hasta);
}
