import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { ExposureReport, WorkloadRow } from '../../core/models';
import { STATUS_LABEL, errorMessage, poll } from '../../core/util';
import { MoneyPipe } from '../../shared/shared';

interface MarketLine { market: string; currency: string; count: number; amount: number; pct: number; }

/** Manager view: how much liability is outstanding (never mixed across currencies) and how the team is coping. */
@Component({
  selector: 'app-manager-dashboard',
  imports: [RouterLink, MoneyPipe],
  template: `
    <div class="page-head">
      <div>
        <h1>Portfolio &amp; team</h1>
        <p class="muted">Exposure = approved amount, else officer reserve, else claimant estimate, for every open claim.</p>
      </div>
      <a class="btn" routerLink="/staff/queue">View unassigned queue</a>
    </div>
    @if (error()) { <p class="error">{{ error() }}</p> }

    @if (exposure(); as e) {
      <h2>Outstanding liability exposure</h2>
      <div class="tiles">
        @for (t of e.totals; track t.currency) {
          <div class="tile big"><span>{{ t.amount | money: t.currency }}</span>{{ t.currency }} · {{ t.openCount }} open claims</div>
        }
        <div class="tile" [class.warn]="e.unassignedCount > 0"><span>{{ e.unassignedCount }}</span>Unassigned</div>
        <div class="tile" [class.alert]="e.slaBreachedCount > 0"><span>{{ e.slaBreachedCount }}</span>Past decision SLA</div>
        <div class="tile" [class.warn]="e.unreservedCount > 0"><span>{{ e.unreservedCount }}</span>No reserve set</div>
      </div>

      <div class="grid2">
        <div class="card">
          <h3>By market</h3>
          @for (m of byMarket(); track m.market + m.currency) {
            <div class="bar-row">
              <span class="label">{{ m.market }} <small class="muted">{{ m.count }} claims</small></span>
              <div class="bar"><div [style.width.%]="m.pct"></div></div>
              <span class="num">{{ m.amount | money: m.currency }}</span>
            </div>
          } @empty { <p class="empty">No open claims.</p> }
        </div>
        <div class="card">
          <h3>By status</h3>
          <table class="table compact">
            <thead><tr><th>Status</th><th>Currency</th><th class="num">Claims</th><th class="num">Exposure</th></tr></thead>
            <tbody>
              @for (s of byStatus(); track s.key) {
                <tr><td>{{ s.status }}</td><td>{{ s.currency }}</td><td class="num">{{ s.count }}</td>
                    <td class="num">{{ s.amount | money: s.currency }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }

    <h2>Team workload</h2>
    <table class="table">
      <thead>
        <tr><th>Officer</th><th>Market</th><th class="num">In review</th><th class="num">Waiting on claimant</th>
            <th class="num">To settle</th><th class="num">SLA breached</th><th class="num">Oldest</th>
            <th class="num">Decided (30d)</th><th class="num">Avg days to decide</th></tr>
      </thead>
      <tbody>
        @for (w of workload(); track w.officerId) {
          <tr [class.breach]="w.slaBreached > 0">
            <td>{{ w.officerName }}</td><td>{{ w.market }}</td>
            <td class="num">{{ w.inReview }}</td><td class="num">{{ w.awaitingClaimant }}</td>
            <td class="num">{{ w.approvedAwaitingSettlement }}</td>
            <td class="num">{{ w.slaBreached }}</td><td class="num">{{ w.oldestAgeDays }}d</td>
            <td class="num">{{ w.decidedLast30Days }}</td><td class="num">{{ w.avgDecisionDays }}</td>
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class ManagerDashboard {
  private api = inject(Api);

  exposure = signal<ExposureReport | null>(null);
  workload = signal<WorkloadRow[]>([]);
  error = signal('');

  byMarket = computed<MarketLine[]>(() => {
    const rows = this.exposure()?.rows ?? [];
    const map = new Map<string, MarketLine>();
    for (const r of rows) {
      const k = r.market + r.currency;
      const cur = map.get(k) ?? { market: r.market, currency: r.currency, count: 0, amount: 0, pct: 0 };
      cur.count += r.count;
      cur.amount += r.amount;
      map.set(k, cur);
    }
    // Bars are scaled within each currency: amounts in different currencies are not comparable.
    const maxByCurrency = new Map<string, number>();
    for (const m of map.values()) maxByCurrency.set(m.currency, Math.max(maxByCurrency.get(m.currency) ?? 0, m.amount));
    return [...map.values()]
      .map(m => ({ ...m, pct: (m.amount / (maxByCurrency.get(m.currency) || 1)) * 100 }))
      .sort((a, b) => a.currency.localeCompare(b.currency) || b.amount - a.amount);
  });

  byStatus = computed(() => {
    const map = new Map<string, { key: string; status: string; currency: string; count: number; amount: number }>();
    for (const r of this.exposure()?.rows ?? []) {
      const key = r.status + r.currency;
      const cur = map.get(key) ?? { key, status: STATUS_LABEL[r.status], currency: r.currency, count: 0, amount: 0 };
      cur.count += r.count;
      cur.amount += r.amount;
      map.set(key, cur);
    }
    return [...map.values()];
  });

  constructor() {
    poll(async () => {
      try {
        const [e, w] = await Promise.all([this.api.exposure(), this.api.workload()]);
        this.exposure.set(e);
        this.workload.set(w);
        this.error.set('');
      } catch (err) {
        this.error.set(errorMessage(err));
      }
    }, 20_000);
  }
}
