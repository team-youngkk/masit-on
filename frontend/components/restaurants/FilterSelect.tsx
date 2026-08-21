'use client'

import { useEffect, useId, useRef, useState } from 'react'

export type FilterSelectOption = {
  value: string
  label: string
}

type FilterSelectProps = {
  id: string
  formId: string
  name: string
  options: readonly FilterSelectOption[]
  value: string
  submittedValue?: string
  placeholder: string
  disabled?: boolean
  className: string
  menuClassName: string
  optionClassName: string
  selectedOptionClassName: string
  controlClassName: string
}

export function FilterSelect({
  id,
  formId,
  name,
  options,
  value,
  submittedValue,
  placeholder,
  disabled = false,
  className,
  menuClassName,
  optionClassName,
  selectedOptionClassName,
  controlClassName,
}: FilterSelectProps) {
  const [open, setOpen] = useState(false)
  const [selectedValue, setSelectedValue] = useState(value)
  const [activeIndex, setActiveIndex] = useState(() => {
    const selectedIndex = options.findIndex((option) => option.value === value)
    return selectedIndex >= 0 ? selectedIndex + 1 : 0
  })
  const [menuPosition, setMenuPosition] = useState({ left: 0, width: 288 })
  const controlRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([])
  const listboxId = useId()

  useEffect(() => {
    setSelectedValue(value)
    const selectedIndex = options.findIndex((option) => option.value === value)
    setActiveIndex(selectedIndex >= 0 ? selectedIndex + 1 : 0)
    setOpen(false)
  }, [options, value])

  useEffect(() => {
    if (!open) return
    optionRefs.current[activeIndex]?.focus()
  }, [activeIndex, open])

  useEffect(() => {
    if (!open) return

    const updateMenuPosition = () => {
      const control = controlRef.current
      const trigger = triggerRef.current
      if (!control || !trigger) return

      const controlRect = control.getBoundingClientRect()
      const triggerRect = trigger.getBoundingClientRect()
      const viewportPadding = 16
      const width = Math.min(288, window.innerWidth - viewportPadding * 2)
      const left = Math.max(
        viewportPadding,
        Math.min(
          triggerRect.left,
          window.innerWidth - viewportPadding - width,
        ),
      )

      setMenuPosition({ left: left - controlRect.left, width })
    }

    updateMenuPosition()
    window.addEventListener('resize', updateMenuPosition)
    return () => window.removeEventListener('resize', updateMenuPosition)
  }, [open])

  useEffect(() => {
    if (!open) return

    const closeWhenOutside = (event: MouseEvent) => {
      if (!controlRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const closeWhenFocusLeaves = (event: FocusEvent) => {
      if (!controlRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', closeWhenOutside)
    document.addEventListener('focusin', closeWhenFocusLeaves)
    return () => {
      document.removeEventListener('mousedown', closeWhenOutside)
      document.removeEventListener('focusin', closeWhenFocusLeaves)
    }
  }, [open])

  const values = [
    { value: '', label: placeholder },
    ...options,
  ]
  const selectedOption = options.find((option) => option.value === selectedValue)
  const selectedLabel = selectedOption?.label ?? placeholder
  const formValue = submittedValue ?? selectedValue

  const openAt = (index: number) => {
    setActiveIndex(Math.max(0, Math.min(index, values.length - 1)))
    setOpen(true)
  }
  const closeAndRestoreFocus = () => {
    setOpen(false)
    triggerRef.current?.focus()
  }
  const selectValue = (nextValue: string, nextIndex: number) => {
    setSelectedValue(nextValue)
    setActiveIndex(nextIndex)
    closeAndRestoreFocus()
  }

  return (
    <div ref={controlRef} className={controlClassName}>
      <input type="hidden" name={name} value={formValue} form={formId} />
      <button
        type="button"
        id={id}
        ref={triggerRef}
        className={className}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listboxId}
        aria-label={`${placeholder} 필터`}
        onClick={() => (open ? closeAndRestoreFocus() : openAt(activeIndex))}
        onKeyDown={(event) => {
          if (disabled) return
          if (event.key === 'Escape') {
            closeAndRestoreFocus()
          } else if (
            event.key === 'ArrowDown' ||
            event.key === 'Enter' ||
            event.key === ' '
          ) {
            event.preventDefault()
            openAt(activeIndex)
          } else if (event.key === 'ArrowUp') {
            event.preventDefault()
            openAt(values.length - 1)
          }
        }}
      >
        {selectedLabel}
      </button>
      {open && !disabled ? (
        <div
          id={listboxId}
          className={menuClassName}
          role="listbox"
          aria-label={placeholder}
          style={menuPosition}
        >
          {values.map((option, index) => {
            const selected = selectedValue === option.value
            return (
              <button
                key={option.value || placeholder}
                type="button"
                role="option"
                aria-selected={selected}
                tabIndex={index === activeIndex ? 0 : -1}
                ref={(element) => {
                  optionRefs.current[index] = element
                }}
                className={`${optionClassName} ${selected ? selectedOptionClassName : ''}`}
                onClick={() => selectValue(option.value, index)}
                onKeyDown={(event) => {
                  if (event.key === 'ArrowDown') {
                    event.preventDefault()
                    openAt((index + 1) % values.length)
                  } else if (event.key === 'ArrowUp') {
                    event.preventDefault()
                    openAt((index - 1 + values.length) % values.length)
                  } else if (event.key === 'Home') {
                    event.preventDefault()
                    openAt(0)
                  } else if (event.key === 'End') {
                    event.preventDefault()
                    openAt(values.length - 1)
                  } else if (event.key === 'Escape') {
                    event.preventDefault()
                    closeAndRestoreFocus()
                  } else if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    selectValue(option.value, index)
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
