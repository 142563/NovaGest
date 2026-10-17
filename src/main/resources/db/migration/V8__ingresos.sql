-- Tarjeta 19: ingresos propios del ciudadano (borrador para revision del DBA). Version 8: la 7 la usa pagos (tarjeta 18).
-- Dato personal: solo el dueno lo ve y lo modifica. Borrado logico (eliminado_en).
-- Los permisos ingreso:leer/crear/editar/eliminar ya existen desde V2 y solo los tiene el rol CIUDADANO.

CREATE TABLE ingreso (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     BIGINT         NOT NULL REFERENCES usuario (id),
    fecha          DATE           NOT NULL,
    monto_gtq      NUMERIC(14, 2) NOT NULL,
    descripcion    VARCHAR(200),
    creado_en      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ    NOT NULL DEFAULT now(),
    eliminado_en   TIMESTAMPTZ,
    CONSTRAINT ck_ingreso_monto_positivo CHECK (monto_gtq > 0)
);

-- Listado del dueno ordenado por fecha, sin los eliminados
CREATE INDEX ix_ingreso_usuario_fecha ON ingreso (usuario_id, fecha) WHERE eliminado_en IS NULL;
