#!/usr/bin/env node
/**
 * smoke-fresh-db.mjs —— V7.4 空库可部署冒烟
 *
 * 做什么：在一个全新空 schema 上按文件名顺序执行 docs/sql/*.sql（默认跑两遍，
 *         第二遍验证幂等——裸 ADD COLUMN / 重复建表会立刻现形），
 *         然后断言 F-07 / F-08 修复点真的生效：
 *           - sys_oper_log 有 4 个审计列且能写入
 *           - gen_pipeline / gen_pipeline_step 存在
 *         可选 --with-server 再起一次 jar 指向该库，验证 /health。
 *
 * 安全边界（不可绕过）：
 *   - 目标库名必须含 _smoke 或 _test，且绝不可以是 gen_platform
 *   - 默认不删库（--drop-after 才删），便于失败后排查
 *   - 只在显式执行时连数据库，不写入任何现有业务库
 *   - 脚本内所有 USE / CREATE DATABASE / DROP DATABASE 先剥离再全文检测，残留即拒执行
 *
 * 用法：
 *   node scripts/smoke-fresh-db.mjs
 *   node scripts/smoke-fresh-db.mjs --with-server --drop-after
 *   node scripts/smoke-fresh-db.mjs --no-twice            # 只跑一遍（调试用）
 *   CG_MYSQL="C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" node ...
 *
 * 环境变量：CG_MYSQL / CG_DB_HOST / CG_DB_USER / CG_DB_PASSWORD / CG_JAVA_HOME / CG_SERVER_PORT
 */
