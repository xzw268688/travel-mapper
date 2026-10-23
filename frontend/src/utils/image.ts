/**
 * Canvas re-draw both compresses the image and drops EXIF metadata (including GPS).
 */
export async function compressImage(file: File, maxWidth = 1600, quality = 0.85): Promise<File> {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, maxWidth / bitmap.width)
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(bitmap.width * scale))
  canvas.height = Math.max(1, Math.round(bitmap.height * scale))
  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('浏览器不支持图片压缩')
  }
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/jpeg', quality)
  )
  if (!blob) {
    throw new Error('图片压缩失败')
  }
  return new File([blob], 'image.jpg', { type: 'image/jpeg' })
}
