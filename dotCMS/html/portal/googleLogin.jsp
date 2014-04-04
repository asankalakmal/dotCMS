<%@page import="com.foreverliving.auth.GoogleAuthenticator"%>
<%@page contentType="text/html;charset=UTF-8" language="java" %>
<%
    /**
     * Created by Jake Liscom on 2014-04-04.
     * Based on https://github.com/mdanter/OAuth2v1
     * Copyright Forever Living 2014
     */
%>
<html>
    <head>
    </head>
    <body>
    <%
        //no cache
            response.setHeader("Pragma", "No-cache");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setDateHeader("Expires", -1);

        //The GoogleAuthenticator handles all the heavy lifting, and contains all "secrets" required for constructing a google login url.
        GoogleAuthenticator helper = new GoogleAuthenticator();

        if (request.getParameter("code") == null || request.getParameter("state") == null) {
            //set the secure state token in session to be able to track what we sent to google
                session.setAttribute("state", helper.getStateToken());

            //Make it redirect
                out.println(String.format("<a href='%s'>log in with google</a>", helper.buildLoginUrl()));
                out.println(String.format("<script>window.location.replace('%s');</script>",helper.buildLoginUrl()));
                //response.sendRedirect(helper.buildLoginUrl());
        }
        else {
            if (request.getParameter("code") != null && request.getParameter("state") != null
                    && request.getParameter("state").equals(session.getAttribute("state"))) {
                session.removeAttribute("state"); //clean up

                //Authenticate them
                    if (!helper.authenticateUser(request, response, session, application)) {
                        out.println("Not authorized");
                        return;
                    }
            }
            else
                response.sendError(500, "Invalid request");
        }
    %>
    </body>
</html>
