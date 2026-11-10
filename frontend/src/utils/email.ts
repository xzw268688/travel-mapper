/**
 * 邮箱输入容错：中文输入法下 @ 和 . 容易被敲成全角，复制粘贴也可能带入空白。
 * 统一做 NFKC 归一化（全角转半角）并去掉空白后再校验与提交。
 */
export function normalizeEmail(value: string): string {
  return value.normalize('NFKC').replace(/\s+/g, '')
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidEmail(value: string): boolean {
  return EMAIL_PATTERN.test(normalizeEmail(value))
}