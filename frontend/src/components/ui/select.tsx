import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Lightweight native-select wrapper that exposes a Shadcn-compatible API subset.
 * Uses a real `<select>` element for full a11y and no extra dependencies.
 */

interface SelectProps {
  value: string
  onValueChange: (value: string) => void
  children: React.ReactNode
  placeholder?: string
}

export function Select({ value, onValueChange, children, placeholder: placeholderProp }: SelectProps) {
  const items: Array<{ value: string; label: string }> = []
  let placeholder = placeholderProp

  React.Children.forEach(children, (child) => {
    if (!React.isValidElement(child)) return
    if ((child.type as React.ElementType) === SelectContent) {
      React.Children.forEach(
        (child.props as { children?: React.ReactNode }).children,
        (item) => {
          if (!React.isValidElement(item)) return
          const p = item.props as { value?: string; children?: React.ReactNode }
          if (p.value != null) {
            items.push({
              value: String(p.value),
              label: typeof p.children === 'string' ? p.children : String(p.value),
            })
          }
        },
      )
    }
    // Extract placeholder from SelectValue child
    if ((child.type as React.ElementType) === SelectValue) {
      const p = child.props as { placeholder?: string }
      if (!placeholder && p.placeholder) {
        placeholder = p.placeholder
      }
    }
  })

  const matched = items.find((i) => i.value === value)
  const displayLabel = matched?.label ?? (value === '' ? (placeholder ?? '') : value)
  const ariaLabel = matched?.label ?? placeholder ?? 'Select option'

  return (
    <div className="relative">
      <div className="flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm">
        <span className={cn('block truncate', !matched && 'text-muted-foreground')}>{displayLabel}</span>
      </div>
      <select
        className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
        aria-label={ariaLabel}
      >
        {items.map((item) => (
          <option key={item.value} value={item.value}>
            {item.label}
          </option>
        ))}
      </select>
    </div>
  )
}

// These are type-only shims so the import syntax `Select, SelectContent, SelectTrigger, SelectItem, SelectValue`
// continues to compile. They render null — all DOM output is in `Select` above.

export const SelectTrigger: React.FC<{ children?: React.ReactNode; className?: string }> = () => null

export const SelectContent: React.FC<{ children?: React.ReactNode }> = () => null

export const SelectItem: React.FC<{ value: string; children?: React.ReactNode }> = () => null

export const SelectValue: React.FC<{ placeholder?: string }> = () => null
