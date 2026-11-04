# Integração contínua e pull requests

A CI executa o build e os testes automaticamente no GitHub. O arquivo `.github/workflows/ci.yml` é a configuração versionada desse processo. Não publica nem faz deploy da aplicação.

## O que o workflow faz

1. Executa em push de qualquer branch, em pull requests e por acionamento manual.
2. Reserva um runner Ubuntu 24.04 com limite de 20 minutos.
3. Obtém o código com checkout.
4. Configura JDK Temurin 21 e cache de dependências Maven, usando pom.xml como referência do cache.
5. Confirma que Docker está disponível.
6. Executa `mvn --batch-mode --no-transfer-progress clean verify`.
7. Disponibiliza os relatórios Surefire e Failsafe como artifact por sete dias, inclusive quando os testes falham, desde que a execução não tenha sido cancelada.

O comando executa os 38 casos atuais e os 12 testes de integração, totalizando 50. Não usamos flags para ignorar testes. Testcontainers inicia seu próprio PostgreSQL; não precisamos de um serviço Postgres no YAML, Compose, `.env` ou secrets de banco no GitHub.

Cache acelera downloads; não substitui a execução do build ou dos testes. `--batch-mode` evita interação, e `--no-transfer-progress` reduz os logs dos downloads. Surefire executa os testes rápidos; Failsafe executa e verifica os testes de integração.

O workflow tem permissão `contents: read` e checkout sem persistir credenciais. Não precisa de permissão para publicar commits. Novos commits cancelam execuções antigas do mesmo evento/branch ou PR. Push e pull request podem gerar duas execuções separadas, o que é esperado nesta configuração inicial.

Usamos actions oficiais: checkout v7, setup-java v6 e upload-artifact v7, conforme documentação consultada. As tags de versão principal recebem atualizações do mantenedor; uma evolução possível é fixar SHA completo e atualizar automaticamente com ferramenta apropriada.

## Arquivos desta etapa

| Arquivo | Responsabilidade |
| --- | --- |
| `.github/workflows/ci.yml` | Gatilhos, ambiente, build, testes e relatórios. |
| `.github/pull_request_template.md` | Modelo para descrever mudança, evidências de teste e revisão. |
| `docs/ci.md` | Guia de ativação, branches e acompanhamento. |
| `README.md` | Link e resumo do fluxo de CI. |

## Organizar a branch principal

Na consulta ao remoto, a branch padrão era `feat/get-payment-intent`, no commit `3e1e40b`. A entrega de testes de integração está em `test/payment-intent-integration`, commit `fdcf4aa`, que também inclui o cadastro e a consulta anteriores. Não havia branch main nas referências remotas consultadas.

Para começar com uma base já testada, crie main apontando para essa entrega. Na raiz, após conferir `git status` e `git log --oneline -4`:

```powershell
git branch main fdcf4aa
git push -u origin main
```

Esses comandos criam e enviam uma referência para o histórico existente; não apagam branches nem reescrevem commits. Se main já existir quando você executar, pare e confira seu histórico antes de tentar novamente. Não utilize force push.

No GitHub, abra Settings, procure Default branch e altere a branch padrão para main. Essa configuração é externa ao código e não é alterada pelo workflow.

A configuração de CI está na branch local `ci/java-build`, criada a partir da entrega de integração. Criar main a partir de fdcf4aa permite revisar apenas os arquivos de CI no próximo PR.

## Registrar e enviar a CI

Na branch ci/java-build:

```powershell
git branch --show-current
git diff --check
git add .github/workflows/ci.yml .github/pull_request_template.md docs/ci.md README.md
git diff --cached
git commit -m "ci: add Java build and integration tests workflow"
git push -u origin ci/java-build
```

No GitHub, abra Pull requests → New pull request. Escolha **base: main** e **compare: ci/java-build**. Confirme que o diff contém somente os quatro arquivos desta etapa.

Título sugerido: `ci: add Java build and integration tests workflow`.

Descrição sugerida:

> Executa o build Java 21 e os testes JUnit/Mockito/MockMvc e Testcontainers em pushes e pull requests. Armazena relatórios Surefire/Failsafe para diagnóstico e adiciona um modelo de descrição de PR.
>
> Validação local: informe o resultado do actionlint e do Maven. A execução no GitHub precisa ser conferida na aba Actions e nos checks do PR.

O template aparece automaticamente em novos PRs quando estiver presente na branch padrão. Neste primeiro PR, você pode copiar a estrutura do arquivo manualmente.

## Acompanhar a execução

Depois do push, abra Actions → Java CI → execução mais recente. Também confira os checks no PR e confirme que correspondem ao commit atual.

O job `Build and test (Java 21)` deve ficar verde. Nos logs, confira os resumos: 38 testes pelo Surefire, 12 pelo Failsafe e BUILD SUCCESS. A contagem crescerá quando novos testes forem adicionados.

Se falhar, abra a etapa vermelha. Os relatórios estarão em Artifacts no resumo da execução, quando tiverem sido gerados. Falhas anteriores à geração dos relatórios podem deixar apenas os logs. Corrija localmente, faça outro commit e push na mesma branch: o PR é atualizado e a CI executa novamente.

Não considere a execução local como evidência de execução no GitHub: o workflow remoto só estará validado após uma execução lá.

## Revisão e merge

Revise Files changed e os testes antes de fazer merge do PR. Num projeto individual, você pode revisar o próprio diff, mas não pode aprovar formalmente o próprio PR como outro revisor. Não exija uma aprovação externa se você for o único colaborador.

Quando o job estiver disponível, você pode configurar uma regra para main exigindo que o check `Build and test (Java 21)` passe antes do merge, se essa opção estiver disponível no seu repositório. O workflow, sozinho, exibe resultados; não impede um merge manual sem uma regra correspondente.

Depois do merge, com o diretório de trabalho limpo:

```powershell
git switch main
git pull --ff-only origin main
```

Para uma próxima entrega, parta da main atualizada e crie uma nova branch. Evite continuar desenvolvendo na branch de uma entrega já concluída.

O acionamento manual por Run workflow fica disponível quando o arquivo estiver na branch padrão. A configuração inicial já executa no push da branch de CI, mesmo antes do merge.

## Referências

- [Build Java com Maven no GitHub](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
- [setup-java](https://github.com/actions/setup-java)
- [checkout](https://github.com/actions/checkout)
- [upload-artifact](https://github.com/actions/upload-artifact)
- [actionlint](https://github.com/rhysd/actionlint)

## Validação desta entrega

O workflow passou pelo actionlint 1.7.12, obtido do release oficial e conferido por SHA-256. O build Maven foi executado localmente com as opções batch-mode e no-transfer-progress e um ajuste de caminho do cache exigido pelo sandbox: BUILD SUCCESS, 38 testes existentes e 12 de integração passando, sem falhas, erros ou casos ignorados.

A execução de actions/checkout, actions/setup-java, cache e upload-artifact no runner do GitHub ainda depende do envio e da primeira execução remota. Não houve commit, push, criação de main remota ou alteração da branch padrão por esta implementação.
