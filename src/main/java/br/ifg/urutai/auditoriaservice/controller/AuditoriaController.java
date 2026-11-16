package br.ifg.urutai.auditoriaservice.controller;

import br.ifg.urutai.auditoriaservice.dto.AuditoriaResposta;
import br.ifg.urutai.auditoriaservice.dto.ConfirmacaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.CorrecaoRequisicao;
import br.ifg.urutai.auditoriaservice.dto.PreviaResposta;
import br.ifg.urutai.auditoriaservice.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/auditorias")
@Tag(name = "Auditorias", description = "Auditoria de presença por foto da sala")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @PostMapping(value = "/previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Reconhecer a foto da sala e montar a lista para revisão (não grava nada)")
    public PreviaResposta previa(@RequestParam Long turmaId, @RequestPart("foto") MultipartFile foto) {
        return auditoriaService.previa(turmaId, foto);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Confirmar a auditoria revisada")
    public AuditoriaResposta confirmar(@Valid @RequestBody ConfirmacaoRequisicao dados) {
        return auditoriaService.confirmar(dados);
    }

    @GetMapping
    @Operation(summary = "Histórico de auditorias (filtros opcionais; datas em yyyy-MM-dd)")
    public List<AuditoriaResposta> listar(@RequestParam(required = false) Long cursoId,
                                          @RequestParam(required = false) Long turmaId,
                                          @RequestParam(required = false) Long usuarioId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return auditoriaService.listar(cursoId, turmaId, usuarioId, inicio, fim);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da auditoria, com resultados e correções")
    public AuditoriaResposta buscar(@PathVariable Long id) {
        return auditoriaService.buscar(id);
    }

    @PostMapping("/{id}/correcoes")
    @Operation(summary = "Corrigir auditoria confirmada (justificativa obrigatória)")
    public AuditoriaResposta corrigir(@PathVariable Long id, @Valid @RequestBody CorrecaoRequisicao dados) {
        return auditoriaService.corrigir(id, dados);
    }
}
