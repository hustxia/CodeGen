#!/usr/bin/env node
/**
 * smoke-v9.16.mjs —— V9.16「一键试用与演示体验」冒烟测试
 *
 * 覆盖昨天（V9.16）交付的三大功能域 + 同批 SQL 完整性：
 *   A. Docker 一键起：多阶段 Dockerfile / docker-compose / init SQL 数字版本序 / admin 种子
 *   B. H2 demo 模式：application-h2.yml 参数 / SQL 转换器产物 / 脚本数字序 / 种子
 *   C. 演示页提速：8 图 WebP + picture 兜底 + lazy + GoatCounter 占位
 *   D. 动态冒烟（--with-server）：H2 profile 真起服务 + /actuator/health + admin 登录
 *
 * 分层：静态断言无需 Docker/MySQL/Java（本机未装 docker，故 Docker 只做静态契约）；
 *       动态层用现成 jar + JDK21 起 H2 内存库，验证 demo 模式真的能跑起来。
 *
 * 用法：
 *   node scripts/smoke-v9.16.mjs                    # 静态层
 *   node scripts/smoke-v9.16.mjs --with-server      # 静态层 + H2 真启动 + 登录
 *   CG_JAVA_HOME="D:/tool/jdk-21.0.12.1+1" node scripts/smoke-v9.16.mjs --with-server
 *
 * 安全边界：不连任何现有业务库；H2 内存库随进程退出销毁；不启动 MySQL/Docker；
 *           动态层用独立端口 8129，避免占用默认 8120。
 */
import { readFileSync, readdirSync, existsSync, mkdirSync, writeFileSync, statSync } from 'node:fs'
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(__dirname, '..')
const LOG_DIR = path.join(ROOT, 'logs', 'smoke')
const JAVA_HOME = process.env.CG_JAVA_HOME || process.env.JAVA_HOME || 'D:/tool/jdk-21.0.12.1+1'
const PORT = '8129'

const argv = process.argv.slice(2)
const WITH_SERVER = argv.includes('--with-server')

const lines = []
const say = (s) => { console.log(s); lines.push(s) }
let failures = 0
let passes = 0
const ok = (s) => { passes++; say(`  PASS  ${s}`) }
const bad = (s) => { failures++; say(`  FAIL  ${s}`) }
const assert = (cond, msg) => { cond ? ok(msg) : bad(msg) }
const section = (s) => say(`\n== ${s} ==`)

function finish(code) {
  mkdirSync(LOG_DIR, { recursive: true })
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const file = path.join(LOG_DIR, `smoke-v9.16-${stamp}.md`)
  writeFileSync(file, `# V9.16 冒烟测试 ${new Date().toLocaleString('zh-CN')}\n\n` +
    `- 静态断言 ${passes} PASS / ${failures} FAIL\n- 动态层：${WITH_SERVER ? '启用' : '未启用（--with-server）'}\n\n` +
    `\`\`\`\n${lines.join('\n')}\n\`\`\`\n`, 'utf8')
  say(`\nRESULT ${failures === 0 ? 'PASS' : 'FAIL'}  (${passes} PASS / ${failures} FAIL)`)
  say(`REPORT ${file}`)
  process.exit(failures === 0 && code === 0 ? 0 : 1)
}

const read = (p) => readFileSync(path.join(ROOT, p), 'utf8').replace(/^\uFEFF/, '')

/** 提取 SQL 文件的版本元组：001_V1.0__init.sql / V1.0__init.sql -> [1,0]；失败返回 null */
function versionOf(name) {
  const m = name.match(/(?:^|_)V(\d+)\.(\d+)__/)
  return m ? [Number(m[1]), Number(m[2])] : null
}
function cmpVer(a, b) { return a[0] - b[0] || a[1] - b[1] }
function fmtVer(v) { return `V${v[0]}.${v[1]}` }

say(`V9.16 冒烟测试（静态${WITH_SERVER ? ' + H2 动态' : ''}）root=${ROOT}`)

// ============================================================
// A. Docker 一键起（静态契约；本机无 docker daemon，只能校验文件与顺序）
// ============================================================
section('A. Docker 一键起（静态契约）')
const dockerfile = read('Dockerfile')
assert(/FROM\s+maven:.*\s+AS\s+builder/i.test(dockerfile), 'Dockerfile 多阶段构建（builder 阶段）')
assert(/FROM\s+eclipse-temurin:21-jre/i.test(dockerfile), 'Dockerfile 运行阶段 Temurin 21 JRE')
assert(/COPY\s+--from=builder.*codegen-server\.jar/m.test(dockerfile), 'Dockerfile 从 builder 拷贝 fat jar')
assert(/HEALTHCHECK/i.test(dockerfile) && /actuator\/health/.test(dockerfile), 'Dockerfile HEALTHCHECK 指向 actuator health')
assert(/EXPOSE\s+8120/.test(dockerfile), 'Dockerfile EXPOSE 8120')

