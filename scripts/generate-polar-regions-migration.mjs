#!/usr/bin/env node
/**
 * 从 scripts/data/polar-regions.json 生成极地地区迁移。
 * 用法：node scripts/generate-polar-regions-migration.mjs
 *
 * V10 已应用，不能修改；极地地区单独生成 V11，使用 INSERT IGNORE。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dataPath = resolve(root, 'scripts/data/polar-regions.json')
const outputPath = resolve(
  root,
  'backend/src/main/resources/db/migration/V11__polar_regions.sql'
)

function sqlString(value) {
  return `'${String(value).replace(/'/g, "''")}'`
}

export function buildPolarMigrationSql(entries) {
  const rows = [...entries]
    .sort((left, right) => left.sortOrder - right.sortOrder || left.code.localeCompare(right.code))
    .map(
      (entry) =>
        `  (${sqlString(entry.code)}, ${sqlString(entry.nameZh)}, ${sqlString(entry.nameEn)}, ` +
        `${entry.continent ? sqlString(entry.continent) : 'NULL'}, 'ENABLED', ${entry.sortOrder})`
    )

  return [
    '-- 由 scripts/generate-polar-regions-migration.mjs 生成，请勿手工修改。',
    '-- 数据源：scripts/data/polar-regions.json',
    '-- 南极洲与北极地区；世界国家数据由 V10__all_world_countries.sql 维护。',
    'INSERT IGNORE INTO `country`',
    '  (`code`, `name_zh`, `name_en`, `continent`, `status`, `sort_order`)',
    'VALUES',
    rows.join(',\n') + ';',
    ''
  ].join('\n')
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const entries = JSON.parse(readFileSync(dataPath, 'utf8'))
  writeFileSync(outputPath, buildPolarMigrationSql(entries), 'utf8')
  console.log(`已生成 ${outputPath}（${entries.length} 条极地地区）`)
}