/* 조건부 className을 합친다. false·null·undefined는 버린다. */
export function cn(...values: Array<string | false | null | undefined>) {
  return values.filter(Boolean).join(' ')
}
