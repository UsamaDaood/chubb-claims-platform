import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { ClaimSummary, ClaimType } from '../../core/models';
import { errorMessage, poll } from '../../core/util';
import { ClaimTable } from '../../shared/shared';

@Component({
  selector: 'app-queue',
  imports: [FormsModule, ClaimTable],
  template: `
    <div class="page-head">
      <div>
        <h1>Unassigned claims</h1>
        <p class="muted">Oldest first, so the claims closest to breaching their SLA are at the top.</p>
      </div>
      <label class="inline">Type
        <select [ngModel]="type()" (ngModelChange)="setType($event)">
          <option value="">All</option>
          <option value="MOTOR">Motor</option>
          <option value="PROPERTY">Property</option>
        </select>
      </label>
    </div>

    @if (error()) { <p class="error">{{ error() }}</p> }

    <app-claim-table [claims]="claims()" [link]="link" [showClaimant]="true"
                     [pickUp]="isOfficer" (picked)="pickUp($event)"
                     emptyText="The queue is empty. Nice work." />
  `,
})
export class Queue {
  private api = inject(Api);
  private auth = inject(Auth);
  private router = inject(Router);

  claims = signal<ClaimSummary[]>([]);
  type = signal<ClaimType | ''>('');
  error = signal('');
  isOfficer = this.auth.user()?.role === 'OFFICER';
  link = (id: number) => this.auth.claimLink(id);

  constructor() {
    poll(() => this.load(), 15_000);
  }

  setType(t: ClaimType | '') {
    this.type.set(t);
    void this.load();
  }

  async load() {
    try {
      this.claims.set(await this.api.queue(this.type() || undefined));
      this.error.set('');
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  /** If a colleague got there first the API answers 409; we surface that and refresh rather than failing silently. */
  async pickUp(id: number) {
    try {
      await this.api.assign(id);
      await this.router.navigate(this.auth.claimLink(id));
    } catch (e) {
      this.error.set(errorMessage(e));
      await this.load();
    }
  }
}
