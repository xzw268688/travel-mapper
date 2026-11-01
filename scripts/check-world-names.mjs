#!/usr/bin/env node
/**
 * 校验底图要素是否都有中文名称映射。
 * 用法：node scripts/check-world-names.mjs
 * 缺失时以退出码 1 结束，便于 CI 或提交前检查。
 */
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const geoPath = resolve(root, 'frontend/public/world.json')
const namesPath = resolve(root, 'frontend/src/assets/world-names-zh.json')

const geo = JSON.parse(readFileSync(geoPath, 'utf8'))
const names = JSON.parse(readFileSync(namesPath, 'utf8'))

const features = Array.isArray(geo.features) ? geo.features : []
const sourceNames = features
  .map((feature) => feature?.properties?.name)
  .filter((name) => typeof name === 'string' && name.length > 0)

const unique = [...new Set(sourceNames)]
const missing = unique.filter((name) => !names[name])
const unused = Object.keys(names).filter((name) => !unique.includes(name))

console.log(`底图要素：${unique.length} 个`)
console.log(`对照表条目：${Object.keys(names).length} 个`)

if (missing.length > 0) {
  console.error(`\n缺少中文名称（${missing.length}）：`)
  missing.forEach((name) => console.error(`  - ${name}`))
}
if (unused.length > 0) {
  console.warn(`\n对照表中有 ${unused.length} 个名称未在当前底图出现（不影响运行）：`)
  unused.forEach((name) => console.warn(`  - ${name}`))
}

if (missing.length > 0) {
  process.exit(1)
}
console.log('\n全部底图要素均有中文名称。')