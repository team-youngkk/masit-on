import { createElement, Fragment, type ReactNode } from 'react'

export type CollectionScreenStateKind =
  | 'normal'
  | 'loading'
  | 'empty'
  | 'authentication'
  | 'not-found'
  | 'error'

type CollectionScreenStateProps = {
  state: CollectionScreenStateKind
  message?: string
  traceId?: string
  className?: string
  traceClassName?: string
  action?: ReactNode
  children?: ReactNode
}

export function CollectionScreenState({
  state,
  message,
  traceId,
  className,
  traceClassName,
  action,
  children,
}: CollectionScreenStateProps) {
  if (state === 'normal') {
    return createElement(Fragment, null, children)
  }

  const role = state === 'loading' || state === 'empty' ? undefined : 'alert'
  const live = state === 'loading' ? 'polite' : undefined

  return createElement(
    'div',
    { className, role, 'aria-live': live, 'data-collection-state': state },
    message ? createElement('p', null, message) : null,
    traceId
      ? createElement('p', { className: traceClassName }, `traceId: ${traceId}`)
      : null,
    action,
  )
}
