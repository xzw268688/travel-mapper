#!/usr/bin/env node
/**
 * 基于 Vite manifest 的 gzip 体积预算检查。
 *
 * 用法：npm run build && npm run budget
 * 可用环境变量临时调整阈值（单位 KB，gzip 后）：
 *   BUDGET_ENTRY_KB=400 BUDGET_CHUNK_KB=600 BUDGET_FIRST_SCREEN_KB=800
 */
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const distDir = path.join(root, 'dist')
const manifestPath = path.join(distDir, '.vite', 'manifest.json')

const budgets = {
  entryKb: numberFromEnv('BUDGET_ENTRY_KB', 400),
  chunkKb: numberFromEnv('BUDGET_CHUNK_KB', 600),
  firstScreenKb: numberFromEnv('BUDGET_FIRST_SCREEN_KB', 800)
}

const HEAVY_MODULES = ['echarts', 'zrender', '@tiptap', 'prosemirror', 'richtexteditor']

function numberFromEnv(name, fallback) {
  const raw = process.env[name]
  if (!raw) return fallback
  const value = Number(raw)
  if (!Number.isFinite(value) || value <= 0) {
    console.error(`环境变量 ${name} 必须是正数，实际为 ${raw}`)
    process.exit(2)
  }
  return value
}

function fail(message) {
  console.error(`\n✗ ${message}`)
  process.exit(1)
}

function gzipSize(file) {
  const absolute = path.join(distDir, file)
  if (!fs.existsSync(absolute)) {
    fail(`manifest 引用的文件不存在：${file}`)
  }
  return zlib.gzipSync(fs.readFileSync(absolute), { level: 9 }).length
}

function rawSize(file) {
  return fs.statSync(path.join(distDir, file)).size
}

function kb(bytes) {
  return `${(bytes / 1024).toFixed(1)} KB`
}

function collectImports(manifest, key, result = new Set()) {
  const chunk = manifest[key]
  if (!chunk || result.has(key)) return result
  result.add(key)
  for (const imported of chunk.imports ?? []) {
    collectImports(manifest, imported, result)
  }
  return result
}

function findWorldJson(manifest) {
  for (const [key, chunk] of Object.entries(manifest)) {
    const candidates = [key, chunk.file, chunk.src].filter(Boolean)
    if (candidates.some((value) => value.endsWith('world.json'))) {
      return chunk.file
    }
  }
  // 基线版本 world.json 位于 public/，不会进入 manifest。
  if (fs.existsSync(path.join(distDir, 'world.json'))) {
    return 'world.json'
  }
  return null
}

function listJsFiles(dir, result = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const absolute = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      listJsFiles(absolute, result)
    } else if (entry.name.endsWith('.js')) {
      result.push(path.relative(distDir, absolute).split(path.sep).join('/'))
    }
  }
  return result
}

if (!fs.existsSync(manifestPath)) {
  fail('未找到 dist/.vite/manifest.json，请先运行 npm run build')
}

const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
const entry = manifest['index.html']
if (!entry) {
  fail('manifest 中没有 index.html 入口')
}

const entryKeys = collectImports(manifest, 'index.html')
const entryJsFiles = [...entryKeys]
  .map((key) => manifest[key].file)
  .filter((file) => file.endsWith('.js'))
const entryCssFiles = [...entryKeys].flatMap((key) => manifest[key].css ?? [])

const entryJsBytes = entryJsFiles.reduce((sum, file) => sum + gzipSize(file), 0)
const entryCssBytes = entryCssFiles.reduce((sum, file) => sum + gzipSize(file), 0)

const mapViewKey = Object.keys(manifest)
  .find((key) => key.endsWith('src/views/MapView.vue'))
const mapViewKeys = mapViewKey ? collectImports(manifest, mapViewKey) : new Set()
const firstScreenKeys = new Set([...entryKeys, ...mapViewKeys])
const firstScreenJsFiles = [...firstScreenKeys]
  .map((key) => manifest[key].file)
  .filter((file) => file.endsWith('.js'))
