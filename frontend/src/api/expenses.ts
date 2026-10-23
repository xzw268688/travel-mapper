import { apiDelete, apiGet, apiPost, apiPut } from './http'

export interface ExpenseCreate {
  countryCode: string
  cityCode?: string | null
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

export function createExpense(body: ExpenseCreate): Promise<any> {
  return apiPost('/expenses', body)
}

export function listExpenses(params: Record<string, unknown> = {}): Promise<ExpensePage> {
  return apiGet<ExpensePage>('/expenses', params)
}

export function updateExpense(id: number, body: Record<string, unknown>): Promise<any> {
  return apiPut(`/expenses/${id}`, body)
}

export function deleteExpense(id: number): Promise<any> {
  return apiDelete(`/expenses/${id}`)
}

export function getExpenseSummary(groupBy: string): Promise<ExpenseSummary> {
  return apiGet<ExpenseSummary>('/expenses/summary', { groupBy })
}
