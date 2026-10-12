import type { AlertColor } from '@mui/material/Alert';
import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useNotify } from '@/app/providers/notificationContext';
import type { JobSubmission } from '@/models/admin';
import { shortId } from './labels';
import { adminPaths } from './paths';

/** Feedback for queued market-data work, with a "View job" action that opens the job. */
export function useJobNotifier() {
  const notify = useNotify();
  const navigate = useNavigate();
  return useCallback((message: string, jobId?: string | null, severity: AlertColor = 'success') => notify({
    message,
    severity,
    action: jobId ? { label: 'View job', onClick: () => navigate(adminPaths.job(jobId)) } : undefined,
  }), [notify, navigate]);
}

/** "Job #0199d1c4" */
export const jobReference = (jobId: string) => `Job #${shortId(jobId)}`;

/** Explains a submission that matched an already queued or running job instead of creating a new one. */
export function duplicateJobMessage(description: string, submission: JobSubmission): string {
  const state = submission.job.status === 'RUNNING' ? 'already running' : 'already queued';
  return `${description} is ${state}. ${jobReference(submission.job.id)}`;
}
