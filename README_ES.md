# Majiang · Generador de Código (CodeGen) — Presentación del Proyecto

> Idiomas: [中文](README_ZH.md) · [English](README.md) · [日本語](README_JA.md) · [Português](README_PT.md) · **Español** · [한국어](README_KO.md) · [Français](README_FR.md) · [Deutsch](README_DE.md) · [हिन्दी](README_HI.md) · [العربية](README_AR.md) · [বাংলা](README_BN.md) · [Русский](README_RU.md) · [اردو](README_UR.md)

---

> Un generador de código multiproyecto para **Spring Boot + Vue**: partiendo de la estructura de las tablas de la base de datos, produce código de negocio de front-end y back-end **compilable, ejecutable y con menús y permisos**, y se encarga del ciclo completo de entrega: escritura en disco, copias de seguridad, registro contable (ledger) y reversión.

GitHub: https://github.com/hustxia/CodeGen

## 1. ¿Qué es esto?

CodeGen es una fábrica de código "guiada por tablas". Basta con incorporar tus tablas de negocio (importándolas desde la base de datos o pegando un DDL), configurar los campos y los interruptores de generación y pulsar "Generar": el sistema produce por ti el Entity/Mapper/Service/Controller/DTO/VO del backend, el `api.js` y las páginas Vue del frontend, los menús y las rutas, e incluso páginas de impresión A4 y esqueletos de pruebas de integración, escribiéndolo todo de forma segura en tu proyecto destino.

No se limita a "escupir código": es una **línea de entrega con puertas de calidad y capacidad de reversión**:

```
Toma de tablas (importación de BD / DDL)
   → Configuración de campos y opciones (con recomendaciones de IA)
      → Generación con un clic: autochequeo → copia de seguridad → escritura en disco → compilación/validación → registro de menú → reconstrucción de rutas
         → Registro contable (ledger) → reversible
```

---

## 2. ¿Qué problemas resuelve?

| Punto de dolor | Qué hace CodeGen |
| --- | --- |
| Infierno de CRUD repetitivo: cada tabla exige montañas de código repetitivo | Los interruptores por tabla generan un front-end y back-end completos con un clic, incluidos CRUD, paginación, validación e importación/exportación |
| Desajustes de campos y APIs entre front-end y back-end que obligan a corregir una y otra vez | Los mismos metadatos de tabla alimentan a la vez las APIs del backend y las páginas del frontend: cero desviación semántica |
| Normas de equipo difíciles de unificar; cada persona escribe con un estilo distinto | Las plantillas son la norma: 22 plantillas FreeMarker gestionadas de forma centralizada, editables en línea y con reversión de versiones |
| Los generadores "sobrescriben y pierden": la lógica escrita a mano queda arrasada | Mecanismo de fusión por bloques marcados (marker block): el código escrito a mano se conserva y solo se sustituyen las zonas generadas |
| Resultados generados poco fiables: los problemas aparecen al compilar | Autochequeo con compilación en memoria (`javac` real), comprobación de contratos estructurales, vista previa de diferencias en disco: si no compila, no se escribe |
| Montar el andamiaje de un sistema nuevo desde cero lleva mucho tiempo | La línea de producción "crea un sistema con una frase": crear BD → andamiaje → generar → empaquetar → registrar menús, todo automático |

---

## 3. Panorama de capacidades

- **Toma de tablas**: análisis de DDL, importación directa desde la base de datos, asistente de incorporación por lotes, deducción automática de la configuración de columnas (control / validación / diccionario / relación).
- **Formas de tabla**: tabla simple, tabla en árbol (API `tree` + tabla en árbol), maestro-detalle (guardado en la misma transacción + pestañas de detalle), tablas intermedias de muchos a muchos.
- **Artefactos generados**: Entity / Mapper / Service / Controller / DTO / VO + `api.js` + páginas Vue + páginas de impresión A4 + esqueletos de pruebas de integración.
- **Mejoras de negocio**: desplegables de relación, traducción por diccionario, enmascaramiento de datos, validación de unicidad, vinculación de formularios, permisos de datos, importación/exportación de Excel, flujo de aprobación, y paquetes de capacidades de pago / archivos / móvil.
- **Puertas de calidad**: autochequeo con compilación en memoria (`javac` real), comprobación de contratos estructurales, comprobación de coherencia de permisos, vista previa de diferencias en disco, validación real de los artefactos de frontend.
- **Aterrizaje seguro**: escritura en disco + copias de seguridad por lote en `.gen-backup`, fusión por bloques marcados que preserva el código escrito a mano, ledger de cuatro estados en `gen_output` (creado / fusionado / omitido / fallido), reversión histórica.
- **Aislamiento multisistema**: cada proyecto destino es una "configuración de sistema" (rutas de front y back, nombre de paquete, prefijo de gateway, classpath de dependencias, valores por defecto de los interruptores de generación), lo que permite atender varios proyectos en paralelo.
- **Mejoras de IA**: crear un sistema con una frase (modelado por IA), enriquecimiento de datos semilla con IA, configuración recomendada por tabla con IA (vista previa de diferencias + aplicación selectiva).

