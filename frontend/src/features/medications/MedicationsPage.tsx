import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import ChartsTab from './ChartsTab'
import LogTab from './LogTab'
import MedicationsTab from './MedicationsTab'

export default function MedicationsPage() {
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('medications')

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => navigate('/profile')} aria-label="Back to profile">
          ← Back
        </Button>
        <h1 className="text-xl font-semibold">Medications</h1>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="medications">Medications</TabsTrigger>
          <TabsTrigger value="log">Log</TabsTrigger>
          <TabsTrigger value="charts">History & Charts</TabsTrigger>
        </TabsList>
        <TabsContent value="medications" className="mt-4">
          <MedicationsTab />
        </TabsContent>
        <TabsContent value="log" className="mt-4">
          <LogTab />
        </TabsContent>
        <TabsContent value="charts" className="mt-4">
          <ChartsTab />
        </TabsContent>
      </Tabs>
    </div>
  )
}
