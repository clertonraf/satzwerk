import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

export default function ProfilePage() {
  return (
    <Card className="border-border bg-card/90 shadow-sm">
      <CardHeader>
        <CardTitle>Profile</CardTitle>
        <CardDescription>Settings and account scaffolding placeholder.</CardDescription>
      </CardHeader>
      <CardContent>User-facing profile screens can grow from this route.</CardContent>
    </Card>
  )
}
