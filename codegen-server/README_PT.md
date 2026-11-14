# Majiang · Gerador de Código (CodeGen) — Apresentação do Projeto

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · **Português** · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

> Idiomas: [中文](README_ZH.md) · [English](README.md) · [日本語](README_JA.md) · **Português** · [Español](README_ES.md) · [한국어](README_KO.md) · [Français](README_FR.md) · [Deutsch](README_DE.md) · [हिन्दी](README_HI.md) · [العربية](README_AR.md) · [বাংলা](README_BN.md) · [Русский](README_RU.md) · [اردو](README_UR.md)

---

> Um gerador de código multi-projeto para **Spring Boot + Vue**: a partir da estrutura das tabelas do banco de dados, produz código de negócio de front-end e back-end **compilável, executável e pronto com menus e permissões**, cuidando do ciclo completo de entrega: gravação em disco, backup, registro em livro-razão e rollback.

GitHub: https://github.com/hustxia/CodeGen

## 1. O Que É

O CodeGen é uma "fábrica de código orientada a tabelas". Basta trazer suas tabelas de negócio (importação direta do banco ou colando um DDL), configurar os campos e as chaves de geração e clicar em "Gerar" — ele produz o Entity/Mapper/Service/Controller/DTO/VO do back-end, o `api.js` e as páginas Vue do front-end, menus e rotas, e até páginas de impressão A4 e esqueletos de testes de integração, gravando tudo com segurança no seu projeto-alvo.

Ele não apenas "cospe código" — é um **pipeline de entrega com portões de qualidade e capacidade de rollback**:

```
Entrada de tabelas (importação do BD / DDL)
   → Configuração de campos e chaves (com recomendações de IA)
      → Geração com um clique: autoverificação → backup → gravação → compilação/validação → registro de menu → recriação de rotas
         → Registro no livro-razão → com rollback
```

---

## 2. Quem São os Utilizadores e Como o Usam

| Utilizador | Necessidade típica | O caminho que a plataforma oferece |
| --- | --- | --- |
| Programador back-end | Tem um conjunto de tabelas (ou DDL) e quer levantar CRUD rapidamente | Página de config. de tabelas: importar → configurar campos → Geração com um clique |
| Programador full-stack / independente | Quer criar do zero um sistema completo (com login, permissões, dicionários) | Página Pipeline: construa um sistema com uma frase |
| Líder de equipa | Quer unificar o estilo de código, as convenções e a estrutura de diretórios | Administração do sistema: um sistema = uma configuração de saída, aplicada em lote |
| Mantenedor da plataforma | Quer alterar os templates sem alterar o código | Gestão de templates: edição online, efeito imediato ao guardar, com rollback de versões |

## 3. Que Problemas Ele Resolve

| Dor | O que o CodeGen faz |
| --- | --- |
| Inferno do CRUD repetitivo — uma montanha de código boilerplate para cada tabela | Chaves por tabela geram front e back completos com um clique, incluindo CRUD, paginação, validação, importação/exportação |
| Campos e APIs desalinhados entre front e back, com retrabalho constante na integração | Os mesmos metadados da tabela alimentam as APIs do back-end e as páginas do front-end — zero desvio semântico |
| Convenções de equipe difíceis de unificar; cada um escreve de um jeito | O template é a convenção: 22 templates FreeMarker gerenciados centralmente, com edição online e rollback de versões |
| Geradores que "sobrescrevem e perdem" a lógica escrita à mão | Mecanismo de mesclagem por blocos marcadores: o código escrito à mão é preservado; apenas as regiões geradas são substituídas |
| Código gerado não confiável até o momento da compilação | Autoverificação por compilação em memória (`javac` real), checagens de contrato estrutural, pré-visualização de diff em disco — nada é gravado se não compilar |
| Montar o esqueleto de um novo sistema do zero toma tempo | Pipeline "construa um sistema com uma frase": criar BD → esqueleto → gerar → empacotar → registrar menus, tudo automatizado |

---

## 4. Panorama de Capacidades

