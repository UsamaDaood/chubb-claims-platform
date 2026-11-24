import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { Action, ClaimDetail } from '../../core/models';
import { errorMessage, poll } from '../../core/util';
import { MoneyPipe, StatusBadge } from '../../shared/shared';

type Panel = 'assess' | 'info' | 'approve' | 'reject' | 'settle';

/**
 * One detail page for both audiences. What the user can DO is driven entirely by `allowedActions`
 * computed on the server, so the UI never duplicates the state machine or authorisation rules.
 */
@Component({
  selector: 'app-claim-detail',
  imports: [FormsModule, DatePipe, RouterLink, StatusBadge, MoneyPipe],
  template: `
    @if (detail(); as d) {
      <a class="back" [routerLink]="backLink()">← Back</a>
      <div class="page-head">
        <div>
          <h1>{{ d.claim.claimNumber }} <app-status-badge [status]="d.claim.status" /></h1>
          <p class="muted">
            {{ d.claim.type === 'MOTOR' ? 'Motor' : 'Property' }} · {{ d.claim.market }} · reported by {{ d.claim.claimantName }}
            @if (d.claim.assignedOfficerName) { · handled by {{ d.claim.assignedOfficerName }} }
          </p>
        </div>
        @if (d.claim.slaBreached) { <span class="pill red">SLA breached ({{ d.claim.ageDays }}d)</span> }
      </div>

      @if (error()) { <p class="error">{{ error() }}</p> }

      <div class="grid2">
        <div class="card">
          <h3>Incident</h3>
          <dl>
            <dt>Date</dt><dd>{{ d.incidentDate | date: 'mediumDate' }}</dd>
            <dt>Location</dt><dd>{{ d.incidentLocation || '—' }}</dd>
            <dt>Description</dt><dd class="wrap">{{ d.description }}</dd>
          </dl>
        </div>
        <div class="card">
          <h3>Amounts</h3>
          <dl>
            <dt>Claimed</dt><dd>{{ d.claim.estimatedAmount | money: d.claim.currency }}</dd>
            @if (d.claim.reserveAmount !== null) { <dt>Reserve</dt><dd>{{ d.claim.reserveAmount | money: d.claim.currency }}</dd> }
            @if (d.claim.approvedAmount !== null) { <dt>Approved</dt><dd>{{ d.claim.approvedAmount | money: d.claim.currency }}</dd> }
            @if (d.decisionReason) { <dt>Decision</dt><dd class="wrap">{{ d.decisionReason }}</dd> }
            @if (d.paymentReference) { <dt>Payment ref</dt><dd>{{ d.paymentReference }}</dd> }
          </dl>
        </div>
      </div>

      @if (d.infoRequests.length) {
        <div class="card">
          <h3>Information requests</h3>
          @for (r of d.infoRequests; track r.id) {
            <div class="qa">
              <p><strong>{{ r.question }}</strong> <small class="muted">{{ r.requestedAt | date: 'd MMM, HH:mm' }}</small></p>
              @if (r.answer) {
                <p class="answer">{{ r.answer }}</p>
              } @else if (can('PROVIDE_INFO')) {
                <textarea rows="3" placeholder="Your answer" [ngModel]="answers()[r.id] ?? ''" (ngModelChange)="setAnswer(r.id, $event)"></textarea>
                <button class="btn primary" [disabled]="busy() || !(answers()[r.id] ?? '').trim()" (click)="respond(r.id)">Send answer</button>
              } @else {
                <p class="muted">Waiting for the claimant to respond.</p>
              }
            </div>
          }
        </div>
      }

      @if (staffActions().length) {
        <div class="card">
          <h3>Actions</h3>
          <div class="actions">
            @if (can('ASSIGN')) { <button class="btn primary" [disabled]="busy()" (click)="assign()">Pick up this claim</button> }
            @if (can('ASSESS')) { <button class="btn" (click)="panel.set('assess')">Set reserve</button> }
            @if (can('REQUEST_INFO')) { <button class="btn" (click)="panel.set('info')">Request information</button> }
            @if (can('APPROVE')) { <button class="btn primary" (click)="panel.set('approve')">Approve</button> }
            @if (can('REJECT')) { <button class="btn danger" (click)="panel.set('reject')">Reject</button> }
            @if (can('SETTLE')) { <button class="btn primary" (click)="panel.set('settle')">Mark settled</button> }
          </div>

          @switch (panel()) {
            @case ('assess') {
              <form class="form" (ngSubmit)="assess()">
                <label>Reserve ({{ d.claim.currency }}) <input type="number" min="0" name="reserve" [(ngModel)]="reserve" /></label>
                <label>Internal notes (not visible to claimant) <textarea rows="2" name="notes" [(ngModel)]="notes"></textarea></label>
                <button class="btn primary" [disabled]="busy() || reserve === null">Save reserve</button>
              </form>
            }
            @case ('info') {
              <form class="form" (ngSubmit)="requestInfo()">
                <label>What do you need from the claimant? <textarea rows="3" name="q" [(ngModel)]="question"></textarea></label>
                <button class="btn primary" [disabled]="busy() || !question.trim()">Send request</button>
              </form>
            }
            @case ('approve') {
              <form class="form" (ngSubmit)="approve()">
                <label>Approved amount ({{ d.claim.currency }}) <input type="number" min="1" name="amt" [(ngModel)]="amount" /></label>
                <label>Reason <textarea rows="2" name="why" [(ngModel)]="reason"></textarea></label>
                <button class="btn primary" [disabled]="busy() || !amount">Approve claim</button>
              </form>
            }
            @case ('reject') {
              <form class="form" (ngSubmit)="reject()">
                <label>Reason (shown to the claimant) <textarea rows="3" name="rej" [(ngModel)]="reason"></textarea></label>
                <button class="btn danger" [disabled]="busy() || !reason.trim()">Reject claim</button>
              </form>
            }
            @case ('settle') {
              <form class="form" (ngSubmit)="settle()">
                <label>Payment reference <input name="ref" [(ngModel)]="payRef" /></label>
                <button class="btn primary" [disabled]="busy()">Confirm settlement</button>
              </form>
            }
          }
        </div>
      }

      <div class="card">
        <h3>Timeline</h3>
        <ol class="timeline">
          @for (a of d.activity; track $index) {
            <li [class.internal]="a.internal">
              <span class="when">{{ a.at | date: 'd MMM, HH:mm' }}</span>
              <span>{{ a.message }} <small class="muted">· {{ a.actorName }}@if (a.internal) { · internal }</small></span>
            </li>
          }
        </ol>
      </div>
    } @else if (error()) {
      <p class="error">{{ error() }}</p>
    } @else {
      <p class="muted">Loading…</p>
    }
  `,
})
export class ClaimDetailPage {
  private api = inject(Api);
  private auth = inject(Auth);
  private route = inject(ActivatedRoute);

