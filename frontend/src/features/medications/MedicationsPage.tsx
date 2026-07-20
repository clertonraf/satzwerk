import { useState } from 'react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import ChartsTab from './ChartsTab'
import LogTab from './LogTab'
import MedicationsTab from './MedicationsTab'

export default function MedicationsPage() {
  const [activeTab, setActiveTab] = useState('medications')

  return (
    <div className="space-y-4">
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
