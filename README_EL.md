# Majiang · Δημιουργός κώδικα (CodeGen) — Εισαγωγή έργου

> Languages: [简体中文](README_ZH.md) · [繁體中文](README_ZH_TW.md) · [English](README.md) · [日本語](README_JA.md) · [한국어](README_KO.md) · [Türkçe](README_TR.md) · [Русский](README_RU.md) · [Tiếng Việt](README_VI.md) · [ไทย](README_TH.md) · [Deutsch](README_DE.md) · [Español](README_ES.md) · [Français](README_FR.md) · [Українська](README_UK.md) · [Polski](README_PL.md) · [Português (Brasil)](README_PT_BR.md) · [Português](README_PT.md) · [العربية](README_AR.md) · [فارسی](README_FA.md) · [Bahasa Indonesia](README_ID.md) · [Italiano](README_IT.md) · **Ελληνικά** · [മലയാളം](README_ML.md) · [Roman Urdu](README_UR_ROMA.md) · [اردو](README_UR.md) · [Română](README_RO.md) · [Български](README_BG.md) · [हिन्दी](README_HI.md) · [বাংলা](README_BN.md) · [Nederlands](README_NL.md) · [Svenska](README_SV.md) · [Dansk](README_DA.md) · [Suomi](README_FI.md) · [Norsk](README_NO.md) · [Čeština](README_CS.md) · [Magyar](README_HU.md) · [Hrvatski](README_HR.md) · [Slovenčina](README_SK.md) · [Slovenščina](README_SL.md) · [Eesti](README_ET.md) · [עברית](README_HE.md) · [தமிழ்](README_TA.md) · [తెలుగు](README_TE.md) · [မြန်မာ](README_MY.md) · [ខ្មែរ](README_KM.md) · [Filipino](README_TL.md) · [Kiswahili](README_SW.md) · [Հայերեն](README_HY.md) · [ქართული](README_KA.md)

---

> Ένας δημιουργός κώδικα πολλαπλών έργων για **Spring Boot + Vue**: ξεκινώντας από τη δομή των πινάκων της βάσης δεδομένων, παράγει εταιρικό κώδικα front-end και back-end **με δυνατότητα μεταγλώττισης, εκτέλεσης και ετοιμότητας μενού και δικαιωμάτων**, και χειρίζεται ολόκληρο τον κύκλο παράδοσης: εγγραφή σε δίσκο, αντίγραφο ασφαλείας, καταχώριση στο καθολικό και επαναφορά (rollback).

GitHub: https://github.com/hustxia/CodeGen

## 1. Τι είναι αυτό

Το CodeGen είναι ένα «εργοστάσιο κώδικα» οδηγούμενο από πίνακες. Φέρε τους πίνακες επιχειρηματικής λογικής σου (μέσω εισαγωγής βάσης δεδομένων ή επικολλώντας DDL), ρύθμισε τα πεδία και τους διακόπτες δημιουργίας, και μετά πάτα «Δημιουργία» — παράγει το back-end Entity/Mapper/Service/Controller/DTO/VO, το front-end `api.js` και τις σελίδες Vue, τα μενού και τις διαδρομές, ακόμη και σελίδες εκτύπωσης A4 και σκελετούς ενσωματωμένων δοκιμών (integration test), και τα εγγράφει με ασφάλεια στο έργο-στόχο σου.

Δεν απλώς «βγάζει κώδικα» — είναι μια **γραμμή παράδοσης με πύλες ποιότητας και δυνατότητα επαναφοράς**:

```
Table intake (DB import / DDL)
   → Field & switch configuration (with AI recommendations)
      → One-click generation: self-check → backup → write to disk → compile/validate → register menu → rebuild routes
         → Ledger bookkeeping → rollback-capable
```

---

## 2. Ποιοι είναι οι χρήστες και πώς θα το χρησιμοποιήσουν

