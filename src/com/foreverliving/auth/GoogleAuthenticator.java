package com.foreverliving.auth;

import com.dotmarketing.business.Layout;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.gson.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

import com.dotmarketing.util.Logger;
import com.liferay.portal.RequiredLayoutException;
import com.liferay.portal.action.LogoutAction;
import com.liferay.portal.util.CookieKeys;
import com.liferay.util.StringPool;
import com.dotmarketing.cms.factories.PublicEncryptionFactory;
import com.dotmarketing.business.APILocator;
import com.liferay.portal.model.User;
import com.dotmarketing.util.SecurityLogger;
import com.dotmarketing.util.UtilMethods;
import com.liferay.portal.util.WebKeys;
import com.dotmarketing.factories.PreviewFactory;
import com.dotmarketing.util.Config;
import com.dotmarketing.beans.*;
import org.apache.struts.Globals;

/**
 * Created by Jake Liscom on 2014-04-04.
 * Based on https://github.com/mdanter/OAuth2v1
 * Copyright Forever Living 2014
 */
public class GoogleAuthenticator {

    private static final int REMEMBER_ME_DURATION=Config.getIntProperty("GOOGLE_REMEMBER_ME_DURATION");
    private static final String CLIENT_ID = Config.getStringProperty("GOOGLE_CLIENT_ID");
    private static final String CLIENT_SECRET = Config.getStringProperty("GOOGLE_CLIENT_SECRET");

    //Callback URI that google will redirect to after successful authentication
        private static final String CALLBACK_URI = Config.getStringProperty("GOOGLE_CALLBACK_URI");

    private static final String REDIRECT_PATH = "/html/portal/login.jsp";//path to login code
    private static final String DEFAULT_REDIRECT_AFTER_DISTRIBUTOR_LOGIN_PATH = Config.getStringProperty("DEFAULT_REDIRECT_AFTER_DISTRIBUTOR_LOGIN_PATH");
    private static final String DEFAULT_REDIRECT_AFTER_GENERAL_LOGIN_PATH = Config.getStringProperty("DEFAULT_REDIRECT_AFTER_GENERAL_LOGIN_PATH");

    // start google authentication constants
        private static final Collection<String> SCOPE = Arrays.asList("https://www.googleapis.com/auth/userinfo.profile;https://www.googleapis.com/auth/userinfo.email".split(";"));
        private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo";
        private static final JsonFactory JSON_FACTORY = new JacksonFactory();
        private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private String stateToken;

    private final GoogleAuthorizationCodeFlow flow;

    /**
     * Constructor initializes the Google Authorization Code Flow with CLIENT ID, SECRET, and SCOPE
     */
    public GoogleAuthenticator() {
        flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, CLIENT_ID, CLIENT_SECRET, SCOPE).build();

        generateStateToken();
    }

    /**
     * Builds a login URL based on client ID, secret, callback URI, and scope
     * @return - the google login url
     */
    public String buildLoginUrl() {
        GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();

        return url.setRedirectUri(CALLBACK_URI).setState(stateToken).build();
    }

    /**
     * Generates a secure state token
     */
    private void generateStateToken(){

        SecureRandom sr1 = new SecureRandom();

        stateToken = "google;"+sr1.nextInt();

    }

    /**
     * Accessor for state token
     * @return - the state token
     */
    public String getStateToken(){
        return stateToken;
    }

    /**
     * Expects an Authentication Code, and makes an authenticated request for the user's profile information
     * @param authCode - authentication code provided by google
     * @return - JSON formatted user profile information
     * @throws IOException
     */
    private String getUserInfoJson(String authCode) throws IOException {
        GoogleTokenResponse response = flow.newTokenRequest(authCode).setRedirectUri(CALLBACK_URI).execute();
        Credential credential = flow.createAndStoreCredential(response, null);
        HttpRequestFactory requestFactory = HTTP_TRANSPORT.createRequestFactory(credential);

        // Make an authenticated request
            GenericUrl url = new GenericUrl(USER_INFO_URL);
            HttpRequest request = requestFactory.buildGetRequest(url);
            request.getHeaders().setContentType("application/json");
            String jsonIdentity = request.execute().parseAsString();

        return jsonIdentity;
    }

    /**
     * Authenticate a user
     * @param request
     * @param response
     * @param session
     * @param application
     * @return - true if successful
     */
    public Boolean authenticateUser(HttpServletRequest request,
                                    HttpServletResponse response,
                                    HttpSession session,
                                    ServletContext application) throws Exception{
        JsonParser parser = new JsonParser();
        User user;
        try{
            JsonObject googleUser = parser.parse(getUserInfoJson(request.getParameter("code"))).getAsJsonObject();
            String email = googleUser.get("email").toString().replace("\"", "");

            user = APILocator.getUserAPI().loadByUserByEmail(   email,
                                                                APILocator.getUserAPI().getSystemUser(),
                                                                false);

            //create a treat
                Cookie cookie = new Cookie( CookieKeys.ID,
                        PublicEncryptionFactory.encryptString(user.getUserId()));
                cookie.setMaxAge(REMEMBER_ME_DURATION);
                cookie.setPath("/");
                response.addCookie(cookie);//Remember me cookie

            //get the host they requested if any
                Host host = null;
                if(session.getAttribute("flpSite")!=null) {
                    String requestedSite = session.getAttribute("flpSite").toString();
                    host = APILocator.getHostAPI().findByName(requestedSite, user, false); //Request
                }

            session.setAttribute(WebKeys.CTX_PATH,application.getAttribute(WebKeys.CTX_PATH));//setup the path ourselves because this doesnt go through the backend

            //They do not have just one host so just log them in
                if(host==null) {
                    session.setAttribute(WebKeys.REFERER, DEFAULT_REDIRECT_AFTER_GENERAL_LOGIN_PATH);//where we want to go after the login code
                    response.sendRedirect(REDIRECT_PATH);//login path via login page with remember me cookie
                    return true;
                }

            //get ready to rumble
                session.setAttribute(WebKeys.REFERER, DEFAULT_REDIRECT_AFTER_DISTRIBUTOR_LOGIN_PATH);
                session.setAttribute(com.dotmarketing.util.WebKeys.CURRENT_HOST, host);//Log them into their host
                session.setAttribute(com.dotmarketing.util.WebKeys.CMS_SELECTED_HOST_ID,host.getIdentifier());//Set this host as the current host in admin
                session.setAttribute(com.dotmarketing.util.WebKeys.ADMIN_MODE_SESSION, "true");//give them edit panel

            //compute the DIRECTOR_URL... whatever that means. //this fixes a bug that would not allow users to edit items even though they were in edit mode
                session.setAttribute(Globals.LOCALE_KEY, user.getLocale());//set localization
                session.setAttribute(WebKeys.USER_ID,user.getUserId());//log the user in
                PreviewFactory.setVelocityURLS(request);//This sets the path to admin editors

            response.sendRedirect(session.getAttribute(WebKeys.REFERER).toString());
            return true;
        }
        catch(Exception e){
            SecurityLogger.logInfo(GoogleAuthenticator.class, "Error: " +e.toString());
            try {
                response.sendError(401, "Not Authorized");
            }
            catch(Exception ei){
            }
            return false;
        }
    }
}
