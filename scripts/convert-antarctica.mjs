#!/usr/bin/env node
/**
 * 将南极方位投影的 Antarctica 轮廓转换为世界地图使用的经纬度。
 * 用法：node scripts/convert-antarctica.mjs
 *
 * 原始坐标 (x, y) 为距南极点的角距离（度），x 轴对应 90°E。
 * 逆投影：lon = atan2(y, x) + 90°，lat = -90 + hypot(x, y)。
 *
 * 南极洲是一个环绕极点闭合的环。直接把环形坐标按原顺序写入经纬度
 * 会在 ±180° 经线处出现横跨地图的连线，因此这里按经度排序后，
 * 用 -90° 的底边把轮廓闭合成一个从地图底部向上的区域。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const inputPath = resolve(root, 'scripts/data/antarctica-azimuthal.json')
const outputPath = resolve(root, 'frontend/public/antarctica.json')

const input = JSON.parse(readFileSync(inputPath, 'utf8'))

const toLongitude = (x, y) => {
  const raw = (Math.atan2(y, x) * 180) / Math.PI + 90
  return ((((raw + 180) % 360) + 360) % 360) - 180
}

const feature = input.features?.[0]
if (!feature || feature.geometry?.type !== 'Polygon') {
  throw new Error('antarctica-azimuthal.json 不是预期的 Polygon FeatureCollection')
}

const ring = feature.geometry.coordinates[0]
const coast = ring
  .map(([x, y]) => ({
    lon: Number(toLongitude(x, y).toFixed(4)),
    lat: Number((-90 + Math.hypot(x, y)).toFixed(4))
  }))
  .sort((left, right) => left.lon - right.lon)

const output = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {
        name: 'Antarctica',
        projection: 'equirectangular',
        source: 'scripts/data/antarctica-azimuthal.json'
      },
      geometry: {
        type: 'Polygon',
        coordinates: [
          [
            [-180, -90],
            ...coast.map((point) => [point.lon, point.lat]),
            [180, -90],
            [-180, -90]
          ]
        ]
      }
    }
  ]
}

writeFileSync(outputPath, JSON.stringify(output, null, 2) + '\n', 'utf8')
console.log(`已生成 ${outputPath}（${coast.length} 个海岸点）`)