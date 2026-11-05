# Consulta de intenção por ID

`GET /api/payment-intents/{id}` permite consultar uma intenção cadastrada. A resposta 200 utiliza o mesmo contrato do POST: id, amount, currency, description, status e createdAt.

| Situação | HTTP | Resposta |
| --- | --- | --- |
| UUID existente | 200 | PaymentIntentResponse em JSON. |
| UUID válido, sem registro | 404 | ProblemDetail com título e identificação da intenção ausente. |
| ID que não pode ser convertido em UUID | 400 | ProblemDetail indicando parâmetro inválido. |

## Decisões

O Controller recebe o ID com `@PathVariable UUID id`; o Spring converte o texto do caminho em UUID antes de chamar o serviço. Um texto inválido causa MethodArgumentTypeMismatchException, tratado pelo ApiExceptionHandler. Não é necessário usar `@Valid` para essa conversão.

O serviço utiliza `repository.findById(id)`, já fornecido por JpaRepository, que retorna Optional. Optional representa explicitamente a possibilidade de ausência. `orElseThrow` cria PaymentIntentNotFoundException apenas quando não existe registro. Não capturamos indisponibilidade do banco como 404: falha de infraestrutura é diferente de ausência de dados.

`@Transactional(readOnly = true)` declara a intenção de leitura e permite otimizações do provedor; não é uma barreira de segurança que proíbe qualquer escrita. A entidade é convertida em DTO dentro da transação. O endpoint não cria nem altera registros.

`@GetMapping("/{id}")` define a rota de consulta. `@RestControllerAdvice` e `@ExceptionHandler` traduzem as exceções para ProblemDetail, mantendo o serviço independente do protocolo HTTP. Não houve alteração de tabela ou nova migração.

## Arquivos

| Arquivo | Responsabilidade |
| --- | --- |
| controller/PaymentIntentController.java | Novo GET e extração do ID da URL. |
| service/PaymentIntentService.java | Consulta transacional de leitura e tratamento da ausência. |
| exception/PaymentIntentNotFoundException.java | Nova exceção específica para intenção inexistente. |
| exception/ApiExceptionHandler.java | Respostas padronizadas 404 e 400. |
| service/PaymentIntentServiceTest.java | Consulta, ausência e propagação de falha de banco. |
| controller/PaymentIntentControllerTest.java | Contrato HTTP 200, 404 e 400; ID inválido não chama serviço. |

Os quatro primeiros caminhos são relativos a `src/main/java/com/example/paymentorchestrator/`; os dois testes a `src/test/java/com/example/paymentorchestrator/`. README e documentação do cadastro também foram atualizados.

## Como testar no PowerShell

Com o PostgreSQL disponível, as variáveis do .env carregadas e a aplicação antiga encerrada, execute na raiz:

```powershell
mvn clean verify
mvn spring-boot:run
```

Em outro terminal, crie uma intenção e use o ID retornado:

```powershell
$body = @{ amount = 25.50; currency = 'BRL'; description = 'Teste de consulta' } | ConvertTo-Json
$intent = Invoke-RestMethod -Uri 'http://localhost:8080/api/payment-intents' -Method Post -Headers @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } -ContentType 'application/json' -Body $body
curl.exe -i "http://localhost:8080/api/payment-intents/$($intent.id)"
```

Teste um UUID sem registro (a chance de colisão aleatória é desprezível) e um formato inválido:

```powershell
$missingId = [guid]::NewGuid()
curl.exe -i "http://localhost:8080/api/payment-intents/$missingId"
curl.exe -i 'http://localhost:8080/api/payment-intents/not-a-uuid'
```

Espere respectivamente 200, 404 e 400. Para rodar apenas os testes automatizados, sem Docker ou credenciais: `mvn test`.

## Escopo da validação

Foram adicionados seis testes: três de serviço com Mockito e três HTTP com MockMvc. Esses testes verificam a consulta e os contratos em memória; não comprovam uma requisição real a PostgreSQL. A verificação real do cadastro feita na etapa anterior está documentada separadamente.

Resultado: build final com BUILD SUCCESS e 38 testes passando, sem falhas, erros ou casos ignorados. A consulta desta etapa foi validada com Mockito e MockMvc; não foi executado um teste novo com PostgreSQL real.
