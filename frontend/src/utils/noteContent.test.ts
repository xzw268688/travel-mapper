import { describe, expect, it } from 'vitest'
import {
  contentToPreview,
  hasContent,
  imageKeyFromSrc,
  imageStorageSrc,
  previewToContent
} from './noteContent'

describe('imageStorageSrc', () => {
  it('prefixes object keys with the storage path', () => {
    expect(imageStorageSrc('notes/1/photo.jpg')).toBe('/objects/notes/1/photo.jpg')
  })
})

describe('imageKeyFromSrc', () => {
  it('extracts and decodes object keys', () => {
    expect(imageKeyFromSrc('/objects/notes/1/my%20photo.jpg')).toBe('notes/1/my photo.jpg')
  })

  it('ignores query strings and fragments', () => {
    expect(imageKeyFromSrc('/objects/notes/1/photo.jpg?token=x#preview')).toBe(
      'notes/1/photo.jpg'
    )
  })

  it('returns null for non-storage URLs', () => {
    expect(imageKeyFromSrc('https://cdn.example.com/photo.jpg')).toBeNull()
    expect(imageKeyFromSrc('')).toBeNull()
  })
})

describe('contentToPreview', () => {
  it('replaces storage keys with signed URLs for images and videos', () => {
    const html =
      '<p><img src="/objects/a.jpg"><video src="/objects/b.mp4"></video></p>'

    const result = contentToPreview(html, {
      'a.jpg': 'https://signed/a.jpg',
      'b.mp4': 'https://signed/b.mp4'
    })

    expect(result).toContain('src="https://signed/a.jpg"')
    expect(result).toContain('src="https://signed/b.mp4"')
  })

  it('removes the src when no signed URL is available', () => {
    const result = contentToPreview('<img src="/objects/missing.jpg">', {})

    expect(result).toContain('<img>')
  })

  it('leaves external URLs untouched and handles empty input', () => {
    expect(contentToPreview('<img src="https://cdn.example.com/a.jpg">', {})).toContain(
      'https://cdn.example.com/a.jpg'
    )
    expect(contentToPreview('', {})).toBe('')
  })
})

describe('previewToContent', () => {
  it('restores signed URLs to stable object keys', () => {
    const html = '<img src="https://signed/a.jpg"><img src="blob:local">'

    const result = previewToContent(html, {
      'https://signed/a.jpg': 'a.jpg',
      'blob:local': 'b.jpg'
    })

    expect(result).toContain('src="/objects/a.jpg"')
    expect(result).toContain('src="/objects/b.jpg"')
  })

  it('keeps unknown URLs unchanged and handles empty input', () => {
    const external = '<img src="https://cdn.example.com/a.jpg">'
    expect(previewToContent(external, {})).toContain(
      'https://cdn.example.com/a.jpg'
    )
    expect(previewToContent('', {})).toBe('')
  })
})

describe('hasContent', () => {
  it('treats text, images and videos as content', () => {
    expect(hasContent('<p>hello</p>')).toBe(true)
    expect(hasContent('<p><img src="/objects/a.jpg"></p>')).toBe(true)
    expect(hasContent('<p><video src="/objects/a.mp4"></video></p>')).toBe(true)
  })

  it('treats empty or whitespace-only markup as empty', () => {
    expect(hasContent('')).toBe(false)
    expect(hasContent('<p>   </p>')).toBe(false)
    expect(hasContent('<p></p>')).toBe(false)
  })
})