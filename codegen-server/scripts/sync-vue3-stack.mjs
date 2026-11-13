#!/usr/bin/env node
/**
 * 文档维护（V9.1 之后）：把「技术栈一览 / 系统架构」中的前端技术栈改成双轨标注。
 *
 *   - 生成物（目标项目，V9.0 起）：Vue 3 + Element Plus + Vite
 *   - 平台控制台 codegen-web（代码事实，未升级）：Vue 2.7 + Element UI
 *
 * 设计约束：
 *   1. 幂等：重复执行不会重复插入行。
 *   2. 按字节逐行处理，未命中的行原样保留字节，命中的行沿用该文件原有的 CRLF / LF，
 *      不把 CRLF 文件整篇刷成 LF（之前踩过这个坑）。
 *   3. 只改表格中的技术栈单元格，历史规划文档里描述 Vue 2 的正文不动。
 *
 * 用法：
 *   node codegen-server/scripts/sync-vue3-stack.mjs [rootDir] [--dry]
 */
import fs from 'node:fs';
import path from 'node:path';

const args = process.argv.slice(2);
const dry = args.includes('--dry');
const dirArg = args.find((a) => !a.startsWith('--'));
const root = dirArg ? path.resolve(dirArg) : process.cwd();

const SKIP_DIRS = new Set(['.git', 'node_modules', 'target', 'dist', '.workbuddy']);
const STACK_ROW = /^(\|\s*)([^|]*?)(\s*\|\s*)Vue 2\.7 \+ Element UI(\s*\|\s*)$/;
const ARCH_OLD = 'Vue 2.7 · Element UI';
const ARCH_NEW =
  'Vue 2.7 · Element UI (platform console only; generated projects use Vue 3 + Element Plus + Vite)';
const DONE_MARKERS = [
  '(generated projects)',
  '(platform console)',
  'generated projects use Vue 3',
];

const files = [];
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p);
    else if (entry.name.endsWith('.md')) files.push(p);
  }
}
walk(root);

function splitLines(buf) {
  const lines = [];
  const eols = [];
  let start = 0;
  for (let i = 0; i < buf.length; i++) {
    if (buf[i] !== 0x0a) continue;
    const cr = i > start && buf[i - 1] === 0x0d;
    lines.push(buf.subarray(start, cr ? i - 1 : i));
    eols.push(cr ? Buffer.from([0x0d, 0x0a]) : Buffer.from([0x0a]));
    start = i + 1;
  }
  lines.push(buf.subarray(start));
  return { lines, eols };
}

function transform(line) {
  if (DONE_MARKERS.some((m) => line.includes(m))) return [line];
  const m = line.match(STACK_ROW);
  if (m) {
    const [, lead, label, mid, tail] = m;
    return [
      `${lead}${label} (generated projects)${mid}Vue 3 + Element Plus + Vite${tail}`,
      `${lead}${label} (platform console)${mid}Vue 2.7 + Element UI${tail}`,
    ];
  }
  if (line.includes(ARCH_OLD)) return [line.replace(ARCH_OLD, ARCH_NEW)];
  return [line];
}

let changedFiles = 0;
let changedLines = 0;
const changedPaths = [];

for (const file of files) {
  const buf = fs.readFileSync(file);
  if (!buf.toString('utf8').includes('Vue 2.7')) continue;
  const { lines, eols } = splitLines(buf);
  const out = [];
  let hits = 0;
  for (let i = 0; i < lines.length; i++) {
    const original = lines[i];
    const text = original.toString('utf8');
    const replaced = transform(text);
    if (replaced.length === 1 && replaced[0] === text) {
      out.push(original);
      if (eols[i]) out.push(eols[i]);
      continue;
    }
    hits++;
    replaced.forEach((line, j) => {
      out.push(Buffer.from(line, 'utf8'));
      if (j < replaced.length - 1 || eols[i]) {
        out.push(eols[i] || Buffer.from([0x0a]));
      }
    });
  }
  if (hits === 0) continue;
  changedFiles++;
  changedLines += hits;
  changedPaths.push(path.relative(root, file).replace(/\\/g, '/'));
  if (!dry) fs.writeFileSync(file, Buffer.concat(out));
}

console.log(
  JSON.stringify(
    {
      dry,
      root: root.replace(/\\/g, '/'),
      scannedMd: files.length,
      changedFiles,
      changedLines,
      sample: changedPaths.slice(0, 8),
    },
    null,
    2,
  ),
);
