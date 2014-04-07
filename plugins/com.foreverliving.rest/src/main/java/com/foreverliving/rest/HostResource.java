package com.foreverliving.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.liferay.portal.model.User;
import org.quartz.SimpleTrigger;
import com.dotcms.enterprise.HostAssetsJobProxy;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.quartz.QuartzUtils;
import com.dotmarketing.quartz.SimpleScheduledTask;
import com.dotmarketing.quartz.job.HostCopyOptions;
import com.dotcms.rest.*;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.*;

/**
 * Created by jake on 4/3/14.
 */
@Path("/host")
public class HostResource extends WebResource {

    @GET
    @Path("/{domain}")
    @Produces("application/json")
    public Response getHost(@Context HttpServletRequest request, @PathParam("domain") String domain)throws Exception {
        User user=APILocator.getUserAPI().getSystemUser();

        Host source=APILocator.getHostAPI().findByName(domain, user, false);
        return Response.ok(new Gson().toJson(source),MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/{domain}")
    @Produces("application/json")
    public Response createHost(@Context HttpServletRequest request, @PathParam("domain") String domain)throws Exception {
        User user=APILocator.getUserAPI().getSystemUser();

        //does the dest already exists?
        if(APILocator.getHostAPI().findByName(domain, user, false)!=null)
            return Response.status(Response.Status.CONFLICT).build();

        Host host=new Host();
        host.setHostname(domain);
        host.setDefault(false);
        host=APILocator.getHostAPI().save(host, user, false);

        return Response.ok(new Gson().toJson(host),MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/{source}&{dest}")
    @Produces("application/json")
    public Response copyHost(@Context HttpServletRequest request, @PathParam("source") String sourceDomain, @PathParam("dest") String destDomain)throws Exception {
        User user=APILocator.getUserAPI().getSystemUser();

        Host source=APILocator.getHostAPI().findByName(sourceDomain, user, false);

        //makse sure the source exsists
        if(source==null)
            return Response.status(Response.Status.NOT_FOUND).build();

        //does the dest already exists?
        if(APILocator.getHostAPI().findByName(destDomain, user, false)!=null)
            return Response.status(Response.Status.CONFLICT).build();

        Host host=new Host();
        host.setHostname(destDomain);
        host.setDefault(false);
        host=APILocator.getHostAPI().save(host, user, false);

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("sourceHostId", source.getIdentifier());
        parameters.put("destinationHostId", host.getIdentifier());
        parameters.put("copyOptions", new HostCopyOptions(true));

        Calendar startTime = Calendar.getInstance();
        SimpleScheduledTask task = new SimpleScheduledTask("setup-host-" + host.getIdentifier(),
                "setup-host-group", "Setups host " + host.getIdentifier() + " from host " + source.getIdentifier(),
                HostAssetsJobProxy.class.getCanonicalName(),
                false,
                "setup-host-" + source.getIdentifier() + System.currentTimeMillis()+ "-trigger",
                "setup-host-trigger-group", startTime.getTime(),
                null,
                SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT,
                5,
                true,
                parameters,
                0,
                0);

        QuartzUtils.scheduleTask(task);

        return Response.ok(new Gson().toJson(host),MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{domain}")
    public Response deleteHost(@Context HttpServletRequest request, @PathParam("domain") String domain)throws Exception {
        User user=APILocator.getUserAPI().getSystemUser();
        Host host=APILocator.getHostAPI().findByName(domain, user, false);

        if(host == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        //APILocator.getHostAPI().archive(host, user, false);
        APILocator.getHostAPI().delete(host, user, false, true);

        return Response.ok().build();
    }
}
