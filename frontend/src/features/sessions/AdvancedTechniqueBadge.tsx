import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { formatAdvancedTechnique, getAdvancedTechniqueDescription } from '@/features/workouts/advancedTechnique'

interface AdvancedTechniqueBadgeProps {
  technique: string | null | undefined
}

export default function AdvancedTechniqueBadge({ technique }: AdvancedTechniqueBadgeProps) {
  const label = formatAdvancedTechnique(technique)
  const description = getAdvancedTechniqueDescription(technique)

  if (!label) {
    return null
  }

  if (!description) {
    return (
      <span className="inline-flex w-fit rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground">
        {label}
      </span>
    )
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="inline-flex w-fit cursor-pointer rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground hover:bg-secondary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {label}
        </button>
      </PopoverTrigger>
      <PopoverContent className="max-w-xs text-sm" side="top">
        <p>{description}</p>
      </PopoverContent>
    </Popover>
  )
}
