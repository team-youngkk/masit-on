import { createElement, Fragment } from 'react'

export function RegistrationDuplicateResult({
  resourceName,
  existing,
  onContinue,
  errorClassName,
  buttonClassName,
}: {
  resourceName: string
  existing: string | null
  onContinue?: () => void
  errorClassName?: string
  buttonClassName?: string
}) {
  return createElement(
    Fragment,
    null,
    createElement('p', { className: errorClassName }, `이미 등록된 ${resourceName}입니다.`),
    existing ? createElement('pre', null, existing) : null,
    onContinue
      ? createElement('button', { type: 'button', className: buttonClassName, onClick: onContinue },
        `기존 ${resourceName} 사용하고 다음 단계`)
      : null,
  )
}
