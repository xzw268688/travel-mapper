/**
 * v-html 渲染的正文图片无法在模板上声明懒加载属性，
 * 渲染完成后统一补齐，避免首屏一次性下载全部正文图片。
 */
export function decorateLazyImages(root: HTMLElement | null | undefined): void {
  if (!root) {
    return
  }
  root.querySelectorAll('img').forEach((image) => {
    if (!image.hasAttribute('loading')) {
      image.setAttribute('loading', 'lazy')
    }
    if (!image.hasAttribute('decoding')) {
      image.setAttribute('decoding', 'async')
    }
  })
}