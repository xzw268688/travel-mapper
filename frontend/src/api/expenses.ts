import { apiDelete, apiGet, apiPost, apiPut } from './http'

export interface ExpenseCreate {
  countryCode: string
  cityCode?: string | null
  tripId?: number | null
  currency: string
  amount: string
  category: string
  expenseDate: string
  remark?: string
}

export interface ExpenseResponse {
  id: number
  countryCode: string
  countryName: string
  cityCode: string | null
  cityName: string | null
  tripId: number | null
  currency: string
  amount: string
  exchangeRateSnapshot: string | null
  amountCny: string | null
  rateMissing: boolean
  category: string
  expenseDate: string
  remark: string | null
  createdAt: string
}

export interface ExpensePage {
  total: number
  page: number
  size: number
  list: ExpenseResponse[]
}

export interface SummaryGroup {
  key: string
  label: string
  totalCny: string
  categories: Array<{ category: string; totalCny: string }>
  originalCurrencies: Array<{ currency: string; total: string }>
}

export interface ExpenseSummary {
  groupBy: string[]
  currency: string
  totalCny: string
  hasUnconfiguredCurrency: boolean
  unconfiguredCurrencies: string[]
  groups: SummaryGroup[]
}

export interface ExpenseDateGroup {
  date: string
  totalCny: string
  originalCurrencies: Array<{ currency: string; total: string }>
  items: ExpenseResponse[]
}

export interface ExpenseDateGrouped {
  totalCny: string
  hasUnconfiguredCurrency: boolean
  unconfiguredCurrencies: string[]
  groups: ExpenseDateGroup[]
}

export interface ExpenseFilters {
  countryCode?: string
  cityCode?: string
  tripId?: number
  category?: string
  from?: string
  to?: string
}

export function createExpense(body: ExpenseCreate): Promise<unknown> {
  return apiPost('/expenses', body)
}

export function listExpenses(params: Record<string, unknown> = {}): Promise<ExpensePage> {
  return apiGet<ExpensePage>('/expenses', params)
}

export function updateExpense(id: number, body: Record<string, unknown>): Promise<unknown> {
  return apiPut(`/expenses/${id}`, body)
}

export function deleteExpense(id: number): Promise<{ id: number; tripDeleted: boolean }> {
  return apiDelete(`/expenses/${id}`)
}

export function listExpensesByDate(
  params: Record<string, unknown> = {}
): Promise<ExpenseDateGrouped> {
  return apiGet<ExpenseDateGrouped>('/expenses/by-date', params)
}

export function getExpenseSummary(
  groupBy: string,
  filters: ExpenseFilters = {}
): Promise<ExpenseSummary> {
  return apiGet<ExpenseSummary>('/expenses/summary', { groupBy, ...filters })
}