  readonly id = Number(this.route.snapshot.paramMap.get('id'));
  detail = signal<ClaimDetail | null>(null);
  error = signal('');
  busy = signal(false);
  panel = signal<Panel | null>(null);
  answers = signal<Record<number, string>>({});

  reserve: number | null = null;
  notes = '';
  question = '';
  amount: number | null = null;
  reason = '';
  payRef = '';

  constructor() {
    poll(() => this.refresh(), 15_000);
  }

  backLink = () => this.auth.home();
  can = (a: Action) => this.detail()?.allowedActions.includes(a) ?? false;
  staffActions = () => (this.detail()?.allowedActions ?? []).filter(a => a !== 'PROVIDE_INFO');
  setAnswer(id: number, v: string) { this.answers.update(m => ({ ...m, [id]: v })); }

  async refresh() {
    if (this.busy()) return;
    try { this.detail.set(await this.api.claim(this.id)); this.error.set(''); }
    catch (e) { this.error.set(errorMessage(e)); }
  }

  /** Runs a command; on failure (e.g. 409 because someone else acted first) shows the reason and reloads fresh state. */
  private async run(cmd: () => Promise<ClaimDetail>) {
    this.busy.set(true);
    this.error.set('');
    try {
      this.detail.set(await cmd());
      this.panel.set(null);
      this.reserve = this.amount = null;
      this.notes = this.question = this.reason = this.payRef = '';
    } catch (e) {
      this.error.set(errorMessage(e));
      this.busy.set(false);
      await this.refresh();
    } finally {
      this.busy.set(false);
    }
  }

  assign() { return this.run(() => this.api.assign(this.id)); }
  assess() { return this.run(() => this.api.assess(this.id, Number(this.reserve), this.notes)); }
  requestInfo() { return this.run(() => this.api.requestInfo(this.id, this.question.trim())); }
  approve() { return this.run(() => this.api.approve(this.id, Number(this.amount), this.reason)); }
  reject() { return this.run(() => this.api.reject(this.id, this.reason.trim())); }
  settle() { return this.run(() => this.api.settle(this.id, this.payRef)); }
  respond(requestId: number) {
    return this.run(() => this.api.respond(this.id, requestId, (this.answers()[requestId] ?? '').trim()));
  }
}
