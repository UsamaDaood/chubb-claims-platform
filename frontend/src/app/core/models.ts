// Mirrors the backend contract (Dtos.java / OpenAPI at /v3/api-docs).
// Next step: generate this file from the OpenAPI spec so the two cannot drift.

export type Role = 'CLAIMANT' | 'OFFICER' | 'MANAGER';
export type Market = 'SG' | 'HK' | 'MY' | 'TH' | 'ID' | 'VN';
export type ClaimType = 'MOTOR' | 'PROPERTY';
export type ClaimStatus = 'SUBMITTED' | 'IN_REVIEW' | 'INFO_REQUESTED' | 'APPROVED' | 'REJECTED' | 'SETTLED';
export type Action = 'ASSIGN' | 'ASSESS' | 'REQUEST_INFO' | 'APPROVE' | 'REJECT' | 'SETTLE' | 'PROVIDE_INFO';

export interface UserView { id: string; name: string; role: Role; market: Market | null; }
export interface LoginResponse { token: string; user: UserView; }

export interface ClaimSummary {
  id: number;
  claimNumber: string;
  market: Market;
  type: ClaimType;
  status: ClaimStatus;
  currency: string;
  estimatedAmount: number;
  reserveAmount: number | null;
  approvedAmount: number | null;
  claimantId: string;
  claimantName: string;
  assignedOfficerId: string | null;
  assignedOfficerName: string | null;
  submittedAt: string;
  updatedAt: string;
  ageDays: number;
  slaDueAt: string;
  slaBreached: boolean;
}

export interface ActivityView { type: string; actorName: string; message: string; at: string; internal: boolean; }
export interface InfoRequestView { id: number; question: string; requestedAt: string; answer: string | null; answeredAt: string | null; }

export interface ClaimDetail {
  claim: ClaimSummary;
  description: string;
  incidentDate: string;
  incidentLocation: string | null;
  decisionReason: string | null;
  paymentReference: string | null;
  activity: ActivityView[];
  infoRequests: InfoRequestView[];
  allowedActions: Action[];
}

export interface NotificationView { id: number; claimId: number; message: string; createdAt: string; read: boolean; }

export interface SubmitClaimRequest {
  type: ClaimType;
  description: string;
  incidentDate: string;
  incidentLocation: string;
  estimatedAmount: number;
}

export interface OfficerSummary {
  inReview: number; awaitingClaimant: number; approvedAwaitingSettlement: number;
  slaBreached: number; dueWithin24h: number; unassignedInMarket: number; decidedLast30Days: number;
}
export interface ExposureRow { market: Market; type: ClaimType; status: ClaimStatus; currency: string; count: number; amount: number; }
export interface CurrencyTotal { currency: string; openCount: number; amount: number; }
export interface ExposureReport {
  rows: ExposureRow[]; totals: CurrencyTotal[];
  unassignedCount: number; slaBreachedCount: number; unreservedCount: number;
}
export interface WorkloadRow {
  officerId: string; officerName: string; market: Market;
  inReview: number; awaitingClaimant: number; approvedAwaitingSettlement: number;
  slaBreached: number; oldestAgeDays: number; decidedLast30Days: number; avgDecisionDays: number;
}