const compose = read('docker-compose.yml')
assert(/image:\s*mysql:8\.0/.test(compose), 'compose 使用 mysql:8.0 镜像')
assert(/condition:\s*service_healthy/.test(compose), 'compose depends_on 等待 MySQL healthy')
assert(/\.\/scripts\/docker\/init:\/docker-entrypoint-initdb\.d:ro/.test(compose), 'compose 挂载 init SQL 目录（只读）')
assert(/3306:3306/.test(compose) && /8120:8120/.test(compose), 'compose 暴露 3306 与 8120 端口')
assert(/mysql_data:\s*$/m.test(compose), 'compose 声明具名数据卷 mysql_data')
assert(existsSync(path.join(ROOT, '.dockerignore')), '.dockerignore 存在')

const initDir = path.join(ROOT, 'scripts/docker/init')
const initFiles = existsSync(initDir) ? readdirSync(initDir).filter((f) => f.endsWith('.sql')).sort() : []
say(`  INFO  init SQL ${initFiles.length} 个：${initFiles.slice(0, 3).join(', ')} ... ${initFiles.slice(-3).join(', ')}`)
assert(initFiles.length >= 33, `init SQL 数量 >= 33（实际 ${initFiles.length}）`)
// 数字前缀必须 001..N 连续
const prefixOk = initFiles.every((f, i) => Number(f.slice(0, 3)) === i + 1)
assert(prefixOk, 'init SQL 数字前缀 001..N 连续无跳号')
// 版本号必须按数字序非递减（V9.15 必须排在 V9.2 后面，字典序陷阱）
const vers = initFiles.map(versionOf)
assert(vers.every((v) => v !== null), '所有 init SQL 文件名含 _V<major>.<minor>__ 版本标记')
const monotonic = vers.every((v, i) => i === 0 || cmpVer(vers[i - 1], v) <= 0)
if (!monotonic) {
  const firstBad = vers.findIndex((v, i) => i > 0 && cmpVer(vers[i - 1], v) > 0)
  bad(`init SQL 版本号按数字序非递减（首个逆序：${fmtVer(vers[firstBad - 1])} → ${fmtVer(vers[firstBad])}，${initFiles[firstBad]}）`)
} else {
  ok(`init SQL 版本号数字序非递减（${fmtVer(vers[0])} → ${fmtVer(vers[vers.length - 1])}）`)
}
// V6.10 必须在 V6.9 之后（历史字典序陷阱）
const i69 = vers.findIndex((v) => v[0] === 6 && v[1] === 9)
const i610 = vers.findIndex((v) => v[0] === 6 && v[1] === 10)
assert(i69 >= 0 && i610 > i69, `字典序陷阱修复：V6.10 排在 V6.9 之后（位置 ${i610} > ${i69}）`)
// admin/admin123 种子
const init1 = read('scripts/docker/init/001_V1.0__init.sql')
assert(/admin123/.test(init1), 'V1.0 init 含 admin/admin123 种子说明')
assert(/\$2[aby]\$/.test(init1), 'V1.0 init 管理员口令为 BCrypt 哈希（非明文）')
assert(/INSERT INTO `sys_user`[\s\S]*?'admin'/.test(init1), 'V1.0 init 含 sys_user admin 记录')
// BOM
const bomFiles = initFiles.filter((f) => readFileSync(path.join(initDir, f)).slice(0, 3).equals(Buffer.from([0xEF, 0xBB, 0xBF])))
assert(bomFiles.length === 0, `init SQL 无 UTF-8 BOM（历史坑：BOM 致 ERROR 1064）${bomFiles.length ? ` 命中=${bomFiles.join(',')}` : ''}`)

