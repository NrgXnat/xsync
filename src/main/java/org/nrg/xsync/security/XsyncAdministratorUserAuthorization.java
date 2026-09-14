package org.nrg.xsync.security;

import lombok.extern.slf4j.Slf4j;
import org.nrg.xapi.authorization.AbstractXapiAuthorization;
import org.aspectj.lang.JoinPoint;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.helpers.Roles;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.utils.XsyncUtils;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class XsyncAdministratorUserAuthorization extends AbstractXapiAuthorization {
    @Override
    protected boolean checkImpl(AccessLevel accessLevel, JoinPoint joinPoint, UserI user, HttpServletRequest request) {
        return Roles.checkRole(user, XsyncUtils.XSYNC_ADMINISTRATOR_ROLE);
    }

    @Override
    protected boolean considerGuests() {
        return false;
    }
}
