-- Auditoria de Presença (Dicionário de Dados 2.8 a 2.10), esquema completo para banco novo.
-- No MySQL do Railway, cv07 e cv08 já existiam: lá se aplica docs/migracoes/001-aluno-id-e-correcoes.sql.
-- Depende de cv01_usuarios (auth) e cv05_turmas (turma). ddl-auto=none.

CREATE TABLE IF NOT EXISTS cv07_auditorias (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    data              DATE        NOT NULL,
    horario           TIME        NOT NULL,
    situacao          VARCHAR(30) NOT NULL,
    bigint_usuario_id BIGINT      NOT NULL,
    bigint_turma_id   BIGINT      DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_cv07_auditorias_usuario (bigint_usuario_id),
    KEY idx_cv07_auditorias_turma (bigint_turma_id),
    CONSTRAINT fk_cv07_auditorias_turma FOREIGN KEY (bigint_turma_id) REFERENCES cv05_turmas (id),
    CONSTRAINT fk_cv07_auditorias_usuario FOREIGN KEY (bigint_usuario_id) REFERENCES cv01_usuarios (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cv08_resultados_auditoria (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    situacao_presenca   VARCHAR(30) NOT NULL,
    situacao_face       VARCHAR(30) NOT NULL,
    bigint_auditoria_id BIGINT      NOT NULL,
    aluno_id            BIGINT      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cv08_resultados_auditoria_auditoria (bigint_auditoria_id),
    CONSTRAINT uk_cv08_resultados_auditoria_aluno UNIQUE (bigint_auditoria_id, aluno_id),
    CONSTRAINT fk_cv08_resultados_auditoria_auditoria FOREIGN KEY (bigint_auditoria_id) REFERENCES cv07_auditorias (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cv10_correcoes_auditoria (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    bigint_usuario_id   BIGINT       NOT NULL,
    bigint_auditoria_id BIGINT       NOT NULL,
    dt_correcao         DATETIME     NOT NULL,
    justificativa       VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cv10_correcoes_auditoria_auditoria (bigint_auditoria_id),
    KEY idx_cv10_correcoes_auditoria_usuario (bigint_usuario_id),
    CONSTRAINT fk_cv10_correcoes_auditoria_auditoria FOREIGN KEY (bigint_auditoria_id) REFERENCES cv07_auditorias (id),
    CONSTRAINT fk_cv10_correcoes_auditoria_usuario FOREIGN KEY (bigint_usuario_id) REFERENCES cv01_usuarios (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
