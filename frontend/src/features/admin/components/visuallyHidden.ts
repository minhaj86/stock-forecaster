/** Hides content visually while keeping it available to screen readers (e.g. the "Actions" column header). */
export const visuallyHidden = {
  border: 0, clip: 'rect(0 0 0 0)', height: '1px', margin: -1, overflow: 'hidden', padding: 0,
  position: 'absolute', whiteSpace: 'nowrap', width: '1px',
} as const;