---

## 4. Escenarios típicos

| Escenario | Descripción |
| --- | --- |
| Tabla en árbol | Datos jerárquicos como categorías de producto: genera automáticamente la API `tree`, la tabla en árbol y el desplegable de selección de padre |
| CRUD de tabla simple | Clientes, productos, etc.: desplegables de clave foránea + traducción por diccionario + importación/exportación listos para usar |
| Maestro-detalle | Pedido de venta + líneas de pedido: guardado en la misma transacción, cajón de detalle con dos pestañas (datos maestros / detalle) |
| Solo lectura / estadísticas | Movimientos de inventario, etc.: se desactivan los interruptores de alta, baja y modificación y solo se conservan consulta y exportación |
| Impresión de documentos | Al marcar el interruptor "Imprimir" se generan una página A4 de impresión y su ruta, con cabecera + tabla de detalle |
| Crear un sistema con una frase | Describes el dominio de negocio y la IA crea la BD y las tablas + andamiaje + generación completa + registro de menús de una sola vez |

> Todos estos escenarios cuentan con una demostración ejecutable (proyecto `demo_admin`) y capturas de pantalla asociadas; consulta `docs/典型场景操作指南-演示后台demo.md` (Chino).

---

## 5. Flujo de trabajo principal

```
┌────────────────┐   ┌────────────────────────┐   ┌──────────────────────────────────────────────┐   ┌──────────────────────┐
│ Toma de tablas │ → │ Configuración de       │ → │ Generación con un clic                       │ → │ Registro · Reversión │
│ (BD / DDL)     │   │ campos y opciones      │   │ (autochequeo→copia→escritura→compilar        │   │ por lote             │
└────────────────┘   │ (con recomendación IA) │   │  →menú→rutas)                                │   └──────────────────────┘
                     └────────────────────────┘   └──────────────────────────────────────────────┘
```

- **Vista previa de diferencias**: los artefactos generados se comparan archivo por archivo con el estado actual del disco; se confirma antes de escribir nada, evitando sobrescrituras accidentales.
- **Ledger de generación**: cada archivo registra su acción, número de bytes, MD5 y ubicación de la copia de seguridad; un lote completo puede revertirse de una sola vez.

---

## 6. Crear un sistema con una frase (Pipeline)

Capacidad avanzada orientada a "montar un back-office desde cero". Tras describir el dominio de negocio, la línea de producción avanza automáticamente por 5 etapas:

| Etapa | Significado |
| --- | --- |
| `CHECK` | Valida la configuración del proyecto destino y el classpath de dependencias |
| `WRITE` | Crea la base de datos y las tablas, escribe el andamiaje y los artefactos generados |
| `COMPILE` | `mvn package` del backend / build del frontend, autochequeo con compilación en memoria |
| `MENU` | Genera y registra los permisos de menú (doble salida: SQL como artefacto / escritura directa en modo internal) |
| `ROUTE` | Reconstruye los fragmentos de rutas del frontend |

Cualquier fallo en una etapa interrumpe el proceso de inmediato y devuelve un error legible que facilita la localización del problema.

---

## 7. Arquitectura del sistema

Front-end y back-end separados, con dos repositorios que trabajan conjuntamente:

| Repositorio | Rol | Stack |
| --- | --- | --- |
| `codegen-server` | Backend: motor de generación, plantillas, validación, ledger, pipeline | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.5 · FreeMarker · JWT · MySQL 8 · springdoc-openapi |
| `codegen-web` | Frontend: consola (configuración de tablas / plantillas / ledger / administración del sistema) | Vue 2.7 · Element UI · axios · Vue Router (modo history) |

Capas del backend:

```
controller/    Capa de API (cadena de generación Gen* / administración del sistema Sys* / pipeline)
service/impl/  Implementaciones centrales (cadena de generación, motor de plantillas, fusión, validación, ejecución del pipeline)
util/          Motor de plantillas, ensamblado de modelos, fusión por bloques marcados, compilación de autochequeo
config/        Configuración de salida, configuración del autochequeo, sembrador de plantillas
common/        Result / BaseEntity / excepciones / auditoría
resources/gen-templates/   22 plantillas FreeMarker (grupos backend / frontend / shared / test)
```

**Capacidades de base que el proyecto destino debe aportar obligatoriamente** (el generador solo produce código de negocio; si falta la base, el fallo es silencioso):

