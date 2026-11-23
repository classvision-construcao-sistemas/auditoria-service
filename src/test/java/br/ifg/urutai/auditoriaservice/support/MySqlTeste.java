package br.ifg.urutai.auditoriaservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

// Sobe um MySQL no Docker com as tabelas do projeto (docs/ddl-auditoria.sql).
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTeste {

    private static final String INIT = "/docker-entrypoint-initdb.d/";

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/00-dependencias.sql"), INIT + "00-dependencias.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("docs/ddl-auditoria.sql"), INIT + "01-ddl-auditoria.sql");

    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return MYSQL;
    }
}
