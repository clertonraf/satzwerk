import { useNavigate, useMatch } from 'react-router-dom'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import ExercisesPage from './ExercisesPage'
import PlansPage from './PlansPage'

export default function WorkoutsPage() {
  const navigate = useNavigate()
  const isExercises = useMatch('/workouts/exercises')
  const activeTab = isExercises ? 'exercises' : 'plans'

  function handleTabChange(value: string) {
    if (value === 'exercises') {
      navigate('/workouts/exercises')
    } else {
      navigate('/workouts')
    }
  }

  return (
    <Tabs value={activeTab} onValueChange={handleTabChange}>
      <TabsList>
        <TabsTrigger value="plans">Plans</TabsTrigger>
        <TabsTrigger value="exercises">Exercises</TabsTrigger>
      </TabsList>
      <TabsContent value="plans" className="mt-4">
        <PlansPage />
      </TabsContent>
      <TabsContent value="exercises" className="mt-4">
        <ExercisesPage />
      </TabsContent>
    </Tabs>
  )
}
