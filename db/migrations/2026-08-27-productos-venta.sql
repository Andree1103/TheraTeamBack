-- Venta de productos (pelotas, therabands, bajalenguas, cajas de material...).
--
-- La plata sigue registrandose como un Pago con es_adicional = true: eso ya significa
-- "ingreso que no cubre ninguna deuda ni genera saldo a favor", que es exactamente lo que
-- es una venta, y hace que caiga sola en el cierre de caja sin tocar CajaService.
--
-- Lo que agregan estas dos tablas es lo que el pago solo no puede dar: precio de lista fijo
-- (hoy el concepto es texto libre y cada quien teclea el suyo) y cantidades, para poder
-- responder "cuantas pelotas vendi este mes" y "cuanto dejo la venta de productos".
--
-- Idempotente: se puede reaplicar sin romper nada.

BEGIN;

CREATE TABLE IF NOT EXISTS productos (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(120)  NOT NULL,
    descripcion         VARCHAR(255),
    precio              NUMERIC(10,2) NOT NULL DEFAULT 0,
    -- Contador simple: baja al vender, se repone a mano desde el catalogo. Una venta que
    -- deje el stock en negativo se rechaza.
    stock               INTEGER       NOT NULL DEFAULT 0,
    -- Un producto descontinuado se desactiva, no se borra: las ventas viejas lo referencian.
    activo              BOOLEAN       NOT NULL DEFAULT true,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    idusuario_creacion  BIGINT
);

CREATE TABLE IF NOT EXISTS venta_items (
    id                  BIGSERIAL PRIMARY KEY,
    pago_id             BIGINT        NOT NULL REFERENCES pagos(id) ON DELETE CASCADE,
    producto_id         BIGINT        NOT NULL REFERENCES productos(id),
    -- Nombre y precio congelados al momento de la venta: si manana suben el precio de la
    -- pelota o le cambian el nombre, la boleta vieja debe seguir diciendo lo que se cobro.
    nombre_producto     VARCHAR(120)  NOT NULL,
    cantidad            INTEGER       NOT NULL,
    precio_unitario     NUMERIC(10,2) NOT NULL,
    subtotal            NUMERIC(10,2) NOT NULL
);

-- Al abrir un pago se listan sus items; el reporte agrupa por producto en un rango de fechas.
CREATE INDEX IF NOT EXISTS idx_venta_items_pago     ON venta_items (pago_id);
CREATE INDEX IF NOT EXISTS idx_venta_items_producto ON venta_items (producto_id);

COMMIT;

-- Verificacion
SELECT table_name, column_name, data_type FROM information_schema.columns
WHERE table_name IN ('productos', 'venta_items') ORDER BY table_name, ordinal_position;
