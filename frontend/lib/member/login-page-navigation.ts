export type LoginPageSessionStatus = 'loading' | 'authenticated' | 'anonymous' | 'unavailable'
export type LoginPageAction = 'wait' | 'redirect' | 'render' | 'retry'

export function getLoginPageAction(status: LoginPageSessionStatus): LoginPageAction {
  if (status === 'loading') return 'wait'
  if (status === 'authenticated') return 'redirect'
  if (status === 'unavailable') return 'retry'
  return 'render'
}

export function shouldPreserveLoginForm(action: LoginPageAction, formWasRendered: boolean): boolean {
  return action === 'render' || action === 'retry' || (action === 'wait' && formWasRendered)
}
