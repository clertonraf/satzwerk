import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import type { CreateExerciseRequest } from '@/services/exerciseService'

const schema = z.object({
  name: z.string().trim().min(1, 'Name is required'),
  muscleGroup: z.string().trim().min(1, 'Muscle group is required'),
  description: z.string().optional(),
  videoUrl: z.string().url('Must be a valid URL').optional().or(z.literal('')),
  equipment: z.string().optional(),
})

type ExerciseFormValues = z.infer<typeof schema>

interface ExerciseFormProps {
  onSubmit: (data: CreateExerciseRequest) => void | Promise<void>
  defaultValues?: Partial<CreateExerciseRequest>
  isLoading?: boolean
}

function normalizeExerciseValues(values: ExerciseFormValues): CreateExerciseRequest {
  const description = values.description?.trim()
  const videoUrl = values.videoUrl?.trim()
  const equipment = values.equipment?.trim()

  return {
    name: values.name.trim(),
    muscleGroup: values.muscleGroup.trim(),
    ...(description ? { description } : {}),
    ...(videoUrl ? { videoUrl } : {}),
    ...(equipment ? { equipment } : {}),
  }
}

export default function ExerciseForm({ onSubmit, defaultValues, isLoading = false }: ExerciseFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ExerciseFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: defaultValues?.name ?? '',
      muscleGroup: defaultValues?.muscleGroup ?? '',
      description: defaultValues?.description ?? '',
      videoUrl: defaultValues?.videoUrl ?? '',
      equipment: defaultValues?.equipment ?? '',
    },
  })

  return (
    <Card className="border-border bg-card/90 shadow-sm">
      <CardHeader>
        <CardTitle>Exercise details</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="space-y-4"
          noValidate
          onSubmit={handleSubmit(async (values) => {
            await onSubmit(normalizeExerciseValues(values))
          })}
        >
          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor="name">
              Name
            </label>
            <Input id="name" aria-invalid={Boolean(errors.name)} disabled={isLoading} {...register('name')} />
            {errors.name ? <p className="text-sm text-destructive">{errors.name.message}</p> : null}
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor="muscleGroup">
              Muscle group
            </label>
            <Input
              id="muscleGroup"
              aria-invalid={Boolean(errors.muscleGroup)}
              disabled={isLoading}
              {...register('muscleGroup')}
            />
            {errors.muscleGroup ? <p className="text-sm text-destructive">{errors.muscleGroup.message}</p> : null}
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor="description">
              Description
            </label>
            <Textarea id="description" disabled={isLoading} {...register('description')} />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor="videoUrl">
              Video URL
            </label>
            <Input
              id="videoUrl"
              type="url"
              aria-invalid={Boolean(errors.videoUrl)}
              disabled={isLoading}
              {...register('videoUrl')}
            />
            {errors.videoUrl ? <p className="text-sm text-destructive">{errors.videoUrl.message}</p> : null}
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor="equipment">
              Equipment
            </label>
            <Input id="equipment" disabled={isLoading} {...register('equipment')} />
          </div>

          <CardFooter className="px-0 pb-0">
            <Button type="submit" disabled={isLoading}>
              Save
            </Button>
          </CardFooter>
        </form>
      </CardContent>
    </Card>
  )
}
