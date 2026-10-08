-- El historial de las revisiones del libro.
--
-- El reconciliador ya existia, pero habia que acordarse de ejecutarlo — y un aviso que depende
-- de que alguien se acuerde acaba sin mirarse. Aqui queda el resultado de cada pasada, la deje
-- el proceso de la noche o una persona a mano.
--
-- Guardar el historial y no solo el ultimo resultado importa por dos motivos:
--
--   1. "Llevamos N dias cuadrando" es el dato que decide cuando se puede dar el paso a la
--      fase 3. Sin historia hay que fiarse de la memoria de alguien.
--   2. Cuando aparezca una diferencia, lo primero que se pregunta es desde cuando. Con la
--      cadena de revisiones se acota al dia.
--
-- El detalle va como JSON y no como tabla aparte a proposito: no se consulta por partes, se lee
-- entero cuando algo falla. Una tabla hija seria mas ceremonia para el mismo resultado.

CREATE TABLE reconciliaciones (
  id           BIGSERIAL PRIMARY KEY,
  momento      TIMESTAMP     NOT NULL DEFAULT now(),
  automatica   BOOLEAN       NOT NULL DEFAULT true,
  cuadra       BOOLEAN       NOT NULL,
  diferencias  INTEGER       NOT NULL,
  origenes     NUMERIC(14,2) NOT NULL,
  destinos     NUMERIC(14,2) NOT NULL,
  detalle      TEXT
);

CREATE INDEX ix_reconciliaciones_momento ON reconciliaciones (momento DESC);
-- Para "¿cuando fue la ultima vez que NO cuadro?", que es la consulta que se hara con prisa.
CREATE INDEX ix_reconciliaciones_fallos  ON reconciliaciones (momento DESC) WHERE NOT cuadra;

SELECT 'tabla de reconciliaciones creada' AS resultado;
