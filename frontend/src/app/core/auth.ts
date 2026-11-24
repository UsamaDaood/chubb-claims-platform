import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Api } from './api';
import { Role, UserView } from './models';

const KEY = 'claims.session';

interface Session { token: string; user: UserView; }

@Injectable({ providedIn: 'root' })
export class Auth {
  private api = inject(Api);
  private session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  get token(): string | null { return this.session()?.token ?? null; }

  async login(userId: string): Promise<UserView> {
    const res = await this.api.login(userId);
    const s = { token: res.token, user: res.user };
    this.session.set(s);
    try { sessionStorage.setItem(KEY, JSON.stringify(s)); } catch { /* storage unavailable: session lives in memory */ }
    return res.user;
  }

  logout(): void {
    this.session.set(null);
    try { sessionStorage.removeItem(KEY); } catch { /* ignore */ }
  }

  /** Landing route for each user type: one Angular app, three experiences. */
  home(): string {
    switch (this.session()?.user.role) {
      case 'CLAIMANT': return '/claims';
      case 'OFFICER': return '/staff/desk';
      case 'MANAGER': return '/staff/manager';
      default: return '/login';
    }
  }

  claimLink(id: number): string[] {
    return this.session()?.user.role === 'CLAIMANT' ? ['/claims', String(id)] : ['/staff/claims', String(id)];
  }

  private restore(): Session | null {
    try { return JSON.parse(sessionStorage.getItem(KEY) ?? 'null'); } catch { return null; }
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const router = inject(Router);
  const token = auth.token;
  const authed = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(authed).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !req.url.endsWith('/auth/login')) {
        auth.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};

/** Route guard: only the listed roles get in; everyone else is bounced to their own home. */
export const roleGuard = (...roles: Role[]): CanActivateFn => () => {
  const auth = inject(Auth);
  const router = inject(Router);
  const u = auth.user();
  if (!u) return router.parseUrl('/login');
  return roles.includes(u.role) ? true : router.parseUrl(auth.home());
};
