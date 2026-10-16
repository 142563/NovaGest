-- Tarjeta 15: tramites, campos que declara cada tipo y alcance de los roles
-- (borrador para revision del equipo de DBA, tarjeta P2).

-- 1) Alcance del rol. El codigo nunca pregunta "es administrador?": pregunta si el rol tiene alcance
--    sobre todos los registros. El CIUDADANO solo ve y toca lo propio; los demas roles ven todo.
ALTER TABLE rol ADD COLUMN alcance_total BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE rol SET alcance_total = TRUE WHERE nombre IN ('GESTOR', 'AUDITOR', 'ADMINISTRADOR');

-- 2) Campos que declara cada tipo de tramite. Los datos propios de cada tramite se validan contra esto.
CREATE TABLE campo_tipo_tramite (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_tramite_id BIGINT         NOT NULL REFERENCES tipo_tramite (id),
    clave           VARCHAR(50)    NOT NULL,
    etiqueta        VARCHAR(100)   NOT NULL,
    tipo_dato       VARCHAR(10)    NOT NULL,
    obligatorio     BOOLEAN        NOT NULL DEFAULT FALSE,
    patron          VARCHAR(200),
    minimo          NUMERIC(14, 2),
    orden           INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT ck_campo_tipo_dato CHECK (tipo_dato IN ('TEXTO', 'NUMERO', 'FECHA', 'BOOLEANO')),
    CONSTRAINT ck_campo_clave CHECK (clave ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT uq_campo_tipo_clave UNIQUE (tipo_tramite_id, clave)
);

-- Campos propuestos para la demostracion (a confirmar con el equipo y el docente).
INSERT INTO campo_tipo_tramite (tipo_tramite_id, clave, etiqueta, tipo_dato, obligatorio, patron, minimo, orden)
SELECT t.id, c.clave, c.etiqueta, c.tipo_dato, c.obligatorio, c.patron, c.minimo, c.orden
FROM tipo_tramite t
JOIN (VALUES
    ('Boleto de ornato', 'dpi', 'DPI (CUI) de 13 digitos', 'TEXTO', TRUE, '^[0-9]{13}$', NULL::numeric, 1),
    ('Boleto de ornato', 'ocupacion', 'Ocupacion', 'TEXTO', TRUE, NULL, NULL, 2),
    ('Boleto de ornato', 'ingreso_mensual', 'Ingreso mensual en quetzales', 'NUMERO', TRUE, NULL, 0, 3),
    ('Boleto de ornato', 'direccion', 'Direccion de residencia', 'TEXTO', FALSE, NULL, NULL, 4),
    ('Pago de IGSS', 'numero_afiliacion', 'Numero de afiliacion', 'TEXTO', TRUE, '^[0-9]{1,13}$', NULL, 1),
    ('Pago de IGSS', 'periodo', 'Periodo que se paga (AAAA-MM)', 'TEXTO', TRUE, '^[0-9]{4}-(0[1-9]|1[0-2])$', NULL, 2),
    ('Pago de IGSS', 'salario', 'Salario mensual en quetzales', 'NUMERO', TRUE, NULL, 0, 3),
    ('Pago de IGSS', 'patrono', 'Patrono', 'TEXTO', FALSE, NULL, NULL, 4)
) AS c (tipo, clave, etiqueta, tipo_dato, obligatorio, patron, minimo, orden) ON c.tipo = t.nombre
WHERE t.eliminado_en IS NULL;

-- 3) Tramites. El estado nunca se edita como un campo mas: se cambia por transiciones (tarjeta 16).
CREATE TABLE tramite (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_tramite_id BIGINT       NOT NULL REFERENCES tipo_tramite (id),
    ciudadano_id    BIGINT       NOT NULL REFERENCES usuario (id),
    gestor_id       BIGINT REFERENCES usuario (id),
    estado          VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
    datos           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    motivo          VARCHAR(500),
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    eliminado_en    TIMESTAMPTZ,
    CONSTRAINT ck_tramite_estado CHECK (estado IN
        ('BORRADOR', 'ENVIADO', 'EN_REVISION', 'OBSERVADO', 'POR_PAGAR', 'FINALIZADO', 'RECHAZADO'))
);

CREATE INDEX ix_tramite_ciudadano ON tramite (ciudadano_id) WHERE eliminado_en IS NULL;
CREATE INDEX ix_tramite_estado ON tramite (estado) WHERE eliminado_en IS NULL;
CREATE INDEX ix_tramite_tipo ON tramite (tipo_tramite_id);
