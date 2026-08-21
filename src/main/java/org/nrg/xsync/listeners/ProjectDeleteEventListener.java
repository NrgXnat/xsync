package org.nrg.xsync.listeners;

import lombok.extern.slf4j.Slf4j;
import org.nrg.config.services.ConfigService;
import org.nrg.framework.constants.Scope;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XsyncXsyncprojectdata;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.XftItemEvent;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xsync.services.local.XsyncConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.bus.Event;
import reactor.bus.EventBus;
import reactor.fn.Consumer;

import java.util.List;

import static reactor.bus.selector.Selectors.type;

@Slf4j
@Component
public class ProjectDeleteEventListener implements Consumer<Event<XftItemEvent>> {

    private final XsyncConfigurationService xsyncConfigurationService;
    private final ConfigService configService;
    private final SaveItemHelper saveItemHelper;

    @Autowired
    public ProjectDeleteEventListener(final XsyncConfigurationService xsyncConfigurationService,
                                      final EventBus eventBus,
                                      final ConfigService configService,
                                      final SaveItemHelper saveItemHelper) {

        this.xsyncConfigurationService = xsyncConfigurationService;
        this.configService = configService;
        this.saveItemHelper = saveItemHelper;
        eventBus.on(type(XftItemEvent.class), this);
    }

    @Override
    public void accept(Event<XftItemEvent> xftItemEventEvent) {
        final XftItemEvent xftItemEvent = xftItemEventEvent.getData();
        if (xftItemEvent == null) {
            return;
        }
        final String xsiType = xftItemEvent.getXsiType();
        final String action = xftItemEvent.getAction();
        if (!(xsiType.equals(XnatProjectdata.SCHEMA_ELEMENT_NAME) && action.equals(XftItemEvent.DELETE))) {
            return;
        }

        final String projectId = xftItemEvent.getId();
        if (projectId == null) {
            return;
        }
        List<XsyncXsyncprojectdata> xsyncElements = xsyncConfigurationService.getAllXsyncElementsForProject(
                Users.getAdminUser(), projectId);

        configService.getAll().stream()
                .filter(c -> c.getTool().equals("xsync"))
                .filter(c-> c.getScope().equals(Scope.Project))
                .filter(c -> c.getEntityId().equals(projectId))
                .forEach(configService::delete);

        for (XsyncXsyncprojectdata xsyncElement : xsyncElements) {
            EventMetaI eventMeta = EventUtils.ADMIN_EVENT(Users.getAdminUser());
            try {
                saveItemHelper.delete(xsyncElement, Users.getAdminUser(), eventMeta);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
