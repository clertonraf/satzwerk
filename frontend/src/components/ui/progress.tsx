import * as React from "react"
import * as ProgressPrimitive from "@radix-ui/react-progress"

import { cn } from "@/lib/utils"

interface ProgressProps extends React.ComponentPropsWithoutRef<typeof ProgressPrimitive.Root> {
  indicatorClassName?: string
  indicatorStyle?: React.CSSProperties
}

const Progress = React.forwardRef<
  React.ElementRef<typeof ProgressPrimitive.Root>,
  ProgressProps
>(({ className, value, max = 100, indicatorClassName, indicatorStyle, ...props }, ref) => {
  const safeMax = max > 0 ? max : 100
  const clamped = Math.min(safeMax, Math.max(0, value ?? 0))
  const fillPercentage = (clamped / safeMax) * 100

  return (
    <ProgressPrimitive.Root
      ref={ref}
      value={clamped}
      max={safeMax}
      className={cn(
        "relative h-4 w-full overflow-hidden rounded-full bg-secondary",
        className
      )}
      {...props}
    >
      <ProgressPrimitive.Indicator
        className={cn("h-full w-full flex-1 bg-primary transition-all", indicatorClassName)}
        style={{ ...indicatorStyle, transform: `translateX(-${100 - fillPercentage}%)` }}
      />
    </ProgressPrimitive.Root>
  )
})
Progress.displayName = ProgressPrimitive.Root.displayName

export { Progress }
