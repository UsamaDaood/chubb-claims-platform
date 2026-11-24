import { Routes } from '@angular/router';
import { roleGuard } from '../../core/auth';

export const STAFF_ROUTES: Routes = [
  { path: 'desk', canActivate: [roleGuard('OFFICER')], loadComponent: () => import('./desk').then(m => m.Desk) },
  { path: 'queue', loadComponent: () => import('./queue').then(m => m.Queue) },
  { path: 'manager', canActivate: [roleGuard('MANAGER')], loadComponent: () => import('./manager-dashboard').then(m => m.ManagerDashboard) },
  { path: 'claims/:id', loadComponent: () => import('../claim-detail/claim-detail').then(m => m.ClaimDetailPage) },
  { path: '', pathMatch: 'full', redirectTo: 'desk' },
];
