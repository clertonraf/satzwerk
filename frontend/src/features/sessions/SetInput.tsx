import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { convertWeightHint } from '@/features/sessions/sessionHelpers'

const schema = z.object({
  weight: z
    .string()
    .trim()
    .min(1, 'Weight is required')
    .refine((value) => {
      const n = Number(value)
      return !isNaN(n) && n > 0
    }, 'Must be a positive number')
    .transform((value) => Number(value)),
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
  setNumber: number
  isLoading?: boolean
  unit: 'kg' | 'lb'
}

export default function SetInput({ onLog, setNumber, isLoading = false, unit }: SetInputProps) {
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<SetInputFormValues, undefined, SetInputValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      weight: '',
      reps: '',
    },
  })

  const weightValue = useWatch({ control, name: 'weight' })
  const hint = convertWeightHint(weightValue, unit)

  return (
    <form
      className="grid gap-3 rounded-lg border border-border p-4"
      onSubmit={handleSubmit((values) => {
        onLog({ ...values, setNumber })
        reset()
      })}
    >
      <p className="text-sm font-medium">Set {setNumber}</p>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-2">
          <label className="text-sm font-medium" htmlFor={`weight-${setNumber}`}>
            Weight ({unit})
          </label>
          <Input id={`weight-${setNumber}`} type="number" inputMode="decimal" step="0.001" {...register('weight')} />
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
          <Input id={`reps-${setNumber}`} type="number" inputMode="numeric" min={1} {...register('reps')} />
          {errors.reps ? <p className="text-sm text-destructive">{errors.reps.message}</p> : null}
        </div>
      </div>

      <Button className="justify-self-start" type="submit" disabled={isLoading}>
        Log Set
      </Button>
    </form>
  )
}
