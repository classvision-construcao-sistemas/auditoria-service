# Class Vision — Auditoria Service

API REST Spring Boot 4 / Java 21 da **Auditoria de Presença** (UC-AUD-01 a 03, UC-HIS-01 e 02): o coordenador envia a foto da sala, o serviço consulta o reconhecimento facial, devolve a lista para revisão e grava a auditoria confirmada, o histórico e as correções.

Diagrama de sequência: [`docs/diagrama-sequencia-auditoria.png`](docs/diagrama-sequencia-auditoria.png) (fonte PlantUML ao lado).

## Execução

1. Tabelas (`ddl-auto=none`): `cv07_auditorias`, `cv08_resultados_auditoria` e `cv10_correcoes_auditoria`. No MySQL do Railway já estão criadas (migração [`docs/migracoes/001-aluno-id-e-correcoes.sql`](docs/migracoes/001-aluno-id-e-correcoes.sql) aplicada em 16/11/2026). Para um banco novo, rode [`docs/ddl-auditoria.sql`](docs/ddl-auditoria.sql).
2. Defina as variáveis e suba:

| Variável | Uso |
|---|---|
| `DB_HOST`, `DB_PORT` (3306), `DB_NAME`, `DB_USER`, `DB_PASSWORD` | MySQL |
| `JWT_SECRET` | Mesmo segredo do cv-authentication-service |
| `TURMA_SERVICE_URL` | Base do turma-service (padrão `http://localhost:8081`) |
| `RECONHECIMENTO_SERVICE_URL` | Base do reconhecimento-facial-service (padrão `http://localhost:5000`) |
| `PORT` | Porta HTTP (padrão 8080; o Railway define sozinho) |

```bash
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Endpoints

Todos exigem `Authorization: Bearer <JWT>`. Escrita só para `COORDENADOR` (RN-01) ou `ADMINISTRADOR`; leitura para qualquer usuário autenticado.

| Método | Rota | Função |
|---|---|---|
| POST | `/auditorias/previa` (multipart: `turmaId`, `foto`) | Reconhece a foto e devolve a lista Presente / Possível falta. Não grava nada. |
| POST | `/auditorias` | Confirma a auditoria revisada (grava auditoria + resultados) |
| GET | `/auditorias?cursoId&turmaId&usuarioId&inicio&fim` | Histórico (datas `yyyy-MM-dd`) |
| GET | `/auditorias/{id}` | Detalhe com resultados e correções |
| POST | `/auditorias/{id}/correcoes` | Corrige presença com justificativa obrigatória (RSEG-06) |

### Exemplos

Prévia:
```bash
curl -H "Authorization: Bearer $TOKEN" -F turmaId=1 -F foto=@sala.jpg http://localhost:8080/auditorias/previa
```
```json
{"turmaId":1,"data":"2026-11-16","horario":"16:11:44",
 "alunos":[{"alunoId":2024020,"situacaoPresenca":"PRESENTE","situacaoFace":"RECONHECIDA","confianca":0.93},
           {"alunoId":2024021,"situacaoPresenca":"POSSIVEL_FALTA","situacaoFace":null,"confianca":null}],
 "facesNaoIdentificadas":1,"facesForaDaTurma":1,"totalFaces":3}
```

Confirmar (`data` e `horario` opcionais; sem eles vale a hora do servidor):
```json
{"turmaId":1,"resultados":[{"alunoId":2024020,"situacaoPresenca":"PRESENTE","situacaoFace":"RECONHECIDA"},
                           {"alunoId":2024021,"situacaoPresenca":"POSSIVEL_FALTA"}]}
```

Corrigir:
```json
{"justificativa":"Aluno chegou atrasado","resultados":[{"alunoId":2024021,"situacaoPresenca":"PRESENTE"}]}
```

## Regras implementadas

- JWT validado com `JWT_SECRET`; autoridades lidas da claim `roles`; `usuario_id` vem da claim `id`.
- Só alunos com vínculo `ATIVO` na turma entram (RN-06); aluno fora da turma ou repetido é recusado.
- A foto nunca é gravada: existe só durante a requisição (RSEG-04).
- Várias auditorias por turma no mesmo dia são permitidas (RN-05).
- Falha no turma-service ou no reconhecimento devolve 502 com mensagem, para o app oferecer "tentar de novo" (RF-AUD-05).

## Pendências conhecidas

- **Matrícula x aluno_id**: o reconhecimento devolve a matrícula e a turma guarda `alunoId`. Até existir um serviço de alunos, o `alunoId` vinculado na turma deve ser a própria matrícula numérica.
- **Faces não identificadas**: `auditar-sala` devolve só a contagem, sem recorte da face; associar uma face específica a um aluno fica para quando o reconhecimento devolver isso. Hoje o coordenador marca o aluno como Presente manualmente.
- **Turma inativa (RN-04)**: a turma não tem campo de situação; o bloqueio depende do turma-service expor isso.
- **Instituição**: o histórico não filtra por `instituicaoId` (mesmo comportamento do turma-service).

## Testes

```bash
./mvnw test
```
