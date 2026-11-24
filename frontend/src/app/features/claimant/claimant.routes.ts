import { Routes } from '@angular/router';

export const CLAIMANT_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./claim-list').then(m => m.ClaimList) },
  { path: 'new', loadComponent: () => import('./claim-new').then(m => m.ClaimNew) },
  { path: ':id', loadComponent: () => import('../claim-detail/claim-detail').then(m => m.ClaimDetailPage) },
];
