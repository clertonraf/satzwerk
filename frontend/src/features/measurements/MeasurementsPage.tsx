import { useQuery } from '@tanstack/react-query'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { measurementsApi } from '@/services/measurementsApi'
import { queryKeys } from '@/services/queryKeys'
import ChartsTab from './ChartsTab'
import HistoryTab from './HistoryTab'
import LogTab from './LogTab'

export default function MeasurementsPage() {
  const { data: measurements = [], isLoading } = useQuery({
    queryKey: queryKeys.measurements.all(),
    queryFn: () => measurementsApi.getAll(),
  })

  return (
    <div className="space-y-4">
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
