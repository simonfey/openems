import { Component, OnInit } from '@angular/core';
import { AuthService } from './auth.service';
import { Websocket } from '../shared/shared';
import { AuthenticateWithTokenRequest } from '../shared/jsonrpc/request/authenticateWithTokenRequest';
import { CookieService } from 'ngx-cookie-service';
import { Router } from '@angular/router';

@Component({
    selector: 'auth-callback',
    templateUrl: './authcallback.component.html',
})
export class AuthCallbackComponent implements OnInit {

    constructor(
        private authService: AuthService, 
        private cookieService: CookieService, 
        private router: Router,
        private websocket: Websocket,
        ) { }

    ngOnInit() {
        this.authService.handleAuthCallback().then(redirectResult => {
            this.authService.isAuthenticated().then(authenticated => {
                if (authenticated) {
                    this.authService.getIdToken().then(idToken => {
                        if (this.websocket.status == 'waiting for credentials') {
                            this.websocket.login(new AuthenticateWithTokenRequest({ token: idToken.__raw }))
                        } else {
                            this.cookieService.set('token', idToken.__raw, { expires: 365, path: '/', sameSite: 'Strict' });
                        }
                        this.router.navigate(['/index']);
                    });
                }
            })
        })
    }
}