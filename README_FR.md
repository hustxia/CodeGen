# Majiang · Générateur de code (CodeGen) — Présentation du projet

> Langues: [中文](README_ZH.md) · [English](README.md) · [日本語](README_JA.md) · [Português](README_PT.md) · [Español](README_ES.md) · [한국어](README_KO.md) · **Français** · [Deutsch](README_DE.md)

> Un générateur de code multi-projets pour **Spring Boot + Vue** : à partir de la structure des tables de la base de données, il produit du code métier front-end et back-end **compilable, exécutable et doté des menus et des permissions**, et assure la boucle de livraison complète — écriture sur disque, sauvegarde, tenue du registre et retour arrière.

---
Adresse GitHub : https://github.com/hustxia/CodeGen


## 1. Qu'est-ce que c'est

CodeGen est une usine à code « pilotée par les tables ». Il suffit de brancher vos tables métier (import depuis la base ou collage d'un DDL), de configurer les champs et les options de génération, puis de cliquer sur « Générer » : il produit à votre place les Entity/Mapper/Service/Controller/DTO/VO du back-end, les `api.js` et les pages Vue du front-end, les menus et les routes, et même les pages d'impression A4 et les squelettes de tests d'intégration — puis les écrit en toute sécurité dans votre projet cible.

Il ne se contente pas de « cracher du code » : c'est un **pipeline de livraison doté de barrières qualité et d'une capacité de retour arrière** :

```
Prise en charge de la table (import BD / DDL)
   → Configuration des champs et des options (avec recommandations IA)
      → Génération en un clic : auto-vérification → sauvegarde → écriture sur disque → compilation/validation → enregistrement des menus → reconstruction des routes
         → Tenue du registre → retour arrière possible
```

---

## 2. Quels problèmes cela résout

| Point de douleur | Ce que fait CodeGen |
| --- | --- |
| Enfer du CRUD répétitif : pour chaque table, une montagne de code de remplissage à écrire à la main | Des options au niveau de la table produisent en un clic un front-end et un back-end complets, avec création/lecture/mise à jour/suppression, pagination, validation, import/export |
| Champs et API front/back désalignés, allers-retours d'intégration sans fin | Les mêmes métadonnées de table pilotent à la fois les API back-end et les pages front-end : zéro dérive sémantique |
| Conventions d'équipe difficiles à unifier, chacun code à sa manière | Le template fait la norme : 22 templates FreeMarker gérés de façon centralisée, modifiables en ligne avec retour arrière de version |
| Les générateurs « écrasent et perdent » la logique écrite à la main | Mécanisme de fusion par blocs de marqueurs (marker block) : le code écrit à la main est conservé, seules les zones générées sont remplacées |
| Code généré peu fiable, les problèmes n'apparaissent qu'à la compilation | Auto-vérification par compilation en mémoire (vrai `javac`), contrôle des contrats structurels, aperçu du diff sur disque : rien n'est écrit sur disque si la compilation échoue |
| Monter l'échafaudage d'un nouveau système depuis zéro prend du temps | Pipeline « un système en une phrase » : création de la base → échafaudage → génération → packaging → enregistrement des menus, entièrement automatisé |

---

## 3. Panorama des capacités cœur

- **Prise en charge des tables** : analyse de DDL, import direct des tables de la base, assistant de reprise en lot, déduction automatique de la configuration des colonnes (contrôle / validation / dictionnaire / relation).
- **Formes de tables** : table simple, table arborescente (API tree + grille arborescente), maître-détail (enregistrement dans la même transaction + onglets de détail), tables de jonction plusieurs-à-plusieurs.
- **Artefacts générés** : Entity / Mapper / Service / Controller / DTO / VO + `api.js` + pages Vue + pages d'impression A4 + squelettes de tests d'intégration.
- **Enrichissements métier** : listes déroulantes de relations, traduction par dictionnaire, masquage de données, contrôle d'unicité, liaison de formulaires, permissions de données, import/export Excel, workflow d'approbation, packs de capacités paiement / fichiers / mobile.
- **Barrières qualité** : auto-vérification par compilation en mémoire (vrai `javac`), contrôle des contrats structurels, contrôle de cohérence des permissions, aperçu du diff sur disque, validation réelle des artefacts front-end.
- **Atterrissage sûr** : écriture sur disque + sauvegardes par lot `.gen-backup`, fusion par blocs de marqueurs préservant le code écrit à la main, registre `gen_output` à quatre états (créé / fusionné / ignoré / échoué), retour arrière historique.
- **Isolation multi-systèmes** : chaque projet cible est une « configuration système » (chemins front/back, nom de package, préfixe de passerelle, classpath des dépendances, valeurs par défaut des options de génération) ; plusieurs projets peuvent être servis en parallèle.
- **Enrichissements IA** : un système en une phrase (modélisation IA), enrichissement IA des données de seed, configuration recommandée par l'IA au niveau de la table (aperçu du diff + application par cases à cocher).

---

## 4. Scénarios typiques

| Scénario | Description |
| --- | --- |
| Table arborescente | Données hiérarchiques telles que les catégories de produits : génération automatique de l'API tree, de la grille arborescente et de la liste déroulante de sélection du parent |
| CRUD table simple | Clients, produits, etc. : listes déroulantes de clés étrangères + traduction par dictionnaire + import/export prêts à l'emploi |
| Maître-détail | Commande de vente + lignes de commande : enregistrement dans la même transaction, tiroir de détail à deux onglets (informations de l'entête / détails) |
| Lecture seule / statistiques | Flux de stock, etc. : options de création, modification et suppression désactivées, seules la consultation et l'export sont conservés |
| Documents imprimables | Cochez l'option « impression » pour produire une page d'impression A4 et sa route, avec entête + tableau de détail |
| Un système en une phrase | Décrivez le domaine métier : l'IA crée la base et les tables + l'échafaudage + la génération complète + l'enregistrement des menus, d'un seul trait |

> Tous ces scénarios disposent d'une démo exécutable (projet `demo_admin`) et de captures d'écran associées ; voir `docs/典型场景操作指南-演示后台demo.md` (chinois).

---

## 5. Workflow cœur

```
┌─────────────────┐ → ┌─────────────────────┐ → ┌───────────────────────────────────────────────────────┐ → ┌──────────────────────┐
│ Prise en charge │   │ Configuration des   │   │ Génération en un clic (auto-vérif. → sauvegarde →     │   │ Registre · Retour    │
│ de la table     │   │ champs / options    │   │ écriture → compilation → menu → routes)               │   │ arrière              │
└─────────────────┘   └─────────────────────┘   └───────────────────────────────────────────────────────┘   └──────────────────────┘
   Import BD/DDL         contrôles / validation /     Écriture sur disque uniquement si toutes les barrières     Traçabilité par lot
                         dictionnaires               qualité passent ; le code écrit à la main est préservé
```

- **Aperçu du diff** : les artefacts générés sont comparés fichier par fichier à l'état actuel du disque ; l'écriture n'a lieu qu'après confirmation, afin d'éviter tout écrasement accidentel.
- **Registre de génération** : pour chaque fichier sont enregistrés l'action, le nombre d'octets, le MD5 et l'emplacement de sauvegarde ; un lot entier peut être restauré.

---

## 6. Un système en une phrase (Pipeline)

Capacité avancée destinée à « monter un back-office à partir de zéro ». Après la description du domaine métier, le pipeline progresse automatiquement en 5 étapes :

| Étape | Signification |
| --- | --- |
| `CHECK` | Valider la configuration du projet cible et le classpath des dépendances |
| `WRITE` | Créer la base et les tables, écrire l'échafaudage et les artefacts générés |
| `COMPILE` | `mvn package` du back-end / build du front-end, auto-vérification par compilation en mémoire |
| `MENU` | Produire et enregistrer les permissions de menu (double sortie : SQL en artefact / écriture directe en mode internal) |
| `ROUTE` | Reconstruire les fragments de routes front-end |

L'échec d'une étape interrompt immédiatement le processus et renvoie une erreur lisible, ce qui facilite le diagnostic.

---

## 7. Architecture du système

Front-end et back-end séparés, deux dépôts qui collaborent :

| Dépôt | Rôle | Stack |
| --- | --- | --- |
| `codegen-server` | Back-end : moteur de génération, templates, validation, registre, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Front-end : console (configuration des tables / templates / registre / administration système) | Vue 2.7 · Element UI · axios · Vue Router (history) |

Couches du back-end :

```
controller/    couche API (chaîne de génération Gen* / administration système Sys* / pipeline)
service/impl/  implémentations cœur (chaîne de génération, moteur de templates, fusion, validation, exécution du pipeline)
util/          moteur de templates, assemblage du modèle, fusion par blocs de marqueurs, compilation d'auto-vérification
config/        configuration de sortie, configuration d'auto-vérification, seeder de templates
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 templates FreeMarker (groupes backend / frontend / shared / test)
```

**Capacités de socle que le projet cible doit impérativement fournir** (le générateur ne produit que le code métier ; un socle manquant provoque des défaillances silencieuses) :

1. Plugin de pagination MyBatis-Plus (`PaginationInnerInterceptor`)
2. Classes de socle telles que `Result` / `BaseEntity` / `PageQuery` (le préfixe de package doit correspondre à `root-package`)
3. `toPageData` dans le fichier `utils/request.js` du front-end (convertit `Page{records,total}` en `{list,total}`)
4. Les dépendances `springdoc-openapi-starter-webmvc-ui` (les artefacts utilisent `@Operation`) et `poi-ooxml` (Excel)

---

## 8. Stack technique en un coup d'œil

| Dimension | Choix |
| --- | --- |
| Langage / framework back-end | Java 21 / Spring Boot 3.2.5 |
| Persistance | MyBatis-Plus 3.5.5 |
| Moteur de templates | FreeMarker (22 templates de seed, édition en ligne + retour arrière de version) |
| Sécurité | Authentification JWT |
| Base de données | MySQL 8 |
| Front-end | Vue 2.7 + Element UI |
| IA locale | qwen2.5-coder:7b (modélisation / configuration recommandée / enrichissement des données de seed) |
| Documentation d'API | springdoc-openapi (Swagger UI) |

---

## 9. Démarrage rapide

### 1. Créer la base (13 tables de plateforme + compte par défaut admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configurer le projet cible

Copiez le template de configuration et adaptez-le aux chemins de votre machine :

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` est exclu par `.gitignore` et ne sera pas commité. Principaux paramètres :

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # base d'écriture sur disque des artefacts
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # sous-répertoire front-end en cas de monorepo
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # préfixe des imports de classes de socle dans les artefacts
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

Exportez le répertoire des dépendances d'auto-vérification de la façon suivante :

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Démarrer le back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Le service écoute par défaut sur le port **8120** ; documentation d'API sur `/swagger-ui/index.html`.

### 4. Démarrer le front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

Le front-end écoute par défaut sur le port **9530**, avec `/dev-api` proxifié vers le back-end sur le port 8120. Compte par défaut `admin / admin123`.

### 5. Paramètres clés

| Paramètre | Description |
| --- | --- |
| `gen.output.*` | Chemins d'écriture des artefacts, noms de packages, mapping des modules |
| `gen.selfcheck.classpath` | Classpath des dépendances pour la compilation en mémoire d'auto-vérification, prend en charge `dir/*` |
| `gen.menu.outlet` | `artifact` ne produit que le SQL des menus et les fragments de routes ; `internal` écrit en plus dans le `sys_menu` de la plateforme elle-même |

---

## 10. Navigation dans la documentation

| Document | Contenu |
| --- | --- |
| `docs/代码生成器-操作手册.md` (chinois) | Déploiement, prise en main en cinq minutes, configuration des tables, reprise en lot, gestion des templates, registre, catalogue d'API, pipeline complet (avec captures d'écran réelles) |
| `docs/典型场景操作指南-演示后台demo.md` (chinois) | 6 tables couvrant 5 scénarios typiques, avec démo exécutable et captures d'exécution réelle |
| `docs/项目介绍.md` (chinois) | Document de présentation du projet, de même source que le présent fichier |
| `docs/代码生成器-Vx.x-规划.md` (chinois) | Plans de version et inventaire des fonctionnalités de V3.1 → V7.3 |
| `codegen-web/README.md` | Référence de prise en main rapide de la console front-end |

---

## 11. Évolution des versions

| Version | Thème / document de planification | Commit de livraison |
| --- | --- | --- |
| V1.2 / V1.3 | Génération en un clic / lot / retour arrière ; aperçu du diff et synchronisation de structure | — (uniquement dans les commentaires de `src/api/gen.js`) |
| V2.0 | Routes dynamiques (arbre de menus fourni par le back-end) | — |
| V3.0 / V3.1 | Registre multi-systèmes ; boucle multi-systèmes + premiers tests automatisés | V3.1 (174 lignes) |
| V4.2 | Résultats de génération traçables et réversibles (registre d'écriture) | V4.2 (292 lignes) |
| V4.3 | Régression des templates et garde-fous qualité | V4.3 (200 lignes) |
| V4.4 | Un maître et plusieurs détails, écriture en cascade complète, voies de templates séparées | V4.4 (154 lignes) |
| V4.5 | Documents imprimables A4 (zéro dépendance tierce) | V4.5 (127 lignes) |
| V5.0 | Un système en une phrase : pipeline + modélisation IA + socle d'échafaudage | — |
| V5.1 | Complétion automatique des colonnes d'audit DDL | V5.1 (100 lignes) |
| V5.2 | Étape d'initialisation des données de seed dans le pipeline | V5.2 (107 lignes), 84ec8dd |
| V5.5 | Finalisation de la génération de projets multi-modules | af73dd3 |
| V6.0 | Infrastructure microservices (Nacos / Gateway / Feign, troisième mode « micro ») | V6.0 (111 lignes), 1deecbd |
| V6.1 | Double moteur de workflow Activiti | V6.1 (100 lignes), 02b671e |
| V6.2 | Surface de livraison des artefacts (CLI / artefacts de déploiement / sécurité de la configuration) | V6.2 (114 lignes), 02b671e |
| V6.3 | Pack de capacités paiement (WeChat / Alipay + Mock) | V6.3 (104 lignes), 1c4ee6a, 22eede5 |
| V6.4 | Ligne de base sécurité et permissions (persistance du token / BCrypt / permissions d'API / protection de la connexion) | V6.4 (107 lignes), 007a202 |
| V6.5 | Mobile uni-app (H5 + mini-programme WeChat) | V6.5 (105 lignes), 779ac41 |
| V6.6 | Pack de capacités fichiers | 98f3c09 |
| V6.7 | Recette des artefacts par les tests (tri-état `test_enabled` + squelette H2) | V6.7 (134 lignes), 4e7d318 |
| V6.8 | Maintenabilité des artefacts (dryRun / saut des fichiers protégés / paramétrage de la marque) | V6.8 (141 lignes), be6f858 |
| V6.9 | Centre de documentation de la plateforme (socle back-end, finalisation front-end dans le même lot) | — |
| V6.10 | Observabilité de la plateforme : Actuator + fenêtre de métriques en mémoire + page de supervision embarquée | — |
| V7.0 | Recommandations IA de pages et de menus (preview / apply) | V7.0 (94 lignes), 45f2670 |
| V7.1 | Enrichissement IA des données de seed (l'IA gère la sémantique, les règles gèrent la structure) | V7.1 (42 lignes), 5ca6e72 |
| V7.2 | Écriture des livrables SQL dans `db/` après succès du pipeline | eebff88 |
| **V7.3** | **Fiabilité de la livraison : tests des artefacts compilés par auto-vérification / sortie unique de livraison SQL / recommandations IA au niveau de la table** | **V7.3 (138 lignes)** |
| **V7.4** | **Livraison reproductible : déployable depuis une base vide, chaque commit garanti exécutable** | **Version actuelle** |

---

> **V6.10 Observabilité de la plateforme** : intégration d'Actuator + Micrometer, échantillonnage toutes les 5 secondes dans un tampon circulaire en mémoire (fenêtre de 30 minutes), et ajout d'une page embarquée « Supervision système » — API d'agrégation back-end `/monitor/*` + panneaux ECharts côté front-end. Seuls les endpoints `health/info/metrics` sont autorisés par liste blanche ; `/actuator/health` est accessible sans token, tandis que `metrics` et d'autres exigent une authentification ; `env/beans/heapdump/threaddump/loggers` ne sont jamais exposés. La fenêtre d'échantillonnage est strictement interne au processus : perdue au redémarrage, non partagée entre plusieurs instances. Voir `docs/可观测性说明.md` (chinois) pour les détails.

## 12. État du projet

- La ligne de versions planifiée atteint aujourd'hui **V7.3** ; les capacités continuent d'évoluer (modélisation IA, enrichissement des données de seed, système de packs de capacités, etc.).
- Un projet de démonstration d'accompagnement `demo_admin` (back-end 8140 / front-end 9040) est disponible : il couvre les scénarios arborescent / CRUD / maître-détail / lecture seule / impression et peut être lancé indépendamment pour vérification.
- Les deux dépôts sont hébergés sur Gitee : `https://gitee.com/hustxiayuhong/CodeGen.git`
