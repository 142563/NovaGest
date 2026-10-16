-- Tarjeta 14: tipos de tramite (borrador para revision del equipo de DBA, tarjeta P2).
-- Esquema generico: nombre, descripcion y monto. Los datos propios de cada tramite se guardan
-- aparte, en el propio tramite (tarjeta 15). Borrado logico con eliminado_en.

CREATE TABLE tipo_tramite (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre         VARCHAR(100)   NOT NULL,
    descripcion    VARCHAR(255),
    monto          NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (monto >= 0),
    activo         BOOLEAN        NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ    NOT NULL DEFAULT now(),
    eliminado_en   TIMESTAMPTZ
);

-- Un nombre solo puede estar en uso por un tipo no eliminado, sin importar mayusculas.
CREATE UNIQUE INDEX uq_tipo_tramite_nombre_activo ON tipo_tramite (lower(nombre)) WHERE eliminado_en IS NULL;

-- Tipos iniciales de la demostracion. Los montos quedan en 0.00 hasta definirlos con el equipo;
-- un administrador los ajusta con PUT /api/tipos-tramite/{id}.
INSERT INTO tipo_tramite (nombre, descripcion, monto) VALUES
    ('Boleto de ornato', 'Pago del boleto de ornato', 0.00),
    ('Pago de IGSS', 'Pago de cuotas del IGSS', 0.00);
