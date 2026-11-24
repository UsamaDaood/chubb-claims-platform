import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ClaimDetail, ClaimSummary, ClaimType, ExposureReport, LoginResponse, NotificationView,
  OfficerSummary, SubmitClaimRequest, UserView, WorkloadRow,
} from './models';

/** Thin typed wrapper over the REST contract. Components await these directly and keep state in signals. */
@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);

  users = () => firstValueFrom(this.http.get<UserView[]>('/api/auth/users'));
  login = (userId: string) => firstValueFrom(this.http.post<LoginResponse>('/api/auth/login', { userId }));

  // claimant
  mine = () => firstValueFrom(this.http.get<ClaimSummary[]>('/api/claims/mine'));
  submit = (body: SubmitClaimRequest, idempotencyKey: string) =>
    firstValueFrom(this.http.post<ClaimDetail>('/api/claims', body, { headers: { 'Idempotency-Key': idempotencyKey } }));
  respond = (claimId: number, requestId: number, answer: string) =>
    firstValueFrom(this.http.post<ClaimDetail>(`/api/claims/${claimId}/info-requests/${requestId}/response`, { answer }));

  // shared
  claim = (id: number) => firstValueFrom(this.http.get<ClaimDetail>(`/api/claims/${id}`));

  // staff
  queue = (type?: ClaimType) =>
    firstValueFrom(this.http.get<ClaimSummary[]>('/api/claims/queue', { params: type ? { type } : {} }));
  assigned = () => firstValueFrom(this.http.get<ClaimSummary[]>('/api/claims/assigned'));
  assign = (id: number) => firstValueFrom(this.http.post<ClaimDetail>(`/api/claims/${id}/assign`, {}));
  assess = (id: number, reserveAmount: number, notes: string) =>
    firstValueFrom(this.http.post<ClaimDetail>(`/api/claims/${id}/assessment`, { reserveAmount, notes }));
  requestInfo = (id: number, question: string) =>
    firstValueFrom(this.http.post<ClaimDetail>(`/api/claims/${id}/info-requests`, { question }));
  approve = (id: number, approvedAmount: number, reason: string) =>
    firstValueFrom(this.http.post<ClaimDetail>(`/api/claims/${id}/approve`, { approvedAmount, reason }));
  reject = (id: number, reason: string) =>
    firstValueFrom(this.http.post<ClaimDetail>(`/api/claims/${id}/reject`, { reason }));
  settle = (id: number, paymentReference: string) =>
    firstValueFrom(this.http.post<ClaimDetail>(`/api/claims/${id}/settle`, { paymentReference }));

  // dashboards
  officerSummary = () => firstValueFrom(this.http.get<OfficerSummary>('/api/dashboard/officer'));
  exposure = () => firstValueFrom(this.http.get<ExposureReport>('/api/dashboard/exposure'));
  workload = () => firstValueFrom(this.http.get<WorkloadRow[]>('/api/dashboard/workload'));

  // notifications
  notifications = () => firstValueFrom(this.http.get<NotificationView[]>('/api/notifications'));
  markRead = (id: number) => firstValueFrom(this.http.post<void>(`/api/notifications/${id}/read`, {}));
}
