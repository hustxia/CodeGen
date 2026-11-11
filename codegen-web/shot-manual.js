/**
 * 操作手册配图：登录态注入 → 逐页截图到 ../codegen-server/docs/images/manual
 * 用法：node shot-manual.js <token>
 */
const puppeteer = require('D:/workspace/codegen-demo-target/wms_warehouse-web/node_modules/puppeteer-core')
const fs = require('fs')
const path = require('path')

const TOKEN = process.argv[2]
const BASE = 'http://127.0.0.1:9530'
const OUT = 'D:/workspace/project/codegen-server/docs/images/manual'

const sleep = ms => new Promise(r => setTimeout(r, ms))

async function clickText(page, selector, text) {
  return page.evaluate((sel, txt) => {
    const el = Array.from(document.querySelectorAll(sel)).find(e => (e.innerText || '').trim().includes(txt))
    if (el) { el.click(); return true }
    return false
  }, selector, text)
}

;(async () => {
  if (!TOKEN) throw new Error('缺少 token')
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1600, height: 1000 }
  })
  const page = await browser.newPage()
  await page.setCookie({ name: 'Codegen-Token', value: TOKEN, domain: '127.0.0.1', path: '/' })

  const shot = async (name) => {
    await page.screenshot({ path: path.join(OUT, name) })
    console.log('saved', name)
  }

  // 1 表配置列表
  await page.goto(`${BASE}/gen/table`, { waitUntil: 'networkidle2', timeout: 60000 })
  await page.waitForSelector('.el-table__body tr', { timeout: 30000 })
  await sleep(800)
  await shot('01-table-list.png')

  // 2 字段配置弹窗
  await clickText(page, '.el-table__fixed-right button, .el-table__body button, button', '字段配置')
  await sleep(1200)
  const dlg = await page.$('.el-dialog__wrapper:not([style*="display: none"]) .el-dialog')
  if (dlg) { await shot('02-field-config.png') } else { console.log('字段配置弹窗未打开') }
  await page.keyboard.press('Escape')
  await sleep(600)

  // 3 一键生成弹窗
  await clickText(page, 'button', '一键生成')
  await sleep(900)
  await shot('03-apply-dialog.png')
  await page.keyboard.press('Escape')
  await sleep(600)

  // 4 批量接管向导
  await page.goto(`${BASE}/gen/wizard`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await shot('04-wizard.png')

  // 5 系统管理
  await page.goto(`${BASE}/gen/project`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await shot('05-project.png')

  // 6 代码模板
  await page.goto(`${BASE}/gen/template`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await shot('06-template.png')

  // 7 生成台账
  await page.goto(`${BASE}/gen/output`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await shot('07-output.png')

  // 8 接口目录
  await page.goto(`${BASE}/gen/api-catalog`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await shot('08-api-catalog.png')

  // 9 一句话建系统（可能无动态路由，失败不致命）
  await page.goto(`${BASE}/gen/pipeline`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  const text = await page.evaluate(() => document.body.innerText)
  if (text.includes('一句话建系统')) await shot('09-pipeline.png')
  else console.log('流水线页不可直达:', text.slice(0, 80).replace(/\s+/g, ' '))

  await browser.close()
})().catch(e => { console.error(e); process.exit(1) })
