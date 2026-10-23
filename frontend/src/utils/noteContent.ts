const OBJECT_PREFIX = '/objects/'

/**
 * 正文中的图片以 `/objects/{objectKey}` 形式持久化。
 * 该路径不含协议，能通过服务端 Jsoup 白名单；展示与编辑时再替换为签名 URL。
 */
export function imageStorageSrc(objectKey: string): string {
  return `${OBJECT_PREFIX}${objectKey}`
}

export function imageKeyFromSrc(src: string): string | null {
  if (!src.startsWith(OBJECT_PREFIX)) {
    return null
  }
  const clean = src.slice(OBJECT_PREFIX.length).split(/[?#]/)[0]
  try {
    return decodeURIComponent(clean)
  } catch {
    return clean
  }
}

/** 将正文中的对象 Key 替换为签名 URL，用于展示/编辑器预览。 */
export function contentToPreview(html: string, urlByKey: Record<string, string>): string {
  if (!html) {
    return ''
  }
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('img').forEach((img) => {
    const key = imageKeyFromSrc(img.getAttribute('src') || '')
    if (key) {
      const url = urlByKey[key]
      if (url) {
        img.setAttribute('src', url)
      } else {
        img.removeAttribute('src')
      }
    }
  })
  return doc.body.innerHTML
}

/**
 * 将编辑器中的签名 URL / blob URL 还原为稳定的对象 Key，保存前调用。
 * 匹配不到的图片原样保留（例如外链图片，服务端会按白名单处理）。
 */
export function previewToContent(html: string, keyByUrl: Record<string, string>): string {
  if (!html) {
    return ''
  }
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('img').forEach((img) => {
    const src = img.getAttribute('src') || ''
    const key = keyByUrl[src]
    if (key) {
      img.setAttribute('src', imageStorageSrc(key))
    }
  })
  return doc.body.innerHTML
}

/** 正文是否包含实际内容（文字或图片），避免只保存空段落。 */
export function hasContent(html: string): boolean {
  if (!html) {
    return false
  }
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return Boolean(doc.body.textContent?.trim()) || doc.querySelectorAll('img').length > 0
}