1. Plugin de paginación de MyBatis-Plus (`PaginationInnerInterceptor`)
2. Clases base como `Result` / `BaseEntity` / `PageQuery` (el prefijo del paquete debe coincidir con `root-package`)
3. `toPageData` en el `utils/request.js` del frontend (convierte `Page{records,total}` en `{list,total}`)
4. Dependencias `springdoc-openapi-starter-webmvc-ui` (el código generado usa `@Operation`) y `poi-ooxml` (Excel)

---

## 8. Stack tecnológico de un vistazo

| Dimensión | Elección |
| --- | --- |
| Lenguaje / framework del backend | Java 21 / Spring Boot 3.2.5 |
| Persistencia | MyBatis-Plus 3.5.5 |
| Motor de plantillas | FreeMarker (22 plantillas semilla, edición en línea + reversión de versiones) |
| Seguridad | Autenticación JWT |
| Base de datos | MySQL 8 |
| Frontend | Vue 2.7 + Element UI |
| IA local | qwen2.5-coder:7b (modelado / configuración recomendada / enriquecimiento de datos semilla) |
| Documentación de API | springdoc-openapi (Swagger UI) |

---

## 9. Inicio rápido

### 1. Crear la base de datos (13 tablas de plataforma + cuenta por defecto admin/admin123)

```bash
mysql -uroot -p -e "CREATE DATABASE gen_platform DEFAULT CHARSET utf8mb4;"
mysql -uroot -p gen_platform < docs/sql/V1.0__init.sql
```

### 2. Configurar el proyecto destino

