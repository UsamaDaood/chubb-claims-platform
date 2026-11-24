import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ClaimSummary } from '../../core/models';
import { errorMessage, poll } from '../../core/util';
import { ClaimTable } from '../../shared/shared';

@Component({
  selector: 'app-claim-list',
  imports: [RouterLink, ClaimTable],
  template: `
    <div class="page-head">
      <div>
        <h1>My claims</h1>
        <p class="muted">Track progress, answer questions and see decisions. Updates appear automatically.</p>
      </div>
      <a class="btn primary" routerLink="/claims/new">Report an incident</a>
    </div>

    @if (error()) { <p class="error">{{ error() }}</p> }

    @if (needsAction().length) {
      <div class="banner">
        <strong>We need something from you.</strong>
        @for (c of needsAction(); track c.id) {
          <a [routerLink]="link(c.id)">Reply on {{ c.claimNumber }} →</a>
        }
      </div>
    }

    <app-claim-table [claims]="claims()" [link]="link" emptyText="You haven't reported any incidents yet." />
  `,
})
export class ClaimList {
  private api = inject(Api);
  private auth = inject(Auth);

  claims = signal<ClaimSummary[]>([]);
  error = signal('');
  needsAction = computed(() => this.claims().filter(c => c.status === 'INFO_REQUESTED'));
  link = (id: number) => this.auth.claimLink(id);

  constructor() {
    poll(async () => {
      try { this.claims.set(await this.api.mine()); this.error.set(''); }
      catch (e) { this.error.set(errorMessage(e)); }
    }, 15_000);
  }
}
