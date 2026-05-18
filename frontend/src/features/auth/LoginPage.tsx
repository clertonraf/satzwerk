import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { tokenService } from '@/services/tokenService'
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

const loginSchema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Required'),
})

type LoginFormValues = z.infer<typeof loginSchema>

export default function LoginPage() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const setAccessToken = useAuthStore((state) => state.setAccessToken)
  const navigate = useNavigate()
  const location = useLocation()
  const redirectTo = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/'
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  })

  if (accessToken) {
    return <Navigate to="/" replace />
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      const response = await authService.login(values)
      setAccessToken(response.accessToken)
      tokenService.saveRefreshToken(response.refreshToken)
      navigate(redirectTo, { replace: true })
    } catch (error) {
      setError('root', {
        message:
          isAxiosError(error) && error.response?.status === 401
            ? 'Incorrect email or password'
            : 'Unable to log in right now',
      })
    }
  })

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 py-10 text-foreground dark">
      <Card className="w-full max-w-md border-border bg-card/90 shadow-xl shadow-black/20">
        <CardHeader>
          <CardTitle>Log in</CardTitle>
          <CardDescription>Access your Satzwerk training dashboard.</CardDescription>
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
              <label className="text-sm font-medium" htmlFor="password">
                Password
              </label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                aria-invalid={Boolean(errors.password)}
                {...register('password')}
              />
              {errors.password ? <p className="text-sm text-destructive">{errors.password.message}</p> : null}
            </div>

            {errors.root ? <p className="text-sm text-destructive">{errors.root.message}</p> : null}

            <Button className="w-full" type="submit" disabled={isSubmitting}>
              Log in
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-muted-foreground">
            Need an account?{' '}
            <Link className="font-medium text-foreground underline underline-offset-4" to="/register">
              Register
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