| Χρήστες | Τυπικές ανάγκες | Η διαδρομή που παρέχει η πλατφόρμα |
| --- | --- | --- |
| Προγραμματιστής back-end | Έχουν μια σειρά πινάκων (ή DDL) και θέλουν να στήσουν γρήγορα CRUD | Σελίδα ρύθμισης πινάκων: εισαγωγή → ρύθμιση πεδίων → δημιουργία με ένα κλικ |
| Προγραμματιστής Full-stack / ανεξάρτητος | θέλουν να στήσουν ένα πλήρες σύστημα από το μηδέν (με σύνδεση, δικαιώματα, λεξικό) | Σελίδα pipeline: Χτίσε ένα σύστημα με μία πρόταση |
| Επικεφαλής ομάδας | θέλουν να ενοποιήσουν το στυλ κώδικα, τα πρότυπα κώδικα και τη δομή καταλόγων | Διαχείριση συστήματος: ένα σύστημα = μία ρύθμιση εξόδου, ισχύει μαζικά |
| Συντηρητής πλατφόρμας | θέλουν να αλλάξουν πρότυπα χωρίς να αλλάξουν κώδικα | Διαχείριση προτύπων: online επεξεργασία, αποθήκευση με άμεση ισχύ, υποστηρίζει επαναφορά εκδόσεων |

## 3. Ποια προβλήματα επιλύει

| Σημείο πόνου | Τι κάνει το CodeGen |
| --- | --- |
| Κόλαση επαναλαμβανόμενου CRUD — μια σωρός boilerplate για κάθε πίνακα | Οι διακόπτες ανά πίνακα παράγουν ένα πλήρες front-end και back-end με ένα κλικ, συμπεριλαμβανομένων CRUD, σελιδοποίησης, επικύρωσης, εισαγωγής/εξαγωγής |
| Ασυμφωνίες πεδίων και API μεταξύ front-end και back-end που προκαλούν άπειρες διορθώσεις ενσωμάτωσης | Τα ίδια μεταδεδομένα του πίνακα οδηγούν τόσο τα back-end APIs όσο και τις σελίδες front-end — μηδενική σημασιολογική απόκλιση |
| Οι συμβάσεις της ομάδας δυσκολεύουν στην ενοποίηση· το στυλ του καθενός διαφέρει | Τα πρότυπα (templates) είναι η σύμβαση: 22 πρότυπα FreeMarker κεντρικά διαχειριζόμενα, επεξεργάσιμα online με επαναφορά εκδόσεων |
| Οι δημιουργοί «αντικαθιστούν και χάνουν» τη χειρόγραφη λογική | Μηχανισμός συγχώνευσης τμημάτων-δεικτών (marker-block): ο χειρόγραφος κώδικας διατηρείται, αντικαθίστανται μόνο οι παραγόμενες περιοχές |
| Ο παραγόμενος κώδικας δεν είναι αξιόπιστος μέχρι τη μεταγλώττιση | Αυτοέλεγχος μεταγλώττισης στη μνήμη (πραγματικό `javac`), δομικοί έλεγχοι συμβολαίου, προεπισκόπηση diff στον δίσκο — τίποτα δεν προσγειώνεται στον δίσκο αν δεν μεταγλωττίζεται |
| Η δημιουργία ενός νέου συστήματος από το μηδέν διαρκεί πολύ | Γραμμή «χτίσε ένα σύστημα με μία πρόταση»: δημιουργία DB → scaffold → δημιουργία → πακετάρισμα → εγγραφή μενού, πλήρως αυτοματοποιημένο |

---

## 4. Πανοραμική δυνατοτήτων

