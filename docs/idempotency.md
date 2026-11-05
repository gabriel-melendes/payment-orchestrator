# Idempotência no cadastro de intenção de pagamento

O POST agora exige `Idempotency-Key`. O cliente mantém a mesma chave ao repetir uma solicitação cujo resultado não recebeu. Uma chave diferente representa outra operação, mesmo que o JSON seja igual.

| Situação | HTTP | Efeito |
| --- | --- | --- |
| Chave nova e dados válidos | 201 Created | Cria uma intenção. |
| Mesma chave e mesmos dados válidos | 200 OK | Retorna a intenção existente, com o mesmo ID e data. |
| Mesma chave com dados válidos diferentes | 409 Conflict | Preserva a intenção original. |
| Chave ausente ou inválida | 400 Bad Request | Não grava. |
| Payload inválido | 400 Bad Request | Não reserva a chave; é possível corrigir e tentar novamente. |

Essa é uma mudança no contrato de criação: clientes antigos sem o cabeçalho passam a receber 400. A consulta GET continua funcionando da mesma forma.

## Formato e comparação

A chave tem de 1 a 128 caracteres ASCII. Começa com letra ou número e pode conter letras, números, ponto, hífen, sublinhado e dois-pontos. O valor distingue maiúsculas e minúsculas. Um UUID é uma boa opção para uma operação nova. O cliente precisa guardar a chave para usá-la nas retentativas.

São comparados amount, currency e description. A comparação monetária usa BigDecimal.compareTo: 125.5 e 125.50 são equivalentes. Descrição null e descrição vazia são diferentes; não removemos espaços nem alteramos a descrição. Campos desconhecidos continuam fora do contrato e são ignorados pelo Jackson.

A chave tem escopo global nesta API e não expira automaticamente. Não há autenticação, processamento financeiro ou integração com provedores. Num sistema com vários clientes, será necessário definir escopo por cliente e uma política de retenção. A idempotência atual protege o cadastro; não garante uma cobrança externa única.

## Concorrência e transação

Uma verificação prévia com SELECT, sozinha, não protege contra duas requisições simultâneas: ambas poderiam observar que a chave ainda não existe.

A V3 cria uma UNIQUE constraint em idempotency_key. O repository executa um INSERT nativo com `ON CONFLICT ON CONSTRAINT uk_payment_intents_idempotency_key DO NOTHING`. A restrição do PostgreSQL arbitra a concorrência, inclusive entre processos ou instâncias diferentes.

A operação tem dois comandos na mesma transação READ_COMMITTED:

1. Tentar inserir. O PostgreSQL pode aguardar a transação concorrente que usa a mesma chave. Uma linha inserida indica criação; zero linhas indica que a chave já foi usada.
2. Consultar por chave e comparar os dados persistidos com o pedido. A nova consulta vê o commit vencedor e permite devolver a intenção ou lançar conflito.

Não capturamos uma violação UNIQUE para continuar usando uma transação PostgreSQL abortada. O conflito tratado pelo INSERT é apenas o da constraint de idempotência; outros erros de integridade ou de infraestrutura continuam sendo falhas.

O SQL nativo é uma decisão específica para PostgreSQL, tecnologia já adotada pelo projeto. `@Modifying` identifica a operação de escrita e `@Query(nativeQuery = true)` mantém o SQL no Repository. O Service mantém a coordenação e comparação; o Controller escolhe 201 ou 200 com ResponseEntity.

O UUID da criação agora é gerado explicitamente pelo serviço, pois o INSERT nativo não executa a geração automática do JPA. A entidade continua mapeada por JPA para as consultas. O DTO é construído a partir do registro lido após o INSERT, preservando a precisão de data e dinheiro do banco também na primeira resposta.

A coluna admite NULL para preservar intenções anteriores à V3. Esses registros continuam consultáveis por ID, sem chave atribuída retroativamente. Novos POSTs sempre exigem chave. A restrição UNIQUE permite vários registros antigos com NULL; a restrição CHECK valida o formato de chaves presentes. Não existe endpoint para trocar ou excluir a chave.

## Arquivos e responsabilidades

Caminhos relativos a src/main/java/com/example/paymentorchestrator:

| Arquivo | Responsabilidade |
| --- | --- |
| domain/IdempotencyKey.java | Novo record de domínio: valida o valor da chave no construtor, sem depender de Spring. |
| service/PaymentIntentCreationResult.java | Resultado interno com DTO e indicação de criação/repetição. |
| service/PaymentIntentService.java | Inserção idempotente, comparação e transação READ_COMMITTED. |
| repository/PaymentIntentRepository.java | INSERT condicional e consulta por chave. |
| entity/PaymentIntent.java | Mapeamento da coluna idempotency_key, inclusive registros antigos. |
| controller/PaymentIntentController.java | Cabeçalho e resposta 201/200. |
| exception/InvalidIdempotencyKeyException.java | Erro específico de chave ausente ou inválida. |
| exception/IdempotencyConflictException.java | Erro específico de chave reutilizada com outros dados. |
| exception/ApiExceptionHandler.java | Tradução das exceções para ProblemDetail 400/409. |