- **Entrada de tabelas**: análise de DDL, importação direta do banco, assistente de entrada em lote, configuração de colunas derivada automaticamente (controles / validações / dicionários / relacionamentos).
- **Formas de tabela**: tabela simples, tabela em árvore (API tree + grade em árvore), mestre-detalhe (salvamento na mesma transação + abas de detalhe), tabelas intermediárias muitos-para-muitos.
- **Artefatos gerados**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + páginas Vue + páginas de impressão A4 + esqueletos de testes de integração.
- **Aprimoramentos de negócio**: dropdowns de relacionamento, tradução por dicionário, mascaramento de dados, verificação de unicidade, vinculação de formulários, permissões por escopo de dados, importação/exportação Excel, fluxo de aprovação, pacotes de capacidade de pagamento / arquivos / mobile.
- **Portões de qualidade**: autoverificação por compilação em memória (`javac` real), checagens de contrato estrutural, verificação de consistência de permissões, pré-visualização de diff em disco, validação real dos artefatos de front-end.
- **Aterrissagem segura**: gravação em disco + backups em lote `.gen-backup`, mesclagem por blocos marcadores preservando código manual, livro-razão `gen_output` com quatro estados (criado / mesclado / ignorado / falhou), rollback histórico.
- **Isolamento multi-sistema**: cada projeto-alvo é uma "configuração de sistema" (caminhos de front/back, nomes de pacote, prefixo de gateway, classpath de dependências, padrões das chaves de geração) — vários projetos atendidos em paralelo.
- **Aprimoramentos com IA**: construir um sistema com uma frase (modelagem por IA), aprimoramento de dados-semente por IA, configuração recomendada por IA por tabela (pré-visualização de diff + aplicar marcado).

---

## 5. Cenários Típicos

| Cenário | Descrição |
| --- | --- |
| Tabela em árvore | Dados hierárquicos como categorias de produtos — API tree, grade em árvore e dropdown de seleção de pai gerados automaticamente |
| CRUD de tabela simples | Clientes, produtos etc. — dropdowns de FK + tradução por dicionário + importação/exportação prontos para uso |
| Mestre-detalhe | Pedidos de venda + itens do pedido — salvamento na mesma transação, gaveta de detalhes com abas duplas (info mestre / detalhes) |
| Somente leitura / estatística | Fluxos de estoque etc. — chaves de escrita desativadas, apenas consulta e exportação |
| Documentos para impressão | Marque a chave "imprimir" para gerar página de impressão A4 e rota, com cabeçalho + tabela de detalhes |
| Sistema com uma frase | Descreva o domínio de negócio — IA cria BD e tabelas + esqueleto + geração completa + registro de menus de uma vez |

> Todos os cenários têm demonstrações executáveis (projeto `demo_admin`) com capturas de tela — consulte `docs/典型场景操作指南-演示后台demo.md` (em chinês).

---

## 6. Fluxo de Trabalho Principal

```
┌─────────────────┐   ┌─────────────────────┐   ┌───────────────────────────────────────────────────────┐
│ Entrada tabelas  │ → │ Config. campos/chaves│ → │ Geração 1-clique (autoverif.→backup→gravação→         │ → │ Razão · Rollback │
│                  │   │                      │   │ compilação→menu→rota)                                 │   │                  │
└─────────────────┘   └─────────────────────┘   └───────────────────────────────────────────────────────┘
  Importação BD/DDL      controles/validação/    Só aterrissa no disco após todos os portões de          Rastreabilidade
                         dicionários             qualidade; código manual é preservado                   por lote
```

- **Pré-visualização de diff**: os artefatos gerados são comparados arquivo a arquivo com o estado atual do disco; você confirma antes de gravar, evitando sobrescritas acidentais.
- **Livro-razão de geração**: cada arquivo registra ação, tamanho em bytes, MD5 e local do backup; um lote inteiro pode ser revertido.

---

## 7. Construa um Sistema com Uma Frase (Pipeline)

Capacidade avançada para "montar um back-end do zero". Depois de descrever o domínio de negócio, o pipeline avança automaticamente por 5 etapas:

| Etapa | Significado |
| --- | --- |
| `CHECK` | Valida a configuração do projeto-alvo e o classpath de dependências |
| `WRITE` | Cria banco e tabelas, grava o esqueleto e os artefatos gerados |
| `COMPILE` | `mvn package` do back-end / build do front-end, autoverificação por compilação em memória |
| `MENU` | Produz e registra permissões de menu (dupla saída: SQL de artefato / gravação interna direta) |
| `ROUTE` | Recria os fragmentos de rotas do front-end |

Qualquer falha de etapa interrompe imediatamente com um erro legível para fácil localização.

---

## 8. Arquitetura do Sistema

Front-end e back-end separados, com dois repositórios cooperando:

| Repositório | Papel | Stack |
| --- | --- | --- |
| `codegen-server` | Back-end: motor de geração, templates, validação, livro-razão, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end: console (config. de tabelas / templates / livro-razão / administração) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (modo history) |

Camadas do back-end:

