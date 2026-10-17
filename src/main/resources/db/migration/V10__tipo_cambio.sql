-- Tarjeta 22: tipos de cambio guardados (borrador para revision del DBA). Version 10: la 8 es ingresos y la 9 pagos.
-- Cache de lo que publica el Banguat: un valor por fecha y moneda (codigo del Banguat).
-- Si el Banguat no responde, la API usa el ultimo valor guardado y lo marca como estimado (RNF-04).
-- valor: tal como lo publica el Banguat (venta); su significado depende de la moneda (ConvencionCotizacion).

CREATE TABLE tipo_cambio (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha     DATE           NOT NULL,
    moneda    INTEGER        NOT NULL,
    valor     NUMERIC(18, 6) NOT NULL,
    fuente    VARCHAR(30)    NOT NULL,
    creado_en TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_tipo_cambio_fecha_moneda UNIQUE (fecha, moneda),
    CONSTRAINT ck_tipo_cambio_valor_positivo CHECK (valor > 0)
);

CREATE INDEX ix_tipo_cambio_moneda_fecha ON tipo_cambio (moneda, fecha);
