import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { UserView } from '../../core/models';
import { errorMessage } from '../../core/util';

@Component({
  selector: 'app-login',
  template: `
    <section class="login">
      <h1>Chubb APAC Claims</h1>
      <p class="muted">Demo sign-in: pick a persona. In production this is replaced by the corporate identity provider.</p>
      @if (error()) { <p class="error">{{ error() }}</p> }
      @for (g of groups(); track g.title) {
        <h3>{{ g.title }}</h3>
        <div class="cards">
          @for (u of g.users; track u.id) {
            <button class="card persona" (click)="signIn(u)">
              <strong>{{ u.name }}</strong>
              <span class="muted">{{ u.market ?? 'All markets' }}</span>
            </button>
          }
        </div>
      }
    </section>
  `,
})
export class LoginPage {
  private api = inject(Api);
  private auth = inject(Auth);
  private router = inject(Router);

  users = signal<UserView[]>([]);
  error = signal('');
  groups = computed(() => [
    { title: 'Claimants', users: this.users().filter(u => u.role === 'CLAIMANT') },
    { title: 'Claims officers', users: this.users().filter(u => u.role === 'OFFICER') },
    { title: 'Management', users: this.users().filter(u => u.role === 'MANAGER') },
  ]);

  constructor() {
    this.api.users().then(u => this.users.set(u)).catch(e => this.error.set(errorMessage(e)));
  }

  async signIn(u: UserView) {
    try {
      await this.auth.login(u.id);
      await this.router.navigateByUrl(this.auth.home());
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }
}