- **Εισαγωγή πινάκων**: ανάλυση DDL, άμεση εισαγωγή από βάση δεδομένων, οδηγός μαζικής εισαγωγής, αυτόματη παραγωγή ρύθμισης στηλών (έλεγχοι / επικύρωση / λεξικά / σχέσεις).
- **Μορφές πινάκων**: μονός πίνακας, δεντρικός πίνακας (δεντρικό API + δεντρικό πλέγμα), master-detail (αποθήκευση στην ίδια συναλλαγή + καρτέλες λεπτομερειών), πίνακες σύνδεσης many-to-many.
- **Παραγόμενα τεχνουργήματα (artifacts)**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + σελίδες Vue + σελίδες εκτύπωσης A4 + σκελετοί ενσωματωμένων δοκιμών.
- **Βελτιώσεις επιχειρηματικής λογικής**: αναδυόμενα σχέσεων, μετάφραση λεξικών, απόκρυψη δεδομένων, έλεγχοι μοναδικότητας, σύνδεση φορμών, δικαιώματα εμβέλειας δεδομένων, εισαγωγή/εξαγωγή Excel, ροή εγκρίσεων, πακέτα δυνατοτήτων πληρωμής / αρχείου / κινητού.
- **Πύλες ποιότητας**: αυτοέλεγχος μεταγλώττισης στη μνήμη (πραγματικό `javac`), δομικοί έλεγχοι συμβολαίου, έλεγχοι συνέπειας δικαιωμάτων, προεπισκόπηση diff στον δίσκο, πραγματική επικύρωση τεχνουργημάτων front-end.
- **Ασφαλής προσγείωση**: εγγραφή σε δίσκο + μαζικά αντίγραφα `.gen-backup`, συγχώνευση τμημάτων-δεικτών για διατήρηση χειρόγραφου κώδικα, καθολικό `gen_output` τεσσάρων καταστάσεων (δημιουργήθηκε / συγχωνεύθηκε / παραλείφθηκε / απέτυχε), ιστορική επαναφορά.
- **Απομόνωση πολλαπλών συστημάτων**: κάθε έργο-στόχος είναι μια «διαμόρφωση συστήματος» (διαδρομές front/back, ονόματα πακέτων, πρόθεμα πύλης, classpath εξαρτήσεων, προεπιλογές διακοπτών δημιουργίας) — πολλά έργα εξυπηρετούνται παράλληλα.
- **Βελτιώσεις AI**: χτίσε ένα σύστημα με μία πρόταση (μοντελοποίηση AI), βελτίωση seed-data με AI, συνιστώμενη ρύθμιση ανά πίνακα μέσω AI (προεπισκόπηση diff + εφαρμογή με επιβεβαίωση).

---

## 5. Τυπικά σενάρια

| Σενάριο | Περιγραφή |
| --- | --- |
| Δεντρικός πίνακας | Ιεραρχικά δεδομένα όπως οι κατηγορίες προϊόντων — αυτόματο δεντρικό API, δεντρικό πλέγμα και αναδυόμενο επιλογής γονέα |
| CRUD μονού πίνακα | Πελάτες, προϊόντα κ.λπ. — αναδυόμενα ξένου κλειδιού + μετάφραση λεξικών + εισαγωγή/εξαγωγή έτοιμα προς χρήση |
| Master-detail | Παραγγελίες πώλησης + στοιχεία παραγγελίας — αποθήκευση στην ίδια συναλλαγή, συρτάρι λεπτομερειών με διπλή καρτέλα (πληροφορίες κύριας εγγραφής / λεπτομέρειες) |
| Μόνο ανάγνωση / στατιστικά | Ροές αποθέματος κ.λπ. — οι διακόπτες εγγραφής απενεργοποιούνται, μόνο ερώτημα και εξαγωγή |
| Έγγραφα εκτύπωσης | Τσέκαρε τον διακόπτη «print» για να παραχθεί μια σελίδα εκτύπωσης A4 και μια διαδρομή, με κεφαλίδα + πίνακα λεπτομερειών |
| Χτίσιμο συστήματος με μία πρόταση | Περιέγραψε τον επιχειρηματικό τομέα — το AI δημιουργεί DB και πίνακες + scaffold + πλήρη δημιουργία + εγγραφή μενού σε μια κίνηση |

> Όλα τα σενάρια έχουν εκτελέσιμα demos (έργο `demo_admin`) με στιγμιότυπα — δες το `docs/典型场景操作指南-演示后台demo.md` (κινεζικά).

---

## 6. Κύρια ροή εργασίας

```
┌─────────────┐   ┌───────────────────┐   ┌──────────────────────────────────────────────────┐
│ Table intake │ → │ Field/switch conf. │ → │ One-click generate (self-check→backup→write→     │ → │ Ledger · Rollback │
│              │   │                    │   │ compile→menu→route)                              │   │                   │
└─────────────┘   └───────────────────┘   └──────────────────────────────────────────────────┘
  DB import/DDL     controls/validation     Lands on disk only after all quality gates pass;      Batch-level traceability
                    /dictionaries           hand-written code is preserved
```

