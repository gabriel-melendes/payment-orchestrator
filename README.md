# Payment Orchestrator

Projeto de estudos para entrevistas Java em instituições financeiras. O projeto oferece status HTTP, cadastro e consulta de intenções de pagamento por ID. Veja o [guia do cadastro](docs/payment-intents.md), com contrato, arquivos e decisões da segunda etapa.

## Tecnologias e decisões

Java 21, Spring Boot 3.5.16, Maven, PostgreSQL 17, Docker Compose, JUnit 5 e Mockito (os dois últimos incluídos pelo starter de testes). O parent do Spring Boot gerencia versões compatíveis das dependências.

Fluxo atual: requisição HTTP → `StatusController` → `StatusService` → `StatusResponse` → JSON. O controller cuida do protocolo HTTP; o service representa o comportamento da aplicação. O DTO é um `record` Java imutável que define o contrato de resposta.

O cadastro segue Controller → Service → Repository → PostgreSQL, com DTOs separados da entidade. O status continua sem consultar o banco. A inicialização depende de PostgreSQL para Flyway e JPA.

Flyway é responsável pelo DDL: `V1__create_payment_schema.sql` cria o schema `payment`. Seu histórico fica em `public.flyway_schema_history`. V2 cria a tabela payment.payment_intents, mapeada pela entidade PaymentIntent. `ddl-auto: validate` verifica os mapeamentos sem criar ou modificar tabelas. Nunca altere uma migração já aplicada; crie V2, V3 etc. `open-in-view: false` impede consultas JPA durante a serialização HTTP e incentiva limites de transação na camada de serviço quando necessários.

O cadastro usa Bean Validation e Mockito nos testes de serviço. Ele registra intenções sem executar pagamentos. Consulte o guia da segunda etapa para regras e limites.

## Arquivos e responsabilidades

| Arquivo | Responsabilidade |
| --- | --- |
| `pom.xml` | Versões, dependências, Java 21 e empacotamento executável Spring Boot. |
| `compose.yaml` | PostgreSQL 17, healthcheck, porta local e volume persistente. |
| `.env.example` | Modelo de variáveis; copie para `.env` e substitua a senha. |
| `.gitignore` | Exclui build, IDEs, credenciais locais e arquivos do sistema. |
| `src/main/java/com/example/paymentorchestrator/PaymentOrchestratorApplication.java` | Ponto de entrada e configuração automática. |
| `src/main/java/com/example/paymentorchestrator/controller/StatusController.java` | GET `/api/status`, com injeção do serviço pelo construtor. |
| `src/main/java/com/example/paymentorchestrator/service/StatusService.java` | Produz o status `UP`. |
| `src/main/java/com/example/paymentorchestrator/dto/StatusResponse.java` | Contrato JSON da resposta. |
| `src/main/java/com/example/paymentorchestrator/repository/package-info.java` | Documenta a camada de persistência. |
| `src/main/resources/application.yml` | Conexão por variáveis, JPA e Flyway. |
| `src/main/resources/db/migration/V1__create_payment_schema.sql` | Primeira versão da estrutura do banco. |
| `src/test/java/com/example/paymentorchestrator/service/StatusServiceTest.java` | Teste unitário do contrato do serviço sem Spring ou banco. |
| `README.md` | Guia de execução e decisões desta etapa. |

## Anotações utilizadas

- `@SpringBootApplication`: reúne configuração, autoconfiguração e descoberta de componentes no pacote da aplicação e seus subpacotes.
- `@RestController`: registra um controller cujos retornos são escritos no corpo HTTP; o Jackson converte o DTO para JSON.
- `@RequestMapping`: define o caminho base do controller.
- `@GetMapping`: associa o método Java ao verbo GET nesse caminho. Uma resposta normal retorna HTTP 200.
- `@Service`: registra a classe como componente de serviço gerenciado pelo Spring.
- `@Test`: anotação JUnit Jupiter que identifica um teste; não é uma anotação Spring.

O construtor único do controller permite injeção automática sem `@Autowired`. Isso explicita a dependência e facilita testes. As anotações de persistência, transação e validação do cadastro são explicadas em [docs/payment-intents.md](docs/payment-intents.md).

## Executar no Windows (PowerShell)

Instale JDK 21, Maven 3.6.3 ou superior, Git e Docker Desktop com suporte a containers Linux/WSL 2. Configure `JAVA_HOME` para o JDK e Java, Maven e Git no PATH. Abra o Docker Desktop e aguarde o mecanismo iniciar. Reabra o terminal após instalar ferramentas.