// ============================================================
// B. H2 demo 模式（静态契约）
// ============================================================
section('B. H2 demo 模式（静态契约）')
const pom = read('pom.xml')
assert(/<artifactId>h2<\/artifactId>/.test(pom), 'pom.xml 引入 H2 依赖')
assert(/com\.h2database/.test(pom), 'pom.xml H2 groupId 正确')
const h2yml = read('src/main/resources/application-h2.yml')
assert(/jdbc:h2:mem:gen_platform/.test(h2yml), 'application-h2.yml 使用内存库 gen_platform')
assert(/MODE=MySQL/.test(h2yml), 'application-h2.yml MODE=MySQL')
assert(/DB_CLOSE_DELAY=-1/.test(h2yml), 'application-h2.yml DB_CLOSE_DELAY=-1')
assert(/DATABASE_TO_LOWER=TRUE/.test(h2yml), 'application-h2.yml DATABASE_TO_LOWER=TRUE')
assert(/driver-class-name:\s*org\.h2\.Driver/.test(h2yml), 'application-h2.yml H2 驱动正确')
assert(/data-locations:\s*""/.test(h2yml), 'application-h2.yml data-locations 置空（避免重复执行 DML）')
assert(/mode:\s*always/.test(h2yml), 'application-h2.yml sql.init.mode=always')

// 解析 spring.sql.init.schema-locations 声明的脚本列表
const locs = [...h2yml.matchAll(/classpath:db\/h2\/([^,\s]+\.sql)/g)].map((m) => m[1])
const h2Dir = path.join(ROOT, 'src/main/resources/db/h2')
const h2Files = existsSync(h2Dir) ? readdirSync(h2Dir).filter((f) => f.endsWith('.sql')).sort() : []
say(`  INFO  H2 schema-locations ${locs.length} 条；磁盘脚本 ${h2Files.length} 个`)
assert(locs.length >= 33, `H2 schema-locations >= 33 条（实际 ${locs.length}）`)
const missingLocs = locs.filter((f) => !existsSync(path.join(h2Dir, f)))
assert(missingLocs.length === 0, `schema-locations 声明的文件全部存在${missingLocs.length ? `（缺失 ${missingLocs.join(',')}）` : ''}`)
const h2Vers = locs.map(versionOf).filter((v) => v !== null)
assert(h2Vers.length === locs.length - 1, `H2 脚本除 seed 外均含版本标记（${h2Vers.length}/${locs.length - 1}）`)
const h2Monotonic = h2Vers.every((v, i) => i === 0 || cmpVer(h2Vers[i - 1], v) <= 0)
assert(h2Monotonic, 'H2 schema-locations 按数字版本序非递减')
assert(/999_demo_seed\.sql/.test(h2yml), 'H2 加载 999_demo_seed.sql 演示种子')
const seed = read('src/main/resources/db/h2/999_demo_seed.sql')
assert(/admin/.test(seed), 'H2 demo 种子含 admin')

// 转换器产物与 docs/sql 同源
assert(existsSync(path.join(ROOT, 'scripts/h2/convert_sql_to_h2.py')), 'SQL→H2 转换器脚本存在')
const converter = read('scripts/h2/convert_sql_to_h2.py')
assert(/MODE=MySQL|ON DUPLICATE KEY|IF NOT EXISTS/.test(converter), '转换器含 MySQL→H2 关键改写规则')
const h2Bom = h2Files.filter((f) => readFileSync(path.join(h2Dir, f)).slice(0, 3).equals(Buffer.from([0xEF, 0xBB, 0xBF])))
assert(h2Bom.length === 0, `H2 SQL 无 BOM${h2Bom.length ? `（命中 ${h2Bom.join(',')}）` : ''}`)

// ============================================================
// C. 演示页提速（静态契约）
// ============================================================
section('C. 演示页提速（静态契约）')
const webpDir = path.join(ROOT, 'docs/images/showcase')
const webps = existsSync(webpDir) ? readdirSync(webpDir).filter((f) => f.endsWith('.webp')) : []
assert(webps.length === 8, `showcase WebP 图片 8 张（实际 ${webps.length}）`)
const webpBytes = webps.reduce((n, f) => n + statSync(path.join(webpDir, f)).size, 0)
say(`  INFO  WebP 合计 ${(webpBytes / 1024).toFixed(0)} KB`)
const demo = read('docs/演示效果.html')
const pictureCount = (demo.match(/<picture>/g) || []).length
const sourceCount = (demo.match(/type="image\/webp"/g) || []).length
const lazyCount = (demo.match(/loading="lazy"/g) || []).length
// 8 张 WebP 全部转换落盘；演示页按业务域选 5 张做 <picture> 缩略图，放大原图保留 PNG——属预期设计
assert(pictureCount >= 5, `演示页 <picture> WebP 缩略图 >= 5 处（实际 ${pictureCount}）`)
assert(sourceCount >= 5, `演示页 image/webp <source> >= 5 处（实际 ${sourceCount}）`)
assert(lazyCount >= 15, `演示页 loading="lazy" >= 15 处（实际 ${lazyCount}）`)
assert(/data-goatcounter=/.test(demo), '演示页含 GoatCounter 统计占位')
// 每个 WebP 引用必须①真实落盘 ②有 PNG 兜底
const showcaseWebpRefs = [...new Set([...demo.matchAll(/images\/showcase\/([a-z0-9-]+)\.webp/g)].map((m) => m[1]))]
const missingWebp = showcaseWebpRefs.filter((name) => !existsSync(path.join(webpDir, `${name}.webp`)))
const noFallback = showcaseWebpRefs.filter((name) => !new RegExp(`images/showcase/${name}\\.png`).test(demo))
assert(missingWebp.length === 0, `页面引用的 WebP 全部落盘${missingWebp.length ? `（缺 ${missingWebp.join(',')}）` : ''}`)
assert(noFallback.length === 0, `WebP 引用均有 PNG 兜底${noFallback.length ? `（缺 ${noFallback.join(',')}）` : ''}`)

