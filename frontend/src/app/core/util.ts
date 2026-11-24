import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, inject } from '@angular/core';
import { ClaimStatus, Market } from './models';

export const CURRENCY: Record<Market, string> = {
  SG: 'SGD', HK: 'HKD', MY: 'MYR', TH: 'THB', ID: 'IDR', VN: 'VND',
};

export const STATUS_LABEL: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  IN_REVIEW: 'In review',
  INFO_REQUESTED: 'Info requested',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SETTLED: 'Settled',
};

/** Surfaces the backend's RFC 7807 `detail` where present. */
export function errorMessage(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    const detail = (e.error as { detail?: string } | null)?.detail;
    if (detail) return detail;
    if (e.status === 0) return 'Cannot reach the server. Is the backend running?';
    return `Request failed (${e.status})`;
  }
  return e instanceof Error ? e.message : 'Something went wrong';
}

/** Runs `fn` now and then every `ms` until the calling component is destroyed. Call from a constructor/field initialiser. */
export function poll(fn: () => void | Promise<void>, ms: number): void {
  const destroyRef = inject(DestroyRef);
  void fn();
  const id = setInterval(() => void fn(), ms);
  destroyRef.onDestroy(() => clearInterval(id));
}
