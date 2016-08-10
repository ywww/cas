package org.apereo.cas.web.flow;

import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.RegisteredServiceAccessStrategy;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.UnauthorizedServiceException;
import org.apereo.cas.web.support.ArgumentExtractor;
import org.apereo.cas.web.support.CookieRetrievingCookieGenerator;
import org.apereo.cas.web.support.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;
import org.springframework.webflow.execution.repository.NoSuchFlowExecutionException;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Class to automatically set the paths for the CookieGenerators.
 * <p>
 * Note: This is technically not threadsafe, but because its overriding with a
 * constant value it doesn't matter.
 * <p>
 * Note: As of CAS 3.1, this is a required class that retrieves and exposes the
 * values in the two cookies for subclasses to use.
 *
 * @author Scott Battaglia
 * @since 3.1
 */
public class InitialFlowSetupAction extends AbstractAction {
    private transient Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private ServicesManager servicesManager;

    private CookieRetrievingCookieGenerator warnCookieGenerator;
    
    private CookieRetrievingCookieGenerator ticketGrantingTicketCookieGenerator;
    
    private List<ArgumentExtractor> argumentExtractors;

    private boolean trackGeoLocation;

    private String googleAnalyticsTrackingId;

    /**
     * If no authentication request from a service is present, halt and warn the user.
     */
    private boolean enableFlowOnAbsentServiceRequest = true;

    private boolean staticAuthentication;

    @Override
    protected Event doExecute(final RequestContext context) throws Exception {
        configureCookieGenerators(context);
        configureWebflowContext(context);
        configureWebflowContextForService(context);
        return result("success");
    }

    private void configureWebflowContextForService(final RequestContext context) {
        final Service service = WebUtils.getService(this.argumentExtractors, context);
        if (service != null) {
            logger.debug("Placing service in context scope: [{}]", service.getId());

            final RegisteredService registeredService = this.servicesManager.findServiceBy(service);
            if (registeredService != null && registeredService.getAccessStrategy().isServiceAccessAllowed()) {
                logger.debug("Placing registered service [{}] with id [{}] in context scope",
                        registeredService.getServiceId(),
                        registeredService.getId());
                WebUtils.putRegisteredService(context, registeredService);

                final RegisteredServiceAccessStrategy accessStrategy = registeredService.getAccessStrategy();
                if (accessStrategy.getUnauthorizedRedirectUrl() != null) {
                    logger.debug("Placing registered service's unauthorized redirect url [{}] with id [{}] in context scope",
                            accessStrategy.getUnauthorizedRedirectUrl(),
                            registeredService.getServiceId());
                    WebUtils.putUnauthorizedRedirectUrl(context, accessStrategy.getUnauthorizedRedirectUrl());
                }
            }
        } else if (!this.enableFlowOnAbsentServiceRequest) {
            logger.warn("No service authentication request is available at [{}]. CAS is configured to disable the flow.",
                    WebUtils.getHttpServletRequest(context).getRequestURL());
            throw new NoSuchFlowExecutionException(context.getFlowExecutionContext().getKey(),
                    new UnauthorizedServiceException("screen.service.required.message", "Service is required"));
        }
        WebUtils.putService(context, service);
    }

    private void configureWebflowContext(final RequestContext context) {
        final HttpServletRequest request = WebUtils.getHttpServletRequest(context);
        WebUtils.putTicketGrantingTicketInScopes(context,
                this.ticketGrantingTicketCookieGenerator.retrieveCookieValue(request));
        WebUtils.putGoogleAnalyticsTrackingIdIntoFlowScope(context, this.googleAnalyticsTrackingId);
        WebUtils.putWarningCookie(context,
                Boolean.valueOf(this.warnCookieGenerator.retrieveCookieValue(request)));
        WebUtils.putGeoLocationTrackingIntoFlowScope(context, this.trackGeoLocation);
        WebUtils.putStaticAuthenticationIntoFlowScope(context, this.staticAuthentication);
    }

    private void configureCookieGenerators(final RequestContext context) {
        final String contextPath = context.getExternalContext().getContextPath();
        final String cookiePath = StringUtils.isNotBlank(contextPath) ? contextPath + '/' : "/";

        if (StringUtils.isBlank(this.warnCookieGenerator.getCookiePath())) {
            logger.info("Setting path for cookies for warn cookie generator to: {} ", cookiePath);
            this.warnCookieGenerator.setCookiePath(cookiePath);
        } else {
            logger.debug("Warning cookie path is set to {} and path {}", this.warnCookieGenerator.getCookieDomain(),
                    this.warnCookieGenerator.getCookiePath());
        }
        if (StringUtils.isBlank(this.ticketGrantingTicketCookieGenerator.getCookiePath())) {
            logger.debug("Setting path for cookies for TGC cookie generator to: {} ", cookiePath);
            this.ticketGrantingTicketCookieGenerator.setCookiePath(cookiePath);
        } else {
            logger.debug("TGC cookie path is set to {} and path {}", this.ticketGrantingTicketCookieGenerator.getCookieDomain(),
                    this.ticketGrantingTicketCookieGenerator.getCookiePath());
        }
    }

    public void setTicketGrantingTicketCookieGenerator(final CookieRetrievingCookieGenerator t) {
        this.ticketGrantingTicketCookieGenerator = t;
    }

    public void setWarnCookieGenerator(final CookieRetrievingCookieGenerator warnCookieGenerator) {
        this.warnCookieGenerator = warnCookieGenerator;
    }

    public void setArgumentExtractors(final List<ArgumentExtractor> argumentExtractors) {
        this.argumentExtractors = argumentExtractors;
    }

    public void setServicesManager(final ServicesManager servicesManager) {
        this.servicesManager = servicesManager;
    }

    public void setEnableFlowOnAbsentServiceRequest(final boolean enableFlowOnAbsentServiceRequest) {
        this.enableFlowOnAbsentServiceRequest = enableFlowOnAbsentServiceRequest;
    }

    public void setTrackGeoLocation(final boolean trackGeoLocation) {
        this.trackGeoLocation = trackGeoLocation;
    }

    public void setGoogleAnalyticsTrackingId(final String googleAnalyticsTrackingId) {
        this.googleAnalyticsTrackingId = googleAnalyticsTrackingId;
    }

    public void setStaticAuthentication(final boolean staticAuthentication) {
        this.staticAuthentication = staticAuthentication;
    }
}
