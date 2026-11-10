#!/usr/bin/env node
/**
 * 从 scripts/data/world-countries.json 生成国家目录迁移。
 * 用法：node scripts/generate-world-countries-migration.mjs
 *
 * 只插入目录中原本没有的国家（现有 9 国由 V2 迁移负责），
 * 使用 INSERT IGNORE 保证重复执行不会覆盖已有数据。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dataPath = resolve(root, 'scripts/data/world-countries.json')
const outputPath = resolve(
  root,
  'backend/src/main/resources/db/migration/V10__all_world_countries.sql'
)

/** V2__seed.sql 已有的国家；迁移不重复写入。 */
export const EXISTING_CODES = new Set(['CN', 'JP', 'TH', 'KR', 'SG', 'FR', 'IT', 'US', 'AU'])

const CONTINENT_ORDER = [
  'ASIA',
  'EUROPE',
  'AFRICA',
  'NORTH_AMERICA',
  'SOUTH_AMERICA',
  'OCEANIA',
  'ANTARCTICA'
]

function sqlString(value) {
  return `'${String(value).replace(/'/g, "''")}'`
}

function continentRank(continent) {
  const index = CONTINENT_ORDER.indexOf(continent)
  return index === -1 ? CONTINENT_ORDER.length : index
}

export function buildMigrationSql(entries) {
  const missing = entries
    .filter((entry) => !EXISTING_CODES.has(entry.code))
    .sort(
      (left, right) =>
        continentRank(left.continent) - continentRank(right.continent) ||
        left.nameZh.localeCompare(right.nameZh, 'zh-CN') ||
        left.code.localeCompare(right.code)
    )

  const rows = missing.map(
    (entry, index) =>
      `  (${sqlString(entry.code)}, ${sqlString(entry.nameZh)}, ${sqlString(entry.nameEn)}, ` +
      `${entry.continent ? sqlString(entry.continent) : 'NULL'}, 'ENABLED', ${1000 + index})`
  )

  return [
    '-- 由 scripts/generate-world-countries-migration.mjs 生成，请勿手工修改。',
    '-- 数据源：scripts/data/world-countries.json',
    '-- 覆盖 world.json 中全部有名称的地理区域；已有国家由 V2__seed.sql 维护，本迁移只补缺失项。',
    'INSERT IGNORE INTO `country`',
    '  (`code`, `name_zh`, `name_en`, `continent`, `status`, `sort_order`)',
    'VALUES',
    rows.join(',\n') + ';',
    ''
  ].join('\n')
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const entries = JSON.parse(readFileSync(dataPath, 'utf8'))
  writeFileSync(outputPath, buildMigrationSql(entries), 'utf8')
  console.log(`已生成 ${outputPath}（${entries.length} 条数据，跳过已存在的 9 国）`)
}