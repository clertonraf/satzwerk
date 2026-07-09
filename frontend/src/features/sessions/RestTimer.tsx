import { useEffect, useMemo, useReducer } from 'react'
import { Button } from '@/components/ui/button'

interface RestTimerProps {
  defaultSeconds?: number
}

interface RestTimerState {
  isRunning: boolean
  secondsLeft: number
}

type RestTimerAction =
  | { type: 'start'; defaultSeconds: number }
  | { type: 'stop'; defaultSeconds: number }
  | { type: 'tick' }
  | { type: 'reset-to-zero' }

function restTimerReducer(state: RestTimerState, action: RestTimerAction): RestTimerState {
  switch (action.type) {
    case 'start':
      return { isRunning: true, secondsLeft: action.defaultSeconds }
    case 'stop':
      return { isRunning: false, secondsLeft: action.defaultSeconds }
    case 'tick':
      if (state.secondsLeft <= 1) {
        return { isRunning: false, secondsLeft: 0 }
      }
      return { ...state, secondsLeft: state.secondsLeft - 1 }
    case 'reset-to-zero':
      return { isRunning: false, secondsLeft: 0 }
  }
}

function formatSeconds(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60

  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

export default function RestTimer({ defaultSeconds = 90 }: RestTimerProps) {
  const [{ isRunning, secondsLeft }, dispatch] = useReducer(restTimerReducer, {
    isRunning: false,
    secondsLeft: defaultSeconds,
  })

  useEffect(() => {
    if (defaultSeconds <= 0) {
      dispatch({ type: 'reset-to-zero' })
    }
  }, [defaultSeconds])

  useEffect(() => {
    if (!isRunning || defaultSeconds <= 0) {
      return
    }

    const interval = window.setInterval(() => {
      dispatch({ type: 'tick' })
    }, 1000)

    return () => window.clearInterval(interval)
  }, [isRunning, defaultSeconds])

  const label = useMemo(() => formatSeconds(secondsLeft), [secondsLeft])

  // Zero or negative rest (e.g. SST technique) means no rest is needed; render nothing.
  if (defaultSeconds <= 0) {
    return null
  }

  if (!isRunning) {
    return (
      <Button
        type="button"
        variant="outline"
        onClick={() => {
          dispatch({ type: 'start', defaultSeconds })
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
          dispatch({ type: 'stop', defaultSeconds })
        }}
      >
        Stop
      </Button>
    </div>
  )
}
