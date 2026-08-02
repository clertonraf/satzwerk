import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { convertWeightHint } from '@/lib/unitFormatters'

const schema = z.object({
  weight: z
    .string()
    .trim()
    .min(1, 'Weight is required')
    .refine((value) => {
      const n = Number(value.replace(',', '.'))
      return Number.isFinite(n) && n >= 0
    }, 'Must be a non-negative number')
    .transform((value) => Number(value.replace(',', '.'))),
  reps: z
    .string()
    .trim()
    .min(1, 'Reps is required')
    .transform((value) => Number(value))
    .refine((value) => value >= 1, 'Reps must be at least 1'),
})

type SetInputFormValues = z.input<typeof schema>
type SetInputValues = z.output<typeof schema>

interface SetInputProps {
  onLog: (data: { weight: number; reps: number; setNumber: number }) => void
  onCancel?: () => void
  setNumber: number
  isLoading?: boolean
  unit: 'kg' | 'lb'
  defaultWeight?: number
  defaultReps?: number
  submitLabel?: string
  resetOnSubmit?: boolean
  variant?: 'card' | 'inline'
}

export default function SetInput({
  onLog,
  onCancel,
  setNumber,
  isLoading = false,
  unit,
  defaultWeight,
  defaultReps,
  submitLabel = 'Log Set',
  resetOnSubmit = true,
  variant = 'card',
}: SetInputProps) {
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<SetInputFormValues, undefined, SetInputValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      weight: defaultWeight != null ? String(Number(defaultWeight.toFixed(3))) : '',
      reps: defaultReps != null ? String(defaultReps) : '',
    },
  })

  const weightValue = useWatch({ control, name: 'weight' })
  const hint = convertWeightHint(weightValue, unit)

  return (
    <form
      className={variant === 'card' ? 'grid gap-3 rounded-lg border border-border p-4' : 'grid gap-3'}
      onSubmit={handleSubmit((values) => {
        onLog({ ...values, setNumber })
        if (resetOnSubmit) reset()
      })}
    >
      <p className="text-sm font-medium">Set {setNumber}</p>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-2">
          <label className="text-sm font-medium" htmlFor={`weight-${setNumber}`}>
            Weight ({unit})
          </label>
          <Input id={`weight-${setNumber}`} type="text" inputMode="decimal" className="text-base" disabled={isLoading} {...register('weight')} />
          {errors.weight ? (
            <p className="text-sm text-destructive">{errors.weight.message}</p>
          ) : hint ? (
            <p className="text-sm text-muted-foreground">{hint}</p>
          ) : null}
        </div>

        <div className="space-y-2">
          <label className="text-sm font-medium" htmlFor={`reps-${setNumber}`}>
            Reps
          </label>
          <Input id={`reps-${setNumber}`} type="number" inputMode="numeric" min={1} className="text-base" disabled={isLoading} {...register('reps')} />
          {errors.reps ? <p className="text-sm text-destructive">{errors.reps.message}</p> : null}
        </div>
      </div>

      <Button className="justify-self-start" type="submit" disabled={isLoading}>
        {submitLabel}
      </Button>
      {onCancel ? (
        <Button className="justify-self-start" type="button" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      ) : null}
    </form>
  )
}
