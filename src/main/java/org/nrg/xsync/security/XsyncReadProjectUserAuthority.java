package org.nrg.xsync.security;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.nrg.xapi.authorization.AbstractXapiAuthorization;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.helpers.Permissions;
import org.nrg.xdat.security.helpers.Roles;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class XsyncReadProjectUserAuthority extends AbstractXapiAuthorization {
    //Project ID must be the first argument within the method signature for this to work correctly
    @Override
    protected boolean checkImpl(AccessLevel accessLevel, JoinPoint joinPoint, UserI user, HttpServletRequest request) {
        try {
            String projectId = (String) joinPoint.getArgs()[0];
            return (Permissions.canReadProject(user, projectId) || Roles.checkRole(user, XsyncUtils.XSYNC_ADMINISTRATOR_ROLE));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean considerGuests() {
        return false;
    }
}
