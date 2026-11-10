import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  addDays,
  currentBeijingMonth,
  formatDate,
  formatDateTime,
  formatDateTimeSeconds,
  monthRange,
  todayInBeijing
} from './datetime'

describe('datetime formatting', () => {
  it('formats Beijing wall-clock strings without timezone conversion', () => {
    expect(formatDate('2026-11-10T20:30:00')).toBe('2026-11-10')
    expect(formatDateTime('2026-11-10T20:30:00')).toBe('2026-11-10 20:30')
    expect(formatDateTimeSeconds('2026-11-10T20:30:45')).toBe('2026-11-10 20:30:45')
  })

  it('returns an empty string for missing values', () => {
    expect(formatDate(null)).toBe('')
    expect(formatDateTime(undefined)).toBe('')
    expect(formatDateTimeSeconds('')).toBe('')
  })
})

describe('Beijing date arithmetic', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('uses the new Beijing date at 00:30 even when UTC is still the previous day', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-09T16:30:00Z'))

    expect(todayInBeijing()).toBe('2026-11-10')
    expect(currentBeijingMonth()).toBe('2026-11')
  })

  it('does not shift the date just before Beijing midnight', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-09T15:30:00Z'))

    expect(todayInBeijing()).toBe('2026-11-09')
  })

  it('adds and subtracts days across month and year boundaries', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
    expect(addDays('2024-03-01', -1)).toBe('2024-02-29')
  })

  it('returns inclusive month ranges', () => {
    expect(monthRange('2026-11')).toEqual(['2026-11-01', '2026-11-30'])
    expect(monthRange('2024-02')).toEqual(['2024-02-01', '2024-02-29'])
    expect(monthRange('2026-12')).toEqual(['2026-12-01', '2026-12-31'])
  })
})