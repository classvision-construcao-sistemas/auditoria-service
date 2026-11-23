package br.ifg.urutai.auditoriaservice.repository;

import br.ifg.urutai.auditoriaservice.client.ReconhecimentoFacialClient;
import br.ifg.urutai.auditoriaservice.client.TurmaClient;
import br.ifg.urutai.auditoriaservice.dto.AuditoriaResposta;
import br.ifg.urutai.auditoriaservice.model.Auditoria;
import br.ifg.urutai.auditoriaservice.model.CorrecaoAuditoria;
import br.ifg.urutai.auditoriaservice.model.ResultadoAuditoria;
import br.ifg.urutai.auditoriaservice.security.UsuarioAutenticado;
import br.ifg.urutai.auditoriaservice.service.AuditoriaService;
import br.ifg.urutai.auditoriaservice.support.MySqlTeste;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Testa a gravação e a leitura no MySQL real, com as tabelas do projeto.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTeste.class)
@ActiveProfiles("teste")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers(disabledWithoutDocker = true)
class AuditoriaRepositoryIT {

    @Autowired
    AuditoriaRepository repositorio;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcTemplate jdbc;

    private static Auditoria nova(Long turmaId, Long usuarioId, LocalDate data, LocalTime horario, Long... alunos) {
        Auditoria auditoria = new Auditoria();
        auditoria.setTurmaId(turmaId);
        auditoria.setUsuarioId(usuarioId);
        auditoria.setData(data);
        auditoria.setHorario(horario);
        auditoria.setSituacao(Auditoria.CONFIRMADA);
        for (Long aluno : alunos) {
            auditoria.adicionarResultado(new ResultadoAuditoria(aluno, ResultadoAuditoria.PRESENTE, null));
        }
        return auditoria;
    }

    // Grava, limpa o cache e lê de novo do banco.
    private Auditoria gravarERecarregar(Auditoria auditoria) {
        Long id = repositorio.saveAndFlush(auditoria).getId();
        em.clear();
        return repositorio.findById(id).orElseThrow();
    }

    private AuditoriaService serviceCom(TurmaClient turmas) {
        UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
        when(usuario.token()).thenReturn("t");
        return new AuditoriaService(repositorio, turmas, mock(ReconhecimentoFacialClient.class), usuario);
    }

    @Nested
    class Mapeamento {

        // Grava a auditoria e os resultados nas colunas certas.
        @Test
        void gravaAuditoriaComResultadosNasColunasDoDdl() {
            Auditoria lida = gravarERecarregar(nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 15, 30), 10L, 20L));

            assertNotNull(lida.getId());
            assertEquals(1L, lida.getTurmaId());
            assertEquals(7L, lida.getUsuarioId());
            assertEquals(LocalDate.of(2026, 11, 20), lida.getData());
            assertEquals(LocalTime.of(8, 15, 30), lida.getHorario());
            assertEquals(List.of(10L, 20L), lida.getResultados().stream().map(ResultadoAuditoria::getAlunoId).sorted().toList());

