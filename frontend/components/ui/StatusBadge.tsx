import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

import styles from './StatusBadge.module.css'

type StatusBadgeProps = {
  children: ReactNode
  tone?: 'success' | 'neutral' | 'warning' | 'danger' | 'info'
  className?: string
}

export function StatusBadge({ children, tone = 'neutral', className }: StatusBadgeProps) {
  return <span className={cn(styles.badge, styles[tone], className)}>{children}</span>
}
