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
    // Also stop when defaultSeconds changes to <= 0 at runtime; adding it to deps
    // triggers cleanup of the previous interval before starting a new one.
    if (!isRunning || defaultSeconds <= 0) {
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
  }, [isRunning, defaultSeconds])

  const label = useMemo(() => formatSeconds(secondsLeft), [secondsLeft])

  // Zero or negative rest (e.g. SST technique) means no rest is needed; render nothing.
  // The interval is already stopped above because defaultSeconds <= 0 guards the effect.
  if (defaultSeconds <= 0) {
    return null
  }

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
