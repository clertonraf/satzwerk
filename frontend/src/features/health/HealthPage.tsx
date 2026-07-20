import { useNavigate, useMatch } from 'react-router-dom'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import MeasurementsPage from '@/features/measurements/MeasurementsPage'
import MedicationsPage from '@/features/medications/MedicationsPage'

export default function HealthPage() {
  const navigate = useNavigate()
  const isMeasurements = useMatch('/health/measurements')
  const activeTab = isMeasurements ? 'measurements' : 'medications'

  function handleTabChange(value: string) {
    if (value === 'measurements') {
      navigate('/health/measurements')
    } else {
      navigate('/health')
    }
  }

  return (
    <Tabs value={activeTab} onValueChange={handleTabChange}>
      <TabsList>
        <TabsTrigger value="medications">Medications</TabsTrigger>
        <TabsTrigger value="measurements">Measurements</TabsTrigger>
      </TabsList>
      <TabsContent value="medications" className="mt-4">
        <MedicationsPage />
      </TabsContent>
      <TabsContent value="measurements" className="mt-4">
        <MeasurementsPage />
      </TabsContent>
    </Tabs>
  )
}
