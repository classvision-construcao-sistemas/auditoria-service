package br.ifg.urutai.auditoriaservice.model;

import jakarta.persistence.*;

/** Dicionário de Dados 2.9: situação de um aluno numa auditoria. */
@Entity
@Table(name = "cv08_resultados_auditoria")
public class ResultadoAuditoria {

    public static final String PRESENTE = "PRESENTE";
    public static final String POSSIVEL_FALTA = "POSSIVEL_FALTA";
    public static final String RECONHECIDA = "RECONHECIDA";
    public static final String NAO_RECONHECIDA = "NAO_RECONHECIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "situacao_presenca", nullable = false, length = 30)
    private String situacaoPresenca;

    @Column(name = "situacao_face", nullable = false, length = 30)
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
        this.situacaoFace = situacaoFace != null ? situacaoFace : NAO_RECONHECIDA;
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
