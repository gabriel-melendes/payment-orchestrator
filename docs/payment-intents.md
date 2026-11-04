# Cadastro de uma intenção de pagamento

Uma intenção registra um pedido de pagamento. Nesta etapa, nenhum dinheiro é movimentado.

## Contrato HTTP

`POST /api/payment-intents`, com `Content-Type: application/json`:

```json
{"amount":125.50,"currency":"BRL","description":"Pedido 123"}
```

| Campo | Regra |
| --- | --- |
| amount | Obrigatório; de 0.01 a 99999999999999999.99; no máximo 17 dígitos inteiros e duas casas decimais. |
| currency | Obrigatório; exatamente BRL, em maiúsculas. |
| description | Opcional; até 255 caracteres; aceita null ou string vazia. |

Retorno **201 Created**:

```json
{
  "id": "25dd53d1-5c56-4ee9-8ea8-8ce9961ad363",
  "amount": 125.50,
  "currency": "BRL",
  "description": "Pedido 123",
  "status": "CREATED",
  "createdAt": "2026-11-04T14:00:00Z"
}
```

ID, status e data são controlados pelo servidor. UUID é gerado pelo provedor JPA; a data vem do Java como instante UTC. Não envie esses campos na entrada. Campos JSON desconhecidos são ignorados pelo padrão do Spring Boot, sem modificar os campos controlados pelo servidor.

Sem descrição, a resposta contém `description: null`. O JSON pode exibir `125.5` ou `125.50`: ambos representam o mesmo valor. No banco são duas casas decimais.

Campos inválidos retornam **400 Bad Request** com `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Dados invalidos",
  "status": 400,
  "detail": "Verifique os campos informados.",
  "instance": "/api/payment-intents",
  "errors": [{"field":"amount","message":"O valor deve ser no minimo 0.01"}]
}
```

JSON malformado, corpo ausente e tipos incompatíveis também retornam 400, sem detalhes internos de exceções. Falhas de infraestrutura não são tratadas como sucesso ou erro de validação.

## Decisões de arquitetura

Fluxo: **Controller → Service → Repository → PostgreSQL**. O controller recebe JSON e ativa validação. O serviço coordena a criação dentro de uma transação. O repository oferece `save` por Spring Data JPA. DTOs separam o contrato HTTP da entidade persistida, evitando retornar diretamente o mapeamento de banco.

Usamos `BigDecimal`, evitando a imprecisão binária de `double`. A entidade normaliza para duas casas com `RoundingMode.UNNECESSARY`: uma fração de centavo nunca é arredondada silenciosamente. A validação do DTO oferece mensagens amigáveis; o construtor também protege invariantes em chamadas internas.

A coluna é `NUMERIC(19,2)` e o banco possui NOT NULL e CHECKs para valor positivo, BRL e CREATED. Entradas SQL diretas podem ser arredondadas pelo PostgreSQL ao converter para NUMERIC(19,2); a aplicação rejeita casas excedentes antes de persistir.

O status é um enum salvo como texto, evitando dependência da ordem dos valores Java. Apenas CREATED existe. Novos estados exigirão uma nova migração para atualizar o CHECK.

A migração V1 foi preservada; V2 adiciona a tabela. Flyway aplica as migrações antes da validação Hibernate. Reinicie a aplicação antiga para carregar o endpoint e aplicar V2; não apague o volume.

Não há consulta por ID, autenticação, processamento ou idempotência nesta etapa. Cada POST válido cria uma nova intenção, inclusive com conteúdo repetido. Retentativas ainda não são deduplicadas.

## Arquivos e responsabilidades

Caminhos relativos a `src/main/java/com/example/paymentorchestrator/`:

| Arquivo | Responsabilidade |
| --- | --- |
| controller/PaymentIntentController.java | POST e retorno HTTP 201. |
| service/PaymentIntentService.java | Coordenação do cadastro e transação. |
| repository/PaymentIntentRepository.java | Persistência via JpaRepository. |
| repository/package-info.java | Documentação atualizada da camada. |
| entity/PaymentIntent.java | Mapeamento JPA, precisão e invariantes. |
| entity/PaymentIntentStatus.java | Status inicial CREATED. |
| dto/CreatePaymentIntentRequest.java | Entrada e validações. |
| dto/PaymentIntentResponse.java | Saída a partir da entidade salva. |
| exception/ApiExceptionHandler.java | Erros de validação e corpo inválido. |

