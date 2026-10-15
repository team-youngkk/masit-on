export type FilterFormLookup = {
  getElementById: (id: string) => unknown
}

type SubmitForm = {
  requestSubmit: () => void
}

export function shouldSubmitFilterChange(
  submitOnChange: boolean,
  currentValue: string,
  nextValue: string,
): boolean {
  return submitOnChange && currentValue !== nextValue
}

export function submitFilterForm(
  documentRef: FilterFormLookup,
  formId: string,
): void {
  const form = documentRef.getElementById(formId)
  if (!isSubmitForm(form)) return
  form.requestSubmit()
}

function isSubmitForm(value: unknown): value is SubmitForm {
  return (
    typeof value === 'object' &&
    value !== null &&
    'requestSubmit' in value &&
    typeof value.requestSubmit === 'function'
  )
}
