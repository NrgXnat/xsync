package org.nrg.xsync.initialization.tasks;

import lombok.extern.slf4j.Slf4j;
import org.nrg.xdat.entities.UserRole;
import org.nrg.xdat.security.XDATUser;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xft.schema.XFTManager;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.initialization.tasks.AbstractInitializingTask;
import org.nrg.xnat.initialization.tasks.InitializingTaskException;
import org.nrg.xnat.services.XnatAppInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Map;

@Component
@Slf4j
public class AssignXsyncAdministratorRoleToSiteAdminOnUpgrade extends AbstractInitializingTask {

    @Autowired
    public AssignXsyncAdministratorRoleToSiteAdminOnUpgrade(XnatAppInfo appInfo, RoleHolder roleHolder) {
        super();
        this.appInfo = appInfo;
        this.roleHolder = roleHolder;
    }

    @Override
    public String getTaskName() {
        return "AssignXsyncAdministratorRoleToSiteAdminOnUpgrade";
    }

    @Override
    protected void callImpl() throws InitializingTaskException {
        if (!appInfo.isInitialized() || !XFTManager.isComplete()) {
            throw new InitializingTaskException(InitializingTaskException.Level.RequiresInitialization);
        }
        final Map<String, Collection<String>> rolesAndUsers =  roleHolder.getRolesAndUsers();
        final Collection<String> allSiteAdmins =  rolesAndUsers.get(UserRole.ROLE_ADMINISTRATOR);
        if (noUserIsAssignedXsyncAdministratorRole(rolesAndUsers)) {
            UserI adminUser = Users.getAdminUser();
            try {
                grantXsyncAdministratorRole(adminUser, allSiteAdmins);
            } catch (Exception e) {
                throw new InitializingTaskException(InitializingTaskException.Level.Error);
            }
        }
    }

    private void grantXsyncAdministratorRole(final UserI adminUser, final Collection<String> allSiteAdmins) throws Exception {
        if (!allSiteAdmins.isEmpty()) {
            for (String userName : allSiteAdmins) {
                UserI user = Users.getUser(userName);
                if (user instanceof XDATUser) {
                    if (user.isEnabled()) {
                        if (!((XDATUser) user).checkRole(XSYNC_ADMINISTRATOR_ROLE)) {
                            if (!roleHolder.addRole(adminUser, user, XSYNC_ADMINISTRATOR_ROLE)) {
                                log.error("Could not assign user {} the " + XSYNC_ADMINISTRATOR_ROLE + " role.", userName);
                            }
                        }
                    } else {
                        log.error("User {}  is not enabled. Could not assign the " + XSYNC_ADMINISTRATOR_ROLE + " role.", userName);
                    }
                }
            }
        }
    }

    private boolean noUserIsAssignedXsyncAdministratorRole(final Map<String, Collection<String>> rolesAndUsers) {
        if (rolesAndUsers.containsKey(XSYNC_ADMINISTRATOR_ROLE)) {
            Collection<String> usersWithXsyncAdministrator = rolesAndUsers.get(XSYNC_ADMINISTRATOR_ROLE);
            return CollectionUtils.isEmpty(usersWithXsyncAdministrator);
        }
        return true;
    }

    private final String XSYNC_ADMINISTRATOR_ROLE = "XsyncAdministrator";
    private final XnatAppInfo appInfo;
    private final RoleHolder roleHolder;
}
