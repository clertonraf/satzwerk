import { Settings } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { ALL_WIDGET_IDS, WIDGET_LABELS, type DashboardWidgetId } from '@/store/dashboardPreferences'

interface DashboardSettingsButtonProps {
  visibleWidgets: DashboardWidgetId[]
  onToggle: (widgetId: DashboardWidgetId, visible: boolean) => void
}

export default function DashboardSettingsButton({ visibleWidgets, onToggle }: DashboardSettingsButtonProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" aria-label="Dashboard settings">
          <Settings className="size-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-48">
        <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-muted-foreground">Widgets</p>
        <ul className="space-y-2">
          {ALL_WIDGET_IDS.map((widgetId) => {
            const checked = visibleWidgets.includes(widgetId)
            return (
              <li key={widgetId} className="flex items-center gap-2">
                <input
                  id={`widget-${widgetId}`}
                  type="checkbox"
                  className="size-4 cursor-pointer"
                  checked={checked}
                  onChange={(e) => onToggle(widgetId, e.target.checked)}
                />
                <label htmlFor={`widget-${widgetId}`} className="cursor-pointer text-sm">
                  {WIDGET_LABELS[widgetId]}
                </label>
              </li>
            )
          })}
        </ul>
      </PopoverContent>
    </Popover>
  )
}
