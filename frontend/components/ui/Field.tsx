'use client'

import type { InputHTMLAttributes, ReactNode } from 'react'
import { useId } from 'react'

import { cn } from '@/lib/cn'

import styles from './Field.module.css'

type FieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & {
  label: ReactNode
  /* 필드 단위 오류 메시지. API 오류 계약의 field 오류를 그대로 표시한다. */
  error?: string
}

export function Field({ label, error, className, ...props }: FieldProps) {
  const id = useId()
  const errorId = `${id}-error`

  /*
   * 호출자가 넘긴 aria-describedby를 덮지 않고 오류 메시지 id와 합친다.
   * props를 먼저 펼치고 계산한 값을 뒤에 두어 스프레드가 이기지 않게 한다.
   */
  const describedBy =
    [props['aria-describedby'], error ? errorId : undefined]
      .filter(Boolean)
      .join(' ') || undefined

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      <input
        {...props}
        id={id}
        className={cn(styles.control, error && styles.invalid, className)}
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={describedBy}
      />
      {error ? (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      ) : null}
    </div>
  )
}