```
controller/    Camada de API (cadeia de geração Gen* / administração Sys* / pipeline)
service/impl/  Implementações principais (cadeia de geração, motor de templates, mesclagem, validação, execução do pipeline)
util/          Motor de templates, montagem de modelos, mesclagem por marcadores, compilação de autoverificação
config/        Config. de saída, config. de autoverificação, semeador de templates
common/        Result / BaseEntity / exceções / auditoria
resources/gen-templates/   22 templates FreeMarker (grupos backend / frontend / shared / test)
```

**Capacidades de base que o projeto-alvo precisa ter** (o gerador produz apenas código de negócio; base faltante falha silenciosamente):

1. Plugin de paginação MyBatis-Plus (`PaginationInnerInterceptor`)
2. Classes de base como `Result` / `BaseEntity` / `PageQuery` (prefixo de pacote igual ao `root-package`)
3. `utils/request.js` do front-end com `toPageData` (converte `Page{records,total}` em `{list,total}`)
4. Dependências `springdoc-openapi-starter-webmvc-ui` (código gerado usa `@Operation`) e `poi-ooxml` (Excel)

---

## 9. Stack Tecnológica em Resumo

| Dimensão | Escolha |
| --- | --- |
| Linguagem / framework back-end | Java 21 / Spring Boot 3.2.5 |
| Persistência | MyBatis-Plus 3.5.5 |
| Motor de templates | FreeMarker (22 templates-semente, edição online + rollback de versões) |
| Segurança | Autenticação JWT |
| Banco de dados | MySQL 8 |
| Front-end (generated projects) | Vue 3 + Element Plus + Vite |
| Front-end (platform console) | Vue 2.7 + Element UI |
| IA local | qwen2.5-coder:7b (modelagem / configuração recomendada / aprimoramento de dados-semente) |
| Documentação de APIs | springdoc-openapi (Swagger UI) |

---

## 10. Início Rápido

### 1. Criar o banco (13 tabelas da plataforma + conta padrão admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configurar o projeto-alvo