- **Προεπισκόπηση diff**: τα παραγόμενα τεχνουργήματα συγκρίνονται αρχείο προς αρχείο με την τρέχουσα κατάσταση του δίσκου· επιβεβαιώνεις πριν γραφτεί οτιδήποτε, αποφεύγοντας τυχαίες αντικαταστάσεις.
- **Καθολικό δημιουργίας**: κάθε αρχείο καταγράφει την ενέργειά του, τον αριθμό byte, το MD5 και την τοποθεσία αντιγράφου· μια ολόκληρη παρτίδα μπορεί να επαναφερθεί.

---

## 7. Χτίσε ένα σύστημα με μία πρόταση (Pipeline)

Μια δυνατότητα ανώτερου επιπέδου που στοχεύει στο «στήσιμο ενός back-end από το μηδέν». Αφού περιγράψεις τον επιχειρηματικό τομέα, η γραμμή προχωρά αυτόματα μέσα από 5 στάδια:

| Στάδιο | Σημασία |
| --- | --- |
| `CHECK` | Επικύρωση της διαμόρφωσης του έργου-στόχου και του classpath εξαρτήσεων |
| `WRITE` | Δημιουργία βάσης δεδομένων και πινάκων, εγγραφή scaffold και παραγόμενων τεχνουργημάτων |
| `COMPILE` | Backend `mvn package` / build front-end, αυτοέλεγχος μεταγλώττισης στη μνήμη |
| `MENU` | Παραγωγή και εγγραφή δικαιωμάτων μενού (διπλή έξοδος: SQL τεχνουργήματος / άμεση εσωτερική εγγραφή) |
| `ROUTE` | Ανακατασκευή αποσπασμάτων διαδρομής front-end |

Οποιαδήποτε αποτυχία σταδίου ματαιώνει αμέσως με ένα ευανάγνωστο σφάλμα για εύκολο εντοπισμό.

---

## 8. Αρχιτεκτονική συστήματος

Front-end και back-end χωριστά, δύο αποθετήρια που συνεργάζονται:

| Αποθετήριο | Ρόλος | Στοίβα (Stack) |
| --- | --- | --- |
| `codegen-server` | Backend: μηχανή δημιουργίας, πρότυπα, επικύρωση, καθολικό, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: κονσόλα (ρύθμιση πινάκων / πρότυπα / καθολικό / διαχείριση συστήματος) | Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite) · axios · Vue Router (λειτουργία history) |

Στρώματα back-end:

```
controller/    API layer (Gen* generation chain / Sys* system management / pipeline)
service/impl/  Core implementations (generation chain, template engine, merge, validation, pipeline execution)
util/          Template engine, model assembly, marker-block merge, self-check compilation
config/        Output config, self-check config, template seeder
common/        Result / BaseEntity / exceptions / audit
resources/gen-templates/   22 FreeMarker templates (backend / frontend / shared / test groups)
```

**Βασικές δυνατότητες που πρέπει να παρέχει το έργο-στόχος** (ο δημιουργός παράγει μόνο επιχειρηματικό κώδικα· μια ελλείπουσα βάση αποτυγχάνει σιωπηλά):

1. Plugin σελιδοποίησης MyBatis-Plus (`PaginationInnerInterceptor`)
2. Βασικές κλάσεις όπως `Result` / `BaseEntity` / `PageQuery` (το πρόθεμα πακέτου πρέπει να ταιριάζει με το `root-package`)
3. Frontend `utils/request.js` με `toPageData` (μετατρέπει το `Page{records,total}` σε `{list,total}`)
4. Εξαρτήσεις `springdoc-openapi-starter-webmvc-ui` (ο παραγόμενος κώδικας χρησιμοποιεί `@Operation`) και `poi-ooxml` (Excel)

---

## 9. Στοίβα τεχνολογίας με μια ματιά

| Διάσταση | Επιλογή |
| --- | --- |
| Γλώσσα / framework back-end | Java 21 / Spring Boot 3.2.5 |
| Επιμονή (Persistence) | MyBatis-Plus 3.5.5 |
| Μηχανή προτύπων | FreeMarker (22 seed πρότυπα, online επεξεργασία + επαναφορά εκδόσεων) |
| Ασφάλεια | Αυθεντικοποίηση JWT |
| Βάση δεδομένων | MySQL 8 |
| Frontend (generated projects) | Vue 3 + Element Plus + Vite |
| Frontend (platform console) | Vue 2.7 + Element UI |
| Τοπικό AI | qwen2.5-coder:7b (μοντελοποίηση / συνιστώμενη ρύθμιση / βελτίωση seed-data) |
| Έγγραφα API | springdoc-openapi (Swagger UI) |

