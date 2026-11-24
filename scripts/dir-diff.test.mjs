#!/usr/bin/env node
/**
 * V9.18 S9 纯函数单测：目录递归比对 + 阈值判定 + 白名单。
 * 运行：node --test scripts/dir-diff.test.mjs
 * 不依赖 Maven / 数据库 / 浏览器，CI 可在几秒内跑完。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { diffDirs, judgeDiff, isWhitelisted, normalizeRel } from './dir-diff.mjs'

/** 按 {相对路径: 内容} 建一棵临时目录树 */
function makeTree(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'dir-diff-'))
  for (const [rel, content] of Object.entries(files)) {
    const abs = path.join(root, rel)
    fs.mkdirSync(path.dirname(abs), { recursive: true })
    fs.writeFileSync(abs, content, 'utf8')
  }
  return root
}

test('identical trees report zero differences', () => {
  const a = makeTree({ 'src/a.js': 'x', 'src/b.js': 'y' })
  const b = makeTree({ 'src/a.js': 'x', 'src/b.js': 'y' })
  const d = diffDirs(a, b)
  assert.deepEqual(d.added, [])
  assert.deepEqual(d.removed, [])
  assert.deepEqual(d.modified, [])
  assert.equal(d.unchanged, 2)
  assert.equal(d.scanned, 2)
})

test('added / removed / modified are classified separately', () => {
  const next = makeTree({ 'keep.js': 'same', 'mod.js': 'after', 'new.js': 'n' })
  const base = makeTree({ 'keep.js': 'same', 'mod.js': 'before', 'gone.js': 'g' })
  const d = diffDirs(next, base)
  assert.deepEqual(d.added, ['new.js'])
  assert.deepEqual(d.removed, ['gone.js'])
  assert.deepEqual(d.modified, ['mod.js'])
  assert.equal(d.unchanged, 1)
})

test('same size but different content is still modified (hash compare)', () => {
  const next = makeTree({ 'x.js': 'aaaa' })
  const base = makeTree({ 'x.js': 'bbbb' })
  const d = diffDirs(next, base)
  assert.deepEqual(d.modified, ['x.js'])
})

test('whitelist prefixes and middle segments are ignored', () => {
  const next = makeTree({ 'node_modules/p/index.js': '1', 'src/node_modules/q.js': '2', 'src/app.js': '3' })
  const base = makeTree({ 'src/app.js': '3' })
  const d = diffDirs(next, base, { whitelist: ['node_modules'] })
  assert.deepEqual(d.added, [])
  assert.equal(d.unchanged, 1)
})

test('isWhitelisted does not match similar-name siblings', () => {
  assert.equal(isWhitelisted('node_modules/a/b.js', ['node_modules']), true)
  assert.equal(isWhitelisted('src/node_modules/a.js', ['node_modules']), true)
  assert.equal(isWhitelisted('mynode_modules/a.js', ['node_modules']), false)
  assert.equal(normalizeRel('src\\a\\b.js'), 'src/a/b.js')
})

test('judgeDiff passes when every dimension is within limits', () => {
  const diff = { added: ['a'], removed: [], modified: ['b', 'c'] }
  const v = judgeDiff(diff, { maxAdded: 1, maxRemoved: 0, maxModified: 2 })
  assert.equal(v.ok, true)
  assert.deepEqual(v.exceeded, [])
})

test('judgeDiff fails and reports each exceeded dimension', () => {
  const diff = { added: ['a', 'b'], removed: ['c'], modified: [] }
  const v = judgeDiff(diff, { maxAdded: 1, maxRemoved: 1, maxModified: 0 })
  assert.equal(v.ok, false)
  assert.deepEqual(v.exceeded.map(e => e.kind), ['added'])
})

test('judgeDiff without limits allows any difference', () => {
  const diff = { added: Array.from({ length: 50 }, (_, i) => 'a' + i), removed: ['x'], modified: ['y'] }
  assert.equal(judgeDiff(diff, {}).ok, true)
})

test('missing roots are rejected explicitly', () => {
  assert.throws(() => diffDirs('', 'x'), /都必须提供/)
})