Copia la plantilla de configuración y ajústala a las rutas de tu máquina:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application-local.yml` está excluido en `.gitignore` y no se enviará al repositorio. Ajustes principales:

```yaml
gen:
  output:
    backend-root: /path/to/your-target-project      # base de escritura en disco de los artefactos
    frontend-root: /path/to/your-target-project
    frontend-path: demo-web                          # subdirectorio del frontend en monorepos
    backend-path: demo-server/src/main/java/com/demo
    package-name: com.demo
    root-package: com.demo                           # prefijo de las clases base importadas por los artefactos
  selfcheck:
    classpath: /path/to/your-target-project/demo-lib/*
```

El directorio de dependencias para el autochequeo se exporta así:

```bash
cd your-target/demo-server
mvn dependency:copy-dependencies -DoutputDirectory=../demo-lib
```

### 3. Arrancar el backend

```bash
mvn package -DskipTests
java -jar target/codegen-server.jar
```

El servicio escucha por defecto en el puerto **8120**; la documentación de la API está en `/swagger-ui/index.html`.

### 4. Arrancar el frontend

```bash
cd ../codegen-web
npm install
npm run serve
```

El frontend escucha por defecto en el puerto **9530** y redirige `/dev-api` al backend en el 8120. Cuenta por defecto `admin / admin123`.

### 5. Elementos de configuración clave

| Ajuste | Descripción |
| --- | --- |
| `gen.output.*` | Rutas de escritura de los artefactos, nombres de paquete, mapeo de módulos |
| `gen.selfcheck.classpath` | Classpath de dependencias para la compilación en memoria del autochequeo; admite `dir/*` |
| `gen.menu.outlet` | `artifact` genera solo el SQL de menús y los fragmentos de rutas; `internal` escribe además en el `sys_menu` de la propia plataforma |

---

## 10. Mapa de documentación

| Documento | Contenido |
| --- | --- |
| `docs/代码生成器-操作手册.md` (Chino) | Despliegue, inicio en cinco minutos, configuración de tablas, incorporación por lotes, gestión de plantillas, ledger, catálogo de APIs y pipeline completo (con capturas reales) |
| `docs/典型场景操作指南-演示后台demo.md` (Chino) | 6 tablas que cubren 5 escenarios típicos, con demo ejecutable y capturas de ejecuciones reales |
| `docs/项目介绍.md` | Documento de presentación del proyecto, con el mismo origen que este archivo |
| `docs/代码生成器-Vx.x-规划.md` | Planificación e inventario de funcionalidades de las versiones V3.1 → V7.3 |
| `codegen-web/README.md` | Referencia de inicio rápido de la consola de frontend |

---

## 11. Historial de versiones

| Versión | Tema / documento de planificación | Commit de implementación |
| --- | --- | --- |
| V1.2 / V1.3 | Generación con un clic / por lotes / reversión; vista previa de diferencias y sincronización de estructura | — (solo en comentarios de `src/api/gen.js`) |
| V2.0 | Rutas dinámicas (árbol de menús entregado por el backend) | — |
| V3.0 / V3.1 | Ledger multisistema; ciclo cerrado multisistema + primeras pruebas automatizadas | V3.1 (174 líneas) |
| V4.2 | Resultados de generación trazables y reversibles (ledger de escritura) | V4.2 (292 líneas) |
| V4.3 | Regresión de plantillas y barandillas de calidad | V4.3 (200 líneas) |
| V4.4 | Un maestro y varios detalles, escritura en cascada completa, carriles de plantillas | V4.4 (154 líneas) |
| V4.5 | Documentos de impresión A4 (sin dependencias de terceros) | V4.5 (127 líneas) |
| V5.0 | Crear un sistema con una frase: pipeline + modelado por IA + base de andamiaje | — |
| V5.1 | Completado automático de columnas de auditoría en DDL | V5.1 (100 líneas) |
| V5.2 | Paso de inicialización de datos semilla en el pipeline | V5.2 (107 líneas), 84ec8dd |
| V5.5 | Cierre de la generación de proyectos multimódulo | af73dd3 |
| V6.0 | Infraestructura de microservicios (Nacos / Gateway / Feign, tercer modo "micro") | V6.0 (111 líneas), 1deecbd |
| V6.1 | Doble motor de flujos de trabajo Activiti | V6.1 (100 líneas), 02b671e |
| V6.2 | Superficie de entrega de artefactos (CLI / artefactos de despliegue / seguridad de configuración) | V6.2 (114 líneas), 02b671e |
| V6.3 | Paquete de capacidades de pago (WeChat / Alipay + Mock) | V6.3 (104 líneas), 1c4ee6a, 22eede5 |
| V6.4 | Línea base de seguridad y permisos (persistencia de token / BCrypt / permisos de API / protección de inicio de sesión) | V6.4 (107 líneas), 007a202 |
| V6.5 | Móvil con uni-app (H5 + Mini Program de WeChat) | V6.5 (105 líneas), 779ac41 |
| V6.6 | Paquete de capacidades de archivos | 98f3c09 |
| V6.7 | Aceptación de pruebas de los artefactos (triestado de `test_enabled` + esqueleto H2) | V6.7 (134 líneas), 4e7d318 |
| V6.8 | Mantenibilidad de los artefactos (dryRun / omisión protegida / parametrización de marca) | V6.8 (141 líneas), be6f858 |
| V6.9 | Centro de documentación de la plataforma (base del backend, cierre del frontend en el mismo lote) | — |
| V6.10 | Observabilidad de la plataforma: Actuator + ventana de métricas en memoria + página de monitorización integrada | — |
| V7.0 | Recomendaciones de IA para páginas y menús (preview / apply) | V7.0 (94 líneas), 45f2670 |
| V7.1 | Enriquecimiento de datos semilla con IA (la IA gobierna la semántica, las reglas la estructura) | V7.1 (42 líneas), 5ca6e72 |
| V7.2 | Volcado de entregables SQL en `db/` tras un pipeline exitoso | eebff88 |
| **V7.3** | **Fiabilidad de la entrega: pruebas de los artefactos compilados en el autochequeo / salida unificada de entregables SQL / recomendaciones de IA por tabla** | **V7.3 (138 líneas)** |
| **V7.4** | **Entrega repetible: desplegable desde una base de datos vacía, cada commit garantiza que se puede ejecutar** | **Versión actual** |

---

> **V6.10 Observabilidad de la plataforma**: se integran Actuator + Micrometer, con muestreo cada 5 segundos hacia un búfer circular en memoria (ventana de 30 minutos), y se añade una nueva página integrada de "Monitorización del sistema": APIs de agregación `/monitor/*` en el backend + paneles ECharts en el frontend. Solo se permiten por lista blanca los endpoints `health/info/metrics`; `/actuator/health` no requiere token, mientras que `metrics` y otros sí exigen autenticación; `env/beans/heapdump/threaddump/loggers` nunca se exponen. La ventana de muestreo es solo dentro del proceso, se pierde al reiniciar y no se comparte entre instancias. Consulta `docs/可观测性说明.md` (Chino) para más detalles.

## 12. Estado del proyecto

- La línea de versiones planificada ha alcanzado la **V7.3**, con capacidades en iteración continua (modelado por IA, enriquecimiento de datos semilla, sistema de paquetes de capacidades, etc.).
- Se ha consolidado el proyecto de demostración complementario `demo_admin` (backend 8140 / frontend 9040), que cubre los escenarios de árbol / CRUD / maestro-detalle / solo lectura / impresión y puede ejecutarse de forma independiente para su verificación.
- Ambos repositorios están alojados en Gitee: `https://gitee.com/hustxiayuhong/CodeGen.git`

---

## 13. Autor y Soporte Técnico

- **Autor**: programador chino (desarrollador independiente)
- **Correo**: `hustxiayuhong@126.com`
- **Soporte técnico**: escriba el correo **en chino**, incluyendo la descripción del problema, el entorno (JDK / MySQL / versión) y los pasos para reproducirlo

> Leo y respondo todos los correos. Se agradecen los informes de errores, las sugerencias de funcionalidades y las propuestas de colaboración.
