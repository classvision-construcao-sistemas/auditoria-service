package br.ifg.urutai.auditoriaservice.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Dicionário de Dados 2.8. turma_id e usuario_id apontam para tabelas de outros serviços. */
@Entity
@Table(name = "cv07_auditorias")
public class Auditoria {

    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String CORRIGIDA = "CORRIGIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime horario;

    @Column(nullable = false, length = 30)
    private String situacao;

    @Column(name = "bigint_turma_id", nullable = false)
    private Long turmaId;

    @Column(name = "bigint_usuario_id", nullable = false)
    private Long usuarioId;

    @OneToMany(mappedBy = "auditoria", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultadoAuditoria> resultados = new ArrayList<>();

    @OneToMany(mappedBy = "auditoria", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dtCorrecao")
    private List<CorrecaoAuditoria> correcoes = new ArrayList<>();

    public void adicionarResultado(ResultadoAuditoria resultado) {
        resultado.setAuditoria(this);
        resultados.add(resultado);
    }

    public void adicionarCorrecao(CorrecaoAuditoria correcao) {
        correcao.setAuditoria(this);
        correcoes.add(correcao);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public LocalTime getHorario() { return horario; }
    public void setHorario(LocalTime horario) { this.horario = horario; }
    public String getSituacao() { return situacao; }
    public void setSituacao(String situacao) { this.situacao = situacao; }
    public Long getTurmaId() { return turmaId; }
    public void setTurmaId(Long turmaId) { this.turmaId = turmaId; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public List<ResultadoAuditoria> getResultados() { return resultados; }
    public List<CorrecaoAuditoria> getCorrecoes() { return correcoes; }
}
