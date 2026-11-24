import { Component, Pipe, PipeTransform, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClaimStatus, ClaimSummary } from '../core/models';
import { STATUS_LABEL } from '../core/util';

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency: string | null | undefined): string {
    if (value === null || value === undefined) return '—';
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency: currency ?? 'USD', maximumFractionDigits: 0,
    }).format(value);
  }
}

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="'badge s-' + status().toLowerCase()">{{ label() }}</span>`,
})
export class StatusBadge {
  status = input.required<ClaimStatus>();
  label = () => STATUS_LABEL[this.status()];
}

/** Claim list used by every role. Ageing and SLA state are first-class columns because they drive an officer's day. */
@Component({
  selector: 'app-claim-table',
  imports: [RouterLink, DatePipe, StatusBadge, MoneyPipe],
  template: `
    <table class="table">
      <thead>
        <tr>
          <th>Claim</th><th>Type</th>
          @if (showClaimant()) { <th>Claimant</th> }
          @if (showOfficer()) { <th>Officer</th> }
          <th>Status</th><th class="num">Amount</th><th>Submitted</th><th>Age / SLA</th>
          @if (pickUp()) { <th></th> }
        </tr>
      </thead>
      <tbody>
        @for (c of claims(); track c.id) {
          <tr [class.breach]="c.slaBreached">
            <td><a [routerLink]="link()(c.id)">{{ c.claimNumber }}</a></td>
            <td>{{ c.type === 'MOTOR' ? 'Motor' : 'Property' }}</td>
            @if (showClaimant()) { <td>{{ c.claimantName }}</td> }
            @if (showOfficer()) { <td>{{ c.assignedOfficerName ?? '—' }}</td> }
            <td><app-status-badge [status]="c.status" /></td>
            <td class="num">{{ (c.approvedAmount ?? c.reserveAmount ?? c.estimatedAmount) | money: c.currency }}</td>
            <td>{{ c.submittedAt | date: 'd MMM' }}</td>
            <td>
              {{ c.ageDays }}d
              @if (c.slaBreached) { <span class="pill red">SLA breached</span> }
            </td>
            @if (pickUp()) {
              <td class="right"><button class="btn small" (click)="picked.emit(c.id)">Pick up</button></td>
            }
          </tr>
        } @empty {
          <tr><td class="empty" colspan="9">{{ emptyText() }}</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class ClaimTable {
  claims = input.required<ClaimSummary[]>();
  link = input.required<(id: number) => string[]>();
  showClaimant = input(false);
  showOfficer = input(false);
  pickUp = input(false);
  emptyText = input('Nothing here.');
  picked = output<number>();
}
