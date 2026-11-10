const BEIJING_TIME_ZONE = 'Asia/Shanghai'

function beijingDateParts(date: Date): { year: string; month: string; day: string } {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: BEIJING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(date)
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? ''
  return { year: value('year'), month: value('month'), day: value('day') }
}

/** 后端返回的日期时间已经是北京时间墙钟，展示时不做时区换算。 */
export function formatDate(value?: string | null): string {
  return value ? value.slice(0, 10) : ''
}

export function formatDateTime(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

export function formatDateTimeSeconds(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

/** 以北京时间计算“今天”，与浏览器所在时区无关。 */
export function todayInBeijing(): string {
  const { year, month, day } = beijingDateParts(new Date())
  return `${year}-${month}-${day}`
}

/** 对 YYYY-MM-DD 做日期加减，使用 UTC 避免本地时区与 DST 干扰。 */
export function addDays(value: string, amount: number): string {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  date.setUTCDate(date.getUTCDate() + amount)
  return date.toISOString().slice(0, 10)
}

export function currentBeijingMonth(): string {
  return todayInBeijing().slice(0, 7)
}

/** 返回某个月的北京时间首日与末日，month 格式为 YYYY-MM。 */
export function monthRange(month = currentBeijingMonth()): [string, string] {
  const [year, monthNumber] = month.split('-').map(Number)
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate()
  return [
    `${year}-${String(monthNumber).padStart(2, '0')}-01`,
    `${year}-${String(monthNumber).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
  ]
}