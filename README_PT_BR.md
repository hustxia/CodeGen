# Majiang · Gerador de Código (CodeGen) — Introdução ao Projeto

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · **Português (Brasil)** · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · [Ελληνικά](README_EL.md) · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Um gerador de código multi-projeto para **Spring Boot + Vue**: a partir das estruturas de tabelas do banco de dados, produz código de negócio front-end e back-end **compilável, executável e pronto com menus e permissões**, e trata todo o ciclo de entrega de escrita em disco, backup, registro contábil e rollback.

GitHub: https://github.com/hustxia/CodeGen

## 1. O Que É Isto

CodeGen é uma fábrica de código "orientada a tabelas". Traga suas tabelas de negócio (via importação de banco de dados ou colando DDL), configure campos e chaves de geração, depois clique em "Gerar" — ele produz o back-end Entity/Mapper/Service/Controller/DTO/VO, o front-end `api.js` e páginas Vue, menus e rotas, até mesmo páginas de impressão A4 e esqueletos de testes de integração, e os grava com segurança no seu projeto de destino.

Ele não apenas "cospe código" — é um **pipeline de entrega com portões de qualidade e capacidade de rollback**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Que Problemas Ele Resolve

| Ponto dolorido | O que o CodeGen faz |
| --- | --- |
| Inferno de CRUD repetitivo — uma pilha de código padrão para cada tabela | Chaves por tabela produzem um front e back-end completos em um clique, incluindo CRUD, paginação, validação, importação/exportação |
| Incompatibilidades de campos e APIs entre front e back causando correções infinitas de integração | Os mesmos metadados da tabela conduzem tanto as APIs do back-end quanto as páginas do front-end — nenhum desvio semântico |
| Convenções de equipe difíceis de unificar; o estilo de cada um é diferente | Os templates são a convenção: 22 templates FreeMarker gerenciados centralmente, editáveis online com rollback de versão |
| Geradores "sobrescrevem e perdem" a lógica escrita manualmente | Mecanismo de mesclagem por bloco-marcador: o código escrito à mão é preservado, apenas as regiões geradas são substituídas |
| Código gerado não confiável até a compilação | Auto-verificação de compilação em memória (verdadeiro `javac`), verificações estruturais de contrato, pré-visualização de diff em disco — nada é gravado em disco a menos que compile |
| Criar um novo sistema do zero consome muito tempo | Pipeline "construir um sistema com uma frase": criar BD → scaffold → gerar → empacotar → registrar menus, totalmente automatizado |

---

## 3. Panorama de Capacidades

- **Entrada de tabelas**: análise de DDL, importação direta de banco de dados, assistente de entrada em lote, configuração de colunas derivada automaticamente (controles / validação / dicionários / relações).
- **Formatos de tabela**: tabela única, tabela em árvore (API de árvore + grade em árvore), mestre-detalhe (salvamento na mesma transação + abas de detalhe), tabelas de junção muitos-para-muitos.
- **Artefatos gerados**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + páginas Vue + páginas de impressão A4 + esqueletos de testes de integração.
- **Aprimoramentos de negócio**: dropdowns de relação, tradução de dicionário, mascaramento de dados, verificações de unicidade, vinculação de formulário, permissões de escopo de dados, importação/exportação Excel, fluxo de aprovação, pacotes de capacidade de pagamento / arquivo / mobile.
- **Portões de qualidade**: auto-verificação de compilação em memória (verdadeiro `javac`), verificações estruturais de contrato, verificações de consistência de permissão, pré-visualização de diff em disco, validação real de artefatos front-end.
- **Pouso seguro**: escrita em disco + backups em lote `.gen-backup`, mesclagem por bloco-marcador para preservar código escrito à mão, ledger de quatro estados `gen_output` (criado / mesclado / ignorado / falhou), rollback histórico.
- **Isolamento multi-sistema**: cada projeto de destino é uma "configuração de sistema" (caminhos front/back, nomes de pacote, prefixo de gateway, classpath de dependência, padrões de chaves de geração) — múltiplos projetos atendidos em paralelo.
- **Aprimoramentos de IA**: construir um sistema com uma frase (modelagem de IA), aprimoramento de dados-semente por IA, configuração recomendada por IA por tabela (pré-visualização de diff + aplicação marcada).