Outros arquivos, relativos à raiz:

| Arquivo | Responsabilidade |
| --- | --- |
| src/main/resources/db/migration/V2__create_payment_intents.sql | Tabela e restrições. |
| src/test/java/com/example/paymentorchestrator/entity/PaymentIntentTest.java | Precisão, limites, moeda e estado inicial. |
| src/test/java/com/example/paymentorchestrator/service/PaymentIntentServiceTest.java | Persistência com Mockito e propagação de falha. |
| src/test/java/com/example/paymentorchestrator/controller/PaymentIntentControllerTest.java | Contrato HTTP, validações e JSON malformado com MockMvc. |
| README.md | Guia principal atualizado. |
| docs/payment-intents.md | Contrato, decisões e execução desta etapa. |

Nenhuma dependência nova foi necessária.

## Anotações novas

- `@PostMapping`: associa o método ao POST do caminho definido no controller.
- `@RequestBody`: converte JSON em DTO; `@Valid` aplica Bean Validation antes de chamar o serviço.
- `@NotNull`, `@NotBlank`, `@DecimalMin`, `@Digits`, `@Pattern`, `@Size`: validam presença, conteúdo e limites; pertencem a Jakarta Validation.
- `@ResponseStatus(CREATED)`: retorna 201 quando a criação termina com sucesso.
- `@Transactional`: delimita a transação no serviço. Falhas de persistência propagadas provocam rollback; o commit ocorre antes do retorno ao controller.
- `@Entity`, `@Table`: registram a entidade JPA e seu schema/tabela.
- `@Id`, `@GeneratedValue`: identificador gerado como UUID pelo provedor JPA.
- `@Column`: configura o mapeamento das colunas, alinhado ao Flyway.
- `@Enumerated(STRING)`: salva o nome textual do enum.
- `@RestControllerAdvice`, `@ExceptionHandler`: centralizam respostas para exceções específicas.
- `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`: preparam as dependências simuladas nos testes. `@ParameterizedTest` executa diferentes exemplos com o mesmo teste.

Não é necessário anotar explicitamente com `@Repository` a interface que estende JpaRepository: Spring Data a descobre e implementa. O construtor protegido sem argumentos da entidade atende ao JPA.

## Executar no PowerShell

Pare a aplicação antiga com Ctrl+C e mantenha seu `.env`. Na raiz, carregue as variáveis e execute:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*DB_[A-Z_]+=') {
        $entry = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($entry[0].Trim(), $entry[1], 'Process')
    }
}
docker compose up -d --wait
mvn clean verify
mvn spring-boot:run
```

Em outro terminal:

```powershell
$body = @{
    amount = 125.50
    currency = 'BRL'
    description = 'Pedido 123'
} | ConvertTo-Json

$response = Invoke-WebRequest -UseBasicParsing `
    -Uri 'http://localhost:8080/api/payment-intents' `
    -Method Post -ContentType 'application/json' -Body $body
$response.StatusCode
$response.Content
```

Espere HTTP 201 e guarde o ID. Para testar uma rejeição, altere amount para 0 e repita: o PowerShell reportará erro HTTP 400; não deve haver novo registro.

Consulte o banco com os nomes do exemplo (ajuste se alterou usuário/banco):

```powershell
docker compose exec postgres psql -U payment_app -d payment_orchestrator -c "SELECT id, amount, currency, status, created_at FROM payment.payment_intents;"
docker compose exec postgres psql -U payment_app -d payment_orchestrator -c "SELECT version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;"
```

Somente os testes automatizados, sem Docker ou credenciais:

```powershell
mvn test
```

## Validacao

Foram executados 32 casos JUnit/Mockito/MockMvc, incluindo o teste de status existente, sem falhas. MockMvc verifica HTTP em memória, sem abrir servidor ou conectar ao banco.

Uma verificação adicional executou o JAR com PostgreSQL 17 em container temporário separado, com credenciais aleatórias, porta própria e armazenamento descartável. Foram confirmados: V1/V2, validação Hibernate, GET status, POST 201, gravação consultada por SQL, descrição opcional, respostas 400 e rejeição de violações dos CHECKs. A instância e o container temporários foram encerrados ao final; o banco de desenvolvimento foi preservado. Essa verificação foi pontual e não faz parte da suíte Maven.

Referências: [validação Spring MVC](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html) e [Jakarta Persistence 3.1](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.pdf).