// ============================================================
// D. 动态冒烟：H2 profile 真启动 + 健康检查 + 登录
// ============================================================
if (WITH_SERVER) {
  section('D. H2 动态冒烟（真起服务）')
  const jar = path.join(ROOT, 'target/codegen-server.jar')
  const javaCandidate = path.join(JAVA_HOME, 'bin', 'java.exe')
  const java = existsSync(javaCandidate) ? javaCandidate : path.join(JAVA_HOME, 'bin', 'java')
  if (!existsSync(jar) || !existsSync(java)) {
    bad(`动态冒烟前置缺失：jar=${existsSync(jar)} java=${existsSync(java)}（设 CG_JAVA_HOME）`)
  } else {
    const child = spawn(java, ['-jar', jar, `--spring.profiles.active=h2`, `--server.port=${PORT}`],
      { cwd: ROOT, stdio: ['ignore', 'pipe', 'pipe'] })
    let out = ''
    child.stdout.on('data', (d) => { out += d.toString() })
    child.stderr.on('data', (d) => { out += d.toString() })
    const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
    const fetchJson = async (url, opts) => {
      const resp = await fetch(url, { signal: AbortSignal.timeout(3000), ...opts })
      return { status: resp.status, body: await resp.json().catch(() => null) }
    }
    let health = null
    let login = null
    try {
      let up = false
      for (let i = 0; i < 50; i++) {
        await sleep(1000)
        try {
          const r = await fetchJson(`http://127.0.0.1:${PORT}/actuator/health`)
          if (r.status === 200 && r.body?.status === 'UP') { up = true; health = r; break }
        } catch { /* 启动中 */ }
        if (child.exitCode !== null) break
      }
      assert(up, `H2 profile 服务 50s 内 /actuator/health=UP${up ? '' : `（进程退出码 ${child.exitCode}）`}`)
      if (up) {
        ok(`actuator health 返回 {"status":"${health.body.status}"}`)
        const lr = await fetchJson(`http://127.0.0.1:${PORT}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'admin', password: 'admin123' }),
        })
        login = lr
        assert(lr.status === 200 && lr.body?.code === 200 && !!lr.body?.data?.token,
          `admin/admin123 登录成功并返回 token（HTTP ${lr.status}, code ${lr.body?.code}）`)
        if (lr.body?.data?.token) {
          const ir = await fetchJson(`http://127.0.0.1:${PORT}/auth/info`, {
            headers: { Authorization: `Bearer ${lr.body.data.token}` },
          })
          assert(ir.status === 200 && ir.body?.data?.username === 'admin',
            `Bearer token 换取 /auth/info 返回 admin（HTTP ${ir.status}）`)
        }
      }
    } catch (e) {
      bad(`动态冒烟异常：${e.message}`)
    } finally {
      try { child.kill('SIGTERM') } catch { /* 已退出 */ }
      await sleep(500)
      if (child.exitCode === null) { try { child.kill('SIGKILL') } catch { /* 已退出 */ } }
    }
    if (!health) {
      const tail = out.split('\n').filter((l) => /ERROR|Exception|Caused by|Failed/i.test(l)).slice(-8)
      if (tail.length) say(`  INFO  启动日志错误摘要：\n    ${tail.join('\n    ')}`)
    }
  }
} else {
  section('D. 动态冒烟')
  say('  SKIP  未加 --with-server（加该参数可跑 H2 真启动 + 登录）')
}

finish(0)
