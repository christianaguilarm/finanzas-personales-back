ALTER TABLE categoria ADD COLUMN clasificacion_gasto VARCHAR(20);

UPDATE categoria
SET clasificacion_gasto = 'NECESIDAD'
WHERE tipo = 'EGRESO' AND parent_id IS NULL;

CREATE SEQUENCE presupuesto_categoria_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE presupuesto_categoria (
    id BIGINT NOT NULL DEFAULT nextval('presupuesto_categoria_id_seq'),
    user_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    monto_mensual NUMERIC(14, 2) NOT NULL,
    CONSTRAINT pk_presupuesto_categoria PRIMARY KEY (id),
    CONSTRAINT uk_presupuesto_categoria_usuario UNIQUE (user_id, categoria_id),
    CONSTRAINT fk_presupuesto_categoria_usuario FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_presupuesto_categoria_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id)
);

ALTER SEQUENCE presupuesto_categoria_id_seq OWNED BY presupuesto_categoria.id;
