import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ClaimSummary, OfficerSummary } from '../../core/models';
import { errorMessage, poll } from '../../core/util';
import { ClaimTable } from '../../shared/shared';

/** The officer's working view: what is on my desk, what is late, and how big the unassigned pile is. */
@Component({
  selector: 'app-desk',
  imports: [RouterLink, ClaimTable],
  template: `
    <div class="page-head">
      <div>
        <h1>My desk</h1>
        <p class="muted">Oldest first. Anything marked red is past its decision SLA.</p>
      </div>
      <a class="btn primary" routerLink="/staff/queue">
        Unassigned queue @if (summary(); as s) { ({{ s.unassignedInMarket }}) }
      </a>
    </div>

    @if (error()) { <p class="error">{{ error() }}</p> }

    @if (summary(); as s) {
      <div class="tiles">
        <div class="tile"><span>{{ s.inReview }}</span>In review</div>
        <div class="tile" [class.alert]="s.slaBreached > 0"><span>{{ s.slaBreached }}</span>SLA breached</div>
        <div class="tile" [class.warn]="s.dueWithin24h > 0"><span>{{ s.dueWithin24h }}</span>Due in 24h</div>
        <div class="tile"><span>{{ s.awaitingClaimant }}</span>Waiting on claimant</div>
        <div class="tile"><span>{{ s.approvedAwaitingSettlement }}</span>Approved, to settle</div>
        <div class="tile"><span>{{ s.decidedLast30Days }}</span>Decided (30d)</div>
      </div>
    }

    <app-claim-table [claims]="mine()" [link]="link" [showClaimant]="true"
                     emptyText="Nothing assigned. Pick something up from the queue." />
  `,
})
export class Desk {
  private api = inject(Api);
  private auth = inject(Auth);

  mine = signal<ClaimSummary[]>([]);
  summary = signal<OfficerSummary | null>(null);
  error = signal('');
  link = (id: number) => this.auth.claimLink(id);

  constructor() {
    poll(async () => {
      try {
        const [m, s] = await Promise.all([this.api.assigned(), this.api.officerSummary()]);
        this.mine.set(m);
        this.summary.set(s);
        this.error.set('');
      } catch (e) {
        this.error.set(errorMessage(e));
      }
    }, 15_000);
  }
}
