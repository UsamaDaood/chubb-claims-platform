import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from './core/auth';
import { NotificationBell } from './shared/notification-bell';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NotificationBell],
  template: `
    @if (auth.user(); as s) {
      <header class="topbar">
        <a class="brand" [routerLink]="auth.home()">Chubb <span>Claims</span></a>
        <nav>
          @switch (s.role) {
            @case ('CLAIMANT') {
              <a routerLink="/claims" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">My claims</a>
              <a routerLink="/claims/new" routerLinkActive="active">Report an incident</a>
            }
            @case ('OFFICER') {
              <a routerLink="/staff/desk" routerLinkActive="active">My desk</a>
              <a routerLink="/staff/queue" routerLinkActive="active">Queue</a>
            }
            @case ('MANAGER') {
              <a routerLink="/staff/manager" routerLinkActive="active">Dashboard</a>
              <a routerLink="/staff/queue" routerLinkActive="active">Queue</a>
            }
          }
        </nav>
        <div class="spacer"></div>
        <app-notification-bell />
        <span class="who">{{ s.name }}</span>
        <button class="btn small" (click)="logout()">Sign out</button>
      </header>
    }
    <main><router-outlet /></main>
  `,
})
export class App {
  protected auth = inject(Auth);
  private router = inject(Router);

  logout() {
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }
}
