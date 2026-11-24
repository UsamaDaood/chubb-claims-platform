import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { Auth, roleGuard } from './core/auth';

/**
 * One SPA, role-scoped lazy areas. A claimant never downloads the staff bundle and vice versa;
 * the backend still enforces every rule - these guards are UX, not security.
 */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login').then(m => m.LoginPage) },
  {
    path: 'claims',
    canActivate: [roleGuard('CLAIMANT')],
    loadChildren: () => import('./features/claimant/claimant.routes').then(m => m.CLAIMANT_ROUTES),
  },
  {
    path: 'staff',
    canActivate: [roleGuard('OFFICER', 'MANAGER')],
    loadChildren: () => import('./features/staff/staff.routes').then(m => m.STAFF_ROUTES),
  },
  { path: '', pathMatch: 'full', canActivate: [() => inject(Router).parseUrl(inject(Auth).home())], children: [] },
  { path: '**', redirectTo: '' },
];