Copie o modelo de configuração e ajuste para os caminhos locais:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` está excluído pelo `.gitignore` e não será commitado. Principais itens:

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # base de gravação dos artefatos
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # subdiretório do front em monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefixo dos imports das classes de base nos artefatos
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

Exporte o diretório de dependências para a autoverificação assim:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Iniciar o back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

O serviço usa por padrão a porta **8120**; documentação em `/swagger-ui/index.html`.

### 4. Iniciar o front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

O front-end usa por padrão a porta **9530**, com proxy `/dev-api` para a porta 8120 do back-end. Conta padrão: `admin / admin123`.

### 5. Itens de configuração importantes

| Configuração | Descrição |
| --- | --- |
| `gen.output.*` | Caminhos de gravação dos artefatos, nomes de pacote, mapeamento de módulos |
| `gen.selfcheck.classpath` | Classpath de dependências para a compilação de autoverificação em memória, suporta `dir/*` |
| `gen.menu.outlet` | `artifact` produz apenas SQL de menu e fragmentos de rota; `internal` também grava no `sys_menu` da própria plataforma |

---

## 11. Mapa da Documentação

| Documento | Conteúdo |
| --- | --- |
| `docs/代码生成器-操作手册.md` (chinês) | Implantação, início em 5 minutos, configuração de tabelas, entrada em lote, gestão de templates, livro-razão, catálogo de APIs, pipeline completo (com capturas de tela) |
| `docs/典型场景操作指南-演示后台demo.md` (chinês) | 6 tabelas cobrindo 5 cenários típicos, com demo executável e capturas reais |
| `docs/项目介绍.md` | Documento de apresentação, mesma origem deste arquivo |
| `docs/代码生成器-Vx.x-规划.md` | Planos de versão e inventários de funcionalidades de V3.1 → V7.3 |
| `codegen-web/README.md` | Referência rápida do console de front-end |

---

## 12. Histórico de Versões

| Versão | Tema / documento de planejamento | Commit de implantação |
| --- | --- | --- |
| V1.2 / V1.3 | Geração com um clique / lote / rollback; pré-visualização de diff e sincronização estrutural | — (apenas em comentários de `src/api/gen.js`) |
| V2.0 | Rotas dinâmicas (árvore de menus entregue pelo back-end) | — |
| V3.0 / V3.1 | Livro-razão multi-sistema; ciclo multi-sistema + primeiros testes automatizados | V3.1 (174 linhas) |
| V4.2 | Resultados de geração rastreáveis e reversíveis (livro-razão de gravação) | V4.2 (292 linhas) |
| V4.3 | Regressão de templates e barreiras de qualidade | V4.3 (200 linhas) |
| V4.4 | Um mestre muitos detalhes, completude da gravação em cascata, divisão de templates | V4.4 (154 linhas) |
| V4.5 | Documentos de impressão A4 (zero dependências de terceiros) | V4.5 (127 linhas) |
| V5.0 | Sistema com uma frase: pipeline + modelagem por IA + base de esqueleto | — |
| V5.1 | Preenchimento automático de colunas de auditoria no DDL | V5.1 (100 linhas) |
| V5.2 | Etapa de inicialização de dados-semente no pipeline | V5.2 (107 linhas), 84ec8dd |
| V5.5 | Fechamento da geração multi-módulo | af73dd3 |
| V6.0 | Infraestrutura de microsserviços (Nacos / Gateway / Feign, terceiro modo micro) | V6.0 (111 linhas), 1deecbd |
| V6.1 | Motor duplo de workflow Activiti | V6.1 (100 linhas), 02b671e |
| V6.2 | Face de entrega de artefatos (CLI / itens de implantação / segurança de configuração) | V6.2 (114 linhas), 02b671e |
| V6.3 | Pacote de capacidade de pagamento (WeChat / Alipay + Mock) | V6.3 (104 linhas), 1c4ee6a, 22eede5 |
| V6.4 | Linha de base de permissões e segurança (persistência de token / BCrypt / permissões de API / proteção de login) | V6.4 (107 linhas), 007a202 |
| V6.5 | Mobile uni-app (H5 + WeChat Mini Program) | V6.5 (105 linhas), 779ac41 |
| V6.6 | Pacote de capacidade de arquivos | 98f3c09 |
| V6.7 | Aceitação de fábrica de testes de artefatos (test_enabled triestado + esqueleto H2) | V6.7 (134 linhas), 4e7d318 |
| V6.8 | Manutenibilidade dos artefatos (dryRun / skip protegido / parametrização de marca) | V6.8 (141 linhas), be6f858 |
| V6.9 | Central de documentação da plataforma (base do back-end, front-end concluído no mesmo lote) | — |
| V6.10 | Observabilidade da plataforma: Actuator + janela de métricas em memória + página de monitor embutida | — |
| V7.0 | Recomendações de páginas e menus por IA (preview / apply) | V7.0 (94 linhas), 45f2670 |
| V7.1 | Aprimoramento de dados-semente por IA (IA cuida da semântica, regras cuidam da estrutura) | V7.1 (42 linhas), 5ca6e72 |
| V7.2 | Entregáveis SQL gravados em `db/` após pipeline bem-sucedido | eebff88 |
| **V7.3** | **Confiabilidade da entrega: testes de artefatos compilados na autoverificação / saída unificada de entrega SQL / recomendações por IA por tabela** | **V7.3 (138 linhas)** |
| **V7.4** | Entrega repetível: implantável a partir de banco vazio, cada commit garantido executável | c306e29 |
| **V7.5** | Configuração de idiomas do projeto: lista de 206 idiomas / idioma padrão / idiomas habilitados, injeção de lang e dir nos gerados | 7a9afa0 + abac56d |
| **V8.0** | i18n de página inteira: framework de 206 idiomas (8 traduzidos profissionalmente), i18n de via dupla com contrato de diff zero em OFF | 9e5dd57 → 7d57aea |
| **V8.1** | Endurecimento de qualidade: arnês de contrato offline (9 modos, diff zero OFF) + testes unitários i18n-core + portão de CI + divisão honesta do README (framework 206 / traduzidos 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | Artefatos gerados modernizados para Vue 3 + Element Plus + Vite; verificados em execução (40/40 asserções, vite build com sucesso, 14 capturas incl. ar-SA RTL); 3 defeitos de execução corrigidos | 93a12d5 + e49330b  |
| **V9.1** | **Refatoração de manutenibilidade · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e  |
| **V9.2** | **Central de testes e centro de verificação · Test Center (7 tables: env/suite/case/dataset/run/result/schedule) + Verification Center (tc_run/tc_case_result/tc_artifact, 9 suites S1-S9, single-run 409 lock, `codegen.testcenter/v1`); 373/373 tests; 206/206 locales; output byte-identical** | V9.2 release  |
| **V9.3** | **Testes automatizados de artefatos gerados · Generated-artifact automated testing: S10 (mvn test + Surefire), S11 (API / TestContainers), S12 (E2E / Playwright); GEN trigger + TC_GEN_ROOT; 12-step pipeline green; real artifact run PASSED 20/20, run/case/artifact persisted** | V9.3 release, **Versão atual** |

---

> **V6.10 Observabilidade da plataforma**: integra Actuator + Micrometer, amostrando a cada 5 segundos em um buffer circular em memória (janela de 30 minutos), além de uma nova página embutida "Monitor do Sistema" — APIs de agregação `/monitor/*` no back-end + painéis ECharts no front-end. Apenas os endpoints `health/info/metrics` estão na lista branca; `/actuator/health` dispensa token, enquanto `metrics` etc. exigem autenticação; `env/beans/heapdump/threaddump/loggers` nunca são expostos. A janela de amostragem é por processo, perdida no reinício e não compartilhada entre instâncias. Consulte `docs/可观测性说明.md` (chinês) para detalhes.

## 13. Estado do Projeto

- A linha de versões planejadas atual chegou a **V9.3**, com evolução contínua de capacidades (modelagem por IA, aprimoramento de dados-semente, sistema de pacotes de capacidade etc.).
- Há um projeto de demonstração `demo_admin` (back-end 8140 / front-end 9040) cobrindo os cenários de árvore / CRUD / mestre-detalhe / somente leitura / impressão, executável de forma independente para verificação.
- Ambos os repositórios estão hospedados no Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Autor e Suporte Técnico

- **Autor**: programador chinês (desenvolvedor independente)
- **E-mail**: `hustxiayuhong@126.com`
- **Suporte técnico**: escreva o e-mail **em chinês**, incluindo a descrição do problema, o ambiente (JDK / MySQL / versão) e os passos para reproduzir

> Leio e respondo todos os e-mails. Relatos de bugs, sugestões de funcionalidades e propostas de colaboração são bem-vindos.

---

## 15. Comparação com ferramentas similares

CodeGen is neither a mere code snippet generator nor a full-stack low-code platform — it sits in a specific niche. Here is how it positions itself relative to the more commonly known tools in the Spring Boot / Java ecosystem.

### vs. Snippet Generators (JPA Buddy · MapStruct · Spring Initializr)

Tools like **JPA Buddy**, **MapStruct**, and **Spring Initializr** are great for scaffolding and small ergonomic wins, but they only produce **fragments**: an entity here, a mapper there, some DTO conversion helpers, or an empty starter project. You still need to wire up the CRUD pages, menus, routes, and permission codes by hand — and when the table changes, you re-do it.

CodeGen, by contrast, produces a **zero-edit, runnable vertical slice**: full backend (Entity → Mapper → Service → Controller → DTO/VO) + full frontend (`api.js` + Vue pages + A4 print page) + menu + route registration. Bring your DDL, click once, and the feature is live.

### vs. Scaffold Frameworks (JeecgBoot · RuoYi · dlow)

**JeecgBoot**, **RuoYi**, and similar frameworks ship a complete admin scaffold first, then bolt a code generator on top. You are required to **fork their scaffold**, carry its conventions, its authentication layer, its menu schema, its file layout, and — critically — its upgrade story. The generator **inherits the parent framework's biases** (e.g., dynamic datasource conventions, Shiro/Spring Security choices, front-end tech-lock).

CodeGen takes the opposite stance: **it never touches your project's infrastructure**. It assumes your project already has `Result`, `BaseEntity`, `PageQuery`, `PaginationInnerInterceptor`, and a `utils/request.js` that returns `{list, total}`. It only **drops business code into your existing layout**, alongside whatever auth stack, UI, and conventions you already chose. No fork. No convention hijack. Clean, surgical.

### vs. Built-in Generators (Yunzhai · JeecgBoot built-in · RuoYi built-in)

Many scaffolds now include a code generator inside them. The generated code works — but quality gates are usually weak: no real compilation check, no structured contract test, no template versioning, no rollback, and no change ledger. When a template change produces broken output, you won't find out until runtime.

CodeGen's **quality-gate chain** is its differentiator:

| Step | What it actually does |
| --- | --- |
| Preview & diff | Generates to memory, renders per-file diffs against disk, you confirm before anything is written |
| Write with backup | Every touched file is copied to `.gen-backup/<batchNo>/` before being overwritten |
| Marked-zone merge | Hand-written code inside `// gen:begin-end` blocks is preserved; only generated zones are replaced |
| In-memory self-check | A real `javac` compile of all generated back-end artifacts (not a regex lint) — nothing lands on disk unless it compiles |
| Independent class-path compile | Generated integration tests are compiled against your target project's classpath — missing dependencies fail at write time, not runtime |
| Four-state ledger | `gen_output` records every file as **created / merged / skipped / failed**, with byte count, MD5, and backup path |
| Rollback by batch | Any batch can be rolled back in one click, restoring exactly the state before that generation ran |

These seven links — preview → backup → merge → javac self-check → class-path compile → ledger → rollback — are **not present in the built-in generators of any popular scaffold framework** the author is aware of.
