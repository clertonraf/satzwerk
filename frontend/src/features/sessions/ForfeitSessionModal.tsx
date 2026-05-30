import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

interface ForfeitSessionModalProps {
  onConfirm: () => void
  onCancel: () => void
}

export default function ForfeitSessionModal({ onConfirm, onCancel }: ForfeitSessionModalProps) {
  return (
    <AlertDialog open onOpenChange={() => undefined}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Forfeit workout session?</AlertDialogTitle>
          <AlertDialogDescription>
            This will delete the current session and all logged sets. This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={onCancel}>Keep training</AlertDialogCancel>
          <AlertDialogAction
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            onClick={onConfirm}
          >
            Forfeit session
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
