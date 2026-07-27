'use client'

import type { InputHTMLAttributes, ReactNode } from 'react'
import { useId } from 'react'

import styles from './Field.module.css'

type FieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & {
  label: ReactNode
  /* 필드 단위 오류 메시지. API 오류 계약의 field 오류를 그대로 표시한다. */
  error?: string
}

export function Field({ label, error, className, ...props }: FieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  const classes = [styles.control, error && styles.invalid, className]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className={classes}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        {...props}
      />
      {error ? (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      ) : null}
    </div>
  )
}