---

## 4. Cenários Típicos

| Cenário | Descrição |
| --- | --- |
| Tabela em árvore | Dados hierárquicos como categorias de produtos — API de árvore, grade em árvore e dropdown de seleção de pai produzidos automaticamente |
| CRUD de tabela única | Clientes, produtos, etc. — dropdowns de FK + tradução de dicionário + importação/exportação prontos para uso |
| Mestre-detalhe | Pedidos de venda + itens de pedido — salvamento na mesma transação, gaveta de detalhe com abas duplas (informações do mestre / detalhes) |
| Somente leitura / estatísticas | Fluxos de inventário, etc. — chaves de escrita desligadas, apenas consulta e exportação |
| Imprimir documentos | Marque a chave "imprimir" para produzir uma página de impressão A4 e rota, com cabeçalho + tabela de detalhes |
| Construção de sistema em uma frase | Descreva o domínio de negócio — a IA cria BD e tabelas + scaffold + geração completa + registro de menus de uma vez |

> Todos os cenários têm demos executáveis (projeto `demo_admin`) com capturas de tela — veja `docs/典型场景操作指南-演示后台demo.md` (em chinês).

---

## 5. Fluxo de Trabalho Principal

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Pré-visualização de diff**: os artefatos gerados são comparados arquivo a arquivo com o estado atual em disco; você confirma antes de qualquer escrita, evitando sobrescritas acidentais.
- **Ledger de geração**: cada arquivo registra sua ação, contagem de bytes, MD5 e local de backup; um lote inteiro pode ser revertido.

---

## 6. Construir um Sistema com Uma Frase (Pipeline)

Uma capacidade de alto nível voltada para "criar um back-end do zero". Após descrever o domínio de negócio, o pipeline avança automaticamente por 5 estágios:

| Estágio | Significado |
| --- | --- |
| `CHECK` | Validar configuração do projeto de destino e classpath de dependência |
| `WRITE` | Criar banco de dados e tabelas, escrever scaffold e artefatos gerados |
| `COMPILE` | Back-end `mvn package` / build do front-end, auto-verificação de compilação em memória |
| `MENU` | Produzir e registrar permissões de menu (duas saídas: SQL de artefato / escrita direta interna) |
| `ROUTE` | Reconstruir fragmentos de rota do front-end |

A falha em qualquer estágio aborta imediatamente com um erro legível para fácil localização.

---

## 7. Arquitetura do Sistema

Front-end e back-end separados, dois repositórios trabalhando juntos:

| Repositório | Papel | Stack |
| --- | --- | --- |
| `codegen-server` | Back-end: motor de geração, templates, validação, ledger, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end: console (configuração de tabela / templates / ledger / gerenciamento de sistema) | Vue 2.7 · Element UI · axios · Vue Router (modo history) |

Camadas do back-end:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Capacidades de base que o projeto de destino deve fornecer** (o gerador produz apenas código de negócio; uma base ausente falha silenciosamente):

1. Plugin de paginação MyBatis-Plus (`PaginationInnerInterceptor`)
2. Classes de base como `Result` / `BaseEntity` / `PageQuery` (o prefixo do pacote deve corresponder a `root-package`)
3. `utils/request.js` do front-end com `toPageData` (converte `Page{records,total}` em `{list,total}`)
4. Dependências `springdoc-openapi-starter-webmvc-ui` (o código gerado usa `@Operation`) e `poi-ooxml` (Excel)

---

## 8. Tech Stack em Resumo