const firstScreenCssFiles = [...firstScreenKeys].flatMap((key) => manifest[key].css ?? [])

const allJsFiles = listJsFiles(distDir)
const chunkSizes = allJsFiles
  .map((file) => ({ file, gzip: gzipSize(file), raw: rawSize(file) }))
  .sort((a, b) => b.gzip - a.gzip)

const worldFile = findWorldJson(manifest)
const worldBytes = worldFile ? gzipSize(worldFile) : 0
const firstScreenJsBytes = firstScreenJsFiles.reduce((sum, file) => sum + gzipSize(file), 0)
const firstScreenCssBytes = firstScreenCssFiles.reduce((sum, file) => sum + gzipSize(file), 0)
const firstScreenBytes = firstScreenJsBytes + firstScreenCssBytes + worldBytes

console.log('包体积明细（gzip，level 9）')
console.log('----------------------------------------')
for (const file of entryJsFiles) {
  console.log(`入口 JS   ${kb(gzipSize(file)).padStart(10)}  ${file}`)
}
for (const file of entryCssFiles) {
  console.log(`入口 CSS  ${kb(gzipSize(file)).padStart(10)}  ${file}`)
}
if (mapViewKey) {
  for (const key of mapViewKeys) {
    const file = manifest[key].file
    if (file.endsWith('.js')) {
      console.log(`地图路由  ${kb(gzipSize(file)).padStart(10)}  ${file}`)
    }
  }
}
if (worldFile) {
  console.log(`地图数据  ${kb(worldBytes).padStart(10)}  ${worldFile}`)
}
console.log('----------------------------------------')
console.log(`入口 JS 合计      ${kb(entryJsBytes)}  / 预算 ${budgets.entryKb} KB`)
console.log(`首屏 JS+CSS      ${kb(firstScreenJsBytes + firstScreenCssBytes)}`)
console.log(`首屏合计          ${kb(firstScreenBytes)}  / 预算 ${budgets.firstScreenKb} KB`)
console.log('\n最大的 JS chunk（前 10）')
for (const chunk of chunkSizes.slice(0, 10)) {
  console.log(
    `  ${kb(chunk.gzip).padStart(10)} gzip  ${kb(chunk.raw).padStart(10)} raw  ${chunk.file}`
  )
}
if (!worldFile) {
  console.log('\n⚠ 未找到 world.json，首屏预算不含地图数据')
}

const failures = []
const heavyInEntry = [...entryKeys].filter((key) =>
  HEAVY_MODULES.some((name) => key.toLowerCase().includes(name))
)
if (heavyInEntry.length > 0) {
  failures.push(`入口包含重依赖模块：${heavyInEntry.join(', ')}`)
}
if (!mapViewKey) {
  failures.push('manifest 中找不到 src/views/MapView.vue，首屏预算无法计算')
}
if (entryJsBytes > budgets.entryKb * 1024) {
  failures.push(
    `入口 JS ${kb(entryJsBytes)} 超过预算 ${budgets.entryKb} KB（超出 ${kb(entryJsBytes - budgets.entryKb * 1024)}）`
  )
}
const largestChunk = chunkSizes[0]
if (largestChunk && largestChunk.gzip > budgets.chunkKb * 1024) {
  failures.push(
    `单个 JS chunk ${largestChunk.file} 为 ${kb(largestChunk.gzip)}，超过预算 ${budgets.chunkKb} KB`
  )
}
if (firstScreenBytes > budgets.firstScreenKb * 1024) {
  failures.push(
    `首屏合计 ${kb(firstScreenBytes)} 超过预算 ${budgets.firstScreenKb} KB（超出 ${kb(firstScreenBytes - budgets.firstScreenKb * 1024)}）`
  )
}

if (failures.length > 0) {
  console.error('\n体积预算未通过：')
  for (const failure of failures) {
    console.error(`  ✗ ${failure}`)
  }
  process.exit(1)
}

console.log('\n✓ 体积预算通过')