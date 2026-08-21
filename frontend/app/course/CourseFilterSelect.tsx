'use client'

import { useEffect, useId, useRef, useState } from 'react'

import styles from './course.module.css'

type CourseFilterOption = {
  value: string
  label: string
}

type CourseFilterSelectProps = {
  id: string
  ariaLabel: string
  options: readonly CourseFilterOption[]
  value: string
  onChange: (value: string) => void
  disabled?: boolean
}

export function CourseFilterSelect({
  id,
  ariaLabel,
  options,
  value,
  onChange,
  disabled = false,
}: CourseFilterSelectProps) {
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(() => Math.max(0, options.findIndex((option) => option.value === value)))
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([])
  const listboxId = useId()

  useEffect(() => {
    const selectedIndex = options.findIndex((option) => option.value === value)
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0)
  }, [options, value])

  useEffect(() => {
    if (!open) return
    optionRefs.current[activeIndex]?.focus()
  }, [activeIndex, open])

  useEffect(() => {
    if (!open) return

    const closeWhenOutside = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', closeWhenOutside)
    return () => document.removeEventListener('mousedown', closeWhenOutside)
  }, [open])

  const selectedOption = options.find((option) => option.value === value) ?? options[0]

  function closeAndRestoreFocus() {
    setOpen(false)
    triggerRef.current?.focus()
  }

  function choose(option: CourseFilterOption, index: number) {
    setActiveIndex(index)
    onChange(option.value)
    closeAndRestoreFocus()
  }

  function openAt(index: number) {
    setActiveIndex(Math.max(0, Math.min(index, options.length - 1)))
    setOpen(true)
  }

  return (
    <div ref={rootRef} className={styles.selectControl}>
      <button
        id={id}
        ref={triggerRef}
        type="button"
        className={styles.select}
        disabled={disabled}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        onClick={() => (open ? closeAndRestoreFocus() : openAt(activeIndex))}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            closeAndRestoreFocus()
          } else if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            openAt(activeIndex)
          } else if (event.key === 'ArrowUp') {
            event.preventDefault()
            openAt(options.length - 1)
          }
        }}
      >
        <span>{selectedOption?.label ?? '전체'}</span>
        <span className={styles.selectChevron} aria-hidden="true">⌄</span>
      </button>
      {open && !disabled ? (
        <div id={listboxId} className={styles.selectMenu} role="listbox" aria-label={ariaLabel}>
          {options.map((option, index) => {
            const selected = option.value === value
            return (
              <button
                key={option.value || 'all'}
                ref={(element) => {
                  optionRefs.current[index] = element
                }}
                type="button"
                role="option"
                aria-selected={selected}
                tabIndex={index === activeIndex ? 0 : -1}
                className={`${styles.selectOption} ${selected ? styles.selectOptionSelected : ''}`}
                onClick={() => choose(option, index)}
                onKeyDown={(event) => {
                  if (event.key === 'ArrowDown') {
                    event.preventDefault()
                    openAt((index + 1) % options.length)
                  } else if (event.key === 'ArrowUp') {
                    event.preventDefault()
                    openAt((index - 1 + options.length) % options.length)
                  } else if (event.key === 'Home') {
                    event.preventDefault()
                    openAt(0)
                  } else if (event.key === 'End') {
                    event.preventDefault()
                    openAt(options.length - 1)
                  } else if (event.key === 'Escape') {
                    event.preventDefault()
                    closeAndRestoreFocus()
                  } else if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    choose(option, index)
                  }
                }}
              >
                {option.label}
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}
