-- Tabelas mínimas de outros serviços que as FKs da auditoria precisam (só para os testes).

CREATE TABLE cv01_usuarios (
    id BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE cv05_turmas (
    id BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

INSERT INTO cv01_usuarios (id) VALUES (7), (8);
INSERT INTO cv05_turmas (id) VALUES (1), (2), (3);
