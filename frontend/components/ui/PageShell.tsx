import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

import styles from './PageShell.module.css'

type PageShellProps = {
  children: ReactNode
  className?: string
  eyebrow?: ReactNode
  title?: ReactNode
  description?: ReactNode
  actions?: ReactNode
  size?: 'default' | 'narrow'
}

export function PageShell({
  children,
  className,
  eyebrow,
  title,
  description,
  actions,
  size = 'default',
}: PageShellProps) {
  const hasHeader = eyebrow != null || title != null || description != null || actions != null

  return (
    <div className={cn(styles.page, size === 'narrow' && styles.narrow, className)}>
      {hasHeader ? (
        <header className={styles.header}>
          <div className={styles.heading}>
            {eyebrow != null ? <p className={styles.eyebrow}>{eyebrow}</p> : null}
            {title != null ? <h1>{title}</h1> : null}
            {description != null ? <p className={styles.description}>{description}</p> : null}
          </div>
          {actions != null ? <div className={styles.actions}>{actions}</div> : null}
        </header>
      ) : null}
      {children}
    </div>
  )
}

type SectionHeaderProps = {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  level?: 2 | 3
}

export function SectionHeader({
  title,
  description,
  actions,
  level = 2,
}: SectionHeaderProps) {
  const Heading = `h${level}` as 'h2' | 'h3'

  return (
    <div className={styles.sectionHeader}>
      <div>
        <Heading>{title}</Heading>
        {description != null ? <p>{description}</p> : null}
      </div>
      {actions != null ? <div className={styles.actions}>{actions}</div> : null}
    </div>
  )
}
