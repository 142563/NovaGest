-- Tarjeta 18: pagos con comprobante (borrador para revision del equipo de DBA, tarjeta P2).
-- No se cobra dinero en la aplicacion: un pago es un registro con un comprobante adjunto (PDF, JPG o PNG)
-- que un gestor valida o rechaza. El archivo vive en disco con un nombre generado por el servidor; en la base
-- quedan su nombre, su hash SHA-256, su tipo y su tamano.

CREATE TABLE pago (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tramite_id          BIGINT         NOT NULL REFERENCES tramite (id),
    ciudadano_id        BIGINT         NOT NULL REFERENCES usuario (id),
    monto               NUMERIC(14, 2) NOT NULL CHECK (monto >= 0),
    estado              VARCHAR(10)    NOT NULL DEFAULT 'PENDIENTE',
    comprobante_archivo VARCHAR(100)   NOT NULL,
    comprobante_hash    VARCHAR(64)    NOT NULL,
    comprobante_tipo    VARCHAR(30)    NOT NULL,
    comprobante_tamano  BIGINT         NOT NULL CHECK (comprobante_tamano > 0),
    creado_por          BIGINT         NOT NULL REFERENCES usuario (id),
    validado_por        BIGINT REFERENCES usuario (id),
    validado_en         TIMESTAMPTZ,
    comentario          VARCHAR(500),
    creado_en           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_pago_estado CHECK (estado IN ('PENDIENTE', 'VALIDADO', 'RECHAZADO')),
    -- Un pago resuelto dice quien lo resolvio
    CONSTRAINT ck_pago_resuelto CHECK (estado = 'PENDIENTE' OR validado_por IS NOT NULL),
    -- Segregacion de funciones (control C-04): quien registra el pago no lo valida ni lo rechaza
    CONSTRAINT ck_pago_segregacion CHECK (validado_por IS NULL OR validado_por <> creado_por)
);

-- Solo un pago pendiente por tramite, y solo uno validado
CREATE UNIQUE INDEX uq_pago_pendiente_por_tramite ON pago (tramite_id) WHERE estado = 'PENDIENTE';
CREATE UNIQUE INDEX uq_pago_validado_por_tramite ON pago (tramite_id) WHERE estado = 'VALIDADO';
CREATE INDEX ix_pago_tramite ON pago (tramite_id);
CREATE INDEX ix_pago_ciudadano ON pago (ciudadano_id);
CREATE INDEX ix_pago_estado ON pago (estado);
