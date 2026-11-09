#!/usr/bin/env node
/**
 * 校验底图要素的中文名称与国家目录数据是否完整、同步。
 * 覆盖世界国家（V10）与极地地区（V11）。
 * 用法：node scripts/check-world-names.mjs
 * 任何一项失败时以退出码 1 结束，便于 CI 或提交前检查。
 */
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  buildMigrationSql,
  EXISTING_CODES
} from './generate-world-countries-migration.mjs'
import { buildPolarMigrationSql } from './generate-polar-regions-migration.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const geoPath = resolve(root, 'frontend/public/world.json')
const antarcticaPath = resolve(root, 'frontend/public/antarctica.json')
const namesPath = resolve(root, 'frontend/src/assets/world-names-zh.json')
const countriesPath = resolve(root, 'scripts/data/world-countries.json')
const polarPath = resolve(root, 'scripts/data/polar-regions.json')
const migrationPath = resolve(
  root,
  'backend/src/main/resources/db/migration/V10__all_world_countries.sql'
)
const polarMigrationPath = resolve(
  root,
  'backend/src/main/resources/db/migration/V11__polar_regions.sql'
)

const geo = JSON.parse(readFileSync(geoPath, 'utf8'))
const antarctica = JSON.parse(readFileSync(antarcticaPath, 'utf8'))
const names = JSON.parse(readFileSync(namesPath, 'utf8'))
const countries = JSON.parse(readFileSync(countriesPath, 'utf8'))
const polarRegions = JSON.parse(readFileSync(polarPath, 'utf8'))
const migrationSql = readFileSync(migrationPath, 'utf8')
const polarMigrationSql = readFileSync(polarMigrationPath, 'utf8')

function featureNames(collection) {
  const features = Array.isArray(collection?.features) ? collection.features : []
  return features
    .map((feature) => feature?.properties?.name)
    .filter((name) => typeof name === 'string' && name.length > 0)
}

/** 北极以点标记展示，不需要 GeoJSON 区域；仍要求目录中有对应条目。 */
const MARKER_ONLY_NAMES = new Set(['Arctic'])

const unique = [...new Set(featureNames(geo))]
const polarNames = [...new Set(featureNames(antarctica))]
const allMapNames = [...new Set([...unique, ...polarNames, ...MARKER_ONLY_NAMES])]

const missingNames = allMapNames.filter((name) => !names[name])
const unusedNames = Object.keys(names).filter((name) => !allMapNames.includes(name))

const countryByName = new Map(countries.map((entry) => [entry.nameEn, entry]))
const missingCountries = unique.filter((name) => !countryByName.has(name))
const extraCountries = countries
  .map((entry) => entry.nameEn)
  .filter((name) => !unique.includes(name))

const polarByName = new Map(polarRegions.map((entry) => [entry.nameEn, entry]))
const missingPolar = polarNames.filter((name) => !polarByName.has(name))
const missingMarkers = [...MARKER_ONLY_NAMES].filter((name) => !polarByName.has(name))
const extraPolar = polarRegions
  .map((entry) => entry.nameEn)
  .filter((name) => !polarNames.includes(name) && !MARKER_ONLY_NAMES.has(name))

const allEntries = [...countries, ...polarRegions]
const codeSet = new Set()
const badCodes = []
for (const entry of allEntries) {
  if (typeof entry.code !== 'string' || entry.code.length !== 2) {
    badCodes.push(`${entry.nameEn}: ${entry.code}`)
  }
  if (codeSet.has(entry.code)) {
    badCodes.push(`${entry.code} 重复（${entry.nameEn}）`)
  }
  codeSet.add(entry.code)
}

const missingZh = allEntries.filter((entry) => !entry.nameZh)
const zhMismatch = allEntries.filter(
  (entry) => names[entry.nameEn] && names[entry.nameEn] !== entry.nameZh
)

