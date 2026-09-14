import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import type { ReactNode } from 'react';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  destructive?: boolean;
  pending?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmDialog(props: ConfirmDialogProps) {
  const { open, title, description, confirmLabel, destructive, pending, onConfirm, onClose } = props;
  return (
    <Dialog open={open} onClose={pending ? undefined : onClose} aria-labelledby="confirm-dialog-title" maxWidth="xs" fullWidth>
      <DialogTitle id="confirm-dialog-title">{title}</DialogTitle>
      <DialogContent>
        <DialogContentText component="div">{description}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose} disabled={pending}>Cancel</Button>
        <Button variant="contained" color={destructive ? 'error' : 'primary'} onClick={onConfirm} loading={pending}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
