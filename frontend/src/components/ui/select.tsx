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
}

export function Select({ value, onValueChange, children }: SelectProps) {
  const items: Array<{ value: string; label: string }> = []

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
  })

  const displayLabel = items.find((i) => i.value === value)?.label ?? value

  return (
    <div className="relative">
      <div className="flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm">
        <span className="block truncate">{displayLabel}</span>
      </div>
      <select
        className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
        aria-label={displayLabel}
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
// continues to compile. The real rendering logic is handled by `Select` above.

export function SelectTrigger({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('hidden', className)}>{children}</div>
}

export function SelectContent({ children }: { children: React.ReactNode }) {
  return <div className="hidden">{children}</div>
}

export function SelectItem({ value, children }: { value: string; children: React.ReactNode }) {
  return <option value={value}>{children}</option>
}

export function SelectValue({ placeholder }: { placeholder?: string }) {
  return <span>{placeholder}</span>
}