Na raiz do projeto:

```powershell
java -version
mvn -version
git --version
docker version
docker compose version
Copy-Item .env.example .env
notepad .env
```

Defina uma senha local em `.env`. O arquivo usa linhas simples `CHAVE=valor`, sem aspas, comentários ao final ou valores multilinha. O script abaixo carrega esse mesmo arquivo na sessão PowerShell para que a aplicação receba as credenciais. O Compose lê `.env` automaticamente; Spring Boot não lê esse arquivo automaticamente.

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*DB_[A-Z_]+=') {
        $entry = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($entry[0].Trim(), $entry[1], 'Process')
    }
}

docker compose config --quiet
docker compose up -d --wait
docker compose ps
mvn clean verify
mvn spring-boot:run
```

Use essa mesma sessão para iniciar a aplicação. As credenciais são obrigatórias em `application.yml`. Os valores padrão de host, porta e banco correspondem ao exemplo. Se a porta 5432 estiver ocupada, altere `DB_PORT` em `.env` e recarregue as variáveis antes de subir os serviços.

Em outro PowerShell, faça a requisição:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/status
curl.exe -i http://localhost:8080/api/status
```

Resposta esperada: HTTP 200 e `{"status":"UP"}`. O status também foi verificado em uma instância de teste separada na segunda etapa.

Para rodar os testes de entidade, serviços e MockMvc (sem Docker ou credenciais):

```powershell
mvn test
```

`mvn clean verify` compila, executa os testes unitários e HTTP em memória e também a integração com PostgreSQL real via Testcontainers, além de gerar o JAR. Requer Docker Desktop iniciado. Não precisa carregar credenciais ou subir o Compose para os testes: eles usam banco e porta HTTP temporários. Veja [o guia de integração](docs/integration-tests.md).

Para executar o JAR, na sessão com as variáveis carregadas e com o banco disponível:

```powershell
java -jar target/payment-orchestrator-0.0.1-SNAPSHOT.jar
```

## Banco e encerramento

```powershell
# Ver a migracao aplicada e o schema criado
docker compose exec postgres psql -U payment_app -d payment_orchestrator -c "TABLE public.flyway_schema_history;"
docker compose logs postgres
# Pare a aplicacao com Ctrl+C; depois pare o banco
docker compose down
```

O comando `psql` acima usa o usuário e banco do exemplo; ajuste-os se você os alterou. O volume `postgres_data` mantém os dados após `docker compose down`. As variáveis de usuário, senha e banco inicializam um volume novo; editar `.env` não altera credenciais de um banco já inicializado. Não use `down -v` para parar rotineiramente: ele remove o volume e apaga os dados. O banco é exposto apenas em `127.0.0.1`.

## Git e revisão

Depois de instalar Git, na raiz:

```powershell
git init
git switch -c feat/project-bootstrap
git status --short
git add pom.xml compose.yaml .env.example .gitignore README.md src
git diff --cached
git commit -m "chore: bootstrap Payment Orchestrator"
```

Revise o diff antes do commit e confirme que `.env` não está incluído. Em equipe, a branch seria enviada para revisão por pull request, com `mvn clean verify` como verificação automatizada em CI. A configuração de CI está em .github/workflows/ci.yml; ativação no GitHub e revisão por PR estão descritas em [docs/ci.md](docs/ci.md). Deploy fica para uma etapa futura. Os passos acima descrevem a inicialização original; em um clone existente, use a branch da sua entrega e revise git status antes de criar o commit.

## Validação

O projeto agora possui testes automatizados de integração com PostgreSQL 17 real. Veja [como executar e interpretar os testes](docs/integration-tests.md). O histórico das verificações anteriores permanece nos guias de cada etapa.


A consulta GET por ID está documentada em [docs/payment-intent-query.md](docs/payment-intent-query.md), com decisões, arquivos e exemplos 200/404/400.

## Integração contínua

O workflow Java CI executa o build Java 21 e a suíte completa em pushes e pull requests. Veja [como consolidar main, abrir o PR e acompanhar a CI](docs/ci.md). Relatórios ficam disponíveis como artifacts, inclusive em falhas de teste. A presença do arquivo local ainda não comprova uma execução no GitHub.

## Idempotência

O POST exige Idempotency-Key: chave nova retorna 201, repetição equivalente retorna 200, reutilização com outros dados válidos retorna 409 e chave inválida retorna 400. A V3 preserva registros antigos e adiciona proteção UNIQUE no banco. Veja [contrato, concorrência e exemplos](docs/idempotency.md).
