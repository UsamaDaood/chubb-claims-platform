import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../core/api';
import { Auth } from '../core/auth';
import { NotificationView } from '../core/models';
import { poll } from '../core/util';

/**
 * Surfaces the async side of claims processing. Notifications are produced by the event consumer
 * after the outbox delivers an event, so they arrive moments after the action that caused them;
 * polling every 10s is the sprint-sized stand-in for SSE/WebSocket push.
 */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe],
  template: `
    <div class="bell">
      <button class="icon-btn" (click)="open.set(!open())" aria-label="Notifications">
        🔔 @if (unread() > 0) { <span class="dot">{{ unread() }}</span> }
      </button>
      @if (open()) {
        <div class="panel">
          @for (n of items(); track n.id) {
            <button class="note" [class.unread]="!n.read" (click)="go(n)">
              <span>{{ n.message }}</span>
              <small>{{ n.createdAt | date: 'd MMM, HH:mm' }}</small>
            </button>
          } @empty {
            <p class="empty">No notifications yet.</p>
          }
        </div>
      }
    </div>
  `,
})
export class NotificationBell {
  private api = inject(Api);
  private auth = inject(Auth);
  private router = inject(Router);

  items = signal<NotificationView[]>([]);
  open = signal(false);
  unread = computed(() => this.items().filter(n => !n.read).length);

  constructor() {
    poll(async () => {
      try { this.items.set(await this.api.notifications()); } catch { /* transient; next poll retries */ }
    }, 10_000);
  }

  async go(n: NotificationView) {
    this.open.set(false);
    if (!n.read) {
      this.items.update(list => list.map(x => (x.id === n.id ? { ...x, read: true } : x)));
      void this.api.markRead(n.id).catch(() => undefined);
    }
    await this.router.navigate(this.auth.claimLink(n.claimId));
  }
}