| Dimensão | Escolha |
| --- | --- |
| Linguagem/framework do back-end | Java 21 / Spring Boot 3.2.5 |
| Persistência | MyBatis-Plus 3.5.5 |
| Motor de templates | FreeMarker (22 templates-semente, edição online + rollback de versão) |
| Segurança | Autenticação JWT |
| Banco de dados | MySQL 8 |
| Front-end | Vue 2.7 + Element UI |
| IA local | qwen2.5-coder:7b (modelagem / config recomendada / aprimoramento de dados-semente) |
| Documentação de API | springdoc-openapi (Swagger UI) |

---

## 9. Início Rápido

### 1. Criar o banco de dados (13 tabelas de plataforma + conta padrão admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configurar o projeto de destino

Copie o modelo de configuração e ajuste aos seus caminhos locais:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` é excluído por `.gitignore` e não será commitado. Configurações principais:

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # write-to-disk base for artifacts
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # frontend subdirectory for monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefix for foundation-class imports in artifacts
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

Exporte o diretório de dependências da auto-verificação assim:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Iniciar o back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

O serviço usa a porta **8120** por padrão; documentação de API em `/swagger-ui/index.html`.

### 4. Iniciar o front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

O front-end usa a porta **9530** por padrão, fazendo proxy de `/dev-api` para a porta 8120 do back-end. Conta padrão `admin / admin123`.

### 5. Itens de configuração principais

| Configuração | Descrição |
| --- | --- |
| `gen.output.*` | Caminhos de escrita de artefatos, nomes de pacote, mapeamento de módulo |
| `gen.selfcheck.classpath` | Classpath de dependência para auto-verificação de compilação em memória, suporta `dir/*` |
| `gen.menu.outlet` | `artifact` produz apenas SQL de menu e fragmentos de rota; `internal` também escreve o próprio `sys_menu` da plataforma |

---

## 10. Mapa de Documentação

| Documento | Conteúdo |
| --- | --- |
| `docs/代码生成器-操作手册.md` (em chinês) | Implantação, início em 5 minutos, configuração de tabela, entrada em lote, gerenciamento de templates, ledger, catálogo de API, pipeline completo (com capturas de tela) |
| `docs/典型场景操作指南-演示后台demo.md` (em chinês) | 6 tabelas cobrindo 5 cenários típicos, com demo executável e capturas de tela de execução real |
| `docs/项目介绍.md` | Documento de introdução do projeto, mesma fonte deste arquivo |
| `docs/代码生成器-Vx.x-规划.md` | Planos de versão e inventários de recursos de V3.1 → V7.3 |
| `codegen-web/README.md` | Referência de início rápido do console front-end |

---

## 11. Histórico de Versões

| Versão | Tema / documento de plano | Commit de landing |
| --- | --- | --- |
| V1.2 / V1.3 | Geração / lote / rollback com um clique; pré-visualização de diff e sincronização de estrutura | — (apenas em comentários de `src/api/gen.js`) |
| V2.0 | Rotas dinâmicas (árvore de menu entregue pelo back-end) | — |
| V3.0 / V3.1 | Ledger multi-sistema; loop multi-sistema + primeiros testes automatizados | V3.1 (174 linhas) |
| V4.2 | Resultados de geração rastreáveis e reversíveis (ledger de escrita) | V4.2 (292 linhas) |
| V4.3 | Regressão de templates e barreiras de qualidade | V4.3 (200 linhas) |
| V4.4 | Um-mestre-muitos-detalhes, completude de escrita em cascata, trilhas de template | V4.4 (154 linhas) |
| V4.5 | Documentos de impressão A4 (zero dependências de terceiros) | V4.5 (127 linhas) |
| V5.0 | Construção de sistema em uma frase: pipeline + modelagem de IA + base de scaffold | — |
| V5.1 | Auto-completar colunas de auditoria DDL | V5.1 (100 linhas) |
| V5.2 | Etapa de inicialização de dados-semente do pipeline | V5.2 (107 linhas), 84ec8dd |
| V5.5 | Conclusão da geração de projeto multi-módulo | af73dd3 |
| V6.0 | Infraestrutura de microsserviços (Nacos / Gateway / Feign, terceiro modo "micro") | V6.0 (111 linhas), 1deecbd |
| V6.1 | Motor duplo de fluxo de trabalho Activiti | V6.1 (100 linhas), 02b671e |
| V6.2 | Superfície de entrega de artefatos (CLI / artefatos de implantação / segurança de config) | V6.2 (114 linhas), 02b671e |
| V6.3 | Pacote de capacidade de pagamento (WeChat / Alipay + Mock) | V6.3 (104 linhas), 1c4ee6a, 22eede5 |
| V6.4 | Linha de base de permissão e segurança (persistência de token / BCrypt / permissões de API / proteção de login) | V6.4 (107 linhas), 007a202 |
| V6.5 | Mobile uni-app (H5 + Mini Programa WeChat) | V6.5 (105 linhas), 779ac41 |
| V6.6 | Pacote de capacidade de arquivos | 98f3c09 |
| V6.7 | Aceitação da fábrica de testes de artefatos (tri-estado test_enabled + esqueleto H2) | V6.7 (134 linhas), 4e7d318 |
| V6.8 | Mantenibilidade de artefatos (dryRun / skip protegido / parametrização de marca) | V6.8 (141 linhas), be6f858 |
| V6.9 | Central de documentação da plataforma (base do back-end, front-end concluído no mesmo lote) | — |
| V6.10 | Observabilidade da plataforma: Actuator + janela de métrica em memória + página de monitor embutida | — |
| V7.0 | Recomendações de página e menu por IA (pré-visualização / aplicar) | V7.0 (94 linhas), 45f2670 |
| V7.1 | Aprimoramento de dados-semente por IA (IA possui semântica, regras possuem estrutura) | V7.1 (42 linhas), 5ca6e72 |
| V7.2 | Entregáveis SQL gravados em `db/` após pipeline bem-sucedido | eebff88 |
| **V7.3** | **Confiabilidade de entrega: testes de artefatos compilados auto-verificados / saída unificada de entrega SQL / recomendações de IA por tabela** | **V7.3 (138 linhas)** |
| **V7.4** | Entrega repetível: implantável a partir de um banco de dados vazio, todo commit garantido executável | **Versão atual** |

---

> **V6.10 Observabilidade da plataforma**: integra Actuator + Micrometer, amostrando a cada 5 segundos em um buffer circular em memória (janela de 30 minutos), mais uma nova página embutida "Monitor do Sistema" — APIs de agregação `/monitor/*` do back-end + painéis ECharts do front-end. Apenas endpoints `health/info/metrics` estão na lista branca; `/actuator/health` é livre de token enquanto `metrics` etc. exigem autenticação; `env/beans/heapdump/threaddump/loggers` nunca são expostos. A janela de amostragem é apenas por processo, perdida na reinicialização e não compartilhada entre instâncias. Veja `docs/可观测性说明.md` (em chinês) para detalhes.

## 12. Status do Projeto

- A linha de versão planejada atual alcançou **V7.3** (capacidades continuam iterando: modelagem de IA, aprimoramento de dados-semente, sistema de pacotes de capacidade, etc.).
- Um projeto demo companheiro `demo_admin` (back-end 8140 / front-end 9040) cobre cenários de árvore / CRUD / mestre-detalhe / somente leitura / impressão e roda de forma independente para verificação.
- Ambos os repositórios estão hospedados no Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 13. Autor & Suporte

- **Autor**: um programador chinês (desenvolvedor independente)
- **Email**: `hustxiayuhong@126.com`
- **Suporte técnico**: por favor escreva seu e-mail **em chinês**, incluindo a descrição do problema, ambiente de execução (JDK / MySQL / versão) e passos de reprodução

> Eu leio e respondo a todos os e-mails. Relatórios de bugs, solicitações de recursos e consultas de colaboração são todos bem-vindos.
