import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { authService } from '@/services/authService'
import { useAuthStore } from '@/store/auth'

const registerSchema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(8, 'At least 8 characters'),
  displayName: z.string().min(1, 'Display name is required'),
})

type RegisterFormValues = z.infer<typeof registerSchema>

export default function RegisterPage() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const setAccessToken = useAuthStore((state) => state.setAccessToken)
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: '',
      password: '',
      displayName: '',
    },
  })

  if (accessToken) {
    return <Navigate to="/" replace />
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      const response = await authService.register(values)
      setAccessToken(response.accessToken)
      localStorage.setItem('refreshToken', response.refreshToken)
      navigate('/', { replace: true })
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setError('email', { message: 'This email is already in use' })
        return
      }

      setError('root', { message: 'Unable to register right now' })
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground dark">
      <Card className="w-full max-w-md border-border bg-card/90 shadow-xl shadow-black/20">
        <CardHeader>
          <CardTitle>Register</CardTitle>
          <CardDescription>Create your Satzwerk account.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" noValidate onSubmit={onSubmit}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="email">
                Email
              </label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                aria-invalid={Boolean(errors.email)}
                {...register('email')}
              />
              {errors.email ? <p className="text-sm text-destructive">{errors.email.message}</p> : null}
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="displayName">
                Display name
              </label>
              <Input
                id="displayName"
                autoComplete="nickname"
                aria-invalid={Boolean(errors.displayName)}
                {...register('displayName')}
              />
              {errors.displayName ? (
                <p className="text-sm text-destructive">{errors.displayName.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="password">
                Password
              </label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                aria-invalid={Boolean(errors.password)}
                {...register('password')}
              />
              {errors.password ? <p className="text-sm text-destructive">{errors.password.message}</p> : null}
            </div>

            {errors.root ? <p className="text-sm text-destructive">{errors.root.message}</p> : null}

            <Button className="w-full" type="submit" disabled={isSubmitting}>
              Register
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link className="font-medium text-foreground underline underline-offset-4" to="/login">
              Log in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
