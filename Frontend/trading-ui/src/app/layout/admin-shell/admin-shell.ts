import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar.component';
import { AdminSidebarComponent } from '../admin-sidebar/admin-sidebar';
import { FooterComponent } from '../footer/footer.component';

@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, AdminSidebarComponent, FooterComponent],
  templateUrl: './admin-shell.html',
  styleUrls: ['./admin-shell.css']
})
export class AdminShell{}