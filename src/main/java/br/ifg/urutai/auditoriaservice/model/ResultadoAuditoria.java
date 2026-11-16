package br.ifg.urutai.auditoriaservice.model;

import jakarta.persistence.*;

/** Dicionário de Dados 2.9: situação de um aluno numa auditoria. */
@Entity
@Table(name = "cv07_resultados_auditoria")
public class ResultadoAuditoria {

    public static final String PRESENTE = "PRESENTE";
    public static final String POSSIVEL_FALTA = "POSSIVEL_FALTA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "situacao_presenca", nullable = false, length = 20)
    private String situacaoPresenca;

    @Column(name = "situacao_face", length = 20)
    private String situacaoFace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bigint_auditoria_id", nullable = false)
    private Auditoria auditoria;

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    public ResultadoAuditoria() {
    }

    public ResultadoAuditoria(Long alunoId, String situacaoPresenca, String situacaoFace) {
        this.alunoId = alunoId;
        this.situacaoPresenca = situacaoPresenca;
        this.situacaoFace = situacaoFace;
    }

    public Long getId() { return id; }
    public String getSituacaoPresenca() { return situacaoPresenca; }
    public void setSituacaoPresenca(String situacaoPresenca) { this.situacaoPresenca = situacaoPresenca; }
    public String getSituacaoFace() { return situacaoFace; }
    public void setSituacaoFace(String situacaoFace) { this.situacaoFace = situacaoFace; }
    public Auditoria getAuditoria() { return auditoria; }
    public void setAuditoria(Auditoria auditoria) { this.auditoria = auditoria; }
    public Long getAlunoId() { return alunoId; }
    public void setAlunoId(Long alunoId) { this.alunoId = alunoId; }
}