import { readFileSync, readdirSync, existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { spawn, spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(__dirname, '..')
const SQL_DIR = path.join(ROOT, 'docs', 'sql')
const LOG_DIR = path.join(ROOT, 'logs', 'smoke')

const argv = process.argv.slice(2)
const has = (flag) => argv.includes(flag)
const opt = (key, def) => {
  const hit = argv.find((a) => a.startsWith(`--${key}=`))
  return hit ? hit.slice(key.length + 3) : def
}

const MYSQL = process.env.CG_MYSQL || 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
const HOST = process.env.CG_DB_HOST || '127.0.0.1'
const USER = process.env.CG_DB_USER || 'root'
const PASSWORD = process.env.CG_DB_PASSWORD || 'root'
const DB = opt('db', 'codegen_v74_smoke')
const PORT = opt('port', process.env.CG_SERVER_PORT || '8124')
const WITH_SERVER = has('--with-server')
const DROP_AFTER = has('--drop-after')
const TWICE = !has('--no-twice')

const lines = []
const say = (s) => { console.log(s); lines.push(s) }
const fail = (s) => { say(`FAIL  ${s}`); finish(1) }

function finish(code) {
  mkdirSync(LOG_DIR, { recursive: true })
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const file = path.join(LOG_DIR, `smoke-fresh-db-${stamp}.md`)
  writeFileSync(file, `# 空库冒烟 ${new Date().toLocaleString('zh-CN')}\n\n\`\`\`\n${lines.join('\n')}\n\`\`\`\n`, 'utf8')
  console.log(`\nREPORT ${file}`)
  process.exit(code)
}

/** 执行一段 SQL，返回 {ok, out, err} */
function sql(text, database = DB) {
  const args = ['-h', HOST, '-u', USER, '--default-character-set=utf8mb4', '--batch', '--skip-column-names']
  if (database) args.push('--database', database)
  const r = spawnSync(MYSQL, args, {
    input: text,
    encoding: 'utf8',
    env: { ...process.env, MYSQL_PWD: PASSWORD },
    maxBuffer: 16 * 1024 * 1024,
  })
  return { ok: r.status === 0, out: (r.stdout || '').trim(), err: (r.stderr || '').trim() }
}

if (!existsSync(MYSQL)) fail(`找不到 mysql 客户端：${MYSQL}（用 CG_MYSQL 指定绝对路径）`)
if (!/^[A-Za-z0-9_]+$/.test(DB) || !/_(smoke|test)/.test(DB) || DB === 'gen_platform') {
  fail(`拒绝执行：库名 ${DB} 不安全，必须含 _smoke/_test 且不能是 gen_platform`)
}
if (!existsSync(SQL_DIR)) fail(`找不到脚本目录：${SQL_DIR}`)

say(`空库冒烟 db=${DB} host=${HOST} user=${USER} withServer=${WITH_SERVER}`)
say(`影响面：只在数据库 ${DB} 内建表；不连接、不修改 gen_platform / gen_platform_test`)

const create = sql(`CREATE DATABASE IF NOT EXISTS \`${DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`, null)
if (!create.ok) fail(`建库失败：${create.err}`)
say('PASS  空库已就绪')

const files = readdirSync(SQL_DIR).filter((f) => f.endsWith('.sql')).sort()
say(`脚本清单（按顺序，共 ${files.length} 个）：${files.join(', ')}`)

/** 去掉 SQL 注释（-- 行注释 / # 行注释 / 块注释），供残留检测用 */
function stripSqlComments(s) {
  return s
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/--[^\n]*/g, ' ')
    .replace(/#[^\n]*/g, ' ')
}

/** 剥离脚本里的库切换语句，并强制把会话钉在临时库上。
 *  V1.0__init.sql 里有 CREATE DATABASE gen_platform; USE gen_platform;，
 *  不剥离的话 --database 参数会被覆盖，DROP TABLE 会落到开发库（2026-11-07 事故）。
 *  防线三层：① 剥离行首建库/切换语句 ② 注释剥离后全文残留检测（含 DROP DATABASE）
 *  ③ 每个脚本头部强制注入 USE <临时库>，且 mysql 客户端仍带 --database 参数。 */
function sanitize(file, content) {
  const cleaned = content
    .replace(/^\s*CREATE\s+(DATABASE|SCHEMA)\b[^;]*;/gim, '')
    .replace(/^\s*USE\s+`?[A-Za-z0-9_]+`?\s*;/gim, '');
  const bare = stripSqlComments(cleaned)
  if (/\bUSE\s+`?[A-Za-z0-9_]+`?/i.test(bare)
    || /\bCREATE\s+(DATABASE|SCHEMA)\b/i.test(bare)
    || /\bDROP\s+(DATABASE|SCHEMA)\b/i.test(bare)) {
    fail(`${file} 仍含库切换/建库/删库语句，拒绝执行（避免落到非临时库）`)
  }
  if (cleaned !== content) say(`SANITIZED  ${file}: 已剥离 CREATE DATABASE / USE，强制落在 ${DB}`);
  return `USE \`${DB}\`;\n${cleaned}`;
}

let executed = 0
/** 按文件名顺序执行全部脚本；pass 为遍次（1=空库首跑，2=幂等重跑） */
function runAllScripts(pass) {
  let done = 0
  for (const f of files) {
    const content = sanitize(f, readFileSync(path.join(SQL_DIR, f), 'utf8'))
    const r = sql(content)
    // mysql 客户端会把部分告警写到 stderr，只有非 0 退出码才算真失败
    if (!r.ok) {
      fail(`第 ${pass} 遍执行 ${f} 失败：\n${r.err}\n${r.out}`)
    }
    done++
    if (r.err && pass === 1) say(`WARN  ${f}: ${r.err.split('\n')[0]}`)
  }
  return done
}

executed = runAllScripts(1)
say(`PASS  全量脚本执行完成 ${executed}/${files.length}，0 ERROR`)

// 幂等回归：同一库再跑一遍，任何裸 ADD COLUMN / 重复建表都会在这里现形
// （2026-11-07 实测抓到 V2.0 mask_type、V6.1 flow_engine、V6.3 pay_enabled、V6.8 app_name 四处）
if (TWICE) {
  const again = runAllScripts(2)
  say(`PASS  二次执行幂等通过 ${again}/${files.length}，0 ERROR（迁移脚本可重跑）`)
}

const cols = sql(`SELECT COLUMN_NAME FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_oper_log'
    AND COLUMN_NAME IN ('create_by','create_time','update_by','update_time');`)
if (!cols.ok) fail(`查审计列失败：${cols.err}`)
const got = cols.out.split('\n').map((s) => s.trim()).filter(Boolean).sort()
const want = ['create_by', 'create_time', 'update_by', 'update_time']
if (JSON.stringify(got) !== JSON.stringify(want)) fail(`sys_oper_log 审计列不齐：实际 ${got.join(',')} / 期望 ${want.join(',')}`)
say('PASS  sys_oper_log 4 个审计列齐全')

const ins = sql(`DELETE FROM sys_oper_log WHERE id = 990001;
INSERT INTO sys_oper_log
  (id, title, oper_type, oper_param, status, create_by, create_time, update_by, update_time)
  VALUES (990001, 'V7.4 空库冒烟', 'OTHER', 'smoke', 1, 0, NOW(), 0, NOW());
SELECT COUNT(*) FROM sys_oper_log WHERE id = 990001;`)
if (!ins.ok || !ins.out.split('\n').some((l) => l.trim() === '1')) fail(`带审计列写入日志失败：${ins.err} / ${ins.out}`)
say('PASS  带 4 个审计列的日志写入成功（F-07 不再恒空）')

const tabs = sql(`SELECT TABLE_NAME FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('gen_pipeline','gen_pipeline_step');`)
if (!tabs.ok) fail(`查流水线表失败：${tabs.err}`)
const tables = tabs.out.split('\n').map((s) => s.trim()).filter(Boolean).sort()
if (tables.length !== 2) fail(`流水线表缺失：实际 ${tables.join(',')}，期望 gen_pipeline + gen_pipeline_step`)
say('PASS  gen_pipeline / gen_pipeline_step 均存在（F-08 不再 Table doesn\'t exist）')

if (WITH_SERVER) {
  const jar = path.join(ROOT, 'target', 'codegen-server.jar')
  if (!existsSync(jar)) fail(`--with-server 需要 ${jar}，先跑 mvn -o -DskipTests package`)
  const java = process.env.CG_JAVA_HOME
    ? path.join(process.env.CG_JAVA_HOME, 'bin', 'java.exe')
    : 'java'
  const url = `jdbc:mysql://${HOST}:3306/${DB}?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`
  const child = spawn(java, ['-jar', jar, `--server.port=${PORT}`,
    `--spring.datasource.url=${url}`, `--spring.datasource.username=${USER}`,
    `--spring.datasource.password=${PASSWORD}`], { cwd: ROOT, stdio: 'ignore' })
  let healthy = false
  for (let i = 0; i < 40; i++) {
    await new Promise((r) => setTimeout(r, 1000))
    try {
      const resp = await fetch(`http://127.0.0.1:${PORT}/health`, { signal: AbortSignal.timeout(2000) })
      if (resp.ok) { healthy = true; say(`PASS  /health HTTP ${resp.status}（服务指向空库 ${DB}）`); break }
    } catch { /* 启动中 */ }
  }
  try { child.kill() } catch { /* 已退出 */ }
  if (!healthy) fail(`服务未能在 40s 内通过 /health（端口 ${PORT}）`)
}

if (DROP_AFTER) {
  const d = sql(`DROP DATABASE IF EXISTS \`${DB}\`;`, null)
  if (!d.ok) fail(`清理临时库失败：${d.err}`)
  say(`PASS  临时库 ${DB} 已清理`)
} else {
  say(`INFO  保留临时库 ${DB} 供排查（--drop-after 可清理）`)
}

say('RESULT PASS')
finish(0)
