package main.java.com.xero.starter;

import java.io.IOException;
import java.util.UUID;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.threeten.bp.OffsetDateTime;

import com.xero.api.*;
import com.xero.api.client.AccountingApi;
import com.xero.models.accounting.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Claim;

@WebServlet("/AuthenticatedResource")
public class AuthenticatedResource extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private AccountingApi accountingApi;
       
    public AuthenticatedResource() {
        super();
    }

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// Get Tokens and Xero Tenant Id from Storage
		TokenStorage store = new TokenStorage();
		String savedAccessToken =store.get(request, "access_token");
		String savedRefreshToken = store.get(request, "refresh_token");
		String xeroTenantId = store.get(request, "xero_tenant_id");	
		String id_token = store.get(request, "id_token");

		// Let's decode our id_token and print out some values
		try {
			DecodedJWT jwt_decoded = JWT.decode(id_token);
			Map<String, Claim> claims = jwt_decoded.getClaims();  
			System.out.println(claims.get("email").asString());
			System.out.println(claims.get("given_name").asString());
			System.out.println(claims.get("family_name").asString());
			System.out.println(claims.get("preferred_username").asString());
			System.out.println(claims.get("xero_userid").asString());
		} catch (JWTDecodeException e) {
			System.out.println(e.toString());
        }

		// Check expiration of token and refresh if necessary
		// This should be done prior to each API call to ensure your accessToken is valid
		String accessToken = new TokenRefresh().checkToken(savedAccessToken,savedRefreshToken,response);

		// Init AccountingApi client
		ApiClient defaultClient = new ApiClient();

		// Get Singleton - instance of accounting client
		accountingApi = AccountingApi.getInstance(defaultClient);	
		
		try {
			// Get All Contacts
			Contacts contacts = accountingApi.getContacts(accessToken,xeroTenantId,null, null, null, null, null, null);
			System.out.println("How many contacts did we find: " + contacts.getContacts().size());
			
			/* CREATE ACCOUNT */
			Account acct = new Account();
			acct.setName("Office Expense for Me");
			acct.setCode("66000");
			acct.setType(com.xero.models.accounting.AccountType.EXPENSE);
			Accounts newAccount = accountingApi.createAccount(accessToken,xeroTenantId,acct);
			System.out.println("New account created: " + newAccount.getAccounts().get(0).getName());

			/* READ ACCOUNT using a WHERE clause */
			String where = "Status==\"ACTIVE\"&&Type==\"BANK\"";
			Accounts accountsWhere = accountingApi.getAccounts(accessToken,xeroTenantId,null, where, null);
			System.out.println("Read accounts where type is bank : " + accountsWhere.getAccounts().size());

			/* READ ACCOUNT using the ID */
			Accounts accounts = accountingApi.getAccounts(accessToken,xeroTenantId,null, null, null);
			UUID accountID = accounts.getAccounts().get(0).getAccountID();
			Accounts oneAccount = accountingApi.getAccount(accessToken,xeroTenantId,accountID);
			System.out.println("Read single account by id - name: " + oneAccount.getAccounts().get(0).getName());
										
			/* UPDATE ACCOUNT */
			UUID newAccountID = newAccount.getAccounts().get(0).getAccountID();
			newAccount.getAccounts().get(0).setDescription("Monsters Inc.");
			newAccount.getAccounts().get(0).setStatus(null);
			Accounts updateAccount = accountingApi.updateAccount(accessToken,xeroTenantId,newAccountID, newAccount);
			System.out.println("Update account by id - description: " + updateAccount.getAccounts().get(0).getDescription());
			
			/* DELETE ACCOUNT */
			UUID deleteAccountID = newAccount.getAccounts().get(0).getAccountID();
			Accounts deleteAccount = accountingApi.deleteAccount(accessToken,xeroTenantId,deleteAccountID);
			System.out.println("Delete account - Status? : " + deleteAccount.getAccounts().get(0).getStatus());

			// GET INVOICE MODIFIED in LAST 24 HOURS
			OffsetDateTime invModified = OffsetDateTime.now();
			invModified.minusDays(1);	
			Invoices InvoiceList24hour = accountingApi.getInvoices(accessToken,xeroTenantId,invModified, null, null, null, null, null, null, null, null, null, null);
			System.out.println("How many invoices modified in last 24 hours?: " + InvoiceList24hour.getInvoices().size());
		
			response.getWriter().append("API calls completed - check your output console results");
		
		} catch (XeroBadRequestException e) {
			// 400
			// ACCOUNTING VALIDATION ERROR
			if (e.getElements() != null && e.getElements().size() > 0) {
				System.out.println("Xero Exception: " + e.getStatusCode());
				for (Element item : e.getElements()) {
					for (ValidationError err : item.getValidationErrors()) {
						System.out.println("Accounting Validation Error Msg: " + err.getMessage());
					}
				}
			}
		} catch (XeroUnauthorizedException e) {
			// 401
			System.out.println("Exception message: " + e.getMessage());
		} catch (XeroForbiddenException e) {
			// 403
			System.out.println("Exception message: " + e.getMessage());
		} catch (XeroNotFoundException e) {
			// 404
			System.out.println("Exception message: " + e.getMessage());
		} catch (XeroMethodNotAllowedException e) {
			// 405
			System.out.println("Exception message: " + e.getMessage());
		} catch (XeroRateLimitException e) {
			// 429
			System.out.println("Exception message: " + e.getMessage());             
		} catch (XeroServerErrorException e) {
			// 500
			System.out.println("Exception message: " + e.getMessage());
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}  
	}
}