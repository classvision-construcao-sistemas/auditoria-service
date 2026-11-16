package br.ifg.urutai.auditoriaservice.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Dicionário de Dados 2.10: log de correção (RSEG-06). Cada correção é um registro novo. */
@Entity
@Table(name = "cv10_correcoes_auditoria")
public class CorrecaoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bigint_usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bigint_auditoria_id", nullable = false)
    private Auditoria auditoria;

    @Column(name = "dt_correcao", nullable = false)
    private LocalDateTime dtCorrecao;

    @Column(nullable = false, length = 255)
    private String justificativa;

    public CorrecaoAuditoria() {
    }

    public CorrecaoAuditoria(Long usuarioId, LocalDateTime dtCorrecao, String justificativa) {
        this.usuarioId = usuarioId;
        this.dtCorrecao = dtCorrecao;
        this.justificativa = justificativa;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Auditoria getAuditoria() { return auditoria; }
    public void setAuditoria(Auditoria auditoria) { this.auditoria = auditoria; }
    public LocalDateTime getDtCorrecao() { return dtCorrecao; }
    public String getJustificativa() { return justificativa; }
}