const expectedSql = buildMigrationSql(countries)
const sqlInSync = expectedSql === migrationSql
const expectedPolarSql = buildPolarMigrationSql(polarRegions)
const polarSqlInSync = expectedPolarSql === polarMigrationSql

const existingCount = countries.filter((entry) => EXISTING_CODES.has(entry.code)).length

console.log(`世界底图要素：${unique.length} 个`)
console.log(`极地底图要素：${polarNames.length} 个（${polarNames.join('、')}）`)
console.log(`对照表条目：${Object.keys(names).length} 个`)
console.log(`国家目录数据：${countries.length} 条（其中现有国家 ${existingCount} 条）`)
console.log(`极地地区数据：${polarRegions.length} 条`)
console.log(`V10 迁移：${sqlInSync ? '已同步' : '与数据源不一致'}`)
console.log(`V11 迁移：${polarSqlInSync ? '已同步' : '与数据源不一致'}`)

if (missingNames.length > 0) {
  console.error(`\n缺少中文名称（${missingNames.length}）：`)
  missingNames.forEach((name) => console.error(`  - ${name}`))
}
if (unusedNames.length > 0) {
  console.warn(`\n对照表中有 ${unusedNames.length} 个名称未在当前底图出现（不影响运行）：`)
  unusedNames.forEach((name) => console.warn(`  - ${name}`))
}
if (missingCountries.length > 0) {
  console.error(`\n国家目录缺少底图区域（${missingCountries.length}）：`)
  missingCountries.forEach((name) => console.error(`  - ${name}`))
}
if (extraCountries.length > 0) {
  console.warn(`\n国家目录中有 ${extraCountries.length} 个名称未在当前底图出现：`)
  extraCountries.forEach((name) => console.warn(`  - ${name}`))
}
if (missingPolar.length > 0) {
  console.error(`\n极地数据缺少底图区域（${missingPolar.length}）：`)
  missingPolar.forEach((name) => console.error(`  - ${name}`))
}
if (missingMarkers.length > 0) {
  console.error(`\n极地标记缺少目录条目（${missingMarkers.length}）：`)
  missingMarkers.forEach((name) => console.error(`  - ${name}`))
}
if (extraPolar.length > 0) {
  console.warn(`\n极地数据中有 ${extraPolar.length} 个名称未在极地底图出现：`)
  extraPolar.forEach((name) => console.warn(`  - ${name}`))
}
if (badCodes.length > 0) {
  console.error(`\n国家代码不合法或重复（${badCodes.length}）：`)
  badCodes.forEach((item) => console.error(`  - ${item}`))
}
if (missingZh.length > 0) {
  console.error(`\n国家目录缺少中文名（${missingZh.length}）：`)
  missingZh.forEach((entry) => console.error(`  - ${entry.nameEn}`))
}
if (zhMismatch.length > 0) {
  console.error(`\n中文名与对照表不一致（${zhMismatch.length}）：`)
  zhMismatch.forEach((entry) =>
    console.error(`  - ${entry.nameEn}: ${entry.nameZh} != ${names[entry.nameEn]}`)
  )
}
if (!sqlInSync) {
  console.error('\nV10 迁移与 scripts/data/world-countries.json 不一致，请运行：')
  console.error('  node scripts/generate-world-countries-migration.mjs')
}
if (!polarSqlInSync) {
  console.error('\nV11 迁移与 scripts/data/polar-regions.json 不一致，请运行：')
  console.error('  node scripts/generate-polar-regions-migration.mjs')
}

const failed =
  missingNames.length > 0 ||
  missingCountries.length > 0 ||
  missingPolar.length > 0 ||
  missingMarkers.length > 0 ||
  badCodes.length > 0 ||
  missingZh.length > 0 ||
  zhMismatch.length > 0 ||
  !sqlInSync ||
  !polarSqlInSync

if (failed) {
  process.exit(1)
}
console.log('\n全部底图要素均有中文名称，世界国家、极地地区与迁移文件校验通过。')