            Map<String, Object> linha = jdbc.queryForMap(
                    "SELECT bigint_turma_id, bigint_usuario_id, situacao FROM cv07_auditorias WHERE id = ?", lida.getId());
            assertEquals(1L, ((Number) linha.get("bigint_turma_id")).longValue());
            assertEquals(7L, ((Number) linha.get("bigint_usuario_id")).longValue());
            assertEquals("CONFIRMADA", linha.get("situacao"));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cv08_resultados_auditoria WHERE bigint_auditoria_id = ? AND situacao_face = 'NAO_RECONHECIDA'",
                    Integer.class, lida.getId()));
        }

        // As correções voltam da mais antiga para a mais nova.
        @Test
        void correcoesVoltamEmOrdemCronologica() {
            Auditoria auditoria = nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L);
            auditoria.adicionarCorrecao(new CorrecaoAuditoria(8L, LocalDateTime.of(2026, 11, 22, 10, 0), "segunda"));
            auditoria.adicionarCorrecao(new CorrecaoAuditoria(7L, LocalDateTime.of(2026, 11, 21, 9, 0), "primeira"));

            Auditoria lida = gravarERecarregar(auditoria);

            assertEquals(List.of("primeira", "segunda"), lida.getCorrecoes().stream().map(CorrecaoAuditoria::getJustificativa).toList());
            assertEquals(LocalDateTime.of(2026, 11, 21, 9, 0), lida.getCorrecoes().getFirst().getDtCorrecao());
        }

        // Mudar a presença de um aluno fica salvo no banco.
        @Test
        void alterarPresencaPersiste() {
            Auditoria lida = gravarERecarregar(nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L));
            lida.getResultados().getFirst().setSituacaoPresenca(ResultadoAuditoria.POSSIVEL_FALTA);
            lida.setSituacao(Auditoria.CORRIGIDA);

            Auditoria relida = gravarERecarregar(lida);

            assertEquals(ResultadoAuditoria.POSSIVEL_FALTA, relida.getResultados().getFirst().getSituacaoPresenca());
            assertEquals(Auditoria.CORRIGIDA, relida.getSituacao());
        }

        // Tirar um resultado da lista apaga ele do banco.
        @Test
        void removerResultadoDaListaApagaDoBanco() {
            Auditoria lida = gravarERecarregar(nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L, 20L));
            lida.getResultados().removeIf(r -> r.getAlunoId().equals(20L));

            Auditoria relida = gravarERecarregar(lida);

            assertEquals(1, relida.getResultados().size());
        }
    }

    @Nested
    class Restricoes {

        // O banco não aceita o mesmo aluno duas vezes na auditoria.
        @Test
        void mesmoAlunoDuasVezesNaAuditoriaFalha() {
            Auditoria auditoria = nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L, 10L);

            assertThrows(DataIntegrityViolationException.class, () -> repositorio.saveAndFlush(auditoria));
        }

        // O banco não aceita turma que não existe.
        @Test
        void turmaInexistenteFalhaPelaFk() {
            assertThrows(DataIntegrityViolationException.class,
                    () -> repositorio.saveAndFlush(nova(999L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L)));
        }

        // O banco não aceita usuário que não existe.
        @Test
        void usuarioInexistenteFalhaPelaFk() {
            assertThrows(DataIntegrityViolationException.class,
                    () -> repositorio.saveAndFlush(nova(1L, 999L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L)));
        }

        // O banco não aceita correção de usuário que não existe.
        @Test
        void correcaoDeUsuarioInexistenteFalhaPelaFk() {
            Auditoria auditoria = nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L);
            auditoria.adicionarCorrecao(new CorrecaoAuditoria(999L, LocalDateTime.now(), "x"));

            assertThrows(DataIntegrityViolationException.class, () -> repositorio.saveAndFlush(auditoria));
        }
    }

    @Nested
    class Historico {

        private final TurmaClient turmas = mock(TurmaClient.class);
        private AuditoriaService service;

        private Long manha;
        private Long tarde;
        private Long outraTurma;
        private Long outroUsuario;
        private Long antiga;

        @BeforeEach
        void popular() {
            service = serviceCom(turmas);
            manha = repositorio.save(nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), 10L)).getId();
            tarde = repositorio.save(nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(14, 0), 10L)).getId();
            outraTurma = repositorio.save(nova(2L, 7L, LocalDate.of(2026, 11, 21), LocalTime.of(8, 0), 10L)).getId();
            outroUsuario = repositorio.save(nova(3L, 8L, LocalDate.of(2026, 11, 22), LocalTime.of(8, 0), 10L)).getId();
            antiga = repositorio.save(nova(1L, 7L, LocalDate.of(2026, 10, 1), LocalTime.of(8, 0), 10L)).getId();
            repositorio.flush();
            em.clear();
        }

        private List<Long> ids(List<AuditoriaResposta> respostas) {
            return respostas.stream().map(AuditoriaResposta::id).toList();
        }

        // Sem filtro, traz tudo da mais recente para a mais antiga.
        @Test
        void semFiltroTrazTudoDoMaisRecenteParaOMaisAntigo() {
            assertEquals(List.of(outroUsuario, outraTurma, tarde, manha, antiga), ids(service.listar(null, null, null, null, null)));
        }

        // Filtra por turma.
        @Test
        void porTurma() {
            assertEquals(List.of(tarde, manha, antiga), ids(service.listar(null, 1L, null, null, null)));
        }

        // Filtra por curso usando as turmas do curso.
        @Test
        void porCursoUsaAsTurmasDoCurso() {
            when(turmas.listarPorCurso(eq(5L), any())).thenReturn(List.of(
                    new TurmaClient.Turma(2L, "B", 5L, null), new TurmaClient.Turma(3L, "C", 5L, null)));

            assertEquals(List.of(outroUsuario, outraTurma), ids(service.listar(5L, null, null, null, null)));
        }

        // Filtra por quem fez a auditoria.
        @Test
        void porUsuario() {
            assertEquals(List.of(outroUsuario), ids(service.listar(null, null, 8L, null, null)));
        }

        // O período inclui o primeiro e o último dia.
        @Test
        void periodoIncluiAsDuasPontas() {
            assertEquals(List.of(outraTurma, tarde, manha),
                    ids(service.listar(null, null, null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21))));
        }

        // Funciona com só a data inicial ou só a data final.
        @Test
        void soInicioOuSoFim() {
            assertEquals(List.of(outroUsuario), ids(service.listar(null, null, null, LocalDate.of(2026, 11, 22), null)));
            assertEquals(List.of(antiga), ids(service.listar(null, null, null, null, LocalDate.of(2026, 10, 31))));
        }

        // Vários filtros juntos.
        @Test
        void filtrosCombinados() {
            assertEquals(List.of(tarde, manha),
                    ids(service.listar(null, 1L, 7L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))));
            assertTrue(service.listar(null, 1L, 8L, null, null).isEmpty());
        }

        // O detalhe traz os resultados e as correções.
        @Test
        void historicoTrazResultadosECorrecoes() {
            AuditoriaResposta resposta = service.buscar(manha);

            assertEquals(1, resposta.resultados().size());
            assertTrue(resposta.correcoes().isEmpty());
        }
    }

    @Nested
    class Volume {

        // Uma auditoria com 1.000 alunos é gravada e lida inteira.
        @Test
        void auditoriaComMilAlunos() {
            Auditoria auditoria = nova(1L, 7L, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0),
                    LongStream.rangeClosed(1, 1_000).boxed().toArray(Long[]::new));

            Auditoria lida = gravarERecarregar(auditoria);

            assertEquals(1_000, lida.getResultados().size());
        }

        // Defeito: o histórico faz uma consulta extra por auditoria. Com 30 auditorias deveriam bastar poucas consultas.
        @Test
        @Tag("defeito-conhecido")
        void historicoNaoFazUmaConsultaPorAuditoria() {
            for (int i = 0; i < 30; i++) {
                Auditoria auditoria = nova(1L, 7L, LocalDate.of(2026, 11, 1).plusDays(i), LocalTime.of(8, 0), 10L, 20L);
                auditoria.adicionarCorrecao(new CorrecaoAuditoria(7L, LocalDateTime.of(2026, 11, 25, 9, 0), "ajuste"));
                repositorio.save(auditoria);
            }
            repositorio.flush();
            em.clear();
            Statistics estatisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            estatisticas.clear();

            List<AuditoriaResposta> historico = serviceCom(mock(TurmaClient.class)).listar(null, 1L, null, null, null);

            assertEquals(30, historico.size());
            long consultas = estatisticas.getPrepareStatementCount();
            assertTrue(consultas <= 5, "o histórico fez " + consultas + " consultas ao banco para 30 auditorias");
        }
    }
}
