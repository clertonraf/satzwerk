import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import type { WorkoutPlan } from '@/services/planService'

interface PlanCardProps {
  plan: WorkoutPlan
  onActivate: (id: string) => void | Promise<unknown>
  onDelete: (id: string) => void | Promise<unknown>
}

export default function PlanCard({ plan, onActivate, onDelete }: PlanCardProps) {
  return (
    <Card className="border-border bg-card/90 shadow-sm">
      <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <CardTitle className="text-xl">{plan.name}</CardTitle>
            {plan.isActive ? (
              <span className="inline-flex rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-medium text-emerald-700">
                Active
              </span>
            ) : null}
            {plan.source === 'IMPORTED' ? (
              <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                Imported
              </span>
            ) : null}
          </div>
          <CardDescription>{plan.source}</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" onClick={() => onActivate(plan.id)} disabled={plan.isActive}>
          Activate
        </Button>
        <Button type="button" variant="destructive" onClick={() => onDelete(plan.id)}>
          Delete
        </Button>
        <Button asChild>
          <Link to={`/plans/${plan.id}`}>Open</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
