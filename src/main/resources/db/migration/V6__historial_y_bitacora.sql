-- Tarjeta 16: historial de estados de cada tramite y bitacora base (borrador para revision del DBA, tarjeta P2).
-- Las dos tablas son de solo insercion: un trigger bloquea UPDATE, DELETE y TRUNCATE para cualquier usuario,
-- incluido el de la aplicacion. Los permisos del usuario de base de datos son responsabilidad del equipo de DBA.
-- La bitacora completa (todas las acciones y su consulta) llega con la tarjeta 23.

CREATE FUNCTION bloquear_modificacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo insercion: no admite % ', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TABLE tramite_historial (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tramite_id      BIGINT      NOT NULL REFERENCES tramite (id),
    estado_anterior VARCHAR(20),
    estado_nuevo    VARCHAR(20) NOT NULL,
    actor_id        BIGINT      NOT NULL REFERENCES usuario (id),
    comentario      VARCHAR(500),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_historial_anterior CHECK (estado_anterior IS NULL OR estado_anterior IN
        ('BORRADOR', 'ENVIADO', 'EN_REVISION', 'OBSERVADO', 'POR_PAGAR', 'FINALIZADO', 'RECHAZADO')),
    CONSTRAINT ck_historial_nuevo CHECK (estado_nuevo IN
        ('BORRADOR', 'ENVIADO', 'EN_REVISION', 'OBSERVADO', 'POR_PAGAR', 'FINALIZADO', 'RECHAZADO'))
);

CREATE INDEX ix_historial_tramite ON tramite_historial (tramite_id, id);

CREATE TRIGGER tg_historial_inmutable BEFORE UPDATE OR DELETE ON tramite_historial
    FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion();
CREATE TRIGGER tg_historial_sin_truncate BEFORE TRUNCATE ON tramite_historial
    FOR EACH STATEMENT EXECUTE FUNCTION bloquear_modificacion();

CREATE TABLE bitacora (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id   BIGINT REFERENCES usuario (id),
    accion     VARCHAR(50) NOT NULL,
    recurso    VARCHAR(50) NOT NULL,
    recurso_id BIGINT,
    antes      JSONB,
    despues    JSONB,
    ip         VARCHAR(45),
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_bitacora_recurso ON bitacora (recurso, recurso_id);
CREATE INDEX ix_bitacora_actor ON bitacora (actor_id);
CREATE INDEX ix_bitacora_fecha ON bitacora (creado_en);

CREATE TRIGGER tg_bitacora_inmutable BEFORE UPDATE OR DELETE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION bloquear_modificacion();
CREATE TRIGGER tg_bitacora_sin_truncate BEFORE TRUNCATE ON bitacora
    FOR EACH STATEMENT EXECUTE FUNCTION bloquear_modificacion();