Outros arquivos:

| Arquivo | Responsabilidade |
| --- | --- |
| src/main/resources/db/migration/V3__add_payment_intent_idempotency.sql | Coluna, UNIQUE e CHECK, sem alterar V1/V2. |
| src/test/java/com/example/paymentorchestrator/domain/IdempotencyKeyTest.java | Formato, tamanho e distinção de maiúsculas/minúsculas. |
| src/test/java/com/example/paymentorchestrator/service/PaymentIntentServiceTest.java | Criação, repetição, divergência e falhas de persistência. |
| src/test/java/com/example/paymentorchestrator/controller/PaymentIntentControllerTest.java | Contrato HTTP 201/200/400/409. |
| src/test/java/com/example/paymentorchestrator/integration/PaymentIntentIT.java | HTTP/SQL real, concorrência e unicidade no PostgreSQL. |
| src/test/java/com/example/paymentorchestrator/integration/PaymentIntentMigrationIT.java | Atualização V2 → V3 preservando registro existente. |
| README.md e docs | Exemplos atualizados para o cabeçalho obrigatório e decisões desta etapa. |

## Anotações

- `@RequestHeader`: lê Idempotency-Key. O binding usa required=false para que a validação do record produza o mesmo erro padronizado tanto para ausência quanto para formato inválido; a chave permanece obrigatória no contrato.
- `@Transactional(isolation = Isolation.READ_COMMITTED)`: delimita a transação e garante snapshots por comando para a consulta posterior ao INSERT concorrente.
- `@Modifying` e `@Query(nativeQuery = true)`: executam SQL de escrita pelo Spring Data JPA.
- `@Param`: associa cada argumento Java ao parâmetro nomeado do SQL, sem concatenação de entradas do usuário.
- `@ExceptionHandler`: transforma erros específicos de chave em 400 ou 409, mantendo o serviço independente de HTTP.

## Executar e testar no PowerShell

Abra o Docker Desktop. Para a suíte completa, sem carregar .env ou subir Compose:

```powershell
mvn clean verify
```

Para teste manual, pare a aplicação antiga com Ctrl+C e inicie a versão nova com as variáveis do .env carregadas, conforme README. Flyway aplicará V3 no banco existente; não apague o volume.

Em outro terminal, ajuste a porta se necessário:

```powershell
$baseUrl = 'http://localhost:8080'
$headers = @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
$body = @{ amount = 125.50; currency = 'BRL'; description = 'Pedido idempotente' } | ConvertTo-Json

$first = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/payment-intents" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$first.StatusCode
$first.Content

$replay = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/payment-intents" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$replay.StatusCode
$replay.Content
```

Espere 201 e depois 200, com o mesmo ID e createdAt. Mantenha os mesmos headers e altere o valor:

```powershell
$changedBody = @{ amount = 126.50; currency = 'BRL'; description = 'Pedido idempotente' } | ConvertTo-Json
Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/payment-intents" -Method Post -Headers $headers -ContentType 'application/json' -Body $changedBody
```

O PowerShell reportará erro HTTP 409. Com uma chave nova, o mesmo JSON pode criar outra intenção. Não gere chave nova dentro de uma rotina de retentativa da mesma operação.

## Referências

- [INSERT e ON CONFLICT no PostgreSQL 17](https://www.postgresql.org/docs/17/sql-insert.html)
- [Snapshots em READ COMMITTED](https://www.postgresql.org/docs/17/transaction-iso.html)
- [Consultas modificadoras no Spring Data JPA 3.5](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/query-methods.html)

## Validação executada

Build concluído com **BUILD SUCCESS**: 59 testes unitários/MockMvc e 23 testes de integração, total de **82**, sem falhas, erros ou casos ignorados. A primeira tentativa de integração encontrou o Docker Desktop fechado; após iniciá-lo, a suíte completa passou.

Os testes reais confirmaram respostas 201/200/409/400, igualdade da resposta no replay, comparação monetária, duas solicitações simultâneas iguais com um único registro, solicitações simultâneas divergentes com um vencedor e um conflito, constraint UNIQUE e atualização V2 → V3 preservando os dados existentes. Os bancos usados são temporários do Testcontainers. A execução da CI no GitHub dependerá do commit e envio desta branch.
