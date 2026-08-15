import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

import styles from './StatePanel.module.css'

type StatePanelProps = {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  icon?: ReactNode
  tone?: 'neutral' | 'success' | 'warning' | 'danger'
  compact?: boolean
  className?: string
  traceId?: string | null
  headingLevel?: 2 | 3 | 4
}

export function StatePanel({
  title,
  description,
  actions,
  icon,
  tone = 'neutral',
  compact = false,
  className,
  traceId,
  headingLevel = 2,
}: StatePanelProps) {
  const isAlert = tone === 'danger' || tone === 'warning'
  const Heading = `h${headingLevel}` as 'h2' | 'h3' | 'h4'

  return (
    <section
      className={cn(styles.panel, styles[tone], compact && styles.compact, className)}
      role={isAlert ? 'alert' : 'status'}
    >
      {icon != null ? <span className={styles.icon} aria-hidden="true">{icon}</span> : null}
      <div className={styles.content}>
        <Heading>{title}</Heading>
        {description != null ? <div className={styles.description}>{description}</div> : null}
        {traceId ? <p className={styles.trace}>traceId: {traceId}</p> : null}
        {actions != null ? <div className={styles.actions}>{actions}</div> : null}
      </div>
    </section>
  )
}
