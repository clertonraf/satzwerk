import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'

interface RestTimerProps {
  defaultSeconds?: number
}

function formatSeconds(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60

  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

export default function RestTimer({ defaultSeconds = 90 }: RestTimerProps) {
  const [isRunning, setIsRunning] = useState(false)
  const [secondsLeft, setSecondsLeft] = useState(defaultSeconds)

  useEffect(() => {
    if (!isRunning) {
      return
    }

    const interval = window.setInterval(() => {
      setSecondsLeft((current) => {
        if (current <= 1) {
          window.clearInterval(interval)
          setIsRunning(false)
          return 0
        }

        return current - 1
      })
    }, 1000)

    return () => window.clearInterval(interval)
  }, [isRunning])

  const label = useMemo(() => formatSeconds(secondsLeft), [secondsLeft])

  if (!isRunning) {
    return (
      <Button
        type="button"
        variant="outline"
        onClick={() => {
          setSecondsLeft(defaultSeconds)
          setIsRunning(true)
        }}
      >
        Start Rest
      </Button>
    )
  }

  return (
    <div className="flex items-center gap-3 rounded-lg border border-border px-4 py-3">
      <p aria-live="polite" className="font-mono text-sm font-semibold">
        {label}
      </p>
      <Button
        type="button"
        variant="ghost"
        onClick={() => {
          setIsRunning(false)
          setSecondsLeft(defaultSeconds)
        }}
      >
        Stop
      </Button>
    </div>
  )
}
