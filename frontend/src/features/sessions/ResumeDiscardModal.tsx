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

interface ResumeDiscardModalProps {
  onResume: () => void
  onDiscard: () => void
}

export default function ResumeDiscardModal({ onResume, onDiscard }: ResumeDiscardModalProps) {
  return (
    <AlertDialog open onOpenChange={() => undefined}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Open workout session found</AlertDialogTitle>
          <AlertDialogDescription>
            You have an open workout session. Resume it or discard it to start a new one.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogAction onClick={onResume}>Resume</AlertDialogAction>
          <AlertDialogCancel
            className="border-destructive text-destructive hover:bg-destructive hover:text-destructive-foreground"
            onClick={onDiscard}
          >
            Discard
          </AlertDialogCancel>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