---

## 10. Γρήγορη έναρξη

### 1. Δημιούργησε τη βάση δεδομένων (13 πίνακες πλατφόρμας + προεπιλεγμένος λογαριασμός admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Ρύθμισε το έργο-στόχο

Αντέγραψε το πρότυπο ρύθμισης και προσάρμοσέ το στις τοπικές σου διαδρομές:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

Το `application-local.yml` εξαιρείται από το `.gitignore` και δεν θα γίνει commit. Βασικές ρυθμίσεις:

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

Εξήγαγε τον κατάλογο εξαρτήσεων για αυτοέλεγχο ως εξής:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Εκκίνησε το back-end

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

Η υπηρεσία χρησιμοποιεί από προεπιλογή τη θύρα **8120**· έγγραφα API στο `/swagger-ui/index.html`.

### 4. Εκκίνησε το front-end

```bash
cd ../codegen-web
npm install
npm run serve
```

Το front-end χρησιμοποιεί από προεπιλογή τη θύρα **9530**, με διαμεσολάβηση (proxy) του `/dev-api` στη θύρα 8120 του back-end. Προεπιλεγμένος λογαριασμός `admin / admin123`.

### 5. Βασικά στοιχεία ρύθμισης

| Ρύθμιση | Περιγραφή |
| --- | --- |
| `gen.output.*` | Διαδρομές εγγραφής τεχνουργημάτων, ονόματα πακέτων, αντιστοίχιση modules |
| `gen.selfcheck.classpath` | Classpath εξαρτήσεων για αυτοέλεγχο μεταγλώττισης στη μνήμη, υποστηρίζει `dir/*` |
| `gen.menu.outlet` | Το `artifact` παράγει μόνο SQL μενού και αποσπάσματα διαδρομής· το `internal` εγγράφει επίσης το ίδιο το `sys_menu` της πλατφόρμας |

---

## 11. Χάρτης τεκμηρίωσης

| Έγγραφο | Περιεχόμενο |
| --- | --- |
| `docs/代码生成器-操作手册.md` (κινεζικά) | Ανάπτυξη, έναρξη σε 5 λεπτά, ρύθμιση πινάκων, μαζική εισαγωγή, διαχείριση προτύπων, καθολικό, κατάλογος API, πλήρης pipeline (με στιγμιότυπα) |
| `docs/典型场景操作指南-演示后台demo.md` (κινεζικά) | 6 πίνακες που καλύπτουν 5 τυπικά σενάρια, με εκτελέσιμο demo και πραγματικά στιγμιότυπα |
| `docs/项目介绍.md` | Έγγραφο εισαγωγής έργου, ίδια πηγή με αυτό το αρχείο |
| `docs/代码生成器-Vx.x-规划.md` | Σχέδια εκδόσεων και καταγραφές δυνατοτήτων από V3.1 → V7.3 |
| `codegen-web/README.md` | Σύντομη αναφορά έναρξης κονσόλας front-end |

---

## 12. Ιστορικό εκδόσεων

