-- Auditoria de Presença (Dicionário de Dados 2.8 a 2.10).
-- Rodar no mesmo MySQL dos outros serviços (ddl-auto=none).

CREATE TABLE IF NOT EXISTS cv06_auditorias (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    data              DATE         NOT NULL,
    horario           TIME         NOT NULL,
    situacao          VARCHAR(50)  NOT NULL,
    bigint_turma_id   BIGINT       NOT NULL,
    bigint_usuario_id BIGINT       NOT NULL,
    CONSTRAINT pk_cv06_auditorias PRIMARY KEY (id),
    CONSTRAINT ck_cv06_auditorias_situacao CHECK (situacao IN ('CONFIRMADA', 'CORRIGIDA')),
    INDEX ix_cv06_auditorias_turma_data (bigint_turma_id, data)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cv07_resultados_auditoria (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    situacao_presenca   VARCHAR(20) NOT NULL,
    situacao_face       VARCHAR(20) NULL,
    bigint_auditoria_id BIGINT      NOT NULL,
    aluno_id            BIGINT      NOT NULL,
    CONSTRAINT pk_cv07_resultados_auditoria PRIMARY KEY (id),
    CONSTRAINT uk_cv07_resultados_auditoria_aluno UNIQUE (bigint_auditoria_id, aluno_id),
    CONSTRAINT ck_cv07_resultados_auditoria_presenca CHECK (situacao_presenca IN ('PRESENTE', 'POSSIVEL_FALTA')),
    CONSTRAINT fk_cv07_resultados_auditoria_auditoria FOREIGN KEY (bigint_auditoria_id)
        REFERENCES cv06_auditorias (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cv08_correcoes_auditoria (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    bigint_usuario_id   BIGINT       NOT NULL,
    bigint_auditoria_id BIGINT       NOT NULL,
    dt_correcao         DATETIME     NOT NULL,
    justificativa       VARCHAR(255) NOT NULL,
    CONSTRAINT pk_cv08_correcoes_auditoria PRIMARY KEY (id),
    CONSTRAINT fk_cv08_correcoes_auditoria_auditoria FOREIGN KEY (bigint_auditoria_id)
        REFERENCES cv06_auditorias (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- FKs para tabelas de outros serviços. Separadas para que as tabelas acima
-- sejam criadas mesmo que o tipo de cv05_turmas.id ou cv01_usuarios.id seja diferente.
ALTER TABLE cv06_auditorias
    ADD CONSTRAINT fk_cv06_auditorias_turma FOREIGN KEY (bigint_turma_id) REFERENCES cv05_turmas (id),
    ADD CONSTRAINT fk_cv06_auditorias_usuario FOREIGN KEY (bigint_usuario_id) REFERENCES cv01_usuarios (id);

ALTER TABLE cv08_correcoes_auditoria
    ADD CONSTRAINT fk_cv08_correcoes_auditoria_usuario FOREIGN KEY (bigint_usuario_id) REFERENCES cv01_usuarios (id);
