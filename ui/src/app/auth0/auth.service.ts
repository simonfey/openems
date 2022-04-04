import { Injectable } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { environment } from '../../environments';
import createAuth0Client, { Auth0Client } from '@auth0/auth0-spa-js';

@Injectable({
  providedIn: 'root',
})
export class AuthService {

  public env = environment;
  auth0Client: Auth0Client;

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {
  }

  initialize = (): Promise<void> => {
    return new Promise(async (resolve, reject) => {
      this.route.queryParams.subscribe(async params => {
        this.auth0Client = await createAuth0Client({
          domain: "openems.eu.auth0.com",
          client_id: "JZFRU4b1YXNGAiqxC300FxxEkavQBB7K",
          redirect_uri: `${window.location.origin}` + "/callback",
          scope: 'openid profile email'
        });
        resolve();
      });
    });
  }

  public isAuthenticated(): Promise<any> {
    return this.auth0Client.isAuthenticated();
  }

  public getIdToken(): Promise<any> {
    return this.auth0Client.getIdTokenClaims();
  }

  public handleAuthCallback(): Promise<void> {
    return new Promise(async (resolve, reject) => {
      this.auth0Client.handleRedirectCallback().then(() => {
        // auth0Client requires a re-initialization after handling callback
        this.initialize().then(() => {
          resolve();
        });
      });
    });
  }

  public login(): void {
    this.auth0Client.loginWithRedirect();
  }

  public logout(): void {
    this.auth0Client.logout({ returnTo: `${window.location.origin}` + '/index' });
  }
}
