import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useSessionStore } from '@/store/session'

const schema = z.object({
  weight: z
    .string()
    .trim()
    .min(1, 'Weight is required')
    .transform((value) => Number(value))
    .refine((value) => value > 0, 'Must be positive'),
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
}

export default function SetInput({ onLog, setNumber, isLoading = false }: SetInputProps) {
  const weightUnit = useSessionStore((state) => state.weightUnit)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<SetInputFormValues, undefined, SetInputValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      weight: '',
      reps: '',
    },
  })

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
            Weight ({weightUnit})
          </label>
          <Input id={`weight-${setNumber}`} type="number" inputMode="decimal" step="any" {...register('weight')} />
          {errors.weight ? <p className="text-sm text-destructive">{errors.weight.message}</p> : null}
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
