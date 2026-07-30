package org.nrg.xsync.security;

import org.aspectj.lang.JoinPoint;
import org.nrg.xapi.authorization.AbstractXapiAuthorization;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.security.helpers.AccessLevel;
import org.nrg.xdat.security.helpers.Permissions;
import org.nrg.xdat.security.helpers.Roles;
import org.nrg.xft.security.UserI;
import org.nrg.xsync.utils.XsyncUtils;

import javax.servlet.http.HttpServletRequest;

public class XsyncDeleteSubjectUserAuthorizer extends AbstractXapiAuthorization {
    //Subject ID must be the first argument within the method signature for this to work correctly
    @Override
    protected boolean checkImpl(AccessLevel accessLevel, JoinPoint joinPoint, UserI user, HttpServletRequest request) {
        try {
            String subjectId = (String) joinPoint.getArgs()[0];
            XnatSubjectdata subjectData = XnatSubjectdata.getXnatSubjectdatasById(subjectId, user, false);
            return (Permissions.canDelete(user, subjectData) || Roles.checkRole(user, XsyncUtils.XSYNC_ADMINISTRATOR_ROLE));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean considerGuests() {
        return false;
    }
}
