package com.foreverliving.rest;

import com.dotmarketing.osgi.GenericBundleActivator;
import org.osgi.framework.BundleContext;
import java.util.Hashtable;
import com.dotcms.rest.config.*;


public class Activator extends GenericBundleActivator {

    /**
     * Start the plugin
     * @param bundleContext
     * @throws Exception
     */
    @Override
    public void start ( BundleContext bundleContext ) throws Exception {
        //Add our resources to the dotCMS rest service
            RestServiceUtil.addResource(HostResource.class);
            RestServiceUtil.addResource(FLPIUserResource.class);
    }

    /**
     * Stop the plugin
     * @param bundleContext
     * @throws Exception
     */
    @Override
    public void stop ( BundleContext bundleContext ) throws Exception {
        //Remove our resources to the dotCMS rest service
            RestServiceUtil.removeResource(HostResource.class);
            RestServiceUtil.removeResource(FLPIUserResource.class);
    }

}