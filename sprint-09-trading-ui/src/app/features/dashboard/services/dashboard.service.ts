import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Dashboard Service
 * Handles API calls for dashboard data
 */
@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  // TODO: Define API endpoints
  // private readonly API_BASE = '/api/dashboard';

  constructor(private http: HttpClient) {}

  // TODO: Implement getPortfolioDashboard()
  // Calls: GET /api/dashboard/portfolio
  getPortfolioDashboard(): Observable<any> {
    // TODO: Implement
    throw new Error('Not implemented');
  }

  // TODO: Implement getPositions()
  // Calls: GET /api/dashboard/positions
  getPositions(): Observable<any> {
    // TODO: Implement
    throw new Error('Not implemented');
  }

  // TODO: Implement getPortfolioPerformance()
  // Calls: GET /api/dashboard/performance
  getPortfolioPerformance(days: number = 30): Observable<any> {
    // TODO: Implement
    throw new Error('Not implemented');
  }

  // TODO: Implement refreshDashboard()
  refreshDashboard(): Observable<any> {
    // TODO: Implement
    throw new Error('Not implemented');
  }
}
