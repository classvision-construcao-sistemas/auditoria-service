-- Aplicada no MySQL do Railway em 16/11/2026.
-- cv08_resultados_auditoria foi criada sem aluno_id (o Dicionário de Dados 2.9 pede) e
-- não existia tabela de correção (2.10). A cv08 estava vazia quando a coluna foi adicionada.

ALTER TABLE cv08_resultados_auditoria
    ADD COLUMN aluno_id BIGINT NOT NULL,
    ADD CONSTRAINT uk_cv08_resultados_auditoria_aluno UNIQUE (bigint_auditoria_id, aluno_id);

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
