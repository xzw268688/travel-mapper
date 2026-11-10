import { describe, expect, it } from 'vitest'
import { isValidEmail, normalizeEmail } from './email'

describe('normalizeEmail', () => {
  it('converts full-width characters to half-width', () => {
    expect(normalizeEmail('ａｂｃ＠ｅｘａｍｐｌｅ．ｃｏｍ')).toBe('abc@example.com')
  })

  it('removes whitespace introduced by copy and paste', () => {
    expect(normalizeEmail('  user @ example . com ')).toBe('user@example.com')
  })

  it('keeps an already normalized address unchanged', () => {
    expect(normalizeEmail('user@example.com')).toBe('user@example.com')
  })
})

describe('isValidEmail', () => {
  it.each(['user@example.com', 'a.b+tag@sub.example.cn', '用户@例子.中国'])(
    'accepts %s',
    (value) => {
      expect(isValidEmail(value)).toBe(true)
    }
  )

  it.each(['', 'user', 'user@', '@example.com', 'user@example'])(
    'rejects %s',
    (value) => {
      expect(isValidEmail(value)).toBe(false)
    }
  )

  it('accepts full-width input after normalization', () => {
    expect(isValidEmail('ｕｓｅｒ＠ｅｘａｍｐｌｅ．ｃｏｍ')).toBe(true)
  })
})