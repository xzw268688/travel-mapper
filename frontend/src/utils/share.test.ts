import { describe, expect, it } from 'vitest'
import { sharePageUrl } from './share'

describe('sharePageUrl', () => {
  it('builds an absolute share URL from the current origin', () => {
    expect(sharePageUrl('abc123')).toBe(`${window.location.origin}/share/abc123`)
  })

  it('encodes tokens that contain URL-significant characters', () => {
    expect(sharePageUrl('a/b?c=d')).toBe(
      `${window.location.origin}/share/a%2Fb%3Fc%3Dd`
    )
  })
})