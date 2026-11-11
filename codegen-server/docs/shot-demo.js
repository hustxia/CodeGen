/**
 * 演示后台配图：登录 → 逐页截图到 docs/images/demo
 * 用法：node shot-demo.js
 * 前置：demo_admin 后端(8140) 以 --auth.captcha-enabled=false 启动，前端(9040) npm run serve 运行中
 */
const puppeteer = require('D:/workspace/codegen-demo-target/wms_warehouse-web/node_modules/puppeteer-core')
const fs = require('fs')
const path = require('path')

const BASE = 'http://127.0.0.1:9040'
const OUT = path.join(__dirname, 'images', 'demo')
const sleep = ms => new Promise(r => setTimeout(r, ms))

async function waitTable(page) {
  try { await page.waitForSelector('.el-table__body tr', { timeout: 20000 }) } catch (e) { /* not a table page */ }
  await sleep(900)
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    headless: 'new', args: ['--no-sandbox'], defaultViewport: { width: 1600, height: 1000 }
  })
  const page = await browser.newPage()
  const errors = []
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()) })
  page.on('pageerror', e => errors.push(String(e)))
  page.on('response', async r => {
    const u = r.url()
    if (!u.includes('/dev-api')) return
    try {
      const t = await r.text()
      let code = null
      try { code = JSON.parse(t).code } catch (e) {}
      if (code !== null && code !== 0 && code !== 200) console.log('BIZCODE', code, u, t.slice(0, 180))
    } catch (e) {}
  })

  const shot = async (name) => {
    const msg = await page.evaluate(() => Array.from(document.querySelectorAll('.el-message')).map(e => e.innerText).join(' | ') || '')
    if (msg) console.log('TOAST@' + name + ': ' + msg)
    await page.screenshot({ path: path.join(OUT, name), fullPage: false })
    console.log('saved', name)
  }

  // 0) 登录页
  await page.goto(`${BASE}/#/login`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1000)
  await shot('00-login.png')

  // 登录：用户名已预填 admin，密码 + 验证码盲填（后端已关验证码）
  await page.evaluate(() => {
    const set = (sel, val) => {
      const el = document.querySelector(sel)
      if (!el) return
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(el, val)
      el.dispatchEvent(new Event('input', { bubbles: true }))
    }
    set('input[type="password"]', 'admin123')
    const cap = document.querySelector('.captcha-row input')
    if (cap) {
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
      setter.call(cap, '0')
      cap.dispatchEvent(new Event('input', { bubbles: true }))
    }
  })
  await sleep(400)
  // 直接点提交按钮（native-type=submit 的 el-button）
  await page.evaluate(() => {
    const btn = document.querySelector('button[type="submit"]') || Array.from(document.querySelectorAll('.el-button')).find(b => (b.innerText||'').includes('登'))
    if (btn) btn.click()
  })
  // 等待跳回首页，并确认 token 已落地
  const loginOk = await page.waitForFunction(() => {
    return !!localStorage.getItem('token') && (location.hash.startsWith('#/home') || document.body.innerText.includes('首页'))
  }, { timeout: 20000 }).then(() => true).catch(() => false)
  console.log('LOGIN_OK:', loginOk, '| token:', await page.evaluate(() => !!localStorage.getItem('token')))
  await sleep(1500)
  await shot('01-home.png')

  // 1) 商品分类（树形表，已开 default-expand-all 展示完整层级）
  await page.goto(`${BASE}/#/demo_admin/product/demo_category/list`, { waitUntil: 'networkidle2', timeout: 60000 })
  await waitTable(page)
  await sleep(1500)
  await shot('02-category.png')

  // 2) 商品管理（单表 CRUD）
  await page.goto(`${BASE}/#/demo_admin/product/demo_product/list`, { waitUntil: 'networkidle2', timeout: 60000 })
  await waitTable(page)
  await shot('03-product.png')

  // 3) 客户管理（单表 CRUD + 字典）
  await page.goto(`${BASE}/#/demo_admin/customer/demo_customer/list`, { waitUntil: 'networkidle2', timeout: 60000 })
  await waitTable(page)
  await shot('04-customer.png')

  // 4) 销售订单（主子表列表）
  await page.goto(`${BASE}/#/demo_admin/order/demo_order/list`, { waitUntil: 'networkidle2', timeout: 60000 })
  await waitTable(page)
  await shot('05-order-list.png')

  // 4b) 订单详情抽屉（主子表）
  await page.waitForFunction(() => {
    return Array.from(document.querySelectorAll('.el-button')).some(b => (b.innerText || '').trim() === '详情')
  }, { timeout: 10000 }).catch(() => {})
  const clicked = await page.evaluate(() => {
    const btn = Array.from(document.querySelectorAll('.el-button')).find(b => (b.innerText || '').trim() === '详情')
    if (btn) { btn.click(); return true }
    return false
  })
  console.log('clicked 详情:', clicked)
  await page.waitForSelector('.el-drawer', { timeout: 8000 }).catch(() => {})
  await sleep(1200)
  // 切到「订单明细」Tab 展示主子表明细
  await page.evaluate(() => {
    const tab = Array.from(document.querySelectorAll('.el-tabs__item')).find(t => (t.innerText || '').includes('订单明细'))
    if (tab) tab.click()
  })
  await sleep(1200)
  await shot('06-order-detail.png')
  await page.keyboard.press('Escape')
  await sleep(600)

  // 5) 库存流水（只读）
  await page.goto(`${BASE}/#/demo_admin/stock/demo_stock_flow/list`, { waitUntil: 'networkidle2', timeout: 60000 })
  await waitTable(page)
  await shot('07-stock.png')

  // 6) 销售订单打印（用真实存在的订单 id 50001）
  await page.goto(`${BASE}/#/print/demo_order/50001`, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1800)
  await shot('08-print.png')

  console.log('CONSOLE_ERRORS:', errors.length ? errors.join(' | ') : 'none')
  await browser.close()
})().catch(e => { console.error(e); process.exit(1) })
