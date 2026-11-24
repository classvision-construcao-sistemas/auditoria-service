package br.ifg.urutai.auditoriaservice.service;

import br.ifg.urutai.auditoriaservice.client.ReconhecimentoFacialClient;
import br.ifg.urutai.auditoriaservice.client.TurmaClient;
import br.ifg.urutai.auditoriaservice.dto.AuditoriaResposta;
import br.ifg.urutai.auditoriaservice.dto.ConfirmacaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.CorrecaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.PreviaResposta;
import br.ifg.urutai.auditoriaservice.dto.PreviaResposta.AlunoPrevia;
import br.ifg.urutai.auditoriaservice.dto.ResultadoRequisicao;
import br.ifg.urutai.auditoriaservice.exception.RecursoNaoEncontradoException;
import br.ifg.urutai.auditoriaservice.exception.RegraNegocioException;
import br.ifg.urutai.auditoriaservice.model.Auditoria;
import br.ifg.urutai.auditoriaservice.model.CorrecaoAuditoria;
import br.ifg.urutai.auditoriaservice.model.ResultadoAuditoria;
import br.ifg.urutai.auditoriaservice.repository.AuditoriaRepository;
import br.ifg.urutai.auditoriaservice.security.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuditoriaService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final AuditoriaRepository repositorio;
    private final TurmaClient turmas;
    private final ReconhecimentoFacialClient reconhecimento;
    private final UsuarioAutenticado usuario;

    public AuditoriaService(AuditoriaRepository repositorio, TurmaClient turmas,
                            ReconhecimentoFacialClient reconhecimento, UsuarioAutenticado usuario) {
        this.repositorio = repositorio;
        this.turmas = turmas;
        this.reconhecimento = reconhecimento;
        this.usuario = usuario;
    }

    /** UC-AUD-01 a UC-AUD-03: reconhece a foto e monta a lista para revisão. A foto só existe durante a requisição. */
    public PreviaResposta previa(Long turmaId, MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new RegraNegocioException("Envie a foto da sala");
        }
        Set<Long> ativos = alunosAtivos(turmas.buscar(turmaId, usuario.token()));
        if (ativos.isEmpty()) {
            throw new RegraNegocioException("A turma não tem alunos ativos");
        }

        ReconhecimentoFacialClient.Resultado resultado = reconhecimento.auditarSala(foto, usuario.token());

        Map<Long, Double> reconhecidos = new HashMap<>();
        int foraDaTurma = 0;
        for (ReconhecimentoFacialClient.Reconhecido r : resultado.reconhecidos()) {
            Long alunoId = matriculaParaAlunoId(r.matricula());
            if (alunoId != null && ativos.contains(alunoId)) {
                reconhecidos.putIfAbsent(alunoId, r.confianca());
            } else {
                foraDaTurma++;
            }
        }

        List<AlunoPrevia> alunos = ativos.stream().sorted()
                .map(id -> reconhecidos.containsKey(id)
                        ? new AlunoPrevia(id, ResultadoAuditoria.PRESENTE, ResultadoAuditoria.RECONHECIDA, reconhecidos.get(id))
                        : new AlunoPrevia(id, ResultadoAuditoria.POSSIVEL_FALTA, ResultadoAuditoria.NAO_RECONHECIDA, null))
                .toList();

        LocalDateTime agora = LocalDateTime.now(FUSO);
        return new PreviaResposta(turmaId, agora.toLocalDate(), agora.toLocalTime().withNano(0), alunos,
                resultado.naoReconhecidos(), foraDaTurma, resultado.totalFaces());
    }

    /** UC-AUD-03: grava a auditoria revisada pelo coordenador, em lote. */
    @Transactional
    public AuditoriaResposta confirmar(ConfirmacaoRequisicao requisicao) {
        validarAlunos(alunosAtivos(turmas.buscar(requisicao.turmaId(), usuario.token())), requisicao.resultados());

        LocalDateTime agora = LocalDateTime.now(FUSO);
        Auditoria auditoria = new Auditoria();
        auditoria.setTurmaId(requisicao.turmaId());
        auditoria.setUsuarioId(usuario.id());
        auditoria.setData(requisicao.data() != null ? requisicao.data() : agora.toLocalDate());
        auditoria.setHorario(requisicao.horario() != null ? requisicao.horario() : agora.toLocalTime().withNano(0));
        auditoria.setSituacao(Auditoria.CONFIRMADA);
        requisicao.resultados().forEach(r ->
                auditoria.adicionarResultado(new ResultadoAuditoria(r.alunoId(), r.situacaoPresenca(), r.situacaoFace())));

        return AuditoriaResposta.de(repositorio.save(auditoria));
    }

    /** UC-HIS-01: histórico com filtros opcionais. */
    @Transactional(readOnly = true)
    public List<AuditoriaResposta> listar(Long cursoId, Long turmaId, Long usuarioId, LocalDate inicio, LocalDate fim) {
        List<Long> turmaIds = null;
        if (turmaId != null) {
            turmaIds = List.of(turmaId);
        } else if (cursoId != null) {
            turmaIds = turmas.listarPorCurso(cursoId, usuario.token()).stream().map(TurmaClient.Turma::id).toList();
            if (turmaIds.isEmpty()) {
                return List.of();
            }
        }

        List<Long> ids = turmaIds;
        Specification<Auditoria> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            if (ids != null) condicoes.add(raiz.get("turmaId").in(ids));
            if (usuarioId != null) condicoes.add(cb.equal(raiz.get("usuarioId"), usuarioId));
            if (inicio != null) condicoes.add(cb.greaterThanOrEqualTo(raiz.<LocalDate>get("data"), inicio));
            if (fim != null) condicoes.add(cb.lessThanOrEqualTo(raiz.<LocalDate>get("data"), fim));
            return cb.and(condicoes.toArray(Predicate[]::new));
        };

        return repositorio.findAll(filtro, Sort.by(Sort.Direction.DESC, "data", "horario"))
                .stream().map(AuditoriaResposta::de).toList();
    }

    @Transactional(readOnly = true)
    public AuditoriaResposta buscar(Long id) {
        return AuditoriaResposta.de(carregar(id));
    }

    /** UC-HIS-02: altera a presença de alunos já auditados e registra quem corrigiu, quando e por quê. */
    @Transactional
    public AuditoriaResposta corrigir(Long id, CorrecaoRequisicao requisicao) {
        Auditoria auditoria = carregar(id);
        Map<Long, ResultadoAuditoria> porAluno = auditoria.getResultados().stream()
                .collect(Collectors.toMap(ResultadoAuditoria::getAlunoId, Function.identity()));
        validarAlunos(porAluno.keySet(), requisicao.resultados());

        requisicao.resultados().forEach(r -> porAluno.get(r.alunoId()).setSituacaoPresenca(r.situacaoPresenca()));
        auditoria.adicionarCorrecao(new CorrecaoAuditoria(usuario.id(), LocalDateTime.now(FUSO).withNano(0),
                requisicao.justificativa().trim()));
        auditoria.setSituacao(Auditoria.CORRIGIDA);

        return AuditoriaResposta.de(repositorio.saveAndFlush(auditoria));
    }

    private Auditoria carregar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Auditoria não encontrada"));
    }

    /** RN-06: aluno com vínculo inativo fica fora da auditoria. */
    private static Set<Long> alunosAtivos(TurmaClient.Turma turma) {
        if (turma.alunos() == null) {
            return Set.of();
        }
        return turma.alunos().stream().filter(TurmaClient.Vinculo::ativo)
                .map(TurmaClient.Vinculo::alunoId).collect(Collectors.toSet());
    }

    private static void validarAlunos(Set<Long> permitidos, List<ResultadoRequisicao> resultados) {
        Set<Long> vistos = new HashSet<>();
        for (ResultadoRequisicao r : resultados) {
            if (!permitidos.contains(r.alunoId())) {
                throw new RegraNegocioException("Aluno " + r.alunoId() + " não faz parte desta auditoria/turma");
            }
            if (!vistos.add(r.alunoId())) {
                throw new RegraNegocioException("Aluno " + r.alunoId() + " repetido na lista");
            }
        }
    }

    // shortcut: aluno_id = matrícula numérica, porque nenhum serviço faz o de-para matrícula -> aluno; trocar por consulta quando existir.
    static Long matriculaParaAlunoId(String matricula) {
        try {
            return Long.valueOf(matricula.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