| Έκδοση | Θέμα / έγγραφο σχεδίου | Commit προσγείωσης |
| --- | --- | --- |
| V1.2 / V1.3 | Δημιουργία / μαζική / επαναφορά με ένα κλικ· προεπισκόπηση diff και συγχρονισμός δομής | — (μόνο στα σχόλια του `src/api/gen.js`) |
| V2.0 | Δυναμικές διαδρομές (δεντρικό μενού που παραδίδεται από το back-end) | — |
| V3.0 / V3.1 | Καθολικό πολλαπλών συστημάτων· βρόχος πολλαπλών συστημάτων + πρώτες αυτοματοποιημένες δοκιμές | V3.1 (174 γραμμές) |
| V4.2 | Αποτελέσματα δημιουργίας ιχνηλάσιμα και με δυνατότητα επαναφοράς (εγγραφή καθολικού) | V4.2 (292 γραμμές) |
| V4.3 | Παλινδρόμηση προτύπων και φράγματα ποιότητας | V4.3 (200 γραμμές) |
| V4.4 | Ένα-κύριο-πολλά-λεπτομέρειες, πληρότητα εγγραφής σε αλυσίδα, λωρίδες προτύπων | V4.4 (154 γραμμές) |
| V4.5 | Έγγραφα εκτύπωσης A4 (μηδενικές εξαρτήσεις τρίτων) | V4.5 (127 γραμμές) |
| V5.0 | Χτίσιμο συστήματος με μία πρόταση: pipeline + μοντελοποίηση AI + βάση scaffold | — |
| V5.1 | Αυτόματη συμπλήρωση στηλών ελέγχου DDL | V5.1 (100 γραμμές) |
| V5.2 | Βήμα αρχικοποίησης seed-data της pipeline | V5.2 (107 γραμμές), 84ec8dd |
| V5.5 | Ολοκλήρωση δημιουργίας έργου πολλαπλών modules | af73dd3 |
| V6.0 | Υποδομή μικροϋπηρεσιών (Nacos / Gateway / Feign, τρίτη λειτουργία «micro») | V6.0 (111 γραμμές), 1deecbd |
| V6.1 | Διπλός μηχανισμός ροής εργασίας Activiti | V6.1 (100 γραμμές), 02b671e |
| V6.2 | Επιφάνεια παράδοσης τεχνουργημάτων (CLI / τεχνουργήματα ανάπτυξης / ασφάλεια ρυθμίσεων) | V6.2 (114 γραμμές), 02b671e |
| V6.3 | Πακέτο δυνατότητας πληρωμής (WeChat / Alipay + Mock) | V6.3 (104 γραμμές), 1c4ee6a, 22eede5 |
| V6.4 | Βασική γραμμή δικαιωμάτων & ασφάλειας (επιμονή token / BCrypt / δικαιώματα API / προστασία σύνδεσης) | V6.4 (107 γραμμές), 007a202 |
| V6.5 | Κινητό uni-app (H5 + WeChat Mini Program) | V6.5 (105 γραμμές), 779ac41 |
| V6.6 | Πακέτο δυνατότητας αρχείου | 98f3c09 |
| V6.7 | Αποδοχή εργοστασίου δοκιμών τεχνουργημάτων (test_enabled τρία-καταστάσεων + σκελετός H2) | V6.7 (134 γραμμές), 4e7d318 |
| V6.8 | Συντηρησιμότητα τεχνουργημάτων (dryRun / προστατευμένο skip / παραμετροποίηση brand) | V6.8 (141 γραμμές), be6f858 |
| V6.9 | Κέντρο τεκμηρίωσης πλατφόρμας (βάση back-end, front-end ολοκληρώθηκε στην ίδια παρτίδα) | — |
| V6.10 | Παρατηρησιμότητα πλατφόρμας: Actuator + παράθυρο μετρικών στη μνήμη + ενσωματωμένη σελίδα monitor | — |
| V7.0 | Συστάσεις σελίδων & μενού AI (προεπισκόπηση / εφαρμογή) | V7.0 (94 γραμμές), 45f2670 |
| V7.1 | Βελτίωση seed-data με AI (η AI κατέχει τη σημασιολογία, οι κανόνες κατέχουν τη δομή) | V7.1 (42 γραμμές), 5ca6e72 |
| V7.2 | Παραδοτέα SQL εγγράφονται στο `db/` μετά την επιτυχία της pipeline | eebff88 |
| **V7.3** | **Αξιοπιστία παράδοσης: δοκιμές τεχνουργημάτων μεταγλωττισμένων μέσω αυτοελέγχου / ενοποιημένη έξοδος παράδοσης SQL / συστάσεις AI ανά πίνακα** | **V7.3 (138 γραμμές)** |
| **V7.4** | Επαναλαμβανόμενη παράδοση: αναπτύσσεται από κενή βάση δεδομένων, κάθε commit εγγυημένα εκτελέσιμο | c306e29 |
| **V7.5** | Γλωσσική διαμόρφωση έργου: λίστα 206 γλωσσών / προεπιλεγμένη γλώσσα / ενεργοποιημένες γλώσσες, έγχυση lang και dir στον παραγόμενο κώδικα | 7a9afa0 + abac56d |
| **V8.0** | i18n πλήρους σελίδας: πλαίσιο 206 γλωσσών (8 επαγγελματικά μεταφρασμένες), i18n δύο τροχιών με σύμβαση μηδενικής διαφοράς σε OFF | 9e5dd57 → 7d57aea |
| **V8.1** | Ενίσχυση ποιότητας: offline harness συμβολαίου (9 λειτουργίες, μηδενική διαφορά OFF) + unit tests i18n-core + πύλη CI + ειλικρινής διαίρεση README (πλαίσιο 206 / μεταφρασμένες 8) + check-locale-keys.mjs | 93a12d5 + e49330b |
| **V9.0** | Τα παραγόμενα τεχνουργήματα εκσυγχρονίστηκαν σε Vue 3 + Element Plus + Vite; επαληθευμένα κατά την εκτέλεση (40/40 ισχυρισμοί, επιτυχές vite build, 14 στιγμιότυπα incl. ar-SA RTL); διορθώθηκαν 3 σφάλματα εκτέλεσης | 93a12d5 + e49330b  |
| **V9.1** | **Αναδιάρθρωση συντηρησιμότητας · `GenScaffoldServiceImpl` 5,072 → 931 lines / 8 Writers; generated output byte-identical (new vs old jar: 119 + 62 files, `diff -r` empty); 312/312 tests; offline i18n contract 9/9** | 654b8e0 → b0d081e, **τρέχουσα έκδοση** |

