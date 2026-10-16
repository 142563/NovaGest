-- Tarjeta 8: tokens de renovacion (borrador para revision del equipo de DBA, tarjeta P2).
-- El token de renovacion es un valor aleatorio opaco que se entrega una sola vez al cliente.
-- En la base solo se guarda su hash SHA-256 (64 caracteres hexadecimales), nunca el valor.
-- La rotacion en cada uso, el logout y la revocacion en cadena se implementan en la tarjeta 10.

CREATE TABLE token_renovacion (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario (id),
    hash        VARCHAR(64) NOT NULL UNIQUE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    vence_en    TIMESTAMPTZ NOT NULL,
    usado_en    TIMESTAMPTZ,
    revocado_en TIMESTAMPTZ
);

CREATE INDEX ix_token_renovacion_usuario ON token_renovacion (usuario_id);
