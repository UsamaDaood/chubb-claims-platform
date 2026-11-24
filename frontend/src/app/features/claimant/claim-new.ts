import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { CURRENCY, errorMessage } from '../../core/util';

@Component({
  selector: 'app-claim-new',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="page-head"><h1>Report an incident</h1></div>
    <form class="card form" [formGroup]="form" (ngSubmit)="submit()">
      <label>What happened?
        <select formControlName="type">
          <option value="MOTOR">Motor accident or damage</option>
          <option value="PROPERTY">Property damage</option>
        </select>
      </label>
      <label>Date of incident
        <input type="date" formControlName="incidentDate" [max]="today" />
        @if (show('incidentDate')) { <small class="error">Pick a date that isn't in the future.</small> }
      </label>
      <label>Where did it happen?
        <input formControlName="incidentLocation" maxlength="200" />
      </label>
      <label>Estimated cost of damage ({{ currency() }})
        <input type="number" min="1" formControlName="estimatedAmount" />
        @if (show('estimatedAmount')) { <small class="error">Enter an amount greater than zero.</small> }
      </label>
      <label>Describe the incident
        <textarea rows="5" maxlength="2000" formControlName="description"></textarea>
        @if (show('description')) { <small class="error">A short description is required.</small> }
      </label>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <div class="actions">
        <a class="btn" routerLink="/claims">Cancel</a>
        <button class="btn primary" type="submit" [disabled]="busy()">Submit claim</button>
      </div>
    </form>
  `,
})
export class ClaimNew {
  private fb = inject(FormBuilder);
  private api = inject(Api);
  private router = inject(Router);
  private auth = inject(Auth);

  today = new Date().toISOString().slice(0, 10);
  busy = signal(false);
  error = signal('');
  currency = computed(() => CURRENCY[this.auth.user()?.market ?? 'SG']);

  // One idempotency key per form instance: a double-click or network retry cannot create two claims.
  private idempotencyKey = crypto.randomUUID();

  form = this.fb.nonNullable.group({
    type: ['MOTOR' as 'MOTOR' | 'PROPERTY', Validators.required],
    incidentDate: [this.today, Validators.required],
    incidentLocation: [''],
    estimatedAmount: [null as number | null, [Validators.required, Validators.min(1)]],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
  });

  show(name: 'incidentDate' | 'estimatedAmount' | 'description') {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  async submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');
    try {
      const created = await this.api.submit({
        type: v.type, description: v.description, incidentDate: v.incidentDate,
        incidentLocation: v.incidentLocation, estimatedAmount: Number(v.estimatedAmount),
      }, this.idempotencyKey);
      await this.router.navigate(['/claims', created.claim.id]);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