---

> **Παρατηρησιμότητα πλατφόρμας V6.10**: ενσωματώνει Actuator + Micrometer, δειγματοληψία κάθε 5 δευτερόλεπτα σε έναν κυκλικό buffer στη μνήμη (παράθυρο 30 λεπτών), συν μια νέα ενσωματωμένη σελίδα «System Monitor» — API συνάθροισης back-end `/monitor/*` + πάνελ ECharts front-end. Μόνο τα endpoints `health/info/metrics` είναι σε whitelist· το `/actuator/health` είναι χωρίς token ενώ τα `metrics` κ.λπ. απαιτούν auth· τα `env/beans/heapdump/threaddump/loggers` δεν εκτίθενται ποτέ. Το παράθυρο δειγματοληψίας είναι μόνο ανά διεργασία, χάνεται κατά την επανεκκίνηση και δεν κοινοποιείται μεταξύ παρουσιών. Δες το `docs/可观测性说明.md` (κινεζικά) για λεπτομέρειες.

## 13. Κατάσταση έργου

- Η τρέχουσα προγραμματισμένη γραμμή εκδόσεων έχει φτάσει την **V9.1** (οι δυνατότητες συνεχίζουν να επαναλαμβάνονται: μοντελοποίηση AI, βελτίωση seed-data, σύστημα πακέτων δυνατοτήτων κ.λπ.).
- Ένα συνοδευτικό έργο demo `demo_admin` (back-end 8140 / front-end 9040) καλύπτει σενάρια δέντρου / CRUD / master-detail / μόνο-ανάγνωση / εκτύπωσης και τρέχει αυτόνομα για επαλήθευση.
- Και τα δύο αποθετήρια φιλοξενούνται στο Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 14. Συγγραφέας & Υποστήριξη

- **Συγγραφέας**: ένας Κινέζος προγραμματιστής (ανεξάρτητος προγραμματιστής)
- **Email**: `hustxiayuhong@126.com`
- **Τεχνική υποστήριξη**: παρακαλώ γράψε το email σου **στα κινεζικά**, συμπεριλαμβάνοντας την περιγραφή του προβλήματος, το περιβάλλον εκτέλεσης (JDK / MySQL / έκδοση) και τα βήματα αναπαραγωγής

> Διαβάζω και απαντώ σε κάθε email. Αναφορές σφαλμάτων, αιτήματα δυνατοτήτων και προτάσεις συνεργασίας είναι όλα ευπρόσδεκτα.

---

## 15. Σύγκριση με παρόμοια εργαλεία

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
