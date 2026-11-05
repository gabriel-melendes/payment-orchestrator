# Testes de integração com PostgreSQL

Esta etapa transforma a verificação de banco em testes automatizados: uma requisição HTTP real atravessa Controller, Service, Repository, JPA e PostgreSQL. Não utiliza mocks nem H2. As migrações Flyway de produção constroem o schema no banco novo; Hibernate continua com `ddl-auto: validate`.

## Comandos no Windows

Abra o Docker Desktop, aguarde iniciar e confira `docker version`: deve mostrar Client e Server sem erro. Na pasta do projeto:

```powershell
# Testes de entidade, serviços e MockMvc; não iniciam Docker
mvn test

# Build completo: testes existentes e integração com PostgreSQL real
mvn clean verify
```

Não precisa iniciar `docker compose`, carregar o `.env`, parar a aplicação na 8080 ou instalar PostgreSQL no Windows. O container e o servidor HTTP dos testes usam portas disponíveis escolhidas automaticamente. A primeira execução pode baixar dependências Maven, PostgreSQL e imagens auxiliares do Testcontainers; precisa de rede para esses downloads.

## Como funciona

- Surefire executa as classes `*Test` na fase `test`: os casos unitários e HTTP em memória.
- Failsafe executa `*IT` na fase `integration-test` e avalia o resultado na fase `verify`. Use `mvn verify`, para incluir essa avaliação final.
- A classe `PaymentIntentIT` inicia o contexto completo, servidor HTTP real e PostgreSQL 17 temporário. O container é compartilhado apenas entre os métodos dessa classe.
- `@ServiceConnection` informa ao Spring as credenciais e a URL do container, com prioridade sobre a configuração de datasource por variáveis. A senha de teste é aleatória. O banco do Compose não é usado.
- Antes de cada caso, limpamos somente `payment.payment_intents` nesse banco temporário. Os métodos não dependem da ordem de execução.
- Não usamos transação com rollback no método de teste: a requisição HTTP executa em outra thread e suas gravações precisam ser verificadas após o commit.
- O container não possui volume persistente configurado nem reutilização habilitada. Testcontainers encerra os recursos ao final; o contexto Spring é descartado para não reutilizar uma conexão com um container encerrado.
- Sem Docker acessível, os testes de integração falham; não são silenciosamente ignorados. `mvn test` continua disponível para trabalhar sem Docker.

O parent do Spring Boot 3.5.16 gerencia Testcontainers 1.21.4 e Failsafe 3.5.6. Não fixamos versões paralelas no POM. A imagem usa `postgres:17`, como o Compose; a tag acompanha os patches da linha 17. Um ambiente que exija reprodução binária estrita pode fixar o digest numa evolução posterior.

## Cobertura adicionada

| Cenário | Evidência |
| --- | --- |
| Migrações de produção | V1, V2 e V3 presentes e bem-sucedidas no histórico Flyway; coluna monetária numeric com escala 2. |
| Cadastro e consulta | POST 201, registro confirmado por SQL e GET 200 com os mesmos dados. |
| Ausência | GET de UUID inexistente retorna 404 com ProblemDetail. |
| Formato do ID | GET com UUID inválido retorna 400. |
| Entrada inválida | Valor zero, fração de centavo e moeda diferente de BRL retornam 400 e não gravam. |
| JSON malformado | Retorna 400 e não grava. |
| Descrição opcional e repetição | Aceita descrição null e cria IDs diferentes em duas requisições com chaves diferentes. A mesma chave é testada como repetição idempotente. |
| Proteção no banco | SQL direto com valor zero, moeda inválida ou status proibido é rejeitado pelo CHECK correspondente, com SQLSTATE 23514. |

Datas são comparadas com tolerância menor que 1 ms porque o Java representa nanossegundos, enquanto o PostgreSQL armazena microssegundos. Valores monetários usam BigDecimal; comparações numéricas não dependem dos zeros finais na representação JSON.

## Arquivos e responsabilidades

| Arquivo | Mudança |
| --- | --- |
| `pom.xml` | Dependências de Testcontainers e integração Spring Boot, todas em escopo test; Failsafe com metas integration-test e verify. |
| `src/test/java/com/example/paymentorchestrator/integration/PaymentIntentIT.java` | Container, HTTP real, limpeza isolada e verificações SQL/HTTP. |
| `README.md` | Comandos atuais e distinção entre testes rápidos e integração. |
| `docs/integration-tests.md` | Decisões, anotações e diagnóstico desta etapa. |

Nenhum endpoint ou migração foi alterado nesta etapa.

## Anotações utilizadas

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`: inicia a aplicação completa com HTTP numa porta disponível.
- `@Testcontainers`: integra o ciclo de vida dos containers ao JUnit 5.
- `@Container` no campo static: inicia um PostgreSQL compartilhado pela classe e o encerra ao final.
- `@ServiceConnection`: fornece ao Spring os detalhes de conexão para JPA e Flyway.
- `@Autowired`: injeta TestRestTemplate e JdbcTemplate nos testes. Aqui são componentes reais, sem Mockito.
- `@BeforeEach`: limpa os dados de negócio do banco temporário antes de cada cenário.
- `@DirtiesContext(AFTER_CLASS)`: remove o contexto do cache do Spring ao final da classe.
- `@ParameterizedTest` e `@ValueSource`: executam exemplos de entradas e restrições inválidas.

## Diagnóstico e relatórios

Relatórios dos testes existentes: `target/surefire-reports/`. Relatórios de integração: `target/failsafe-reports/`. Esses arquivos são gerados e não devem ser commitados.

Se aparecer `Could not find a valid Docker environment`, confirme que Docker Desktop está iniciado e `docker version` funciona no mesmo terminal. Se houver falha ao baixar imagem, verifique rede, proxy e acesso ao registry. Não desabilite os testes para transformar uma falha de infraestrutura em um build aprovado.

Referências: [integração Testcontainers com Spring Boot](https://github.com/testcontainers/testcontainers-java-spring-boot-quickstart), [extensão JUnit 5](https://java.testcontainers.org/test_framework_integration/junit_5/) e [Maven Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/).

## Resultado da execução

`mvn clean verify` concluiu com **BUILD SUCCESS**: 38 testes existentes e 12 testes de integração passaram, sem falhas, erros ou casos ignorados. A execução utilizou Java 21 e Docker Desktop com PostgreSQL 17.11, obtido pela tag postgres:17. As migrações V1 e V2 foram aplicadas no banco temporário e o contexto Spring foi encerrado ao final.

A suíte foi ampliada com idempotência, concorrência HTTP e atualização de um banco V2 com dados existentes; veja [docs/idempotency.md](idempotency.md). As contagens acima registram a entrega original de integração.
