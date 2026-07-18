import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { measurementsApi } from '@/services/measurementsApi'
import { queryKeys } from '@/services/queryKeys'
import ChartsTab from './ChartsTab'
import HistoryTab from './HistoryTab'
import LogTab from './LogTab'

export default function MeasurementsPage() {
  const navigate = useNavigate()

  const { data: measurements = [], isLoading } = useQuery({
    queryKey: queryKeys.measurements.all(),
    queryFn: () => measurementsApi.getAll(),
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => navigate('/profile')} aria-label="Back to profile">
          ← Back
        </Button>
        <h1 className="text-xl font-semibold">Body Measurements</h1>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <Tabs defaultValue="log">
          <TabsList>
            <TabsTrigger value="log">Log</TabsTrigger>
            <TabsTrigger value="history">History</TabsTrigger>
            <TabsTrigger value="charts">Charts</TabsTrigger>
          </TabsList>
          <TabsContent value="log" className="mt-4">
            <LogTab measurements={measurements} />
          </TabsContent>
          <TabsContent value="history" className="mt-4">
            <HistoryTab measurements={measurements} />
          </TabsContent>
          <TabsContent value="charts" className="mt-4">
            <ChartsTab measurements={measurements} />
          </TabsContent>
        </Tabs>
      )}
    </div>
  )
}
